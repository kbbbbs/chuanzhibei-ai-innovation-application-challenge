package com.cupk.aiacademy.rag.ingestion;

import org.springframework.stereotype.Component;

/** Conservative page-local heuristic. Image-heavy and corrupt-text pages remain reviewable. */
@Component
public class PageAnalysisService {
    public record Assessment(boolean needsOcr,boolean blank,String reason) { }
    public Assessment analyze(String text,boolean hasImages,boolean forceOcr){return analyze(text,hasImages,hasImages?1:0,forceOcr);}
    public Assessment analyze(String text,boolean hasImages,double imageCoverage,boolean forceOcr) {
        String value=text==null?"":text.strip();
        if(forceOcr)return new Assessment(true,false,"FORCED_OCR");
        if(value.isBlank())return hasImages?new Assessment(true,false,"SCAN_OR_VISUAL_PAGE"):new Assessment(false,true,"BLANK");
        long replacements=value.chars().filter(c->c==0xfffd || c==0).count();
        if(replacements>Math.max(2,value.length()/20))return new Assessment(true,false,"CORRUPT_TEXT");
        if(com.cupk.aiacademy.rag.formula.FormulaText.suspectMath(value))return new Assessment(true,false,"FORMULA_TEXT_LAYER");
        if(hasImages && imageCoverage>=.12)return new Assessment(true,false,"MIXED_IMAGE_TEXT");
        return new Assessment(false,false,"TEXT_LAYER");
    }
}
