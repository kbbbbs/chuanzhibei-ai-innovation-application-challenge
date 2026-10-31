package com.cupk.aiacademy.rag.ingestion;

import com.cupk.aiacademy.rag.RagAccessException;
import com.cupk.aiacademy.rag.ocr.PdfPageRenderer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.*;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** Owns one document at a time. Text extraction and rendering are both page-local and 1-based. */
@Component
public class RagPageExtractor {
    public static final int MAX_BYTES=20*1024*1024,MAX_PAGES=200,MAX_TEXT=1_000_000;
    public record SourcePage(int unitNo,Integer pageNumber,String text,boolean hasImages,double imageCoverage,Integer charStart,Integer charEnd,boolean formulaHint) {
        public SourcePage(int unitNo,Integer pageNumber,String text,boolean hasImages,double imageCoverage,Integer charStart,Integer charEnd) {
            this(unitNo,pageNumber,text,hasImages,imageCoverage,charStart,charEnd,false);
        }
    }
    private static final class MathAwareStripper extends PDFTextStripper {
        boolean formulaHint;
        @Override protected void processTextPosition(org.apache.pdfbox.text.TextPosition position) {
            String font=position.getFont()==null?"":position.getFont().getName().toLowerCase(Locale.ROOT);
            String glyph=position.getUnicode()==null?"":position.getUnicode().strip();
            if(isMathFontGlyph(font,glyph))formulaHint=true;
            super.processTextPosition(position);
        }
    }
    static boolean isMathFontGlyph(String font,String glyph) {
        String name=font==null?"":font.toLowerCase(Locale.ROOT),value=glyph==null?"":glyph.strip();
        // Verified source-page Symbol list bullets. Do not exclude the entire private-use range.
        boolean symbolBullet=name.contains("symbol") && value.matches("[\uF0A8\uF0B7]+");
        return name.matches(".*(?:cambria.?math|symbol|stix|cmsy|cmex|cmmi).*") && !value.isEmpty()
                && !symbolBullet && !value.matches("[•·∙▪▫◦●○■□◆◇➢►▶–—-]+");
    }
    private final PdfPageRenderer renderer;
    public RagPageExtractor(){this(new PdfPageRenderer());}
    @Autowired public RagPageExtractor(PdfPageRenderer renderer){this.renderer=renderer;}

    public Document open(byte[] bytes,String filename) {
        if(bytes==null || bytes.length==0 || bytes.length>MAX_BYTES)throw invalid("文件不能为空且不能超过20 MB");
        if(filename==null)throw invalid("文件名不能为空");
        int dot=filename.lastIndexOf('.');String extension=dot<0?"":filename.substring(dot+1).toLowerCase(Locale.ROOT);
        try {
            return switch(extension) {
                case "txt","md" -> new Document(extension.equals("md")?"text/markdown":"text/plain",decode(bytes),false,null,null);
                case "pdf" -> pdf(bytes);
                case "docx" -> word(bytes);
                case "pptx" -> slides(bytes);
                default -> throw invalid("支持PDF、DOCX、PPTX、MD和UTF-8 TXT文件；不支持旧DOC/PPT");
            };
        } catch(RagAccessException failure){throw failure;}
        catch(Exception failure){throw invalid("文件解析失败，请检查格式或文件是否损坏");}
    }
    private Document pdf(byte[] bytes) throws IOException {
        PDDocument document=Loader.loadPDF(bytes);boolean accepted=false;
        try {
            if(document.isEncrypted() || !document.getCurrentAccessPermission().canExtractContent())throw invalid("PDF已加密或禁止提取内容");
            if(document.getNumberOfPages()<1 || document.getNumberOfPages()>MAX_PAGES)throw invalid("PDF必须为1至200页");
            accepted=true;return new Document("application/pdf",null,false,document,null);
        } finally {if(!accepted)document.close();}
    }
    private Document word(byte[] bytes) throws IOException {
        validateZip(bytes);
        try(var word=new XWPFDocument(new ByteArrayInputStream(bytes));var extractor=new XWPFWordExtractor(word)) {
            return new Document("application/vnd.openxmlformats-officedocument.wordprocessingml.document",clean(extractor.getText()),!word.getAllPictures().isEmpty(),null,null);
        }
    }
    private Document slides(byte[] bytes) throws IOException {
        validateZip(bytes);var slides=new XMLSlideShow(new ByteArrayInputStream(bytes));boolean accepted=false;
        try {
            if(slides.getSlides().isEmpty() || slides.getSlides().size()>MAX_PAGES)throw invalid("幻灯片必须为1至200页");
            accepted=true;return new Document("application/vnd.openxmlformats-officedocument.presentationml.presentation",null,false,null,slides);
        } finally {if(!accepted)slides.close();}
    }

