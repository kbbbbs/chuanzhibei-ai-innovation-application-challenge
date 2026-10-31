package com.cupk.aiacademy.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.*;

/** Exact scan of already-authorized JDBC projections. Deliberately performs no independent database query. */
@Component
public class JdbcRagVectorStore implements RagVectorStore {
    private final ObjectMapper json;
    private final IndexVersionService indexes;
    public JdbcRagVectorStore(ObjectMapper json,IndexVersionService indexes){this.json=json;this.indexes=indexes;}

    @Override public Search search(List<Map<String,Object>> rows,double[] query,String fingerprint,String model,int limit) {
        if(query==null || query.length<1 || query.length>4096 || limit<1)throw new IllegalArgumentException("Invalid vector scan parameters");
        double queryNorm=norm(query);if(!Double.isFinite(queryNorm) || queryNorm<=0)throw new IllegalArgumentException("Invalid query vector");
        var matches=new ArrayList<Match>();var reasons=new LinkedHashMap<String,Integer>();int usable=0,skipped=0;
        var seen=new HashSet<String>();
        for(var row:rows) {
            String id=Objects.toString(row.get("id"),"");
            String issue=indexes.incompatibility(row,fingerprint,model,query.length);
            if(id.isBlank())issue="CHUNK_ID_MISSING";
            else if(!seen.add(id))issue="DUPLICATE_CHUNK";
            if(issue==null) {
                Object raw=row.get("vector_json");
                if(raw==null)issue="VECTOR_MISSING";
                else try {
                    var data=json.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(raw.toString());
                    if(!data.isArray() || data.size()!=query.length)issue="VECTOR_DIMENSIONS_MISMATCH";
                    else {
                        var vector=new double[query.length];boolean numeric=true;
                        for(int i=0;i<vector.length;i++) {
                            if(!data.get(i).isNumber()){numeric=false;break;}
                            vector[i]=data.get(i).asDouble();
                        }
                        double candidateNorm=numeric?norm(vector):Double.NaN;
                        if(!Double.isFinite(candidateNorm) || Math.abs(candidateNorm-1)>0.001)issue="VECTOR_NOT_NORMALIZED";
                        else {
                            usable++;double product=0;
                            for(int i=0;i<vector.length;i++)product+=query[i]*vector[i];
                            double cosine=product/(queryNorm*candidateNorm);
                            // A nonpositive alignment is not a semantic candidate; no calibrated absolute cutoff is assumed.
                            if(Double.isFinite(cosine) && cosine>0)matches.add(new Match(id,Math.min(1,cosine)));
                        }
                    }
                } catch(Exception corrupt){issue="VECTOR_INVALID";}
            }
            if(issue!=null){skipped++;reasons.merge(issue,1,Integer::sum);}
        }
        matches.sort(Comparator.comparingDouble(Match::score).reversed().thenComparing(Match::chunkId));
        return new Search(matches.subList(0,Math.min(limit,matches.size())),usable,skipped,reasons);
    }
    private static double norm(double[] vector){double result=0;for(double value:vector){if(!Double.isFinite(value))return Double.NaN;result+=value*value;}return Math.sqrt(result);}
}
