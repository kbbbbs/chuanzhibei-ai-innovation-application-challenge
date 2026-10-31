package com.cupk.aiacademy.rag.ingestion;

import com.cupk.aiacademy.rag.RagAccessException;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class RagPageExtractorTest {
    @Test void mixedPdfKeepsRealPagesAndMeasuresDrawnImageCoverage() throws Exception {
        try(var pdf=new PDDocument();var output=new ByteArrayOutputStream()) {
            var page=new PDPage();pdf.addPage(page);pdf.addPage(new PDPage());
            var image=new BufferedImage(100,100,BufferedImage.TYPE_INT_RGB);
            try(var content=new PDPageContentStream(pdf,page)) {
                content.beginText();content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);
                content.newLineAtOffset(30,700);content.showText("Page heading");content.endText();
                content.drawImage(LosslessFactory.createFromImage(pdf,image),40,100,400,400);
            }
            pdf.save(output);
            try(var source=new RagPageExtractor().open(output.toByteArray(),"mixed.pdf")) {
                assertEquals(2,source.pageCount());assertEquals(2,source.unitCount());assertEquals("PAGE",source.locationType());
                var first=source.page(1);assertEquals(1,first.pageNumber());assertTrue(first.text().contains("Page heading"));
                assertTrue(first.hasImages());assertTrue(first.imageCoverage()>.3);assertNull(first.charStart());
                assertTrue(source.page(2).text().isBlank());assertFalse(source.page(2).hasImages());
                assertThrows(RagAccessException.class,()->source.page(0));
                assertTrue(source.renderPng(1).length>0);
            }
        }
    }

    @Test void textAndWordHaveCharacterLocationsInsteadOfInventedPageNumbers() throws Exception {
        String text="课程标题\n梯度下降更新模型参数。";
        try(var source=new RagPageExtractor().open(text.getBytes(StandardCharsets.UTF_8),"notes.md")) {
            assertEquals(0,source.pageCount());assertEquals(1,source.unitCount());assertEquals("CHARACTER",source.locationType());
            var page=source.page(1);assertNull(page.pageNumber());assertEquals(0,page.charStart());assertEquals(text.length(),page.charEnd());
            assertEquals(text,page.text());
        }
        try(var document=new XWPFDocument();var output=new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Document body.");
            document.createTable(1,2).getRow(0).getCell(0).setText("Table content.");document.write(output);
            try(var source=new RagPageExtractor().open(output.toByteArray(),"notes.docx")) {
                assertNull(source.page(1).pageNumber());assertTrue(source.page(1).text().contains("Table content."));
                assertEquals(0,source.pageCount());assertEquals("CHARACTER",source.locationType());
            }
        }
    }

    @Test void pptxUsesSlideNumbersAndCanRenderEachSlideIndependently() throws Exception {
        try(var show=new XMLSlideShow();var output=new ByteArrayOutputStream()) {
            show.createSlide().createTextBox().setText("First slide.");show.createSlide().createTextBox().setText("Second slide.");show.write(output);
            try(var source=new RagPageExtractor().open(output.toByteArray(),"slides.pptx")) {
                assertEquals("SLIDE",source.locationType());assertEquals(2,source.pageCount());
                assertEquals(2,source.page(2).pageNumber());assertTrue(source.page(2).text().contains("Second"));
                assertTrue(source.renderPng(2).length>0);
            }
        }
    }

    @Test void rejectsOldOfficeBadUtf8AndOverTwoHundredPdfPages() throws Exception {
        var extractor=new RagPageExtractor();
        for(String name:new String[]{"old.doc","old.ppt","unknown.exe"})
            assertThrows(RagAccessException.class,()->extractor.open(new byte[]{1,2,3},name));
        assertThrows(RagAccessException.class,()->extractor.open(new byte[]{(byte)0xc3,0x28},"bad.txt"));
        try(var pdf=new PDDocument();var output=new ByteArrayOutputStream()) {
            for(int i=0;i<201;i++)pdf.addPage(new PDPage());pdf.save(output);
            var failure=assertThrows(RagAccessException.class,()->extractor.open(output.toByteArray(),"large.pdf"));
            assertTrue(failure.getMessage().contains("200"));
        }
    }
}