    public final class Document implements AutoCloseable {
        private final String mimeType,text;
        private final boolean wordImages;
        private final PDDocument pdf;
        private final XMLSlideShow slides;
        private final Map<Integer,SourcePage> pages=new HashMap<>();
        private long extractedCharacters;
        private boolean closed;
        private Document(String mime,String text,boolean wordImages,PDDocument pdf,XMLSlideShow slides) {
            this.mimeType=mime;this.text=text;this.wordImages=wordImages;this.pdf=pdf;this.slides=slides;
            if(text!=null && text.length()>MAX_TEXT)throw invalid("提取文本超过100万字上限");
        }
        public String mimeType(){return mimeType;}
        public int pageCount(){return pdf!=null?pdf.getNumberOfPages():slides!=null?slides.getSlides().size():0;}
        public int unitCount(){return Math.max(1,pageCount());}
        public String locationType(){return pdf!=null?"PAGE":slides!=null?"SLIDE":"CHARACTER";}
        public SourcePage page(int unitNo) {
            check(unitNo);var cached=pages.get(unitNo);if(cached!=null)return cached;
            try {
                SourcePage page;
                if(pdf!=null) {
                    var stripper=new MathAwareStripper();stripper.setSortByPosition(true);stripper.setStartPage(unitNo);stripper.setEndPage(unitNo);
                    String value=clean(stripper.getText(pdf));var original=pdf.getPage(unitNo-1);
                    var graphics=new ImageCoverage(original);graphics.processPage(original);double coverage=graphics.coverage();
                    boolean visuals=coverage>0 || (value.isBlank() && original.hasContents());
                    page=new SourcePage(unitNo,unitNo,value,visuals,coverage,null,null,stripper.formulaHint);
                } else if(slides!=null) {
                    var slide=slides.getSlides().get(unitNo-1);var value=new StringBuilder();double imageArea=0;
                    for(var shape:slide.getShapes())imageArea+=slideContent(shape,value,new AffineTransform(),0);
                    double coverage=Math.min(1,imageArea/(slides.getPageSize().getWidth()*slides.getPageSize().getHeight()));
                    String cleaned=clean(value.toString());
                    page=new SourcePage(unitNo,unitNo,cleaned,coverage>0 || cleaned.isBlank(),coverage,null,null);
                } else page=new SourcePage(unitNo,null,text,wordImages,wordImages && text.isBlank()?1:0,0,text.length());
                extractedCharacters+=page.text().length();if(extractedCharacters>MAX_TEXT)throw invalid("提取文本超过100万字上限");
                pages.put(unitNo,page);return page;
            } catch(RagAccessException failure){throw failure;}
            catch(Exception failure){throw invalid("页面文字提取失败");}
        }
        public byte[] renderPng(int unitNo) {
            check(unitNo);
            // Preview callers must execute the same source-image safety checks as ingestion workers.
            page(unitNo);
            if(pdf!=null)return renderer.render(pdf,unitNo);
            if(slides==null)throw invalid("该格式没有可渲染的真实页面，图片内容需要人工核验");
            return renderer.withSlot(()->renderSlide(unitNo));
        }
        private byte[] renderSlide(int unitNo) {
            var size=slides.getPageSize();double scale=renderer.scale(size.getWidth(),size.getHeight());
            BufferedImage image=new BufferedImage(Math.max(1,(int)(size.getWidth()*scale)),Math.max(1,(int)(size.getHeight()*scale)),BufferedImage.TYPE_INT_RGB);
            var graphics=image.createGraphics();
            try {
                graphics.setColor(Color.WHITE);graphics.fillRect(0,0,image.getWidth(),image.getHeight());graphics.scale(scale,scale);
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                slides.getSlides().get(unitNo-1).draw(graphics);return renderer.encode(image);
            } catch(RagAccessException failure){throw failure;}
            catch(Exception failure){throw invalid("幻灯片渲染失败");}
            finally{graphics.dispose();image.flush();}
        }
        private void check(int unit){if(closed)throw new IllegalStateException("Document source already closed");if(unit<1 || unit>unitCount())throw invalid("非法页面或段落编号");}
        @Override public void close() throws IOException {closed=true;if(pdf!=null)pdf.close();if(slides!=null)slides.close();pages.clear();}
    }

