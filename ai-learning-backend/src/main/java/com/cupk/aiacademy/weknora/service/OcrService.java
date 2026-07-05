package com.cupk.aiacademy.weknora.service;

import com.cupk.aiacademy.weknora.config.OcrConfig;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);
    private static final int OCR_DPI = 300;

    private final OcrConfig config;

    public OcrService(OcrConfig config) {
        this.config = config;
    }

    public boolean needsOcr(byte[] pdfBytes) {
        if (!config.isEnabled()) return false;

        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            int pages = doc.getNumberOfPages();
            if (pages == 0) return false;

            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            long charCount = text.replaceAll("\\s", "").length();

            double avgCharsPerPage = (double) charCount / pages;
            log.debug("PDF text check: {} chars across {} pages (avg {:.0f}/page, threshold {})",
                    charCount, pages, avgCharsPerPage, config.getMinTextLength());
            return avgCharsPerPage < config.getMinTextLength();
        } catch (IOException e) {
            log.warn("Failed to check PDF text content: {}", e.getMessage());
            return false;
        }
    }

    public String extractText(byte[] pdfBytes) {
        StringBuilder result = new StringBuilder();
        Tesseract tesseract = createTesseract();
        if (tesseract == null) return "";

        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            int pages = doc.getNumberOfPages();
            log.info("OCR processing PDF: {} pages at {} DPI", pages, OCR_DPI);

            for (int i = 0; i < pages; i++) {
                try {
                    BufferedImage image = renderer.renderImageWithDPI(i, OCR_DPI, ImageType.RGB);
                    String pageText = tesseract.doOCR(image);
                    if (pageText != null && !pageText.isBlank()) {
                        result.append("## 第").append(i + 1).append("页\n\n");
                        result.append(pageText.trim()).append("\n\n");
                    }
                } catch (IOException | TesseractException e) {
                    log.warn("OCR failed for page {}: {}", i + 1, e.getMessage());
                    result.append("## 第").append(i + 1).append("页\n\n");
                    result.append("(OCR 识别失败)\n\n");
                }
            }

            String finalText = result.toString().trim();
            log.info("OCR complete: extracted {} characters from {} pages", finalText.length(), pages);
            return finalText;
        } catch (IOException e) {
            log.error("Failed to load PDF for OCR: {}", e.getMessage());
            return "";
        }
    }

    private Tesseract createTesseract() {
        try {
            Tesseract tesseract = new Tesseract();
            String dataPath = config.getTesseract().getDataPath();
            if (dataPath != null && !dataPath.isBlank()) {
                tesseract.setDatapath(dataPath);
            }
            tesseract.setLanguage(config.getTesseract().getLanguage());
            return tesseract;
        } catch (Exception e) {
            log.error("Failed to initialize Tesseract: {}. "
                    + "Ensure Tesseract OCR is installed and tessdata path is correct.", e.getMessage());
            return null;
        }
    }
}
