package com.cupk.aiacademy.rag.visual;

import com.cupk.aiacademy.rag.RagAccessException;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VisualCropperTest {
    @Test void cropsNormalizedCoordinatesAgainstActualOriginalPixels() throws Exception {
        var source=new BufferedImage(1000,600,BufferedImage.TYPE_INT_RGB);
        var g=source.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,1000,600);g.setColor(Color.RED);g.fillRect(0,0,500,600);g.dispose();
        var crop=new VisualCropper().crop(png(source),List.of(0,0,499,999));
        assertTrue(crop.width()>=499 && crop.width()<=500);assertEquals(600,crop.height());
        var image=ImageIO.read(new ByteArrayInputStream(crop.png()));assertEquals(Color.RED.getRGB(),image.getRGB(100,100));
        assertTrue(crop.png().length<=1_048_576);
    }

    @Test void fullPageFallbackDoesNotPretendToBeALocalRegionAndLargeNoiseIsBounded() throws Exception {
        var source=new BufferedImage(1800,1200,BufferedImage.TYPE_INT_RGB);var random=new java.util.Random(7);
        for(int y=0;y<source.getHeight();y++)for(int x=0;x<source.getWidth();x++)source.setRGB(x,y,random.nextInt(1<<24));
        var result=new VisualCropper().fullPage(png(source));
        assertTrue(result.png().length<=1_048_576);assertTrue(result.width()<=1800);assertTrue(result.height()<=1200);
        assertTrue(result.width()>0 && result.height()>0);
    }

    @Test void invalidImagesOrCoordinateRangesFailBeforeUnsafeDecoding() throws Exception {
        var cropper=new VisualCropper();byte[] source=png(new BufferedImage(100,100,BufferedImage.TYPE_INT_RGB));
        assertThrows(RagAccessException.class,()->cropper.crop(source,List.of(50,50,10,90)));
        assertThrows(RagAccessException.class,()->cropper.crop(source,List.of(0,0,1000,999)));
        assertThrows(RagAccessException.class,()->cropper.fullPage(new byte[]{1,2,3}));
        assertThrows(RagAccessException.class,()->cropper.fullPage(new byte[10*1024*1024+1]));
        byte[] oversized=png(new BufferedImage(6001,1,BufferedImage.TYPE_INT_RGB));
        assertThrows(RagAccessException.class,()->cropper.fullPage(oversized));
    }

    private byte[] png(BufferedImage image) throws Exception {
        try(var out=new ByteArrayOutputStream()){ImageIO.write(image,"png",out);return out.toByteArray();}
        finally {image.flush();}
    }
}
