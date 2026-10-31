package com.cupk.aiacademy.rag.visual;

import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.rag.RagAccessException;
import com.cupk.aiacademy.rag.RagSearchHit;
import com.cupk.aiacademy.rag.formula.FormulaText;
import com.cupk.aiacademy.rag.ocr.OcrModelClient;
import com.cupk.aiacademy.rag.ocr.OcrResultNormalizer;
import jakarta.annotation.PreDestroy;
import org.springframework.ai.model.Media;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/** Reads only already-authorized, retrieved assets. No remote URL or credentials enter answer evidence. */
@Component
public class RagVisualReader {
    private static final int MAX_IMAGES=2, MAX_TOTAL_BYTES=2*1024*1024;
    private final RagVisualService visuals;
    private final OcrModelClient ocr;
    private final long timeoutMillis;
    private final ExecutorService calls=Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore slots=new Semaphore(2,true);
    private final OcrResultNormalizer normalizer=new OcrResultNormalizer();
    private static final Pattern TABLE_MARKUP=Pattern.compile("(?im)<(?:table|tr|td|th)\\b|^\\s*\\|?\\s*:?-{3,}:?\\s*\\|");
    private static final Pattern NUMBER=Pattern.compile("\\p{N}");
    public record Reading(String evidence,List<Media> images,String warning) {
        public Reading {images=images==null?List.of():List.copyOf(images);}
    }
    public RagVisualReader(RagVisualService visuals,OcrModelClient ocr){this(visuals,ocr,15000);}
    @Autowired
    public RagVisualReader(RagVisualService visuals,OcrModelClient ocr,
            @Value("${rag.chat.visual-timeout-ms:15000}") long timeoutMillis) {
        this.visuals=visuals;this.ocr=ocr;this.timeoutMillis=Math.max(100,Math.min(30000,timeoutMillis));
    }

