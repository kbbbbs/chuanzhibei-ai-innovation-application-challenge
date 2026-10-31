package com.cupk.aiacademy.agent.knowledge;

import com.cupk.aiacademy.agent.core.config.AgentChatProperties;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.pojo.AgentDefinition;
import com.cupk.aiacademy.rag.NativeRagService;
import com.cupk.aiacademy.rag.RagSearchHit;
import com.cupk.aiacademy.rag.RagSearchRequest;
import com.cupk.aiacademy.rag.RagSearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Primary
public class NativeKnowledgeContextProvider implements KnowledgeContextProvider {
    private final NativeRagService knowledge;
    private final ObjectMapper json;
    private final AgentChatProperties limits;

    public NativeKnowledgeContextProvider(NativeRagService knowledge, ObjectMapper json, AgentChatProperties limits) {
        this.knowledge = knowledge;
        this.json = json;
        this.limits = limits;
    }

    @Override
    public void requireReadableEvidence(List<RagSearchHit> citations) {
        if (citations != null && !citations.isEmpty())
            knowledge.requireReadableBaseIds(citations.stream().map(RagSearchHit::kbId).distinct().toList());
    }

    @Override
    public String retrieve(AgentDefinition agent, String query) {
        return retrieveWithEvidence(agent, query).context();
    }

    @Override
    public Retrieval retrieveWithEvidence(AgentDefinition agent, String query) {
        if (agent == null || agent.getKnowledgeBaseIds() == null || agent.getKnowledgeBaseIds().isBlank()) {
            return Retrieval.empty();
        }
        List<String> ids = new ArrayList<>();
        try {
            var stored = json.readTree(agent.getKnowledgeBaseIds());
            if (!stored.isArray()) throw new BusinessException("Agent 知识库配置不是数组，请重新绑定本项目知识库");
            for (var id : stored) {
                if (!id.isTextual()) throw new BusinessException("Agent 知识库配置无效，请重新绑定本项目知识库");
                ids.add(id.asText());
            }
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw new BusinessException("Agent 知识库配置无法读取，请重新绑定本项目知识库");
        }
        if (ids.isEmpty()) return Retrieval.empty();
        knowledge.requireReadableBaseIds(ids);
        if (limits.getKnowledgeContextMaxCharacters() <= 0 || limits.getKnowledgeContextMaxSnippets() <= 0) {
            return new Retrieval("当前已绑定资料，但知识检索被配置为关闭；不得声称回答来自资料。", List.of(),
                    "disabled", "RETRIEVAL_DISABLED", "知识检索已关闭");
        }
        Result result = knowledge.search(new RagSearchRequest(ids, query, limits.getKnowledgeContextMaxSnippets()));
        if (result == null || !"200".equals(result.getCode())) {
            throw new BusinessException("本项目知识检索失败，请稍后重试");
        }
        RagSearchResponse found = json.convertValue(result.getData(), RagSearchResponse.class);
        if (found.hits().isEmpty()) {
            return new Retrieval("本次没有检索到可支持回答的资料。明确说明无法从当前资料确认；不能编造来源标号。",
                    List.of(), found.mode(), "NO_EVIDENCE", found.warning());
        }
        StringBuilder context = new StringBuilder();
        List<RagSearchHit> selected = new ArrayList<>();
        for (RagSearchHit hit : found.hits()) {
            String label = "[" + (selected.size() + 1) + "]";
            String prefix = label + " 来源：" + hit.title() + (hit.page() == null ? "；未分页" : "；页码：" + hit.page())
                    + "；片段：" + hit.chunkId() + "\n";
            int room = limits.getKnowledgeContextMaxCharacters() - context.length() - prefix.length() - 2;
            if (room < 80) break;
            String snippet = com.cupk.aiacademy.rag.formula.FormulaText.safePrefix(hit.content(),room);
            if(snippet.isBlank())continue;
            context.append(prefix).append(snippet).append("\n\n");
            selected.add(new RagSearchHit(hit.chunkId(), hit.documentId(), hit.kbId(), hit.title(), snippet,
                    hit.page(), hit.ordinal(), hit.score(), label,hit.visuals(),hit.indexVersion(),hit.documentVersion(),hit.parseRevision()));
        }
        return new Retrieval(context.toString(), selected, found.mode(), selected.isEmpty() ? "NO_EVIDENCE" : "RETRIEVED",
                found.warning());
    }
}
