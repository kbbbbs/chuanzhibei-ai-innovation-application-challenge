package com.cupk.aiacademy.rag.chat;

import com.cupk.aiacademy.rag.RagSearchHit;
import java.util.List;

public record RagChatResult(String sessionId, String answer, List<RagSearchHit> citations,
                            String mode, String status, String warning,String actualMode,String degradedReason,
                            List<com.cupk.aiacademy.rag.RagIndexSnapshot> indexVersions) {
    public RagChatResult {indexVersions=indexVersions==null?List.of():List.copyOf(indexVersions);}
    public RagChatResult(String sessionId,String answer,List<RagSearchHit> citations,String mode,String status,String warning){
        this(sessionId,answer,citations,mode,status,warning,null,null,List.of());
    }
}
