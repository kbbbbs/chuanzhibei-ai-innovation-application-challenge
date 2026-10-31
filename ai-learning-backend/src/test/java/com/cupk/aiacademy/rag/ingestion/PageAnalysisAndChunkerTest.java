package com.cupk.aiacademy.rag.ingestion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PageAnalysisAndChunkerTest {
    @Test void scansMixedPagesLogosTextAndForcedPagesHaveDistinctDecisions() {
        var analyzer=new PageAnalysisService();
        assertTrue(analyzer.analyze("",true,1,false).needsOcr());
        assertTrue(analyzer.analyze("Heading",true,.65,false).needsOcr());
        assertFalse(analyzer.analyze("Normal text paragraph. ".repeat(40),true,.01,false).needsOcr());
        assertFalse(analyzer.analyze("Normal text paragraph. ".repeat(40),false,0,false).needsOcr());
        assertTrue(analyzer.analyze("Normal text paragraph.",false,0,true).needsOcr());
        assertTrue(analyzer.analyze("",false,0,false).blank());
        assertTrue(analyzer.analyze("\ufffd".repeat(100),false,0,false).needsOcr());
    }

    @Test void chunksKeepExactCharacterRangesAndDoNotSplitUnicodePairs() {
        String text="  Heading\n"+"Gradient descent😀 updates parameters. ".repeat(80)+"\nend  ";
        var chunks=new RagTextChunker().chunks(text,4,7);
        assertTrue(chunks.size()>1);assertEquals(7,chunks.getFirst().ordinal());
        for(var chunk:chunks) {
            assertEquals(4,chunk.page());assertEquals(chunk.content(),text.substring(chunk.charStart(),chunk.charEnd()));
            assertFalse(Character.isLowSurrogate(chunk.content().charAt(0)));
            assertFalse(Character.isHighSurrogate(chunk.content().charAt(chunk.content().length()-1)));
        }
        assertTrue(new RagTextChunker().chunks(" \n\t",null,1).isEmpty());
    }
}