    public Reading read(List<RagSearchHit> hits,int actorId,boolean attachImages) {
        var evidence=new StringBuilder();var media=new ArrayList<Media>();
        var seen=new LinkedHashSet<String>();var warnings=new LinkedHashSet<String>();
        int processed=0,bytesUsed=0;boolean observed=false,visibleTextRead=false;
        for(var hit:hits)for(var visual:hit.visuals()) {
            checkCancelled();
            if(visual==null || visual.id()==null || !seen.add(visual.id()))continue;
            if(processed>=MAX_IMAGES){warnings.add("本次最多读取2张相关图片，其余图片请打开出处核验。");continue;}
            processed++;
            requireAssociation(hit,visual.id(),actorId);
            byte[] image=visuals.imageForActor(visual.id(),actorId);
            checkCancelled();
            if(image==null || image.length==0){warnings.add("部分相关图片没有可读取内容，请查看原页。");continue;}
            if(image.length>MAX_TOTAL_BYTES-bytesUsed){warnings.add("相关图片超过本次读取容量，部分图片尚未读取。");continue;}
            bytesUsed+=image.length;
            String label=hit.citation()==null?"资料":hit.citation();
            String title=visual.caption()==null?"相关插图":visual.caption();
            if(title.length()>200)title=title.substring(0,200);
            if(attachImages) {
                // Fetch after the initial read as well: replacement/revocation must stop the transfer.
                visuals.imageForActor(visual.id(),actorId);
                requireAssociation(hit,visual.id(),actorId);checkCancelled();
                media.add(new Media(MimeTypeUtils.IMAGE_PNG,new ByteArrayResource(image)));
                evidence.append(label).append(" 附图").append(media.size()).append("：").append(title).append('\n');
                continue;
            }
            Future<Observation> pending=calls.submit(()->{
                boolean acquired=false;
                try {
                    acquired=slots.tryAcquire(100,TimeUnit.MILLISECONDS);
                    if(!acquired)return new Observation(new OcrModelClient.Recognition("","VISUAL_BUSY"),false);
                    var first=ocr.recognizeFigure(image,"image/png");
                    if(first!=null && first.errorCode()==null && digitizedTable(first.rawText())) {
                        // Parse-the-figure may estimate data from curves. These are not source table cells.
                        // Re-read visible text under the same image deadline instead of publishing estimates.
                        checkCancelled();requireAssociation(hit,visual.id(),actorId);
                        return new Observation(ocr.recognizeDetailed(image,"image/png"),true);
                    }
                    return new Observation(first,false);
                } finally {if(acquired)slots.release();}
            });
            try {
                var observation=pending.get(timeoutMillis,TimeUnit.MILLISECONDS);
                var recognition=observation.recognition();
                // Preserve authorization failures; never convert them into apparently usable observations.
                visuals.imageForActor(visual.id(),actorId);
                requireAssociation(hit,visual.id(),actorId);checkCancelled();
                if(observation.visibleOnly())warnings.add("已拦截图形解析生成的数值表；本次未恢复曲线定量数据，请对照原图。");
                if(recognition==null || recognition.errorCode()!=null) {
                    warnings.add("部分图片未完整读取，请对照原图核验，不能据此补写图意。");continue;
                }
                String normalized=normalizer.normalize(recognition.rawText()).text();
                if(observation.visibleOnly())normalized=visibleLabelsOnly(normalized);
                String text=FormulaText.safePrefix(normalized,2000).strip();
                if(text.isBlank()){warnings.add("图片尚未产生可用解读，请查看原图。");continue;}
                evidence.append(label).append(observation.visibleOnly()?" OCR可见文字（未读取曲线数值；待核验；":" OCR图像观察（待核验；")
                        .append(title).append("）：\n").append(text).append("\n\n");
                if(observation.visibleOnly())visibleTextRead=true;else observed=true;
            } catch(InterruptedException interrupted) {
                pending.cancel(true);Thread.currentThread().interrupt();throw new BusinessException("图像读取已取消");
            } catch(TimeoutException timeout) {
                pending.cancel(true);warnings.add("部分图片未完整读取（超时），请查看原图。");
            } catch(ExecutionException | com.cupk.aiacademy.model.ModelInvocationException failure) {
                pending.cancel(true);
                if(failure instanceof ExecutionException wrapped && wrapped.getCause() instanceof RagAccessException access)throw access;
                warnings.add("图片解读服务暂时不可用，部分图片尚未读取，请查看原图。");
            }
        }
        if(observed)warnings.add("图像由DeepSeek-OCR解读后提供给回答模型，识别结果仍需对照原图核验。");
        if(visibleTextRead)warnings.add("本次只提供图中可见文字标签，未据图读取曲线数值或确认图形关系。");
        if(!media.isEmpty())warnings.add("本次已向回答模型附带"+media.size()+"张相关图片，请核验图像结论。");
        checkCancelled();
        return new Reading(evidence.toString(),media,warnings.isEmpty()?null:String.join(" ",warnings));
    }
    private void requireAssociation(RagSearchHit hit,String id,int actor) {
        if(visuals.forHit(hit,actor).stream().noneMatch(allowed->id.equals(allowed.id())))
            throw new RagAccessException("403","图像与当前来源片段不匹配或资料版本已改变");
    }
    private void checkCancelled(){if(Thread.currentThread().isInterrupted())throw new BusinessException("图像读取已取消");}
    private boolean digitizedTable(String text) {
        return text!=null && TABLE_MARKUP.matcher(text).find() && NUMBER.matcher(text).find();
    }
    private String visibleLabelsOnly(String text) {
        if(digitizedTable(text))return "";
        return text.lines().filter(line->!NUMBER.matcher(line).find()).collect(java.util.stream.Collectors.joining("\n")).strip();
    }
    private record Observation(OcrModelClient.Recognition recognition,boolean visibleOnly) { }
    @PreDestroy public void shutdown(){calls.shutdownNow();}
}
