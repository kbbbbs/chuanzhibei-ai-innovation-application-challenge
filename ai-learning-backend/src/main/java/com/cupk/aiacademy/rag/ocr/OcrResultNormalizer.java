package com.cupk.aiacademy.rag.ocr;

import com.cupk.aiacademy.model.ModelInvocationException;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class OcrResultNormalizer {
    private static final Pattern REF=Pattern.compile("<\\|ref\\|>(.*?)<\\|/ref\\|>",Pattern.DOTALL);
    private static final Pattern DET=Pattern.compile("<\\|det\\|>.*?<\\|/det\\|>",Pattern.DOTALL);
    private static final Set<String> LAYOUT_LABELS=Set.of("text","title","subtitle","sub_title","image","table","caption","formula",
            "header","footer","page_header","page_footer","page_number","footnote","figure","figure_title","list","list_item","equation");
    public record Normalized(String text,boolean needsReview) { }

    public Normalized normalize(String raw) {
        if(raw==null || raw.length()>4*1024*1024)throw new ModelInvocationException("OCR_INVALID_RESPONSE");
        boolean review=raw.contains("<|ref|>image") || raw.contains("\\frac") || raw.contains("$$")
                || raw.contains("\\begin{") || raw.contains("<table");
        String references=REF.matcher(raw).replaceAll(match->{
            String content=match.group(1).strip();
            // Grounded OCR may put the recognized words themselves inside ref, not just a layout label.
            return Matcher.quoteReplacement(LAYOUT_LABELS.contains(content.toLowerCase(Locale.ROOT))?"\n":"\n"+content+"\n");
        });
        String value=DET.matcher(references).replaceAll("\n")
                .replace("<|endoftext|>","").replace("<|eos|>","").replace("\u0000","")
                .replace("\r\n","\n").replace('\r','\n').replaceAll("\n(?:[\\t ]*\n)+","\n").strip();
        if(value.isBlank())throw new ModelInvocationException("OCR_EMPTY_RESULT");
        return new Normalized(value,review);
    }

    /** OCR determines reading order; only native lines not already represented are appended. */
    public String merge(String nativeText,String ocrText) {
        String recognized=ocrText==null?"":ocrText.strip();
        if(recognized.isBlank())return nativeText==null?"":nativeText.strip();
        // The original PDF glyph order is not a second source of truth for an OCR-reconstructed formula.
        if(!com.cupk.aiacademy.rag.formula.FormulaText.analyze(recognized).blocks().isEmpty())return recognized;
        var represented=new LinkedHashMap<String,String>();
        addLines(represented,recognized);
        var additional=new LinkedHashMap<String,String>();
        addLines(additional,nativeText);
        additional.keySet().removeAll(represented.keySet());
        return additional.isEmpty()?recognized:recognized+"\n"+String.join("\n",additional.values());
    }
    private void addLines(LinkedHashMap<String,String> lines,String text) {
        if(text==null)return;
        for(String line:text.replace("\r\n","\n").replace('\r','\n').split("\n")) {
            String value=line.strip();if(value.isBlank())continue;
            String key=value.replaceAll("\\s+","").toLowerCase(Locale.ROOT);
            lines.putIfAbsent(key,value);
        }
    }
}