    private static double slideContent(XSLFShape shape,StringBuilder text,AffineTransform transform,int depth) {
        return slideContent(shape,text,transform,depth,true);
    }
    private static double slideContent(XSLFShape shape,StringBuilder text,AffineTransform transform,int depth,boolean geometryKnown) {
        if(depth>32)throw invalid("幻灯片图形嵌套过深");
        double images=0;
        if(shape instanceof XSLFTextShape value)text.append(value.getText()).append('\n');
        else if(shape instanceof XSLFTable table) {
            for(var row:table.getRows()){for(var cell:row.getCells())text.append(cell.getText()).append('\t');text.append('\n');}
        } else if(shape instanceof XSLFGroupShape group) {
            Rectangle2D outer=null,inner=null;var child=new AffineTransform(transform);
            // POI's getters dereference optional group transform fields. Text/table traversal does not require them.
            try {outer=group.getAnchor();inner=group.getInteriorAnchor();}
            catch(RuntimeException missingGeometry){/* Preserve readable child text; pictures need explicit geometry below. */}
            boolean childGeometry=geometryKnown && validGeometry(outer) && validGeometry(inner);
            if(childGeometry){child.translate(outer.getX(),outer.getY());child.scale(outer.getWidth()/inner.getWidth(),outer.getHeight()/inner.getHeight());child.translate(-inner.getX(),-inner.getY());}
            for(var item:group.getShapes())images+=slideContent(item,text,child,depth+1,childGeometry);
        } else if(shape instanceof XSLFPictureShape picture) {
            validatePictureHeader(picture);
            var anchor=picture.getAnchor();
            if(!geometryKnown || !validGeometry(anchor))throw invalid("幻灯片图片缺少可用几何位置，请人工核验该页");
            var bounds=transform.createTransformedShape(anchor).getBounds2D();images=Math.abs(bounds.getWidth()*bounds.getHeight());
        }
        if(text.length()>MAX_TEXT)throw invalid("幻灯片文本超过100万字上限");return images;
    }
    private static boolean validGeometry(Rectangle2D value) {
        return value!=null && Double.isFinite(value.getX()) && Double.isFinite(value.getY())
                && Double.isFinite(value.getWidth()) && Double.isFinite(value.getHeight()) && value.getWidth()>0 && value.getHeight()>0;
    }
    /** Header dimensions only: POI's getImageDimension helpers decode the entire raster before returning. */
    private static void validatePictureHeader(XSLFPictureShape picture) {
        try(var input=new MemoryCacheImageInputStream(new ByteArrayInputStream(picture.getPictureData().getData()))) {
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext())throw invalid("嵌入图片格式不能安全核验尺寸，请人工核验该页");
            var reader=readers.next();
            try {
                reader.setInput(input,true,true);int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<=0 || height<=0 || (long)width*height>50_000_000)throw invalid("源图片超过像素安全上限");
            } finally {reader.dispose();}
        } catch(RagAccessException failure){throw failure;}
        catch(Exception failure){throw invalid("嵌入图片尺寸无法安全解析");}
    }
    private static String decode(byte[] bytes) throws CharacterCodingException {
        String value=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        return clean(value.startsWith("\ufeff")?value.substring(1):value);
    }
    private static String clean(String text){return text.replace("\u0000","").replace("\r\n","\n").replace('\r','\n').strip();}
    private static void validateZip(byte[] bytes) throws IOException {
        try(var input=new ZipInputStream(new ByteArrayInputStream(bytes))){long expanded=0;int entries=0;byte[] buffer=new byte[8192];
            while(input.getNextEntry()!=null){if(++entries>10_000)throw invalid("压缩文档条目过多");int read;
                while((read=input.read(buffer))!=-1){expanded+=read;if(expanded>64L*1024*1024)throw invalid("文档解压后超过64 MB上限");}}
        }
    }
    /** PDF graphics operator traversal measures actual image placement, including nested forms. */
    private static final class ImageCoverage extends PDFGraphicsStreamEngine {
        private final double pageArea;private double imageArea;private Point2D current=new Point2D.Float();private int operations;
        ImageCoverage(PDPage page){super(page);var box=page.getCropBox();pageArea=Math.abs((double)box.getWidth()*box.getHeight());}
        double coverage(){return pageArea<=0?0:Math.min(1,imageArea/pageArea);}
        private void step() {if(++operations>1_000_000)throw invalid("页面绘图复杂度超过安全上限");}
        @Override public void drawImage(PDImage image) throws IOException {
            step();int width=image.getWidth(),height=image.getHeight();
            // PDFBox intentionally suppresses IOException from the Do operator; a policy refusal must escape it.
            if(width<=0 || height<=0 || (long)width*height>50_000_000)throw invalid("源图片超过像素安全上限");
            var matrix=getGraphicsState().getCurrentTransformationMatrix();imageArea+=Math.abs(matrix.getScaleX()*matrix.getScaleY()-matrix.getShearX()*matrix.getShearY());
        }
        @Override public void appendRectangle(Point2D a,Point2D b,Point2D c,Point2D d) throws IOException {step();current=a;}
        @Override public void moveTo(float x,float y) throws IOException {step();current=new Point2D.Float(x,y);}
        @Override public void lineTo(float x,float y) throws IOException {step();current=new Point2D.Float(x,y);}
        @Override public void curveTo(float a,float b,float c,float d,float e,float f) throws IOException {step();current=new Point2D.Float(e,f);}
        @Override public Point2D getCurrentPoint(){return current;}
        @Override public void closePath() throws IOException {step();}
        @Override public void endPath() throws IOException {step();}
        @Override public void strokePath() throws IOException {step();}
        @Override public void fillPath(int rule) throws IOException {step();}
        @Override public void fillAndStrokePath(int rule) throws IOException {step();}
        @Override public void clip(int rule) throws IOException {step();}
        @Override public void shadingFill(COSName name) throws IOException {step();}
    }
    private static RagAccessException invalid(String message){return new RagAccessException("400",message);}
}
