package com.cupk.aiacademy.rag.visual;

import com.cupk.aiacademy.rag.*;
import com.cupk.aiacademy.rag.ocr.OcrModelClient;
import com.cupk.aiacademy.model.ModelInvocationException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RagVisualReaderTest {
    @Test void figureDigitizationTableFallsBackToVisibleLabelsWithoutNumericClaims() {
        var service=service();var ocr=mock(OcrModelClient.class);
        when(service.imageForActor("v1",2)).thenReturn(new byte[]{1});
        String fabricated="<table><tr><td>x</td><td>ReLU</td></tr><tr><td>4</td><td>0</td></tr></table>";
        when(ocr.recognizeFigure(any(),anyString())).thenReturn(new OcrModelClient.Recognition(fabricated,null));
        when(ocr.recognizeDetailed(any(),anyString())).thenReturn(new OcrModelClient.Recognition("ReLU\nLeaky ReLU\nELU\nSoftplus\n4\n-4",null));
        var result=new RagVisualReader(service,ocr).read(List.of(hit("c1","[1]","v1")),2,false);
        assertFalse(result.evidence().contains("<table>"),"Chart-to-table values are model estimates, not source cells");
        assertTrue(result.evidence().contains("ReLU"));assertTrue(result.evidence().contains("未读取曲线数值"));
        assertTrue(result.warning().contains("定量"));assertFalse(result.evidence().contains("\n4\n"));
        verify(ocr).recognizeDetailed(any(),eq("image/png"));
    }

    @Test void fallbackThatStillReturnsNumericTablesMustNotProduceUsableFigureEvidence() {
        var service=service();var ocr=mock(OcrModelClient.class);
        when(service.imageForActor("v1",2)).thenReturn(new byte[]{1});
        String fabricated="| x | ReLU |\n|---|---|\n|4|0|";
        when(ocr.recognizeFigure(any(),anyString())).thenReturn(new OcrModelClient.Recognition(fabricated,null));
        when(ocr.recognizeDetailed(any(),anyString())).thenReturn(new OcrModelClient.Recognition(fabricated,null));
        var result=new RagVisualReader(service,ocr).read(List.of(hit("c1","[1]","v1")),2,false);
        assertTrue(result.evidence().isBlank());assertTrue(result.warning().contains("定量"));
    }
    @Test void serializedHitCannotAttachAnUnrelatedAsset() {
        var service=service();var ocr=mock(OcrModelClient.class);
        doReturn(List.of()).when(service).forHit(any(),eq(2));
        assertThrows(RagAccessException.class,()->new RagVisualReader(service,ocr).read(List.of(hit("c1","[1]","private-other-asset")),2,false));
        verify(service,never()).imageForActor(anyString(),anyInt());verifyNoInteractions(ocr);
    }

    @Test void changedActiveChunkAfterFigureReadStopsTheAnswer() {
        var service=service();var ocr=mock(OcrModelClient.class);var source=hit("c1","[1]","v1");
        when(service.forHit(source,2)).thenReturn(source.visuals()).thenThrow(new RagAccessException("404","active chunk changed"));
        when(service.imageForActor("v1",2)).thenReturn(new byte[]{1});
        when(ocr.recognizeFigure(any(),anyString())).thenReturn(new OcrModelClient.Recognition("old private figure",null));
        assertThrows(RagAccessException.class,()->new RagVisualReader(service,ocr).read(List.of(source),2,false));
    }

    @Test void cancellationDuringDirectReadDoesNotReturnMediaForGeneration() {
        var service=service();var ocr=mock(OcrModelClient.class);
        when(service.imageForActor("v1",2)).thenAnswer(invocation->{Thread.currentThread().interrupt();return new byte[]{1};});
        try {
            assertThrows(com.cupk.aiacademy.common.BusinessException.class,()->new RagVisualReader(service,ocr).read(List.of(hit("c1","[1]","v1")),2,true));
        } finally {Thread.interrupted();}
        verifyNoInteractions(ocr);
    }
    @Test void ocrReadUsesPrivateBytesRechecksAccessAndPreservesCitation() {
        var service=service();var ocr=mock(OcrModelClient.class);
        when(service.imageForActor("v1",2)).thenReturn(new byte[]{1,2,3});
        when(ocr.recognizeFigure(any(),eq("image/png"))).thenReturn(new OcrModelClient.Recognition("Input -> convolution -> output",null));
        var reader=new RagVisualReader(service,ocr);
        var reading=reader.read(List.of(hit("c1","[1]","v1")),2,false);
        assertTrue(reading.evidence().contains("[1]"));assertTrue(reading.evidence().contains("Input -> convolution -> output"));
        assertTrue(reading.evidence().contains("OCR"));assertTrue(reading.images().isEmpty());
        verify(service,times(2)).imageForActor("v1",2);
    }

    @Test void repeatedAssetsReadOnceAndOnlyTwoUniqueImagesAreProcessed() {
        var service=service();var ocr=mock(OcrModelClient.class);
        when(service.imageForActor(anyString(),eq(2))).thenReturn(new byte[]{1});
        when(ocr.recognizeFigure(any(),anyString())).thenReturn(new OcrModelClient.Recognition("visible label",null));
        var reading=new RagVisualReader(service,ocr).read(List.of(hit("c1","[1]","v1"),hit("c2","[2]","v1"),hit("c3","[3]","v2"),hit("c4","[4]","v3")),2,false);
        verify(ocr,times(2)).recognizeFigure(any(),anyString());assertTrue(reading.warning().contains("2"));
        assertFalse(reading.evidence().contains("v3"));
    }

    @Test void failedOrTruncatedVisualCannotBecomeClaimedImageEvidence() {
        var service=service();var ocr=mock(OcrModelClient.class);
        when(service.imageForActor("v1",2)).thenReturn(new byte[]{1});
        when(ocr.recognizeFigure(any(),anyString())).thenReturn(new OcrModelClient.Recognition("incomplete finding","OCR_OUTPUT_TRUNCATED"));
        var reading=new RagVisualReader(service,ocr).read(List.of(hit("c1","[1]","v1")),2,false);
        assertFalse(reading.evidence().contains("incomplete finding"));assertTrue(reading.warning().contains("未完整"));
    }

    @Test void explicitDirectModeAttachesBytesAndNeverCallsTextOnlyFallbackOcr() {
        var service=service();var ocr=mock(OcrModelClient.class);
        when(service.imageForActor("v1",2)).thenReturn(new byte[]{1,2,3});
        var reading=new RagVisualReader(service,ocr).read(List.of(hit("c1","[1]","v1")),2,true);
        assertEquals(1,reading.images().size());assertTrue(reading.evidence().contains("[1]"));
        verifyNoInteractions(ocr);
    }

    @Test void accessRevokedAfterFigureReadAbortsInsteadOfReturningPrivateContent() {
        var service=service();var ocr=mock(OcrModelClient.class);
        when(service.imageForActor("v1",2)).thenReturn(new byte[]{1}).thenThrow(new RagAccessException("403","revoked"));
        when(ocr.recognizeFigure(any(),anyString())).thenReturn(new OcrModelClient.Recognition("private finding",null));
        assertThrows(RagAccessException.class,()->new RagVisualReader(service,ocr).read(List.of(hit("c1","[1]","v1")),2,false));
    }

    @Test void sizeLimitStopsImageTransfersAndKeepsAVisibleWarning() {
        var service=service();var ocr=mock(OcrModelClient.class);
        when(service.imageForActor("v1",2)).thenReturn(new byte[2*1024*1024+1]);
        var reading=new RagVisualReader(service,ocr).read(List.of(hit("c1","[1]","v1")),2,false);
        assertTrue(reading.evidence().isBlank());assertTrue(reading.warning().contains("容量"));verifyNoInteractions(ocr);
    }

    @Test void figureTimeoutCancelsTheActualOcrTaskAndReturnsNoInventedObservation() throws Exception {
        var service=service();var ocr=mock(OcrModelClient.class);
        when(service.imageForActor("v1",2)).thenReturn(new byte[]{1});
        var interrupted=new java.util.concurrent.CountDownLatch(1);
        when(ocr.recognizeFigure(any(),anyString())).thenAnswer(invocation->{
            try {Thread.sleep(10000);return new OcrModelClient.Recognition("late observation",null);}
            catch(InterruptedException stopped){interrupted.countDown();Thread.currentThread().interrupt();throw new ModelInvocationException("MODEL_CALL_INTERRUPTED");}
        });
        var reader=new RagVisualReader(service,ocr,100);
        try {
            long start=System.nanoTime();var result=reader.read(List.of(hit("c1","[1]","v1")),2,false);
            assertTrue((System.nanoTime()-start)/1_000_000<2000);assertTrue(result.evidence().isBlank());
            assertTrue(result.warning().contains("超时"));assertTrue(interrupted.await(1,java.util.concurrent.TimeUnit.SECONDS));
        } finally {reader.shutdown();}
    }

    private RagSearchHit hit(String chunk,String citation,String id) {
        var image=mock(RagVisual.class);when(image.id()).thenReturn(id);when(image.caption()).thenReturn("Network diagram");
        return new RagSearchHit(chunk,"d1","b1","slides.pdf","A convolution network",1,1,1,citation,List.of(image));
    }
    private RagVisualService service() {
        var service=mock(RagVisualService.class);
        when(service.forHit(any(),anyInt())).thenAnswer(invocation->invocation.<RagSearchHit>getArgument(0).visuals());
        return service;
    }
}
