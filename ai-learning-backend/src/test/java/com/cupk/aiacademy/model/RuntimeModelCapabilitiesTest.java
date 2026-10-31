package com.cupk.aiacademy.model;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.ForbiddenException;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.rag.RagEmbeddingClient;
import com.cupk.aiacademy.rag.ocr.OcrModelClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import reactor.core.publisher.Flux;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RuntimeModelCapabilitiesTest {
    RuntimeModelProperties properties;
    OpenAiChatModel generation,planning;
    RagEmbeddingClient embedding;
    OcrModelClient ocr;
    RuntimeModelCapabilityService service;
    @BeforeEach void setup() {
        properties=new RuntimeModelProperties();
        properties.setGeneration(RuntimeModelPolicyTest.endpoint("https://generation.example.test", "qwen-generation"));
        properties.setPlanning(RuntimeModelPolicyTest.endpoint("https://planning.example.test", "qwen-planner"));
        generation=mock(OpenAiChatModel.class); planning=mock(OpenAiChatModel.class); embedding=mock(RagEmbeddingClient.class);
        ocr=mock(OcrModelClient.class);
        service=new RuntimeModelCapabilityService(properties,generation,planning,embedding,ocr,new ObjectMapper());
    }
    @AfterEach void cleanup(){ UserContext.clear(); }
    ChatResponse response(String text){ return new ChatResponse(List.of(new Generation(new AssistantMessage(text)))); }

    @Test void statusIsReadOnlyAndDoesNotLeakCredentialsOrEndpoints() throws Exception {
        var status=service.status();
        assertEquals(4,status.size());
        assertEquals("CONFIGURED_UNVERIFIED",status.getFirst().configuration());
        assertEquals("MODEL_NOT_CONFIGURED",status.getLast().configuration());
        assertTrue(status.getLast().adapterImplemented());
        String json=new ObjectMapper().writeValueAsString(status);
        assertFalse(json.contains("unit-test-secret")); assertFalse(json.contains("https://"));
        verifyNoInteractions(generation,planning,embedding);
    }

    @Test void capabilitiesReportPartialSupportRatherThanClaimingEverythingWorks() {
        when(generation.call(any(Prompt.class))).thenAnswer(invocation->{
            Prompt prompt=invocation.getArgument(0);
            if(prompt.getContents().contains("JSON")) return response("not json");
            if(prompt.getContents().contains("K00_SENTINEL_")) {
                var matcher=java.util.regex.Pattern.compile("K00_SENTINEL_[a-f0-9]+").matcher(prompt.getContents());
                assertTrue(matcher.find()); return response(matcher.group());
            }
            return response("ok");
        });
        when(generation.stream(any(Prompt.class))).thenReturn(Flux.just(response("o"),response("k")));
        var report=service.probe("generation");
        assertEquals("FAIL",report.status());
        assertEquals(List.of("PASS","FAIL","PASS","PASS"),report.checks().stream().map(RuntimeModelCapabilityService.Check::status).toList());
        assertTrue(report.checks().getLast().testedCharacters()>=2048);
        verifyNoInteractions(planning,embedding);
    }

    @Test void upstreamFailureIsSanitizedAndRemainingChecksAreSkipped() throws Exception {
        when(planning.call(any(Prompt.class))).thenThrow(new IllegalStateException("api-key=actual-secret https://private.example.test"));
        var report=service.probe("planning");
        assertEquals("FAIL",report.status());
        assertEquals("FAIL",report.checks().getFirst().status());
        assertTrue(report.checks().stream().skip(1).allMatch(c->c.status().equals("SKIPPED")));
        String json=new ObjectMapper().writeValueAsString(report);
        assertFalse(json.contains("actual-secret")); assertFalse(json.contains("private.example"));
        verify(planning,times(1)).call(any(Prompt.class)); verifyNoInteractions(generation,embedding);
    }

    @Test void missingConfigurationIsSkippedWithoutModelCalls() {
        assertEquals("SKIPPED",service.probe("ocr").status());
        assertEquals("SKIPPED",service.probe("embedding").status());
        verifyNoInteractions(generation,planning,embedding,ocr);
    }

    @Test void ocrProbeUsesOnlyASyntheticImageAndChecksItsText() {
        properties.setOcr(RuntimeModelPolicyTest.endpoint("https://ocr.example.test", "deepseek-ai/DeepSeek-OCR"));
        when(ocr.recognize(any(byte[].class),eq("image/png"))).thenAnswer(invocation->{
            byte[] bytes=invocation.getArgument(0);
            var image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            assertNotNull(image); assertTrue(image.getWidth()>=800);
            return "<|ref|>text<|/ref|>OCR TEST 2026";
        });
        var report=service.probe("ocr");
        assertEquals("PASS",report.status());
        assertEquals("OCR_IMAGE",report.checks().getFirst().name());
        when(ocr.recognize(any(byte[].class),eq("image/png"))).thenReturn("unrelated");
        assertEquals("FAIL",service.probe("ocr").status());
        verifyNoInteractions(generation,planning,embedding);
    }

    @Test void embeddingProbeChecksBatchCountDimensionsAndDifferentVectors() {
        properties.setEmbedding(RuntimeModelPolicyTest.endpoint("https://embedding.example.test", "Qwen/Qwen3-VL-Embedding-8B"));
        when(embedding.embed(anyList())).thenReturn(List.of(new double[]{1,0},new double[]{0,1}));
        assertEquals("PASS",service.probe("embedding").status());
        verify(embedding).embed(argThat(values->values.size()==2 && !values.get(0).equals(values.get(1))));
        when(embedding.embed(anyList())).thenReturn(List.of(new double[]{1,0}));
        assertEquals("FAIL",service.probe("embedding").status());
        when(embedding.embed(anyList())).thenReturn(List.of(new double[]{1,0},new double[]{1,0}));
        assertEquals("FAIL",service.probe("embedding").status());
        verifyNoInteractions(generation,planning,ocr);
    }

    @Test void onlyAdminMayReadConfigurationOrTriggerProbe() {
        var controller=new RuntimeModelController(service);
        assertThrows(ForbiddenException.class,controller::status);
        var user=new AuthUtils.LoginUser();user.setUserId(2);user.setRoleCode("teacher");UserContext.set(user);
        assertThrows(ForbiddenException.class,()->controller.probe("generation"));
        user.setRoleCode("admin");
        assertEquals("200",controller.status().getCode());
        assertEquals("200",controller.probe("ocr").getCode());
        verifyNoInteractions(generation,planning,embedding);
    }
}
