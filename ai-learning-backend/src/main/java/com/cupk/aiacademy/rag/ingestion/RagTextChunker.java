package com.cupk.aiacademy.rag.ingestion;

import com.cupk.aiacademy.rag.RagAccessException;
import com.cupk.aiacademy.rag.formula.FormulaText;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class RagTextChunker {
    public record Chunk(Integer page,int ordinal,String content,int charStart,int charEnd) { }
    public List<Chunk> chunks(String text,Integer page,int startingOrdinal) {
        if(text==null || text.isBlank())return List.of();
        if(text.length()>1_000_000 || startingOrdinal<1)throw new RagAccessException("400","文本分块参数超限");
        // Only well-formed formulas are kept whole; a malformed one is ordinary text and must not stretch a chunk.
        var formulas=FormulaText.analyze(text).blocks().stream().filter(block->"STRUCTURE_OK".equals(block.status())).toList();
        var chunks=new ArrayList<Chunk>();int start=0;
        while(start<text.length()) {
            int end=Math.min(text.length(),start+900);
            if(end<text.length()) {
                int paragraph=text.lastIndexOf('\n',end);if(paragraph>start+450)end=paragraph+1;
                if(end>start && Character.isHighSurrogate(text.charAt(end-1)))end--;
            }
            // Keep the whole expression, including its source delimiters, with nearby definitions.
            for(var formula:formulas)if(end>formula.start() && end<formula.end())end=formula.end();
            // If math reaches the boundary, keep its immediately following short definition line.
            // This is bounded context, not a claim to resolve cross-page symbol definitions.
            for(var formula:formulas)if(end==formula.end()) {
                int after=end;
                while(after<text.length() && Character.isWhitespace(text.charAt(after)))after++;
                int lineEnd=text.indexOf('\n',after);if(lineEnd<0)lineEnd=text.length();
                if(lineEnd-end<=300)end=lineEnd;
            }
            for(var formula:formulas)if(end>formula.start() && end<formula.end())end=formula.end();
            int left=start,right=end;
            while(left<right && Character.isWhitespace(text.charAt(left)))left++;
            while(right>left && Character.isWhitespace(text.charAt(right-1)))right--;
            if(left<right)chunks.add(new Chunk(page,startingOrdinal+chunks.size(),text.substring(left,right),left,right));
            if(end==text.length())break;
            int next=Math.max(start+1,end-120);
            for(var formula:formulas)if(next>formula.start() && next<formula.end())next=formula.end();
            start=next;if(start<text.length() && Character.isLowSurrogate(text.charAt(start)))start++;
        }
        return List.copyOf(chunks);
    }
}
