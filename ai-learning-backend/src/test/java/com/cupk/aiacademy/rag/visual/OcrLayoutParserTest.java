package com.cupk.aiacademy.rag.visual;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OcrLayoutParserTest {
    @Test void imageRegionKeepsSourceCoordinatesAndNeighboringQuestionFormulaAndCaption() {
        String raw="<|ref|>title<|/ref|><|det|>[[20,10,900,60]]<|/det|>\n几何测试\n"
                +"<|ref|>text<|/ref|><|det|>[[20,80,900,170]]<|/det|>\n12. 如图，已知 $a^2+b^2=c^2$，求边长。\n"
                +"<|ref|>image<|/ref|><|det|>[[100,200,800,700]]<|/det|>\n"
                +"<|ref|>caption<|/ref|><|det|>[[100,710,800,760]]<|/det|>\n图1 直角三角形\n"
                +"13. 下一题，与前图无关。";
        var layout=new OcrLayoutParser().parse(raw);
        assertEquals(1,layout.regions().size());assertTrue(layout.issues().isEmpty());
        var region=layout.regions().getFirst();assertEquals(List.of(100,200,800,700),region.bbox());
        assertEquals("12",region.questionNumber());assertEquals("图1 直角三角形",region.caption());
        assertTrue(region.context().contains("几何测试"),"the nearby source heading must survive question grouping");
        assertTrue(region.context().contains("$a^2+b^2=c^2$"));
        assertFalse(region.context().contains("下一题"));
        assertTrue(raw.substring(region.sourceStart(),region.sourceEnd()).contains("<|ref|>image"));
        assertTrue(layout.blocks().stream().anyMatch(b->b.type().equals("caption") && b.text().contains("图1")));
    }

    @Test void malformedAndExecutableCoordinatesAreRejectedWithoutEvaluationOrClamping() {
        var parser=new OcrLayoutParser();
        for(String box:List.of("[[0,0,1000,999]]","[[500,0,100,999]]","[[0,0,0,999]]","[[0.1,0,999,999]]",
                "[[0,0,999]]","__import__('os').system('echo bad')","[[0,0,999,999]] trailing")) {
            var result=parser.parse("<|ref|>image<|/ref|><|det|>"+box+"<|/det|>");
            assertTrue(result.regions().isEmpty(),box);assertFalse(result.issues().isEmpty(),box);
        }
    }

    @Test void imageLimitIsExplicitAndUnknownTextStillSurvives() {
        String raw="Ordinary question and table\n| x | y |\n| 1 | 2 |\n";
        for(int i=0;i<8;i++)raw+="<|ref|>chart<|/ref|><|det|>[["+(i*10)+",0,"+(100+i*10)+",300]]<|/det|>\n";
        var layout=new OcrLayoutParser().parse(raw);
        assertEquals(6,layout.regions().size());assertTrue(layout.issues().contains("VISUAL_REGION_LIMIT"));
        assertTrue(layout.blocks().stream().anyMatch(b->b.text().contains("| 1 | 2 |")));
    }

    @Test void absentBoxesAndUnclosedMarkersAreNotInventedAsDetectedImages() {
        var plain=new OcrLayoutParser().parse("Q1. plain text with an illustration mentioned.");
        assertTrue(plain.regions().isEmpty());
        var incomplete=new OcrLayoutParser().parse("<|ref|>image<|/ref|><|det|>[[1,2,3,4]]");
        assertTrue(incomplete.regions().isEmpty());assertTrue(incomplete.issues().contains("LAYOUT_MARKUP_INCOMPLETE"));
    }

    @Test void captionsDoNotCrossQuestionBoundariesAndFormulaNumbersDoNotBecomeQuestionIds() {
        var parser=new OcrLayoutParser();
        var result=parser.parse("第3题 观察下图。\n<|ref|>figure<|/ref|><|det|>[[0,0,999,999]]<|/det|>\n第4题 下一题\n图2 后题配图");
        assertEquals("3",result.regions().getFirst().questionNumber());
        assertEquals("",result.regions().getFirst().caption());assertFalse(result.regions().getFirst().context().contains("第4题"));
        var formula=parser.parse("$$12.5+x=30$$\n<|ref|>image<|/ref|><|det|>[[0,0,999,999]]<|/det|>");
        assertNull(formula.regions().getFirst().questionNumber());
    }
}
