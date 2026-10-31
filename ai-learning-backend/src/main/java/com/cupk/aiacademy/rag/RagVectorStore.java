package com.cupk.aiacademy.rag;

import java.util.List;
import java.util.Map;

/** Replaceable vector access boundary. Implementations may only use the supplied authorized scope. */
public interface RagVectorStore {
    record Match(String chunkId,double score) { }
    record Search(List<Match> matches,int usableCount,int skippedCount,Map<String,Integer> skippedReasons) {
        public Search {matches=List.copyOf(matches);skippedReasons=Map.copyOf(skippedReasons);}
    }
    Search search(List<Map<String,Object>> authorizedRows,double[] query,String fingerprint,String model,int limit);
}
