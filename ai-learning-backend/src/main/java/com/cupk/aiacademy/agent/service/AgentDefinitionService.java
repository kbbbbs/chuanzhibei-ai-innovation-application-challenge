package com.cupk.aiacademy.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.dto.AgentCreateRequest;
import com.cupk.aiacademy.mapper.AgentDefinitionMapper;
import com.cupk.aiacademy.pojo.AgentDefinition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 定义管理服务。
 * 负责维护 t_agent 中的系统提示词、模型参数、知识库和工具配置，
 * 这些配置会在 AgentChatService 中按会话 agentId 生效。
 */
@Service
public class AgentDefinitionService {
    private static final int ACTIVE_STATUS = 1;
    private static final int INACTIVE_STATUS = 0;

    private final AgentDefinitionMapper agentDefinitionMapper;
    private final ObjectMapper objectMapper;

    public AgentDefinitionService(AgentDefinitionMapper agentDefinitionMapper,
                                  ObjectMapper objectMapper) {
        this.agentDefinitionMapper = agentDefinitionMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<AgentDefinition> listDefinitions(String keyword, Integer status) {
        QueryWrapper<AgentDefinition> query = new QueryWrapper<>();
        if (status != null) {
            query.eq("status", normalizeStatus(status));
        }
        if (!isBlank(keyword)) {
            String value = keyword.trim();
            query.and(wrapper -> wrapper.like("code", value).or().like("name", value));
        }
        query.orderByDesc("update_time").orderByDesc("id");
        return agentDefinitionMapper.selectList(query);
    }

    @Transactional(readOnly = true)
    public AgentDefinition getDefinition(Integer agentId) {
        return ensureDefinition(agentId);
    }

    @Transactional
    public AgentDefinition createDefinition(AgentCreateRequest request) {
        validateRequest(request);
        String code = request.getCode().trim();
        ensureCodeUnique(code, null);

        AgentDefinition agent = new AgentDefinition();
        applyRequest(agent, request);
        LocalDateTime now = LocalDateTime.now();
        agent.setCreateTime(now);
        agent.setUpdateTime(now);
        agentDefinitionMapper.insert(agent);
        return agent;
    }

    @Transactional
    public AgentDefinition updateDefinition(Integer agentId, AgentCreateRequest request) {
        validateRequest(request);
        AgentDefinition agent = ensureDefinition(agentId);
        String code = request.getCode().trim();
        ensureCodeUnique(code, agentId);

        applyRequest(agent, request);
        agent.setUpdateTime(LocalDateTime.now());
        agentDefinitionMapper.updateById(agent);
        return agent;
    }

    @Transactional
    public AgentDefinition updateStatus(Integer agentId, Integer status) {
        AgentDefinition agent = ensureDefinition(agentId);
        agent.setStatus(normalizeStatus(status));
        agent.setUpdateTime(LocalDateTime.now());
        agentDefinitionMapper.updateById(agent);
        return agent;
    }

    private void validateRequest(AgentCreateRequest request) {
        if (request == null) {
            throw new BusinessException("Agent 信息不能为空");
        }
        if (isBlank(request.getCode())) {
            throw new BusinessException("Agent 编码不能为空");
        }
        if (isBlank(request.getName())) {
            throw new BusinessException("Agent 名称不能为空");
        }
        if (request.getStatus() != null) {
            normalizeStatus(request.getStatus());
        }
        normalizeJsonArrayField(request.getKnowledgeBaseIds(), "knowledgeBaseIds");
        normalizeJsonArrayField(request.getEnabledTools(), "enabledTools");
    }

    private void applyRequest(AgentDefinition agent, AgentCreateRequest request) {
        agent.setCode(request.getCode().trim());
        agent.setName(request.getName().trim());
        agent.setDescription(trimToNull(request.getDescription()));
        agent.setSystemPrompt(trimToNull(request.getSystemPrompt()));
        agent.setModel(trimToNull(request.getModel()));
        agent.setTemperature(request.getTemperature());
        agent.setKnowledgeBaseIds(normalizeJsonArrayField(request.getKnowledgeBaseIds(), "knowledgeBaseIds"));
        agent.setEnabledTools(normalizeJsonArrayField(request.getEnabledTools(), "enabledTools"));
        agent.setStatus(request.getStatus() == null ? ACTIVE_STATUS : normalizeStatus(request.getStatus()));
    }

    private String normalizeJsonArrayField(String value, String fieldName) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            JsonNode jsonNode = objectMapper.readTree(trimmed);
            if (!jsonNode.isArray()) {
                throw new BusinessException(fieldName + " 必须是 JSON 数组");
            }
            return objectMapper.writeValueAsString(jsonNode);
        } catch (JsonProcessingException e) {
            throw new BusinessException(fieldName + " 必须是 JSON 数组");
        }
    }

    private AgentDefinition ensureDefinition(Integer agentId) {
        if (agentId == null) {
            throw new BusinessException("agentId 不能为空");
        }
        AgentDefinition agent = agentDefinitionMapper.selectById(agentId);
        if (agent == null) {
            throw new BusinessException("Agent 不存在");
        }
        return agent;
    }

    private void ensureCodeUnique(String code, Integer excludeId) {
        QueryWrapper<AgentDefinition> query = new QueryWrapper<>();
        query.eq("code", code);
        if (excludeId != null) {
            query.ne("id", excludeId);
        }
        if (agentDefinitionMapper.selectCount(query) > 0) {
            throw new BusinessException("Agent 编码已存在");
        }
    }

    private int normalizeStatus(Integer status) {
        if (status == null) {
            throw new BusinessException("status 不能为空");
        }
        if (status != ACTIVE_STATUS && status != INACTIVE_STATUS) {
            throw new BusinessException("status 只能为 0 或 1");
        }
        return status;
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
