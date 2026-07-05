package com.cupk.aiacademy.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.AgentCreateRequest;
import com.cupk.aiacademy.mapper.AgentDefinitionMapper;
import com.cupk.aiacademy.pojo.AgentDefinition;
import com.cupk.aiacademy.weknora.service.WeKnoraService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private final WeKnoraService weKnoraService;

    public AgentDefinitionService(AgentDefinitionMapper agentDefinitionMapper,
                                   ObjectMapper objectMapper,
                                   WeKnoraService weKnoraService) {
        this.agentDefinitionMapper = agentDefinitionMapper;
        this.objectMapper = objectMapper;
        this.weKnoraService = weKnoraService;
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

    @Transactional
    public Map<String, Object> uploadMarkdownDocument(Integer agentId, String knowledgeBaseId, MultipartFile file) {
        AgentDefinition agent = ensureDefinition(agentId);
        String kbId = trimToNull(knowledgeBaseId);
        if (kbId == null) {
            throw new BusinessException("knowledgeBaseId 不能为空");
        }
        String fileName = validateMarkdownFile(file);
        if (weKnoraService == null) {
            throw new BusinessException("知识库服务不可用");
        }

        Result uploadResult;
        try {
            uploadResult = weKnoraService.uploadFile(kbId, file.getBytes(), fileName);
        } catch (IOException e) {
            throw new BusinessException("Markdown 文档读取失败");
        }
        if (uploadResult == null || !"200".equals(uploadResult.getCode())) {
            String message = uploadResult == null || isBlank(uploadResult.getMsg())
                    ? "Markdown 文档上传失败"
                    : "Markdown 文档上传失败: " + uploadResult.getMsg();
            throw new BusinessException(message);
        }

        boolean attached = attachKnowledgeBase(agent, kbId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("agentId", agent.getId());
        data.put("knowledgeBaseId", kbId);
        data.put("fileName", fileName);
        data.put("attached", attached);
        data.put("knowledgeBaseIds", agent.getKnowledgeBaseIds());
        data.put("uploadResult", uploadResult.getData());
        return data;
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

    private String validateMarkdownFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Markdown 文档不能为空");
        }
        String fileName = StringUtils.getFilename(file.getOriginalFilename());
        if (isBlank(fileName)) {
            throw new BusinessException("Markdown 文件名不能为空");
        }
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith(".md") && !lowerName.endsWith(".markdown")) {
            throw new BusinessException("只支持上传 Markdown 文档(.md/.markdown)");
        }
        return fileName;
    }

    private boolean attachKnowledgeBase(AgentDefinition agent, String knowledgeBaseId) {
        ArrayNode knowledgeBaseIds = knowledgeBaseIdsArray(agent.getKnowledgeBaseIds());
        for (JsonNode item : knowledgeBaseIds) {
            if (knowledgeBaseId.equals(item.asText())) {
                agent.setKnowledgeBaseIds(writeArray(knowledgeBaseIds));
                return false;
            }
        }
        knowledgeBaseIds.add(knowledgeBaseId);
        agent.setKnowledgeBaseIds(writeArray(knowledgeBaseIds));
        agent.setUpdateTime(LocalDateTime.now());
        agentDefinitionMapper.updateById(agent);
        return true;
    }

    private ArrayNode knowledgeBaseIdsArray(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return objectMapper.createArrayNode();
        }
        try {
            JsonNode jsonNode = objectMapper.readTree(trimmed);
            if (!jsonNode.isArray()) {
                throw new BusinessException("Agent 已保存的 knowledgeBaseIds 格式错误");
            }
            return (ArrayNode) jsonNode;
        } catch (JsonProcessingException e) {
            throw new BusinessException("Agent 已保存的 knowledgeBaseIds 格式错误");
        }
    }

    private String writeArray(ArrayNode arrayNode) {
        try {
            return objectMapper.writeValueAsString(arrayNode);
        } catch (JsonProcessingException e) {
            throw new BusinessException("knowledgeBaseIds 保存失败");
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
