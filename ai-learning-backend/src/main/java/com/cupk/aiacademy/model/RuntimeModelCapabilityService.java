package com.cupk.aiacademy.model;

import com.cupk.aiacademy.rag.RagEmbeddingClient;
import com.cupk.aiacademy.rag.ocr.OcrModelClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Arrays;
import java.util.function.Supplier;

/** Explicit probes use only generated test text, never course files or credentials in output. */
@Service
public class RuntimeModelCapabilityService {
    public record ModelStatus(String role, String configuration, String model,
                              int declaredContextWindowTokens, boolean adapterImplemented) {}
    public record Check(String name, String status, String detail, long latencyMs, int testedCharacters) {}
    public record ProbeReport(String role, String status, List<Check> checks) {}
    private final RuntimeModelProperties properties;
    private final OpenAiChatModel generation;
    private final OpenAiChatModel planning;
    private final RagEmbeddingClient embedding;
    private final OcrModelClient ocr;
    private final ObjectMapper mapper;

    public RuntimeModelCapabilityService(RuntimeModelProperties properties,
            @Qualifier("generationChatModel") OpenAiChatModel generation,
            @Qualifier("planningChatModel") OpenAiChatModel planning,
            RagEmbeddingClient embedding, OcrModelClient ocr, ObjectMapper mapper) {
        this.properties=properties; this.generation=generation; this.planning=planning; this.embedding=embedding; this.ocr=ocr; this.mapper=mapper;
    }

    /** No network activity and no endpoint/key field. Configuration does not establish capability. */
    public List<ModelStatus> status() {
        return List.of("generation","planning","embedding","ocr").stream().map(role -> {
            var endpoint=properties.endpoint(role);
            return new ModelStatus(role,RuntimeModelPolicy.status(endpoint),endpoint.getModel(),
                    endpoint.getContextWindowTokens(),true);
        }).toList();
    }

    public ProbeReport probe(String role) {
        var endpoint=properties.endpoint(role);
        String configured=RuntimeModelPolicy.status(endpoint);
        if (!configured.equals("CONFIGURED_UNVERIFIED")) return skipped(role,configured);
        if (role.equals("ocr")) {
            var check=check("OCR_IMAGE",0,()->ocr.recognize(syntheticOcrImage(),"image/png")
                    .replaceAll("\\s+"," ").contains("OCR TEST 2026"));
            return new ProbeReport(role,check.status(),List.of(check));
        }
        if (role.equals("embedding")) {
            var check=check("EMBEDDING",0,()->{
                var vectors=embedding.embed(List.of("梯度下降沿负梯度方向更新参数。","光合作用把光能转换为化学能。"));
                return vectors.size()==2 && vectors.getFirst().length>0
                        && vectors.getFirst().length==vectors.getLast().length
                        && !Arrays.equals(vectors.getFirst(),vectors.getLast());
            });
            return new ProbeReport(role,check.status(),List.of(check));
        }
        OpenAiChatModel model=role.equals("generation")?generation:planning;
        var checks=new ArrayList<Check>();
        checks.add(check("TEXT",0,()->"ok".equals(content(model.call(prompt("Reply with exactly: ok",false))).strip())));
        if (!checks.getFirst().status().equals("PASS")) {
            for(String name:List.of("JSON_OBJECT","STREAM","CONTEXT_SAMPLE"))
                checks.add(new Check(name,"SKIPPED","TEXT_PROBE_FAILED",0,0));
            return new ProbeReport(role,"FAIL",List.copyOf(checks));
        }
        checks.add(check("JSON_OBJECT",0,()->{
            String text=content(model.call(prompt("Return JSON only: {\"ok\":true}",true)));
            try { var value=mapper.readTree(text); return value.isObject() && value.path("ok").isBoolean() && value.path("ok").asBoolean(); }
            catch(Exception e) { return false; }
        }));
        checks.add(check("STREAM",0,()->{
            var chunks=model.stream(prompt("Reply with exactly: ok",false)).take(128)
                    .collectList().block(Duration.ofSeconds(Math.max(1,endpoint.getTimeoutSeconds())));
            return chunks!=null && "ok".equals(chunks.stream().map(this::content).reduce("",String::concat).strip());
        }));
        String sentinel="K00_SENTINEL_"+UUID.randomUUID().toString().replace("-","");
        String context=sentinel+"\n"+"课程资料中的普通测试段落。".repeat(180)+"\n"+sentinel;
        checks.add(check("CONTEXT_SAMPLE",context.length(),()->sentinel.equals(content(model.call(prompt(
                "Read the following sample and return only its K00_SENTINEL identifier, with no explanation.\n"+context,false))).strip())));
        return new ProbeReport(role,checks.stream().allMatch(c->c.status().equals("PASS"))?"PASS":"FAIL",List.copyOf(checks));
    }

    /** A fixed synthetic image keeps the administrator probe independent of uploaded course material. */
    private byte[] syntheticOcrImage() {
        var image=new java.awt.image.BufferedImage(1100,360,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics=image.createGraphics();
        try {
            graphics.setColor(java.awt.Color.WHITE);graphics.fillRect(0,0,1100,360);
            graphics.setColor(java.awt.Color.BLACK);graphics.setFont(new java.awt.Font("SansSerif",java.awt.Font.PLAIN,64));
            graphics.drawString("OCR TEST 2026",60,200);
        } finally { graphics.dispose(); }
        try (var output=new java.io.ByteArrayOutputStream()) {
            if (!javax.imageio.ImageIO.write(image,"png",output)) throw new IllegalStateException("PNG encoder unavailable");
            return output.toByteArray();
        } catch(java.io.IOException error) { throw new IllegalStateException("Synthetic OCR image unavailable"); }
    }

    private Prompt prompt(String text,boolean json) {
        var options=OpenAiChatOptions.builder().maxTokens(100).temperature(0.0);
        if(json) options.responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_OBJECT,null));
        return new Prompt(text,options.build());
    }
    private String content(ChatResponse response) {
        return response==null || response.getResult()==null || response.getResult().getOutput()==null
                || response.getResult().getOutput().getContent()==null ? "" : response.getResult().getOutput().getContent();
    }
    private Check check(String name,int characters,Supplier<Boolean> run) {
        long started=System.nanoTime();
        String state="PASS",detail="OBSERVED_AT_TESTED_SIZE_ONLY";
        try { if(!run.get()) {state="FAIL";detail="UNEXPECTED_RESPONSE";} }
        catch(Exception e) {state="FAIL";detail=e instanceof ModelConfigurationException config?config.code():
                e instanceof ModelInvocationException invocation?invocation.code():"MODEL_CALL_FAILED";}
        return new Check(name,state,detail,Duration.ofNanos(System.nanoTime()-started).toMillis(),characters);
    }
    private ProbeReport skipped(String role,String code) { return new ProbeReport(role,"SKIPPED",List.of(new Check("CONFIGURATION","SKIPPED",code,0,0))); }
}
