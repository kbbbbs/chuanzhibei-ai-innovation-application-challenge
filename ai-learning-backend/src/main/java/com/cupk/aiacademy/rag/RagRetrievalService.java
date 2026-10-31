package com.cupk.aiacademy.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.*;

/** Ranked lexical/vector retrieval over a caller-authorized, version-pinned snapshot. RRF is not confidence. */
@Service
public class RagRetrievalService {
    public record Ranked(Map<String,Object> row,double score,Integer lexicalRank,Integer vectorRank,double lexicalScore,Double vectorScore) { }
    public record Retrieval(List<Ranked> ranked,String actualMode,String degradedReason,String warning) {
        public Retrieval {ranked=List.copyOf(ranked);}
    }
    private final RagEmbeddingClient embeddings;
    private final RagVectorStore vectors;
    private final IndexVersionService indexes;
    private final int rrfK,candidateLimit;
    public RagRetrievalService(RagEmbeddingClient embeddings,ObjectMapper json) {
        this(embeddings,new JdbcRagVectorStore(json,new IndexVersionService()),new IndexVersionService(),1,20);
    }
    @Autowired public RagRetrievalService(RagEmbeddingClient embeddings,RagVectorStore vectors,IndexVersionService indexes,
            @Value("${rag.retrieval.rrf-k:1}") int rrfK,@Value("${rag.retrieval.candidate-limit:20}") int candidateLimit) {
        if(rrfK<1 || rrfK>10000 || candidateLimit<1 || candidateLimit>2000)throw new IllegalArgumentException("Invalid retrieval ranking configuration");
        this.embeddings=embeddings;this.vectors=vectors;this.indexes=indexes;this.rrfK=rrfK;this.candidateLimit=candidateLimit;
    }

    public Retrieval retrieve(String query,List<Map<String,Object>> authorizedRows,int requested) {
        Objects.requireNonNull(authorizedRows,"Authorized scope is required");
        if(authorizedRows.size()>20000)throw new RagAccessException("400","检索范围超过2万片段，请缩小知识库范围");
        if(authorizedRows.isEmpty())return new Retrieval(List.of(),"NONE","EMPTY_INDEX","当前范围没有有效索引，请先上传并解析资料。");
        if(query==null || query.isBlank() || RagBm25.tokenize(query).isEmpty())return new Retrieval(List.of(),"NONE","EMPTY_QUERY_TERMS","问题缺少可检索的实词，请补充具体概念。");
        var unique=new LinkedHashMap<String,Map<String,Object>>();
        for(var source:authorizedRows) {
            // Spring JDBC column maps are case-insensitive; snapshots must retain those semantics.
            // A plain copy otherwise turns default H2 upper-case columns into apparently missing data.
            var row=new LinkedHashMap<String,Object>();source.forEach((key,value)->row.put(key.toLowerCase(Locale.ROOT),value));
            String id=Objects.toString(row.get("id"),"");if(!id.isBlank())unique.putIfAbsent(id,Collections.unmodifiableMap(row));
        }
        var rows=new ArrayList<>(unique.values());
        double[] lexical=RagBm25.score(query,rows.stream().map(row->Objects.toString(row.get("content"),"")).toList());
        var lexicalOrder=new ArrayList<Integer>();
        for(int i=0;i<rows.size();i++)if(lexical[i]>0 && Double.isFinite(lexical[i]))lexicalOrder.add(i);
        lexicalOrder.sort(Comparator.<Integer>comparingDouble(i->lexical[i]).reversed().thenComparing(i->rows.get(i).get("id").toString()));
        Map<String,Integer> lexicalRanks=new HashMap<>();Map<String,Double> lexicalScores=new HashMap<>();
        for(int rank=0;rank<Math.min(candidateLimit,lexicalOrder.size());rank++) {
            int i=lexicalOrder.get(rank);String id=rows.get(i).get("id").toString();lexicalRanks.put(id,rank+1);lexicalScores.put(id,lexical[i]);
        }
        Map<String,Integer> vectorRanks=new HashMap<>();Map<String,Double> vectorScores=new HashMap<>();
        String actual="KEYWORD",reason=null,warning=null;
        if(!embeddings.isConfigured()) {
            reason=embeddings.isRequested()?"EMBEDDING_NOT_CONFIGURED":"EMBEDDING_DISABLED";
            warning=embeddings.isRequested()?"向量配置不完整，实际使用BM25关键词检索。":"当前使用BM25关键词检索；未启用语义向量检索。";
        } else {
            String fingerprint=embeddings.fingerprint(),model=embeddings.modelName();
            if(rows.stream().noneMatch(row->row.get("vector_json")!=null && indexes.compatible(row,fingerprint,model,null))) {
                reason="NO_COMPATIBLE_VECTORS";warning="所选文档没有兼容当前模型的有效向量，实际使用BM25关键词检索；请重新建立向量索引。";
            } else try {
                var queryVectors=embeddings.embed(List.of(query));
                if(queryVectors==null || queryVectors.size()!=1)throw new IllegalStateException("Incomplete query embedding");
                var search=vectors.search(rows,queryVectors.getFirst(),fingerprint,model,candidateLimit);
                if(search.usableCount()==0) {
                    reason="INCOMPATIBLE_VECTORS";warning="持久化向量与当前查询不兼容，实际使用BM25关键词检索；请重新建立向量索引。";
                } else {
                    actual="HYBRID";
                    for(var match:search.matches()) {
                        var row=unique.get(match.chunkId());
                        // A replacement backend cannot widen scope, inject duplicates, or assign stale metadata.
                        if(row==null || vectorRanks.containsKey(match.chunkId()) || !Double.isFinite(match.score()) || match.score()<=0
                                || !indexes.compatible(row,fingerprint,model,queryVectors.getFirst().length))continue;
                        vectorRanks.put(match.chunkId(),vectorRanks.size()+1);vectorScores.put(match.chunkId(),match.score());
                        if(vectorRanks.size()>=candidateLimit)break;
                    }
                    if(search.skippedCount()>0){reason="PARTIAL_VECTOR_COVERAGE";warning="部分片段缺少兼容的有效向量，仅对其使用关键词检索；请重新建立向量索引。";}
                }
            } catch(RuntimeException provider) {
                reason="EMBEDDING_UNAVAILABLE";warning="向量服务不可用，已明确降级为BM25关键词检索。";
            }
        }
        var ranked=new ArrayList<Ranked>();
        for(var row:rows) {
            String id=row.get("id").toString();Integer a=lexicalRanks.get(id),b=vectorRanks.get(id);
            if(a==null && b==null)continue;
            double score=(a==null?0:1.0/(rrfK+a))+(b==null?0:1.0/(rrfK+b));
            ranked.add(new Ranked(row,score,a,b,lexicalScores.getOrDefault(id,0.0),vectorScores.get(id)));
        }
        ranked.sort(Comparator.comparingDouble(Ranked::score).reversed().thenComparing(rank->rank.row().get("id").toString()));
        return new Retrieval(ranked.subList(0,Math.min(Math.max(1,Math.min(20,requested)),ranked.size())),actual,reason,warning);
    }
}
