package com.cupk.aiacademy.rag;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class RagDocumentParserTest {
    private final RagDocumentParser parser = new RagDocumentParser();

    @Test
    void wordTextAndTableContentAreExtractedWithoutFabricatedWordPageNumbers() throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("Course material: gradient descent.");
            var table = doc.createTable(1, 2);
            table.getRow(0).getCell(0).setText("Learning rate");
            table.getRow(0).getCell(1).setText("0.01");
            doc.write(out);
            var parsed = parser.parse(out.toByteArray(), "course.docx");
            String content = parsed.chunks().getFirst().content();
            assertTrue(content.contains("gradient descent"));
            assertTrue(content.contains("Learning rate"));
            assertEquals(0, parsed.pageCount());
            assertNull(parsed.chunks().getFirst().page());
        }
    }

    @Test
    void slideTextPreservesActualSlideNumbers() throws Exception {
        try (XMLSlideShow slides = new XMLSlideShow(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var first = slides.createSlide();
            first.createTextBox().setText("Gradient descent");
            first.createGroup().createTextBox().setText("Grouped learning notes");
            var table = first.createTable(1, 2);
            table.getCell(0, 0).setText("Learning rate");
            table.getCell(0, 1).setText("0.01");
            slides.createSlide().createTextBox().setText("Photosynthesis");
            slides.write(out);
            var parsed = parser.parse(out.toByteArray(), "lecture.pptx");
            assertEquals(2, parsed.pageCount());
            assertEquals(2, parsed.chunks().size());
            assertEquals(1, parsed.chunks().getFirst().page());
            assertTrue(parsed.chunks().getFirst().content().contains("Grouped learning notes"));
            assertTrue(parsed.chunks().getFirst().content().contains("Learning rate"));
            assertEquals(2, parsed.chunks().getLast().page());
            assertEquals("Photosynthesis", parsed.chunks().getLast().content());
        }
    }

    @Test
    void legacyPowerPointIsRejectedByTheConfirmedSupportedFormatBoundary() throws Exception {
        try (org.apache.poi.hslf.usermodel.HSLFSlideShow slides = new org.apache.poi.hslf.usermodel.HSLFSlideShow();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var slide = slides.createSlide();
            slide.createGroup().createTextBox().setText("Legacy course notes");
            slides.write(out);
            assertThrows(RagAccessException.class,()->parser.parse(out.toByteArray(), "legacy.ppt"));
        }
    }

    @Test
    void scanOnlyPdfIsNotMarkedSuccessfullyParsedAndPageLimitIsEnforced() throws Exception {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            pdf.addPage(new PDPage());
            pdf.save(out);
            RagAccessException empty = assertThrows(RagAccessException.class, () -> parser.parse(out.toByteArray(), "scan.pdf"));
            assertTrue(empty.getMessage().contains("OCR"));
        }
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i <= RagDocumentParser.MAX_PAGES; i++) pdf.addPage(new PDPage());
            pdf.save(out);
            RagAccessException tooMany = assertThrows(RagAccessException.class, () -> parser.parse(out.toByteArray(), "long.pdf"));
            assertTrue(tooMany.getMessage().contains("200"));
        }
    }

    @Test
    void nonUtf8InputIsRejectedRatherThanCorruptedIntoAValidIndex() {
        byte[] invalidUtf8 = {(byte)0xc3, (byte)0x28};
        assertThrows(RagAccessException.class, () -> parser.parse(invalidUtf8, "bad.txt"));
        var good = parser.parse("\ufeff梯度下降用于优化模型参数。".getBytes(StandardCharsets.UTF_8), "中文.md");
        assertFalse(good.chunks().getFirst().content().startsWith("\ufeff"));
        double[] scores = RagBm25.score("解释梯度下降", java.util.List.of(good.chunks().getFirst().content(), "光合作用利用太阳光。"));
        assertTrue(scores[0] > 0);
        assertEquals(0, scores[1]);
    }
}
