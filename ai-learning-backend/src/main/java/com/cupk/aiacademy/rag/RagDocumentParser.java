package com.cupk.aiacademy.rag;

import com.cupk.aiacademy.rag.ingestion.RagPageExtractor;
import com.cupk.aiacademy.rag.ingestion.RagTextChunker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/** Synchronous compatibility surface; asynchronous ingestion uses the same page source and chunker. */
@Component
public class RagDocumentParser {
    public static final int MAX_BYTES=RagPageExtractor.MAX_BYTES,MAX_PAGES=RagPageExtractor.MAX_PAGES,MAX_TEXT=RagPageExtractor.MAX_TEXT;
    public record ParsedChunk(Integer page,int ordinal,String content) { }
    public record ParsedDocument(String mimeType,int pageCount,List<ParsedChunk> chunks) { }
    private final RagPageExtractor extractor;private final RagTextChunker chunker;
    public RagDocumentParser(){this(new RagPageExtractor(),new RagTextChunker());}
    @Autowired public RagDocumentParser(RagPageExtractor extractor,RagTextChunker chunker){this.extractor=extractor;this.chunker=chunker;}
    public ParsedDocument parse(byte[] bytes,String filename) {
        try(var document=extractor.open(bytes,filename)) {
            var chunks=new ArrayList<ParsedChunk>();
            for(int unit=1;unit<=document.unitCount();unit++) {
                var page=document.page(unit);
                for(var chunk:chunker.chunks(page.text(),page.pageNumber(),chunks.size()+1))
                    chunks.add(new ParsedChunk(chunk.page(),chunk.ordinal(),chunk.content()));
            }
            if(chunks.isEmpty())throw new RagAccessException("400","未提取到可检索文本；扫描资料需要后台OCR任务，不能标记为解析成功");
            return new ParsedDocument(document.mimeType(),document.pageCount(),List.copyOf(chunks));
        } catch(RagAccessException failure){throw failure;}
        catch(Exception failure){throw new RagAccessException("400","文件解析失败，请检查文件格式或是否损坏");}
    }
}
