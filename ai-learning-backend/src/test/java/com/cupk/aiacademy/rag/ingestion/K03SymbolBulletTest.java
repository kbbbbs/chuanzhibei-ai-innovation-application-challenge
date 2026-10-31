package com.cupk.aiacademy.rag.ingestion;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class K03SymbolBulletTest {
 @Test void twoObservedPrivateUseListMarkersDoNotClaimThePageContainsMath() {
  assertFalse(RagPageExtractor.isMathFontGlyph("Symbol","\uF0A8"));
  assertFalse(RagPageExtractor.isMathFontGlyph("Symbol","\uF0B7"));
  assertFalse(RagPageExtractor.isMathFontGlyph("Symbol","\uF0A8\uF0B7"));
  assertFalse(RagPageExtractor.isMathFontGlyph("Cambria Math","•"));
 }
 @Test void realMathAndUnverifiedPrivateUseGlyphsKeepTheFormulaProtection() {
  assertTrue(RagPageExtractor.isMathFontGlyph("Cambria Math","∂"));
  assertTrue(RagPageExtractor.isMathFontGlyph("Symbol","∑"));
  assertTrue(RagPageExtractor.isMathFontGlyph("Symbol","\uF0E5"));
  assertTrue(RagPageExtractor.isMathFontGlyph("CambriaMath","x"));
  assertFalse(RagPageExtractor.isMathFontGlyph("Arial","x"));
  assertFalse(RagPageExtractor.isMathFontGlyph("Symbol"," "));
 }
}
