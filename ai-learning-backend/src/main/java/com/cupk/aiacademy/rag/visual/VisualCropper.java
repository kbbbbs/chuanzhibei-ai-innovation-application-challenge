package com.cupk.aiacademy.rag.visual;

import com.cupk.aiacademy.rag.RagAccessException;
import com.cupk.aiacademy.rag.ocr.PdfPageRenderer;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

/** Crops only validated source coordinates. A whole page is an explicit fallback, never a detected local figure. */
public final class VisualCropper {
    public static final int MAX_IMAGE_BYTES=1_048_576;
    private final PdfPageRenderer slots=new PdfPageRenderer();
    public record Cropped(byte[] png,int width,int height) { }

    public Cropped fullPage(byte[] source) { return crop(source,List.of(0,0,999,999)); }
    public Cropped crop(byte[] source,List<Integer> bbox) {
        if(source==null || source.length==0 || source.length>10*1024*1024)throw invalid("图片输入超过处理上限");
        if(bbox==null || bbox.size()!=4 || bbox.stream().anyMatch(v->v==null || v<0 || v>999)
                || bbox.get(0)>=bbox.get(2) || bbox.get(1)>=bbox.get(3))throw invalid("图片区域坐标无效");
        int[] size=new int[2];
        byte[] png=slots.withSlot(()->cropLocked(source,bbox,size));
        return new Cropped(png,size[0],size[1]);
    }

    private byte[] cropLocked(byte[] encoded,List<Integer> bbox,int[] size) {
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(encoded))) {
            if(input==null)throw invalid("无法读取原页图像");
            var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw invalid("无法读取原页图像");
            var reader=readers.next();
            try {
                reader.setInput(input,true,true);int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<1 || height<1 || width>6000 || height>6000 || (long)width*height>8_000_000)
                    throw invalid("原页图片超过像素上限");
                int x=(int)Math.floor(bbox.get(0)/999.0*width),y=(int)Math.floor(bbox.get(1)/999.0*height);
                int right=(int)Math.ceil(bbox.get(2)/999.0*width),bottom=(int)Math.ceil(bbox.get(3)/999.0*height);
                if(right-x<8 || bottom-y<8)throw invalid("图片区域过小，无法可靠裁剪");
                var page=reader.read(0);
                try {
                    double scale=Math.min(1,1600.0/Math.max(right-x,bottom-y));
                    int cropWidth=Math.max(1,(int)Math.round((right-x)*scale)),cropHeight=Math.max(1,(int)Math.round((bottom-y)*scale));
                    for(int attempt=0;attempt<8;attempt++) {
                        var crop=new BufferedImage(cropWidth,cropHeight,BufferedImage.TYPE_INT_RGB);
                        try {
                            var graphics=crop.createGraphics();
                            try {
                                graphics.setColor(Color.WHITE);graphics.fillRect(0,0,cropWidth,cropHeight);
                                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                                graphics.drawImage(page,0,0,cropWidth,cropHeight,x,y,right,bottom,null);
                            } finally {graphics.dispose();}
                            byte[] result=encode(crop);
                            if(result.length<=MAX_IMAGE_BYTES){size[0]=cropWidth;size[1]=cropHeight;return result;}
                        } finally {crop.flush();}
                        cropWidth=(int)Math.floor(cropWidth*.75);cropHeight=(int)Math.floor(cropHeight*.75);
                        if(cropWidth<8 || cropHeight<8)break;
                    }
                    throw invalid("裁剪图片仍超过保存上限");
                } finally {page.flush();}
            } finally {reader.dispose();}
        } catch(RagAccessException failure){throw failure;}
        catch(Exception failure){throw invalid("原页图片读取或裁剪失败");}
    }
    private byte[] encode(BufferedImage image) throws java.io.IOException {
        try(var output=new ByteArrayOutputStream()) {
            if(!ImageIO.write(image,"png",output))throw invalid("PNG编码器不可用");
            return output.toByteArray();
        }
    }
    private static RagAccessException invalid(String message){return new RagAccessException("400",message);}
}
