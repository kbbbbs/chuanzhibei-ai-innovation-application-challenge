package com.cupk.aiacademy.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RagRetrievalServiceTest {
    private final ObjectMapper mapper=new ObjectMapper();
    @Test void synonymCandidateBelowTheOldCutoffCanBeRetrievedWithoutLexicalOverlap() {
        var embeddings=configured();when(embeddings.embed(anyList())).thenReturn(List.of(new double[]{1,0}));
        var row=row("semantic","A nonlinear unit suppresses all negative input values.",new double[]{.5,Math.sqrt(.75)});
        var result=new RagRetrievalService(embeddings,new JdbcRagVectorStore(mapper,new IndexVersionService()),new IndexVersionService(),60,100).retrieve("rectifier",List.of(row),5);
        assertEquals("HYBRID",result.actualMode());assertEquals("semantic",result.ranked().getFirst().row().get("id"));
        assertNull(result.ranked().getFirst().lexicalRank());assertEquals(1,result.ranked().getFirst().vectorRank());
        assertEquals(.5,result.ranked().getFirst().vectorScore(),1e-10);
        assertEquals(1.0/61,result.ranked().getFirst().score(),1e-10);
    }

    @Test void reciprocalRanksFuseChannelsAndAreNotRawWeightedScores() {
        var embeddings=configured();when(embeddings.embed(anyList())).thenReturn(List.of(new double[]{1,0}));
        var a=row("a","gradient gradient",new double[]{.5,Math.sqrt(.75)});
        var b=row("b","gradient",new double[]{1,0});
        var service=new RagRetrievalService(embeddings,new JdbcRagVectorStore(mapper,new IndexVersionService()),new IndexVersionService(),10,50);
        var result=service.retrieve("gradient",List.of(a,b),5);
        assertEquals(2,result.ranked().size());assertEquals("a",result.ranked().getFirst().row().get("id"),"equal fused ranks break ties by stable chunk id");
        assertEquals(1.0/11+1.0/12,result.ranked().getFirst().score(),1e-12);
        assertEquals(1.0/11+1.0/12,result.ranked().get(1).score(),1e-12);
    }

    @Test void unusableIndexMetadataCannotBeAdvertisedAsSemantic() {
        var embeddings=configured();
        for(var change:List.of(Map.of("index_embedding_model","other-model"),Map.of("index_normalized",false),
                Map.of("index_state","BUILDING"),Map.of("index_chunk_revision",""),Map.of("index_embedding_fingerprint","wrong"))) {
            var broken=row("bad","gradient",new double[]{1,0});broken.putAll(change);
            var result=new RagRetrievalService(embeddings,mapper).retrieve("gradient",List.of(broken),5);
            assertEquals("KEYWORD",result.actualMode());assertEquals("NO_COMPATIBLE_VECTORS",result.degradedReason());
        }
        verify(embeddings,never()).embed(anyList());
    }

    @Test void malformedAndWrongDimensionRowsAreSkippedAndPartialCoverageIsVisible() {
        var embeddings=configured();when(embeddings.embed(anyList())).thenReturn(List.of(new double[]{1,0}));
        var good=row("good","gradient",new double[]{1,0});var broken=row("broken","gradient",new double[]{1,0});broken.put("vector_json","[not-json]");
        var dimension=row("dimension","gradient",new double[]{1,0,0});
        var result=new RagRetrievalService(embeddings,mapper).retrieve("gradient",List.of(good,broken,dimension),5);
        assertEquals("HYBRID",result.actualMode());assertEquals("PARTIAL_VECTOR_COVERAGE",result.degradedReason());
        assertTrue(result.warning().contains("部分"));
        assertNotNull(result.ranked().stream().filter(r->r.row().get("id").equals("good")).findFirst().orElseThrow().vectorRank());
        assertTrue(result.ranked().stream().filter(r->!r.row().get("id").equals("good")).allMatch(r->r.vectorRank()==null));
    }

    @Test void providerFailureFallsBackToKeywordWithAStableSafeReason() {
        var embeddings=configured();when(embeddings.embed(anyList())).thenThrow(new IllegalStateException("private provider details"));
        var result=new RagRetrievalService(embeddings,mapper).retrieve("gradient",List.of(row("one","gradient descent",new double[]{1,0})),5);
        assertEquals("KEYWORD",result.actualMode());assertEquals("EMBEDDING_UNAVAILABLE",result.degradedReason());
        assertEquals(1,result.ranked().size());assertFalse(result.warning().contains("private provider"));
    }

    @Test void emptyAndStopwordQueriesDoNotInvokeTheProvider() {
        var embeddings=configured();var service=new RagRetrievalService(embeddings,mapper);
        assertEquals("EMPTY_INDEX",service.retrieve("gradient",List.of(),5).degradedReason());
        var result=service.retrieve("what the",List.of(row("one","gradient",new double[]{1,0})),5);
        assertEquals("NONE",result.actualMode());assertEquals("EMPTY_QUERY_TERMS",result.degradedReason());
        verify(embeddings,never()).embed(anyList());
    }

    @Test void nonpositiveCosineIsNotAPositiveSemanticMatchAndCandidatesAreBounded() {
        var embeddings=configured();when(embeddings.embed(anyList())).thenReturn(List.of(new double[]{1,0}));
        var rows=List.of(row("negative","unrelated alpha",new double[]{-1,0}),row("zero","unrelated beta",new double[]{0,1}),
                row("a","unrelated gamma",new double[]{1,0}),row("b","unrelated delta",new double[]{1,0}));
        var service=new RagRetrievalService(embeddings,new JdbcRagVectorStore(mapper,new IndexVersionService()),new IndexVersionService(),60,1);
        var result=service.retrieve("rectifier",rows,5);
        assertEquals(List.of("a"),result.ranked().stream().map(r->r.row().get("id")).toList());
    }

    @Test void duplicateChunkRowsCannotDoubleCountRrfOrProviderCandidates() {
        var embeddings=configured();when(embeddings.embed(anyList())).thenReturn(List.of(new double[]{1,0}));
        var item=row("one","gradient",new double[]{1,0});
        var result=new RagRetrievalService(embeddings,new JdbcRagVectorStore(mapper,new IndexVersionService()),new IndexVersionService(),60,100).retrieve("gradient",List.of(item,item),5);
        assertEquals(1,result.ranked().size());assertEquals(2.0/61,result.ranked().getFirst().score(),1e-12);
    }

    @Test void allCorruptOrWrongDimensionVectorsDegradeRatherThanClaimHybrid() {
        var embeddings=configured();when(embeddings.embed(anyList())).thenReturn(List.of(new double[]{1,0,0}));
        var result=new RagRetrievalService(embeddings,mapper).retrieve("gradient",List.of(row("one","gradient",new double[]{1,0})),5);
        assertEquals("KEYWORD",result.actualMode());assertEquals("INCOMPATIBLE_VECTORS",result.degradedReason());
    }

    @Test void jdbcUppercaseProjectionRetainsNullsAndCaseInsensitiveColumnSemantics() {
        var embeddings=configured();when(embeddings.embed(anyList())).thenReturn(List.of(new double[]{1,0}));
        var uppercase=new LinkedHashMap<String,Object>();row("upper","gradient",new double[]{1,0}).forEach((key,value)->uppercase.put(key.toUpperCase(Locale.ROOT),value));
        uppercase.put("OPTIONAL_COLUMN",null);
        var result=new RagRetrievalService(embeddings,mapper).retrieve("gradient",List.of(uppercase),5);
        assertEquals("HYBRID",result.actualMode());assertEquals("upper",result.ranked().getFirst().row().get("id"));
        assertTrue(result.ranked().getFirst().row().containsKey("optional_column"));
    }

    @Test void replacementStoreCannotSupplyForeignOrDuplicateMatchesThatPolluteRanks() {
        var embeddings=configured();when(embeddings.embed(anyList())).thenReturn(List.of(new double[]{1,0}));
        RagVectorStore store=(rows,query,fp,model,limit)->new RagVectorStore.Search(List.of(
                new RagVectorStore.Match("outside-scope",1),new RagVectorStore.Match("one",.8),new RagVectorStore.Match("one",.7)),1,0,Map.of());
        var service=new RagRetrievalService(embeddings,store,new IndexVersionService(),60,100);
        var result=service.retrieve("rectifier",List.of(row("one","unrelated",new double[]{1,0})),5);
        assertEquals(1,result.ranked().size());assertEquals(1,result.ranked().getFirst().vectorRank());
        assertEquals(1.0/61,result.ranked().getFirst().score(),1e-12);
    }

    private RagEmbeddingClient configured(){var client=mock(RagEmbeddingClient.class);when(client.isConfigured()).thenReturn(true);when(client.isRequested()).thenReturn(true);when(client.fingerprint()).thenReturn("fp");when(client.modelName()).thenReturn("model");return client;}
    static Map<String,Object> row(String id,String content,double[] vector) {
        var row=new LinkedHashMap<String,Object>();row.put("id",id);row.put("document_id","doc");row.put("index_version","index");
        row.put("document_version",1);row.put("parse_revision",1);row.put("index_document_version",1);row.put("index_parse_revision",1);row.put("content",content);row.put("vector_model","fp");
        row.put("vector_json",Arrays.toString(vector));row.put("index_embedding_fingerprint","fp");row.put("index_embedding_model","model");
        row.put("index_dimensions",2);row.put("index_normalized",true);row.put("index_chunk_revision","native-formula-chunker-v3");row.put("index_state","HYBRID_READY");return row;
    }
}
