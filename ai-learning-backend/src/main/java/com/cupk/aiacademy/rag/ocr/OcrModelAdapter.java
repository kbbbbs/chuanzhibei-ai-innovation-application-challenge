package com.cupk.aiacademy.rag.ocr;

import com.cupk.aiacademy.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** The sole runtime OCR provider. Uses only the independently configured OCR HTTP client. */
@Component
public class OcrModelAdapter implements OcrProvider {
    private static final Set<String> RETRYABLE=Set.of("MODEL_HTTP_429","MODEL_HTTP_500","MODEL_HTTP_502","MODEL_HTTP_503","MODEL_HTTP_504","MODEL_TIMEOUT","MODEL_CALL_FAILED");
    private final RuntimeModelProperties settings;
    private final OcrModelClient client;
    private final OcrResultNormalizer normalizer;
    private final int attempts;
    private final Semaphore slots;
    public OcrModelAdapter(RuntimeModelProperties settings,OcrModelClient client,OcrResultNormalizer normalizer) {
        this(settings,client,normalizer,2,2);
    }
    @Autowired
    public OcrModelAdapter(RuntimeModelProperties settings,OcrModelClient client,OcrResultNormalizer normalizer,
            @Value("${runtime.models.ocr.max-attempts:2}") int attempts,
            @Value("${runtime.models.ocr.max-concurrency:2}") int concurrency) {
        this.settings=settings;this.client=client;this.normalizer=normalizer;
        this.attempts=Math.max(1,Math.min(3,attempts));this.slots=new Semaphore(Math.max(1,Math.min(4,concurrency)),true);
    }
    @Override public OcrResult recognize(OcrRequest request) {
        String status=RuntimeModelPolicy.status(settings.getOcr());
        if(!"CONFIGURED_UNVERIFIED".equals(status))throw new ModelConfigurationException("MODEL_NOT_CONFIGURED".equals(status)?"OCR_NOT_CONFIGURED":status);
        if(request==null || request.image()==null || request.image().length==0)throw new ModelConfigurationException("OCR_IMAGE_INVALID");
        long started=System.nanoTime();boolean acquired=false;
        try {
            acquired=slots.tryAcquire(Math.max(1,Math.min(10,settings.getOcr().getTimeoutSeconds())),TimeUnit.SECONDS);
            if(!acquired)throw new ModelInvocationException("OCR_CONCURRENCY_LIMIT");
            for(int attempt=1;;attempt++) {
                try {
                    var recognition=request.documentMode()?client.recognizeDocument(request.image(),request.mimeType()):client.recognizeDetailed(request.image(),request.mimeType());
                    String prior=null;Double scale=null;
                    if(request.documentMode() && "OCR_OUTPUT_TRUNCATED".equals(recognition.errorCode())) {
                        byte[] resized=smallerPage(request.image());
                        if(resized!=null) {
                            prior=recognition.rawText();scale=.9;
                            try {recognition=client.recognizeDocument(resized,"image/png");}
                            catch(ModelInvocationException retryFailed) {
                                // Keep the partial first response for review. Do not restart the entire
                                // resolution sequence or discard observed source content on retry failure.
                                recognition=new OcrModelClient.Recognition(prior,retryFailed.code());
                            }
                        }
                    }
                    String raw=recognition.rawText();
                    OcrResultNormalizer.Normalized normalized;
                    try {normalized=normalizer.normalize(raw);}
                    catch(ModelInvocationException failedNormalization) {
                        return new OcrResult(raw,"",null,settings.getOcr().getModel(),
                                TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started),true,
                                recognition.errorCode()!=null?recognition.errorCode():failedNormalization.code(),prior,scale);
                    }
                    return new OcrResult(raw,normalized.text(),null,settings.getOcr().getModel(),
                            TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started),
                            recognition.errorCode()!=null || normalized.needsReview(),recognition.errorCode(),prior,scale);
                } catch(ModelInvocationException failure) {
                    if(attempt>=attempts || !RETRYABLE.contains(failure.code()))throw failure;
                    Thread.sleep(100L*attempt);
                }
            }
        } catch(InterruptedException interrupted) {
            Thread.currentThread().interrupt();throw new ModelInvocationException("OCR_CALL_INTERRUPTED");
        } finally {if(acquired)slots.release();}
    }
    @Override public String fingerprint() {
        try {
            String value="deepseek-ocr-formula-routing-bounded-scale-v4\n"+settings.getOcr().getBaseUrl().replaceAll("/+$","")+"\n"+settings.getOcr().getModel();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch(Exception ignored){throw new IllegalStateException("OCR provider fingerprint unavailable");}
    }

    /** One resolution retry, not an open-ended retry loop. Validate dimensions before decoding. */
    private byte[] smallerPage(byte[] bytes) {
        return new PdfPageRenderer().withSlot(()->{
            try(var input=javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
                var readers=javax.imageio.ImageIO.getImageReaders(input);if(!readers.hasNext())return null;
                var reader=readers.next();
                try {
                    reader.setInput(input,true,true);int width=reader.getWidth(0),height=reader.getHeight(0);
                    if(width<100 || height<100 || width>6000 || height>6000 || (long)width*height>8_000_000)return null;
                    var source=reader.read(0);
                    var scaled=new java.awt.image.BufferedImage((int)Math.round(width*.9),(int)Math.round(height*.9),java.awt.image.BufferedImage.TYPE_INT_RGB);
                    try {
                        var graphics=scaled.createGraphics();
                        try {graphics.setColor(java.awt.Color.WHITE);graphics.fillRect(0,0,scaled.getWidth(),scaled.getHeight());
                            graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                            graphics.drawImage(source,0,0,scaled.getWidth(),scaled.getHeight(),null);
                        } finally {graphics.dispose();}
                        return new PdfPageRenderer().encode(scaled);
                    } finally {source.flush();scaled.flush();}
                } finally {reader.dispose();}
            } catch(Exception ignored){return null;}
        });
    }
}
