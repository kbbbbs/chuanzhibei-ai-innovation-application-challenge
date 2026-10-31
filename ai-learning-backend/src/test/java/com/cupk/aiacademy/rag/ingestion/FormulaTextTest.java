package com.cupk.aiacademy.rag.ingestion;

import com.cupk.aiacademy.rag.formula.FormulaText;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FormulaTextTest {
    @Test void keepsTypedFormulaBlocksAndOriginalOffsets() {
        String text="输出 $x_i^2$。\n\\[\\frac{a}{b}\\]\n\\begin{cases}x&x>0\\\\0&x\\le0\\end{cases}";
        var result=FormulaText.analyze(text);
        assertTrue(result.valid());assertEquals(3,result.blocks().size());
        assertFalse(result.blocks().get(0).display());assertTrue(result.blocks().get(1).display());
        assertEquals("x_i^2",result.blocks().get(0).latex());
        for(var block:result.blocks())assertEquals(block.raw(),text.substring(block.start(),block.end()));
    }
    @Test void excludesCodeAndCurrencyAndChecksStructuralFailures() {
        assertTrue(FormulaText.analyze("Price $10 and $20. `$$x$$`\n```tex\n$x$\n```").blocks().isEmpty());
        for(String text:new String[]{"$$x+","\\[\\frac{a}{b\\]","$\\begin{matrix}x\\end{cases}$","$$\\left(x$$"})
            assertFalse(FormulaText.analyze(text).valid(),text);
        assertTrue(FormulaText.analyze("$\\{x\\}$").valid());
    }
    @Test void contextBudgetNeverCutsFormulaAndSkipsAnIncompleteLegacyFormula() {
        String text="Definition\n$$\\frac{a}{b}$$\nvariables";
        assertEquals("Definition\n",FormulaText.safePrefix(text,18));
        assertEquals(text,FormulaText.safePrefix(text,100));
        assertEquals("",FormulaText.safePrefix("$$x+",100));
    }

    @Test void simpleDiagramDimensionLabelsDoNotForceTheFormulaPath() {
        assertFalse(FormulaText.suspectMath("RGB input D=3\noutput D=1\nFeature Map"));
        assertTrue(FormulaText.suspectMath("y=1/n + x"));
        assertTrue(FormulaText.suspectMath("y=1 + x"));
    }
}
