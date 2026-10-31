package com.cupk.aiacademy.rag;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Objects;

/** Read-side contract for a chunk's exact published index. Version activation remains in its repository. */
@Component
public class IndexVersionService {
    public boolean compatible(Map<String,Object> row,String fingerprint,String model,Integer queryDimensions) {
        return incompatibility(row,fingerprint,model,queryDimensions)==null;
    }

    public String incompatibility(Map<String,Object> row,String fingerprint,String model,Integer queryDimensions) {
        if(row==null || blank(row.get("index_version")))return "INDEX_METADATA_MISSING";
        if(!"HYBRID_READY".equals(row.get("index_state")))return "INDEX_NOT_HYBRID_READY";
        if(fingerprint==null || fingerprint.isBlank() || !Objects.equals(fingerprint,row.get("vector_model"))
                || !Objects.equals(fingerprint,row.get("index_embedding_fingerprint")))return "MODEL_FINGERPRINT_MISMATCH";
        if(model==null || model.isBlank() || !Objects.equals(model,row.get("index_embedding_model")))return "MODEL_NAME_MISMATCH";
        Integer dimensions=positive(row.get("index_dimensions"));
        if(dimensions==null || dimensions>4096 || queryDimensions!=null && !dimensions.equals(queryDimensions))return "DIMENSIONS_MISMATCH";
        Object normalized=row.get("index_normalized");
        if(!(Boolean.TRUE.equals(normalized) || normalized instanceof Number n && n.intValue()==1))return "NORMALIZATION_UNVERIFIED";
        if(blank(row.get("index_chunk_revision")))return "CHUNK_REVISION_MISSING";
        Integer document=positive(row.get("document_version")),parse=positive(row.get("parse_revision"));
        if(document==null || parse==null)return "SOURCE_REVISION_MISSING";
        if(!document.equals(positive(row.get("index_document_version"))))return "SOURCE_REVISION_MISMATCH";
        if(!parse.equals(positive(row.get("index_parse_revision"))))return "SOURCE_REVISION_MISMATCH";
        return null;
    }
    private boolean blank(Object value){return value==null || value.toString().isBlank();}
    private Integer positive(Object value){if(!(value instanceof Number n))return null;int result=n.intValue();return result>0?result:null;}
}
