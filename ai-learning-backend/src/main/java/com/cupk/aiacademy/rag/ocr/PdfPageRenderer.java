package com.cupk.aiacademy.rag.ocr;

import com.cupk.aiacademy.rag.RagAccessException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

@Component
public class PdfPageRenderer {
    private static final Semaphore RENDERS=new Semaphore(2,true);
    private final int dpi,maxDimension;private final long maxPixels;
    public PdfPageRenderer(){this(144,8_000_000,4096);}
    @Autowired public PdfPageRenderer(@Value("${rag.ocr.render-dpi:144}") int dpi,
            @Value("${rag.ocr.max-render-pixels:8000000}") long maxPixels,
            @Value("${rag.ocr.max-render-dimension:4096}") int maxDimension) {
        this.dpi=Math.max(72,Math.min(300,dpi));this.maxPixels=Math.max(100_000,Math.min(16_000_000,maxPixels));
        this.maxDimension=Math.max(300,Math.min(6000,maxDimension));
    }
    public byte[] render(PDDocument document,int pageNumber) {
        if(pageNumber<1 || pageNumber>document.getNumberOfPages())throw invalid("非法页码");
        var page=document.getPage(pageNumber-1);var box=page.getCropBox();
        double width=Math.abs(box.getWidth()),height=Math.abs(box.getHeight());
        double scale=scale(width,height);
        return withSlot(()->{
            try {
                var renderer=new PDFRenderer(document);renderer.setSubsamplingAllowed(true);
                BufferedImage image=renderer.renderImageWithDPI(pageNumber-1,(float)(72*scale),ImageType.RGB);
                try{return encode(image);}finally{image.flush();}
            } catch(RagAccessException failure){throw failure;}
            catch(Exception failure){throw invalid("页面渲染失败");}
        });
    }
    /** Shared across PDF, PPTX, all renderer instances and foreground/background callers. */
    public byte[] withSlot(Supplier<byte[]> action) {
        boolean acquired=false;
        try {RENDERS.acquire();acquired=true;return action.get();}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw invalid("页面渲染已取消");}
        finally{if(acquired)RENDERS.release();}
    }
    public double scale(double width,double height) {
        if(!Double.isFinite(width) || !Double.isFinite(height) || width<=0 || height<=0 || width>20_000 || height>20_000)throw invalid("页面尺寸超出安全范围");
        return Math.min(dpi/72.0,Math.min(maxDimension/Math.max(width,height),Math.sqrt(maxPixels/(width*height))));
    }
    public byte[] encode(BufferedImage image) {
        if((long)image.getWidth()*image.getHeight()>maxPixels || Math.max(image.getWidth(),image.getHeight())>maxDimension)
            throw invalid("页面图像超过像素上限");
        try(var output=new ByteArrayOutputStream()) {
            if(!ImageIO.write(image,"png",output))throw invalid("PNG编码器不可用");
            if(output.size()>10*1024*1024)throw invalid("页面图像超过OCR接口容量");
            return output.toByteArray();
        } catch(java.io.IOException failure){throw invalid("页面图像编码失败");}
    }
    /** Only a virtually white image is considered blank; uncertain scans must still be recognized/reviewed. */
    public boolean isVisuallyBlank(byte[] png) {
        try {
            BufferedImage image=ImageIO.read(new ByteArrayInputStream(png));if(image==null)return false;
            try {
                int stride=Math.max(1,(int)Math.sqrt((long)image.getWidth()*image.getHeight()/250_000.0));
                long sampled=0,ink=0;
                for(int y=0;y<image.getHeight();y+=stride)for(int x=0;x<image.getWidth();x+=stride) {
                    sampled++;int rgb=image.getRGB(x,y);
                    if(((rgb>>16)&255)<235 || ((rgb>>8)&255)<235 || (rgb&255)<235)ink++;
                }
                return ink<=Math.max(2,sampled/20_000);
            } finally {image.flush();}
        } catch(Exception failure){return false;}
    }
    private static RagAccessException invalid(String message){return new RagAccessException("400",message);}
}
