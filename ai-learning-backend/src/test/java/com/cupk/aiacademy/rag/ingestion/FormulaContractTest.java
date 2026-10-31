package com.cupk.aiacademy.rag.ingestion;

import com.cupk.aiacademy.rag.RagAccessException;
import com.cupk.aiacademy.rag.ocr.OcrResultNormalizer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FormulaContractTest {
    @Test void mathematicalTextLayerRequiresVisualRecognition() {
        var analysis=new PageAnalysisService();
        assertTrue(analysis.analyze("Gradient: L = 1/n sum (yi - yhat)2",false,0,false).needsOcr());
        assertTrue(analysis.analyze("∂L/∂w ∑ √x",false,0,false).needsOcr());
        assertFalse(analysis.analyze("Lecture 3: the model has 10 parameters.",false,0,false).needsOcr());
    }

    @Test void longDisplayFormulaIsNeverSplitAndRangesRemainExact() {
        String formula="$$\\begin{aligned}"+"x_i &= a_i + b_i \\\\ ".repeat(90)+"\\end{aligned}$$";
        String text="Definition of the loss and variables. ".repeat(20)+"\n"+formula+"\nwhere x is the output. ".repeat(50);
        int from=text.indexOf("$$"),to=from+formula.length();
        var chunks=new RagTextChunker().chunks(text,3,1);
        assertTrue(chunks.stream().anyMatch(c->c.content().contains(formula)),"at least one whole formula must reach retrieval");
        assertTrue(chunks.stream().anyMatch(c->c.content().contains(formula) && c.content().contains("where x is the output.")),
                "the immediately following variable definition belongs with the complete formula");
        for(var chunk:chunks) {
            assertFalse(chunk.charStart()>from && chunk.charStart()<to,"overlap must not restart inside math");
            assertFalse(chunk.charEnd()>from && chunk.charEnd()<to,"chunk end must not cut math");
            assertEquals(text.substring(chunk.charStart(),chunk.charEnd()),chunk.content());
        }
    }

    @Test void inlineFormulaAndMatrixDelimitersSurviveChunkBoundaries() {
        for(String formula:new String[]{"\\(x_{i}^{2}+y_{i}^{2}\\)","$\\frac{a_i}{b_i}$","\\[\\begin{bmatrix}1&2\\\\3&4\\end{bmatrix}\\]"}) {
            String text="a".repeat(895)+formula+" ending ".repeat(200);
            assertTrue(new RagTextChunker().chunks(text,1,1).stream().anyMatch(c->c.content().contains(formula)));
        }
    }

    @Test void malformedOrUnboundedFormulaMustNotBecomeSearchEvidence() {
        assertThrows(RagAccessException.class,()->new RagTextChunker().chunks("Loss $$\\frac{a}{b",1,1));
        assertThrows(RagAccessException.class,()->new RagTextChunker().chunks("$$"+"x+".repeat(10000)+"y$$",1,1));
    }

    @Test void formulaOcrDoesNotAppendFlattenedNativeDuplicates() {
        String ocr="Loss function\n$$L=\\frac{1}{n}\\sum_i (y_i-\\hat y_i)^2$$\nwhere n is sample count.";
        assertEquals(ocr,new OcrResultNormalizer().merge("Loss function\nL 1 n yi yhat 2",ocr));
    }
}
