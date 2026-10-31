package com.cupk.aiacademy.rag.ocr;

import com.cupk.aiacademy.rag.RagAccessException;
import com.cupk.aiacademy.rag.ingestion.RagPageExtractor;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.junit.jupiter.api.Test;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RenderResourceGuardTest {
    @Test void allRendererInstancesShareAtMostTwoConcurrentRasterSlots() throws Exception {
        var entered=new CountDownLatch(2);var release=new CountDownLatch(1);
        var active=new AtomicInteger();var maximum=new AtomicInteger();
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var futures=new ArrayList<Future<byte[]>>();
            for(int i=0;i<6;i++) {
                var renderer=new PdfPageRenderer();
                futures.add(executor.submit(()->renderer.withSlot(()->{
                    int count=active.incrementAndGet();maximum.accumulateAndGet(count,Math::max);entered.countDown();
                    try {if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("test latch timed out");}
                    catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}
                    finally {active.decrementAndGet();}
                    return new byte[]{1};
                })));
            }
            try {assertTrue(entered.await(3,TimeUnit.SECONDS));Thread.sleep(50);assertEquals(2,maximum.get());}
            finally {release.countDown();}
            for(var future:futures)assertArrayEquals(new byte[]{1},future.get(5,TimeUnit.SECONDS));
            assertEquals(2,maximum.get());
        }
    }

    @Test void pptxRenderingUsesTheSameGateInsteadOfAllocatingOutsideIt() throws Exception {
        var renderer=spy(new PdfPageRenderer());var entered=new AtomicInteger();
        doAnswer(call->{entered.incrementAndGet();return call.callRealMethod();}).when(renderer).withSlot(any());
        try(var show=new XMLSlideShow();var output=new ByteArrayOutputStream()) {
            show.createSlide().createTextBox().setText("render gate test");show.write(output);
            try(var document=new RagPageExtractor(renderer).open(output.toByteArray(),"gate.pptx")) {
                assertTrue(document.renderPng(1).length>0);assertEquals(1,entered.get());
            }
        }
    }

    @Test void pdfPreviewChecksOversizedSourceImagesBeforeCallingTheRasterizer() throws Exception {
        var renderer=mock(PdfPageRenderer.class);
        when(renderer.render(any(PDDocument.class),anyInt())).thenThrow(new AssertionError("Oversized source must be rejected before rasterization"));
        try(var pdf=new PDDocument();var output=new ByteArrayOutputStream()) {
            var page=new PDPage();pdf.addPage(page);
            var picture=LosslessFactory.createFromImage(pdf,new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB));
            picture.getCOSObject().setInt(COSName.WIDTH,100_000);picture.getCOSObject().setInt(COSName.HEIGHT,100_000);
            try(var content=new PDPageContentStream(pdf,page)){content.drawImage(picture,0,0,100,100);}
            pdf.save(output);
            try(var document=new RagPageExtractor(renderer).open(output.toByteArray(),"oversized-image.pdf")) {
                assertThrows(RagAccessException.class,()->document.renderPng(1));
                verify(renderer,never()).render(any(PDDocument.class),anyInt());
            }
        }
    }

    @Test void pptxDeclaredImageSizeIsCheckedWithoutDecodingTheGiantRaster() throws Exception {
        byte[] picture;
        try(var output=new ByteArrayOutputStream()) {ImageIO.write(new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB),"png",output);picture=output.toByteArray();}
        try(var show=new XMLSlideShow();var output=new ByteArrayOutputStream()) {
            var slide=show.createSlide();var shape=slide.createPicture(show.addPicture(picture,PictureData.PictureType.PNG));
            shape.setAnchor(new Rectangle2D.Double(0,0,100,100));show.write(output);
            // Patch the packaged image only after POI's createPicture autosizing has handled the genuine 1x1 image.
            ByteBuffer.wrap(picture).putInt(16,100_000).putInt(20,100_000);
            var crc=new CRC32();crc.update(picture,12,17);ByteBuffer.wrap(picture).putInt(29,(int)crc.getValue());
            byte[] malicious;
            try(var zipped=new ZipInputStream(new java.io.ByteArrayInputStream(output.toByteArray()));
                var rewritten=new ByteArrayOutputStream();var writer=new ZipOutputStream(rewritten)) {
                ZipEntry entry;
                while((entry=zipped.getNextEntry())!=null) {
                    writer.putNextEntry(new ZipEntry(entry.getName()));
                    writer.write(entry.getName().startsWith("ppt/media/")?picture:zipped.readAllBytes());writer.closeEntry();
                }
                writer.finish();malicious=rewritten.toByteArray();
            }
            try(var document=new RagPageExtractor().open(malicious,"oversized-image.pptx")) {
                assertThrows(RagAccessException.class,()->document.page(1));
            }
        }
    }
}
