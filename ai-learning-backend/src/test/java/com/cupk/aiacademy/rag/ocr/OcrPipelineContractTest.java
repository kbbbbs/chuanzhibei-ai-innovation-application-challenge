package com.cupk.aiacademy.rag.ocr;

import com.cupk.aiacademy.model.*;
import org.apache.pdfbox.pdmodel.*;
import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OcrPipelineContractTest {
    @Test void resolutionRetryFailureRetainsTheActualPartialResultAndNeverPublishesIt() throws Exception {
        var image=new BufferedImage(300,200,BufferedImage.TYPE_INT_RGB);byte[] pixels;
        try(var output=new ByteArrayOutputStream()){ImageIO.write(image,"png",output);pixels=output.toByteArray();}
        var client=mock(OcrModelClient.class);
        when(client.recognizeDocument(any(),eq("image/png")))
                .thenReturn(new OcrModelClient.Recognition("Partial source formula","OCR_OUTPUT_TRUNCATED"))
                .thenThrow(new ModelInvocationException("MODEL_HTTP_503"));
        var result=new OcrModelAdapter(settings(),client,new OcrResultNormalizer()).recognize(new OcrRequest(pixels,"image/png",1,"zh","failed-retry",true));
        assertEquals("MODEL_HTTP_503",result.errorCode());assertTrue(result.needsReview());
        assertEquals("Partial source formula",result.rawText());assertEquals("Partial source formula",result.priorRawText());
        verify(client,times(2)).recognizeDocument(any(),eq("image/png"));
    }

    @Test void aSecondTruncationStopsAfterOneResolutionRetry() throws Exception {
        var image=new BufferedImage(300,200,BufferedImage.TYPE_INT_RGB);byte[] pixels;
        try(var output=new ByteArrayOutputStream()){ImageIO.write(image,"png",output);pixels=output.toByteArray();}
        var client=mock(OcrModelClient.class);
        when(client.recognizeDocument(any(),eq("image/png")))
                .thenReturn(new OcrModelClient.Recognition("First partial","OCR_OUTPUT_TRUNCATED"))
                .thenReturn(new OcrModelClient.Recognition("Second partial","OCR_OUTPUT_TRUNCATED"));
        var result=new OcrModelAdapter(settings(),client,new OcrResultNormalizer()).recognize(new OcrRequest(pixels,"image/png",1,"zh","bounded-retry",true));
        assertEquals("OCR_OUTPUT_TRUNCATED",result.errorCode());assertEquals("Second partial",result.rawText());
        assertEquals("First partial",result.priorRawText());verify(client,times(2)).recognizeDocument(any(),eq("image/png"));
    }

    @Test void truncatedDocumentRetriesOnceAtBoundedResolutionAndKeepsFirstRawOutput() throws Exception {
        var image=new BufferedImage(300,200,BufferedImage.TYPE_INT_RGB);byte[] pixels;
        try(var output=new ByteArrayOutputStream()){ImageIO.write(image,"png",output);pixels=output.toByteArray();}
        var client=mock(OcrModelClient.class);
        when(client.recognizeDocument(any(),eq("image/png")))
                .thenReturn(new OcrModelClient.Recognition("Initial partial formula","OCR_OUTPUT_TRUNCATED"))
                .thenReturn(new OcrModelClient.Recognition("\\[\\frac{x}{y}\\]",null));
        var result=new OcrModelAdapter(settings(),client,new OcrResultNormalizer()).recognize(new OcrRequest(pixels,"image/png",1,"zh","formula-retry",true));
        assertNull(result.errorCode());assertEquals("\\[\\frac{x}{y}\\]",result.text());
        assertEquals("Initial partial formula",result.priorRawText());assertEquals(.9,result.retryScale());
        var images=org.mockito.ArgumentCaptor.forClass(byte[].class);verify(client,times(2)).recognizeDocument(images.capture(),eq("image/png"));
        var resized=ImageIO.read(new java.io.ByteArrayInputStream(images.getAllValues().get(1)));
        assertEquals(270,resized.getWidth());assertEquals(180,resized.getHeight());
    }
    @Test void normalizationPreservesContentAndFormulaButRemovesGroundingCoordinates() {
        var normalizer=new OcrResultNormalizer();
        String raw="<|ref|>text<|/ref|><|det|>[[0,0,100,100]]<|/det|>\nHeading\n梯度下降\n$$x^2$$\n| a | b |";
        var result=normalizer.normalize(raw);
        assertTrue(result.text().contains("梯度下降"));assertTrue(result.text().contains("$$x^2$$"));assertTrue(result.text().contains("| a | b |"));
        assertFalse(result.text().contains("<|det|>"));assertFalse(result.text().contains("100,100"));
        assertEquals("Heading\n梯度下降",normalizer.merge("Heading\n梯度下降","Heading\n梯度下降"));
        assertThrows(ModelInvocationException.class,()->normalizer.normalize("<|ref|>text<|/ref|><|det|>[[1,2,3,4]]<|/det|>"));
    }

    @Test void adapterKeepsRawModelOutputUnknownConfidenceAndSafeFingerprint() {
        var settings=settings();var client=mock(OcrModelClient.class);
        when(client.recognizeDetailed(any(),eq("image/png"))).thenReturn(new OcrModelClient.Recognition("<|ref|>text<|/ref|>\nOCR TEST",null));
        var adapter=new OcrModelAdapter(settings,client,new OcrResultNormalizer());
        var result=adapter.recognize(new OcrRequest(new byte[]{1},"image/png",3,"zh-en","request-test"));
        assertTrue(result.rawText().contains("<|ref|>"));assertEquals("OCR TEST",result.text());
        assertNull(result.confidence());assertEquals("deepseek-ai/DeepSeek-OCR",result.model());
        assertTrue(adapter.fingerprint().matches("[0-9a-f]{64}"));assertFalse(adapter.fingerprint().contains("ocr-secret"));
        settings.getOcr().setApiKey("");
        assertEquals("OCR_NOT_CONFIGURED",assertThrows(ModelConfigurationException.class,()->adapter.recognize(new OcrRequest(new byte[]{1},"image/png",3,"zh","request-test"))).code());
        verify(client,times(1)).recognizeDetailed(any(),anyString());
    }

    @Test void rendererBoundsPixelsAndRecognizesOnlyNearWhiteBlankPages() throws Exception {
        var renderer=new PdfPageRenderer(144,1_000_000,1200);
        try(var pdf=new PDDocument()) {
            pdf.addPage(new PDPage());
            byte[] rendered=renderer.render(pdf,1);var image=ImageIO.read(new java.io.ByteArrayInputStream(rendered));
            assertTrue((long)image.getWidth()*image.getHeight()<=1_000_000);assertTrue(renderer.isVisuallyBlank(rendered));
        }
        var image=new BufferedImage(300,100,BufferedImage.TYPE_INT_RGB);var graphics=image.createGraphics();
        graphics.setColor(Color.WHITE);graphics.fillRect(0,0,300,100);graphics.setColor(Color.BLACK);graphics.fillRect(30,30,120,25);graphics.dispose();
        try(var output=new ByteArrayOutputStream()){ImageIO.write(image,"png",output);assertFalse(renderer.isVisuallyBlank(output.toByteArray()));}
    }

    @Test void mergePreservesRepeatedOcrRowsAndOnlyDeduplicatesTheNativeLayer() {
        var normalizer=new OcrResultNormalizer();
        assertEquals("Heading\nrow\nrow\nNative-only label",normalizer.merge("Heading\nNative-only label","Heading\nrow\nrow"));
    }

    @Test void transientProviderFailuresRetryButAuthenticationFailuresDoNot() {
        var settings=settings();var client=mock(OcrModelClient.class);
        var request=new OcrRequest(new byte[]{1},"image/png",1,"zh","retry-test");
        when(client.recognizeDetailed(any(),anyString())).thenThrow(new ModelInvocationException("MODEL_HTTP_503")).thenReturn(new OcrModelClient.Recognition("recognized",null));
        var adapter=new OcrModelAdapter(settings,client,new OcrResultNormalizer());
        assertEquals("recognized",adapter.recognize(request).text());verify(client,times(2)).recognizeDetailed(any(),anyString());
        reset(client);when(client.recognizeDetailed(any(),anyString())).thenThrow(new ModelInvocationException("MODEL_HTTP_401"));
        assertEquals("MODEL_HTTP_401",assertThrows(ModelInvocationException.class,()->adapter.recognize(request)).code());
        verify(client,times(1)).recognizeDetailed(any(),anyString());
    }

    @Test void credentialRotationDoesNotChangeProviderIdentityButModelChangeDoes() {
        var settings=settings();var adapter=new OcrModelAdapter(settings,mock(OcrModelClient.class),new OcrResultNormalizer());
        String first=adapter.fingerprint();settings.getOcr().setApiKey("another-private-test-key");
        assertEquals(first,adapter.fingerprint());settings.getOcr().setModel("another-explicit-ocr-model");
        assertNotEquals(first,adapter.fingerprint());
    }

    private RuntimeModelProperties settings(){
        var settings=new RuntimeModelProperties();settings.getOcr().setBaseUrl("https://ocr.example.test/v1");
        settings.getOcr().setApiKey("ocr-secret");settings.getOcr().setModel("deepseek-ai/DeepSeek-OCR");return settings;
    }

    @Test void adapterReturnsIncompleteRawTextForReviewWithoutPretendingThePageSucceeded() {
        var client=mock(OcrModelClient.class);
        when(client.recognizeDetailed(any(),anyString())).thenReturn(new OcrModelClient.Recognition("<|ref|>text<|/ref|>\nPartial page","OCR_OUTPUT_TRUNCATED"));
        var adapter=new OcrModelAdapter(settings(),client,new OcrResultNormalizer());
        var result=adapter.recognize(new OcrRequest(new byte[]{1},"image/png",1,"zh","incomplete-test"));
        assertEquals("OCR_OUTPUT_TRUNCATED",result.errorCode());assertEquals("Partial page",result.text());
        assertTrue(result.needsReview());assertTrue(result.rawText().contains("Partial page"));
        verify(client,times(1)).recognizeDetailed(any(),anyString());
    }

    @Test void emptyNormalizationRetainsTheSuccessfulRawHttpRecognitionForPrivateReview() {
        String raw="<|ref|>image<|/ref|><|det|>[[0,0,1000,1000]]<|/det|>";
        var client=mock(OcrModelClient.class);
        when(client.recognizeDetailed(any(),anyString())).thenReturn(new OcrModelClient.Recognition(raw,null));
        var result=new OcrModelAdapter(settings(),client,new OcrResultNormalizer()).recognize(new OcrRequest(new byte[]{1},"image/png",1,"zh","empty-review"));
        assertEquals("OCR_EMPTY_RESULT",result.errorCode());assertEquals(raw,result.rawText());
        assertEquals("",result.text());assertTrue(result.needsReview());verify(client,times(1)).recognizeDetailed(any(),anyString());
    }
}
