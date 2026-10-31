package com.cupk.aiacademy.rag.ocr;

import com.cupk.aiacademy.model.ModelInvocationException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Observed provider token structures, with synthetic replacement text instead of course content. */
class OcrResultNormalizerRegressionTest {
    @Test void recognizedChineseInsideRefTokensRemainsTextWithOneBoundaryPerBlock() {
        String raw="<|ref|>合成课程标题<|/ref|><|det|>[[0,0,500,80]]<|/det|>"
                +"<|ref|>这是用于回归测试的第一段。<|/ref|><|det|>[[0,90,500,200]]<|/det|>"
                +"<|ref|>列表项甲<|/ref|><|det|>[[0,210,500,260]]<|/det|>";
        var normalizer=new OcrResultNormalizer();
        var result=normalizer.normalize(raw);
        assertEquals("合成课程标题\n这是用于回归测试的第一段。\n列表项甲",result.text());
        assertEquals(result.text(),normalizer.normalize(result.text()).text(),"Worker normalization of an adapter result must be idempotent");
    }

    @Test void actualLayoutLabelsAreRemovedWhileTheirFollowingMarkdownBodyIsKept() {
        String raw="<|ref|>title<|/ref|><|det|>[[0,0,400,80]]<|/det|>\n# 合成标题\n"
                +"<|ref|>text<|/ref|><|det|>[[0,90,400,200]]<|/det|>\n合成正文。\n"
                +"<|ref|>image<|/ref|><|det|>[[0,210,400,300]]<|/det|>";
        var result=new OcrResultNormalizer().normalize(raw);
        assertEquals("# 合成标题\n合成正文。",result.text());
        assertFalse(result.text().contains("400"));assertFalse(result.text().contains("image"));
    }

    @Test void imageOnlyLayoutMetadataNeverBecomesSearchableText() {
        assertEquals("OCR_EMPTY_RESULT",assertThrows(ModelInvocationException.class,()->new OcrResultNormalizer().normalize(
                "<|ref|>image<|/ref|><|det|>[[0,0,1000,1000]]<|/det|>")).code());
    }
}
