package com.cupk.aiacademy.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.agent.core.service.AgentChatService;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.Difficulty;
import com.cupk.aiacademy.common.ForbiddenException;
import com.cupk.aiacademy.common.QuestionType;
import com.cupk.aiacademy.common.RecordStatus;
import com.cupk.aiacademy.dto.GenerateQuestionsFromKbRequest;
import com.cupk.aiacademy.dto.QuestionRequestDTO;
import com.cupk.aiacademy.dto.QuestionResponseDTO;
import com.cupk.aiacademy.mapper.AgentDefinitionMapper;
import com.cupk.aiacademy.mapper.ExamAnswerMapper;
import com.cupk.aiacademy.mapper.ExamPaperQuestionMapper;
import com.cupk.aiacademy.mapper.QuestionMapper;
import com.cupk.aiacademy.pojo.AgentDefinition;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.Question;
import com.cupk.aiacademy.weknora.dto.WeKnoraSearchRequest;
import com.cupk.aiacademy.weknora.service.WeKnoraService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class QuestionService {
    private static final TypeReference<List<QuestionRequestDTO.QuestionOptionRequest>> OPTION_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<QuestionRequestDTO.QuestionAnswerRequest>> ANSWER_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final QuestionMapper questionMapper;
    private final ObjectMapper objectMapper;
    private final WeKnoraService weKnoraService;
    private final PermissionService permissionService;
    private final AgentChatService agentChatService;
    private final AgentDefinitionMapper agentDefinitionMapper;
    private final ExamPaperQuestionMapper examPaperQuestionMapper;
    private final ExamAnswerMapper examAnswerMapper;
    private final OpenAiChatModel chatModel;

    public QuestionService(QuestionMapper questionMapper, ObjectMapper objectMapper,
                           WeKnoraService weKnoraService,
                           PermissionService permissionService,
                           AgentChatService agentChatService,
                           AgentDefinitionMapper agentDefinitionMapper,
                           ExamPaperQuestionMapper examPaperQuestionMapper,
                           ExamAnswerMapper examAnswerMapper,
                           OpenAiChatModel chatModel) {
        this.questionMapper = questionMapper;
        this.objectMapper = objectMapper;
        this.weKnoraService = weKnoraService;
        this.permissionService = permissionService;
        this.agentChatService = agentChatService;
        this.agentDefinitionMapper = agentDefinitionMapper;
        this.examPaperQuestionMapper = examPaperQuestionMapper;
        this.examAnswerMapper = examAnswerMapper;
        this.chatModel = chatModel;
    }

    @Transactional
    public QuestionResponseDTO create(QuestionRequestDTO request) {
        validateQuestionRules(request);
        prepareForWrite(request, null);
        Question question = new Question();
        fillQuestion(question, request);
        LocalDateTime now = LocalDateTime.now();
        question.setCreateTime(now);
        question.setUpdateTime(now);
        questionMapper.insert(question);
        return toResponse(question);
    }

    @Transactional
    public QuestionResponseDTO update(Long id, QuestionRequestDTO request) {
        validateQuestionRules(request);
        Question question = getEntityById(id);
        requireQuestionTeacher(question);
        prepareForWrite(request, question);
        fillQuestion(question, request);
        question.setUpdateTime(LocalDateTime.now());
        questionMapper.updateById(question);
        return toResponse(question);
    }

    @Transactional(readOnly = true)
    public QuestionResponseDTO getById(Long id) {
        Question question = getEntityById(id);
        boolean includeAnswers = canReadWithAnswers(question);
        return toResponse(question, includeAnswers);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<QuestionResponseDTO> list(
            QuestionType type,
            Difficulty difficulty,
            RecordStatus status,
            Long courseId,
            Long teacherId,
            int page,
            int size) {
        return list(type, difficulty, status, courseId, teacherId, null, page, size);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<QuestionResponseDTO> list(
            QuestionType type,
            Difficulty difficulty,
            RecordStatus status,
            Long courseId,
            Long teacherId,
            String keyword,
            int page,
            int size) {
        int pageIndex = Math.max(page, 0);
        int pageSize = size <= 0 ? 10 : size;
        Page<Question> mpPage = new Page<>(pageIndex + 1L, pageSize);
        QueryWrapper<Question> query = new QueryWrapper<>();
        if (type != null) {
            query.eq("question_type", type.getStorageValue());
        }
        if (difficulty != null) {
            query.eq("difficulty", difficulty.getCode());
        }
        boolean includeAnswers = true;
        var user = permissionService.currentUser();
        if (permissionService.isTeacher()) {
            query.eq("teacher_id", user.getUserId());
        } else if (permissionService.isStudent()) {
            includeAnswers = false;
            query.eq("status", RecordStatus.ACTIVE.getCode());
            List<Integer> courseIds = permissionService.studentCourseIds(user.getUserId());
            if (courseIds.isEmpty()) {
                return new PageImpl<>(List.of(), PageRequest.of(pageIndex, pageSize), 0);
            }
            if (courseId != null && courseId > 0) {
                if (!courseIds.contains(courseId.intValue())) {
                    return new PageImpl<>(List.of(), PageRequest.of(pageIndex, pageSize), 0);
                }
                query.eq("course_id", courseId);
            } else {
                query.in("course_id", courseIds);
            }
        } else {
            if (teacherId != null && teacherId > 0) {
                query.eq("teacher_id", teacherId);
            }
            if (courseId != null && courseId > 0) {
                query.eq("course_id", courseId);
            }
            if (status != null) {
                query.eq("status", status.getCode());
            }
        }
        if ("teacher".equals(user.getRoleCode())) {
            if (status != null) {
                query.eq("status", status.getCode());
            }
            if (courseId != null && courseId > 0) {
                query.eq("course_id", courseId);
            }
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            String term = keyword.trim();
            query.and(wrapper -> wrapper.like("title", term)
                    .or()
                    .like("analysis", term)
                    .or()
                    .like("knowledge_point", term));
        }
        query.orderByDesc("id");
        questionMapper.selectPage(mpPage, query);
        boolean finalIncludeAnswers = includeAnswers;
        List<QuestionResponseDTO> records = mpPage.getRecords().stream()
                .map(question -> toResponse(question, finalIncludeAnswers))
                .toList();
        return new PageImpl<>(records, PageRequest.of(pageIndex, pageSize), mpPage.getTotal());
    }

    @Transactional
    public void delete(Long id) {
        Question question = getEntityById(id);
        requireQuestionTeacher(question);
        ensureQuestionNotInUse(question.getId());
        questionMapper.deleteById(question.getId());
    }

    // ==================== PDF 提取 ====================

    private static final String EXTRACTION_PROMPT = """
            你是EduLoop智教云环平台的题库构建助手。请仔细阅读上传的教学文档，提取其中所有的题目。
            严格按照以下JSON数组格式返回，不要输出任何JSON之外的内容，不要用markdown代码块包裹：
            [
              {
                "type": "SINGLE_CHOICE",
                "content": "题目题干内容",
                "options": [{"optionKey": "A", "optionContent": "选项内容", "sortOrder": 1}],
                "answers": [{"answerContent": "正确答案", "sortOrder": 1}],
                "analysis": "题目解析，帮助学生理解知识点",
                "knowledgePoint": "知识点",
                "difficulty": "EASY",
                "score": 5
              }
            ]
            type取值：SINGLE_CHOICE/MULTIPLE_CHOICE/TRUE_FALSE/FILL_BLANK/SHORT_ANSWER
            difficulty取值：EASY/MEDIUM/HARD
            选择题必须包含options数组，非选择题不需要options。
            TRUE_FALSE的options为[{"optionKey":"A","optionContent":"正确","sortOrder":1},{"optionKey":"B","optionContent":"错误","sortOrder":2}]，答案填A或B。
            如果文档中没有找到任何题目，返回空数组[]。""";

    public List<QuestionRequestDTO> extractQuestions(MultipartFile file, Long courseId, Long teacherId) {
        permissionService.requireTeacherOrAdmin();
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".pdf")) {
            throw new BusinessException("仅支持PDF文件");
        }

        String kbId = findOrCreateQuestionKB();

        // 1. 上传 PDF 到 WeKnora 知识库
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (Exception e) {
            throw new BusinessException("读取文件失败: " + e.getMessage());
        }
        var uploadResult = weKnoraService.uploadFile(kbId, fileBytes, originalName);
        if (uploadResult.getCode() != null && !"200".equals(uploadResult.getCode())) {
            throw new BusinessException("上传文档到知识库失败: " + uploadResult.getMsg());
        }

        // 2. 创建会话
        var sessionReq = new com.cupk.aiacademy.weknora.dto.WeKnoraSessionRequest();
        sessionReq.setKnowledgeBaseId(kbId);
        sessionReq.setTitle("EduLoop题库提取-" + System.currentTimeMillis());
        var sessionResult = weKnoraService.createSession(sessionReq);
        if (sessionResult.getCode() != null && !"200".equals(sessionResult.getCode())) {
            throw new BusinessException("创建会话失败: " + sessionResult.getMsg());
        }
        String sessionId = extractSessionId(sessionResult);

        // 3. 调用 AI 提取题目
        String rawResponse;
        try {
            rawResponse = weKnoraService.chatSync(sessionId, EXTRACTION_PROMPT);
        } catch (Exception e) {
            throw new BusinessException("AI提取失败: " + e.getMessage());
        }

        // 4. 解析 JSON
        List<QuestionRequestDTO> questions = parseExtractedJson(rawResponse, courseId, teacherId);
        return questions;
    }

    public List<QuestionRequestDTO> generateFromKnowledgeBase(GenerateQuestionsFromKbRequest request, Long teacherId) {
        if (request.getKnowledgeBaseId() == null || request.getKnowledgeBaseId().isBlank()) {
            throw new BusinessException("知识库不能为空");
        }
        if (request.getCourseId() == null) {
            throw new BusinessException("请选择课程");
        }
        int count = request.getCount() != null && request.getCount() > 0 ? request.getCount() : 5;
        if (count > 20) count = 20;
        List<String> types = request.getTypes() != null && !request.getTypes().isEmpty()
                ? request.getTypes() : List.of("SINGLE_CHOICE", "TRUE_FALSE", "SHORT_ANSWER");
        String difficulty = request.getDifficulty() != null ? request.getDifficulty() : "MEDIUM";

        String systemPrompt = "你是EduLoop智教云环平台的题库构建助手。请基于知识库内容生成题目。";
        String model = null;
        Double temperature = null;
        List<String> kbIds = new ArrayList<>();
        kbIds.add(request.getKnowledgeBaseId());

        if (request.getAgentId() != null) {
            AgentDefinition agent = agentDefinitionMapper.selectById(request.getAgentId());
            if (agent != null && agent.getSystemPrompt() != null && !agent.getSystemPrompt().isBlank()) {
                systemPrompt = agent.getSystemPrompt();
            }
            if (agent != null && agent.getModel() != null && !agent.getModel().isBlank()) {
                model = agent.getModel().trim();
            }
            if (agent != null && agent.getTemperature() != null) {
                temperature = agent.getTemperature().doubleValue();
            }
            if (agent != null && agent.getKnowledgeBaseIds() != null) {
                try {
                    var arr = objectMapper.readTree(agent.getKnowledgeBaseIds().trim());
                    if (arr.isArray()) {
                        for (var node : arr) {
                            String id = node.asText().trim();
                            if (!id.isEmpty() && !kbIds.contains(id)) {
                                kbIds.add(id);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        // 检索知识上下文
        StringBuilder knowledgeContext = new StringBuilder();
        if (weKnoraService != null) {
            try {
                var searchResult = weKnoraService.search(new WeKnoraSearchRequest(kbIds, "知识库内容概要", null, null));
                if (searchResult != null && "200".equals(searchResult.getCode()) && searchResult.getData() != null) {
                    var root = objectMapper.valueToTree(searchResult.getData());
                    List<String> snippets = new ArrayList<>();
                    collectSnippets(root, snippets, 10);
                    for (int i = 0; i < snippets.size(); i++) {
                        if (knowledgeContext.length() > 0) knowledgeContext.append("\n");
                        knowledgeContext.append("[").append(i + 1).append("] ").append(snippets.get(i));
                    }
                }
            } catch (Exception e) {
                log.warn("Knowledge search failed for question generation", e);
            }
        }

        String typeList = String.join("、", types);
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("请基于知识库内容严格生成").append(count).append("道题目。\n");
        userPrompt.append("题型限制：").append(typeList).append("\n");
        userPrompt.append("难度：").append(difficulty).append("\n");
        if (request.getInstruction() != null && !request.getInstruction().isBlank()) {
            userPrompt.append("补充要求：").append(request.getInstruction()).append("\n");
        }
        userPrompt.append("\n严格按照以下JSON数组格式返回，不要输出任何JSON之外的内容，不要用markdown代码块包裹：\n");
        userPrompt.append("[\n");
        userPrompt.append("  {\n");
        userPrompt.append("    \"type\": \"SINGLE_CHOICE\",\n");
        userPrompt.append("    \"content\": \"题目题干内容\",\n");
        userPrompt.append("    \"options\": [{\"optionKey\":\"A\",\"optionContent\":\"选项内容\",\"sortOrder\":1}],\n");
        userPrompt.append("    \"answers\": [{\"answerContent\":\"A\",\"sortOrder\":1}],\n");
        userPrompt.append("    \"analysis\": \"题目解析\",\n");
        userPrompt.append("    \"knowledgePoint\": \"知识点\",\n");
        userPrompt.append("    \"difficulty\": \"").append(difficulty).append("\",\n");
        userPrompt.append("    \"score\": 5\n");
        userPrompt.append("  }\n");
        userPrompt.append("]\n");
        userPrompt.append("type取值：SINGLE_CHOICE/MULTIPLE_CHOICE/TRUE_FALSE/FILL_BLANK/SHORT_ANSWER\n");
        userPrompt.append("difficulty取值：EASY/MEDIUM/HARD\n");
        userPrompt.append("选择题必须包含options数组，非选择题不需要options。\n");
        userPrompt.append("TRUE_FALSE的options固定为[{\"optionKey\":\"A\",\"optionContent\":\"正确\",\"sortOrder\":1},{\"optionKey\":\"B\",\"optionContent\":\"错误\",\"sortOrder\":2}]，答案填A或B。\n");
        userPrompt.append("如果知识库中没有足够内容生成题目，请返回空数组[]。");

        if (knowledgeContext.length() > 0) {
            systemPrompt += "\n\n知识库参考资料（优先基于资料生成题目；资料不足时返回空数组）：\n" + knowledgeContext;
        }

        String rawResponse;
        try {
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemPrompt));
            messages.add(new UserMessage(userPrompt.toString()));

            OpenAiChatOptions.Builder optsBuilder = OpenAiChatOptions.builder();
            if (model != null && !model.isBlank()) optsBuilder.model(model.trim());
            if (temperature != null) optsBuilder.temperature(temperature);
            OpenAiChatOptions opts = optsBuilder.build();

            Prompt prompt;
            if (opts.getModel() != null || opts.getTemperature() != null) {
                prompt = new Prompt(messages, opts);
            } else {
                prompt = new Prompt(messages);
            }
            log.info("Generating questions from KB, prompt length: {}", systemPrompt.length() + userPrompt.length());
            var chatResponse = chatModel.call(prompt);
            rawResponse = chatResponse.getResult().getOutput().getText();
            log.info("Generated questions response length: {}", rawResponse != null ? rawResponse.length() : 0);
        } catch (Exception e) {
            log.error("AI generation failed", e);
            throw new BusinessException("AI生成失败: " + e.getMessage());
        }

        return parseExtractedJson(rawResponse, request.getCourseId(), teacherId);
    }

    public List<Map<String, Object>> batchCreate(List<QuestionRequestDTO> requests) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            QuestionRequestDTO req = requests.get(i);
            Map<String, Object> item = new HashMap<>();
            item.put("index", i);
            try {
                validateQuestionRules(req);
                create(req);
                item.put("success", true);
            } catch (Exception e) {
                item.put("success", false);
                item.put("error", e.getMessage());
            }
            results.add(item);
        }
        return results;
    }

    // ==================== helper ====================

    private String findOrCreateQuestionKB() {
        var kbResult = weKnoraService.listKnowledgeBases();
        String kbName = "EduLoop题库";
        if ("200".equals(kbResult.getCode()) && kbResult.getData() != null) {
            Object data = kbResult.getData();
            if (data instanceof List<?> kbs) {
                for (Object kb : kbs) {
                    if (kb instanceof Map<?, ?> m && kbName.equals(m.get("name"))) {
                        Object id = m.get("id");
                        return id != null ? id.toString() : null;
                    }
                }
            }
        }
        // 不存在则创建
        var createResult = weKnoraService.createKnowledgeBase(kbName, "EduLoop智教云环平台题库，支持PDF自动提取题目");
        if ("200".equals(createResult.getCode()) && createResult.getData() instanceof Map<?, ?> m) {
            Object id = m.get("id");
            if (id != null) return id.toString();
        }
        throw new BusinessException("无法获取或创建知识库，请确认WeKnora服务可用");
    }

    @SuppressWarnings("unchecked")
    private String extractSessionId(com.cupk.aiacademy.common.Result result) {
        if (result.getData() instanceof Map<?, ?> m) {
            Object id = m.get("id");
            if (id != null) return id.toString();
            Object sessionId = m.get("session_id");
            if (sessionId != null) return sessionId.toString();
        }
        throw new BusinessException("无法获取会话ID");
    }

    private List<QuestionRequestDTO> parseExtractedJson(String rawResponse, Long courseId, Long teacherId) {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            return List.of();
        }
        String cleaned = rawResponse.trim();
        // 去除可能的 markdown 代码块标记
        if (cleaned.startsWith("```")) {
            int start = cleaned.indexOf('\n');
            if (start > 0) {
                int end = cleaned.lastIndexOf("```");
                cleaned = end > start ? cleaned.substring(start, end).trim() : cleaned.substring(start).trim();
            }
        }
        try {
            List<QuestionRequestDTO> questions = objectMapper.readValue(cleaned,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, QuestionRequestDTO.class));
            if (questions != null) {
                for (QuestionRequestDTO q : questions) {
                    if (q.getCourseId() == null) q.setCourseId(courseId);
                    if (q.getTeacherId() == null) q.setTeacherId(teacherId);
                    if (q.getStatus() == null) q.setStatus(RecordStatus.ACTIVE);
                    if (q.getDifficulty() == null) q.setDifficulty(Difficulty.MEDIUM);
                }
            }
            return questions != null ? questions : List.of();
        } catch (Exception e) {
            log.warn("解析AI提取结果失败, rawResponse前200字符: {}",
                    rawResponse.length() > 200 ? rawResponse.substring(0, 200) : rawResponse);
            throw new BusinessException("AI返回数据格式异常，请重试。原始响应前200字符: " +
                    (rawResponse.length() > 200 ? rawResponse.substring(0, 200) : rawResponse));
        }
    }

    private static final Logger log = LoggerFactory.getLogger(QuestionService.class);

    private void collectSnippets(com.fasterxml.jackson.databind.JsonNode node, List<String> snippets, int max) {
        if (node == null || node.isNull() || snippets.size() >= max) return;
        if (node.isArray()) {
            for (var child : node) collectSnippets(child, snippets, max);
            return;
        }
        if (node.isObject()) {
            for (var field : node.properties()) collectSnippets(field.getValue(), snippets, max);
            return;
        }
        if (node.isTextual()) {
            String text = node.asText().replaceAll("\\s+", " ").trim();
            if (!text.isBlank() && !snippets.contains(text) && text.length() < 2000) {
                snippets.add(text);
            }
        }
    }

    public Question getEntityById(Long id) {
        Question question = questionMapper.selectById(toInt(id, "题目ID"));
        if (question == null) {
            throw new BusinessException("题目不存在: " + id);
        }
        return question;
    }

    public Question findSameQuestion(QuestionRequestDTO request) {
        if (request == null || request.getCourseId() == null || request.getTeacherId() == null
                || request.getType() == null || request.getContent() == null || request.getContent().trim().isEmpty()) {
            return null;
        }
        QueryWrapper<Question> query = new QueryWrapper<>();
        query.eq("course_id", toInt(request.getCourseId(), "课程ID"))
                .eq("teacher_id", toInt(request.getTeacherId(), "教师ID"))
                .eq("question_type", request.getType().getStorageValue())
                .eq("title", request.getContent().trim())
                .orderByAsc("id")
                .last("limit 1");
        return questionMapper.selectOne(query);
    }

    public Question findEquivalentQuestion(QuestionRequestDTO request) {
        if (request == null || request.getCourseId() == null || request.getTeacherId() == null
                || request.getType() == null || request.getContent() == null || request.getContent().isBlank()) {
            return null;
        }
        QueryWrapper<Question> query = new QueryWrapper<>();
        query.eq("course_id", toInt(request.getCourseId(), "课程ID"))
                .eq("teacher_id", toInt(request.getTeacherId(), "教师ID"))
                .eq("question_type", request.getType().getStorageValue());
        String fingerprint = questionFingerprint(request);
        return questionMapper.selectList(query).stream()
                .filter(question -> fingerprint.equals(questionFingerprint(question)))
                .findFirst()
                .orElse(null);
    }

    public void validateForPaperComposition(QuestionRequestDTO request) {
        validateQuestionRules(request);
    }

    public QuestionResponseDTO toResponse(Question question) {
        return toResponse(question, true);
    }

    public QuestionResponseDTO toResponse(Question question, boolean includeAnswers) {
        QuestionResponseDTO response = new QuestionResponseDTO();
        response.setId(toLong(question.getId()));
        response.setCourseId(toLong(question.getCourseId()));
        response.setTeacherId(toLong(question.getTeacherId()));
        response.setContent(question.getTitle());
        response.setType(QuestionType.fromStorageValue(question.getQuestionType()));
        response.setDifficulty(Difficulty.fromCode(question.getDifficulty()));
        response.setKnowledgePoint(question.getKnowledgePoint());
        response.setAnalysis(question.getAnalysis());
        response.setStatus(RecordStatus.fromCode(question.getStatus()));
        response.setCreatedAt(question.getCreateTime());
        response.setUpdatedAt(question.getUpdateTime());
        response.setOptions(parseOptions(question));
        response.setAnswers(includeAnswers ? parseAnswers(question) : null);
        return response;
    }

    private void prepareForWrite(QuestionRequestDTO request, Question oldQuestion) {
        permissionService.requireTeacherOrAdmin();
        if (request.getCourseId() == null) {
            throw new BusinessException("课程不能为空");
        }
        Course course = permissionService.getCourse(toInt(request.getCourseId(), "课程ID"));
        var user = permissionService.currentUser();
        if ("teacher".equals(user.getRoleCode())) {
            permissionService.requireCourseTeacher(course);
            request.setTeacherId(user.getUserId().longValue());
        } else {
            Integer teacherId = course.getTeacherId();
            if (teacherId == null || teacherId <= 0) {
                teacherId = toNullableInt(request.getTeacherId(), "教师ID");
            }
            permissionService.requireUserRole(teacherId, "teacher", "教师用户不存在或角色不正确");
            request.setTeacherId(teacherId.longValue());
        }
        if (oldQuestion != null && !"admin".equals(user.getRoleCode())
                && oldQuestion.getTeacherId() != null
                && !oldQuestion.getTeacherId().equals(user.getUserId())) {
            throw new ForbiddenException("无权修改该题目");
        }
    }

    private void requireQuestionTeacher(Question question) {
        var user = permissionService.currentUser();
        if ("admin".equals(user.getRoleCode())) {
            return;
        }
        if ("teacher".equals(user.getRoleCode()) && question.getTeacherId() != null
                && question.getTeacherId().equals(user.getUserId())) {
            return;
        }
        throw new ForbiddenException("无权维护该题目");
    }

    private void ensureQuestionNotInUse(Integer questionId) {
        if (questionId == null) return;
        QueryWrapper<com.cupk.aiacademy.pojo.ExamPaperQuestion> paperQuestionQuery = new QueryWrapper<>();
        paperQuestionQuery.eq("question_id", questionId);
        if (examPaperQuestionMapper.selectCount(paperQuestionQuery) > 0) {
            throw new BusinessException("该题目已被试卷使用，请先从试卷中移除或归档试卷后再删除");
        }
        QueryWrapper<com.cupk.aiacademy.pojo.ExamAnswer> answerQuery = new QueryWrapper<>();
        answerQuery.eq("question_id", questionId);
        if (examAnswerMapper.selectCount(answerQuery) > 0) {
            throw new BusinessException("该题目已有学生答题记录，不能删除，可将题目停用");
        }
    }

    private boolean canReadWithAnswers(Question question) {
        var user = permissionService.currentUser();
        if ("admin".equals(user.getRoleCode())) {
            return true;
        }
        if ("teacher".equals(user.getRoleCode())) {
            if (question.getTeacherId() != null && question.getTeacherId().equals(user.getUserId())) {
                return true;
            }
            throw new ForbiddenException("无权查看该题目");
        }
        if (question.getStatus() != null && question.getStatus() != RecordStatus.ACTIVE.getCode()) {
            throw new ForbiddenException("无权查看该题目");
        }
        List<Integer> courseIds = permissionService.studentCourseIds(user.getUserId());
        if (question.getCourseId() == null || !courseIds.contains(question.getCourseId())) {
            throw new ForbiddenException("无权查看该题目");
        }
        return false;
    }

    public List<QuestionResponseDTO.OptionResponse> parseOptions(Question question) {
        String options = question.getOptions();
        if (options == null || options.trim().isEmpty()) {
            return List.of();
        }
        try {
            List<QuestionRequestDTO.QuestionOptionRequest> optionRequests = objectMapper.readValue(options, OPTION_LIST_TYPE);
            return optionRequests.stream()
                    .sorted(Comparator.comparing(QuestionRequestDTO.QuestionOptionRequest::getSortOrder))
                    .map(item -> toOptionResponse(question, item))
                    .toList();
        } catch (Exception ignored) {
            try {
                List<String> values = objectMapper.readValue(options, STRING_LIST_TYPE);
                List<QuestionResponseDTO.OptionResponse> responses = new ArrayList<>();
                for (int i = 0; i < values.size(); i++) {
                    QuestionResponseDTO.OptionResponse response = new QuestionResponseDTO.OptionResponse();
                    response.setId(syntheticChildId(question.getId(), i + 1));
                    response.setOptionKey(optionKey(i));
                    response.setOptionContent(values.get(i));
                    response.setSortOrder(i + 1);
                    responses.add(response);
                }
                return responses;
            } catch (Exception e) {
                return List.of();
            }
        }
    }

    public List<QuestionResponseDTO.AnswerResponse> parseAnswers(Question question) {
        String answer = question.getAnswer();
        if (answer == null || answer.trim().isEmpty()) {
            return List.of();
        }
        try {
            List<QuestionRequestDTO.QuestionAnswerRequest> answerRequests = objectMapper.readValue(answer, ANSWER_LIST_TYPE);
            return answerRequests.stream()
                    .sorted(Comparator.comparing(QuestionRequestDTO.QuestionAnswerRequest::getSortOrder))
                    .map(item -> toAnswerResponse(question, item))
                    .toList();
        } catch (Exception ignored) {
            try {
                List<String> values = objectMapper.readValue(answer, STRING_LIST_TYPE);
                List<QuestionResponseDTO.AnswerResponse> responses = new ArrayList<>();
                for (int i = 0; i < values.size(); i++) {
                    QuestionResponseDTO.AnswerResponse response = new QuestionResponseDTO.AnswerResponse();
                    response.setId(syntheticChildId(question.getId(), i + 1));
                    response.setAnswerContent(values.get(i));
                    response.setSortOrder(i + 1);
                    responses.add(response);
                }
                return responses;
            } catch (Exception e) {
                QuestionResponseDTO.AnswerResponse response = new QuestionResponseDTO.AnswerResponse();
                response.setId(syntheticChildId(question.getId(), 1));
                response.setAnswerContent(answer);
                response.setSortOrder(1);
                return List.of(response);
            }
        }
    }

    private void validateQuestionRules(QuestionRequestDTO request) {
        if (request == null) {
            throw new BusinessException("题目信息不能为空");
        }
        if (request.getType() == null) {
            throw new BusinessException("题目类型不能为空");
        }
        if (request.getAnswers() == null || request.getAnswers().isEmpty()) {
            throw new BusinessException("题目必须包含标准答案");
        }
        boolean isChoiceType = request.getType() == QuestionType.SINGLE_CHOICE
                || request.getType() == QuestionType.MULTIPLE_CHOICE
                || request.getType() == QuestionType.TRUE_FALSE;
        boolean isAutoGradableType = isChoiceType || request.getType() == QuestionType.FILL_BLANK;
        if (isAutoGradableType && request.getAnswers().stream()
                .anyMatch(answer -> answer == null || answer.getAnswerContent() == null || answer.getAnswerContent().isBlank())) {
            throw new BusinessException("自动批改题必须填写正确答案");
        }
        if (isChoiceType) {
            if (request.getOptions() == null || request.getOptions().isEmpty()) {
                throw new BusinessException("选择题必须包含选项");
            }
            int answerCount = request.getAnswers().size();
            if ((request.getType() == QuestionType.SINGLE_CHOICE || request.getType() == QuestionType.TRUE_FALSE)
                    && answerCount != 1) {
                throw new BusinessException("单选题和判断题必须有且仅有一个正确答案，当前答案数量：" + answerCount);
            }
            if (request.getType() == QuestionType.MULTIPLE_CHOICE && answerCount < 2) {
                throw new BusinessException("多选题必须至少有两个正确答案，当前答案数量：" + answerCount);
            }
        } else if (request.getOptions() != null && !request.getOptions().isEmpty()) {
            throw new BusinessException("非选择题不能包含选项");
        }
    }

    private void fillQuestion(Question question, QuestionRequestDTO request) {
        question.setCourseId(toNullableInt(request.getCourseId(), "课程ID"));
        question.setTeacherId(toNullableInt(request.getTeacherId(), "教师ID"));
        question.setTitle(request.getContent());
        question.setQuestionType(request.getType().getStorageValue());
        question.setDifficulty(request.getDifficulty().getCode());
        // t_question.score is retained only for legacy import compatibility; paper links own scores.
        question.setScore(BigDecimal.ZERO);
        question.setKnowledgePoint(request.getKnowledgePoint());
        question.setAnalysis(request.getAnalysis());
        question.setStatus(request.getStatus() == null ? RecordStatus.ACTIVE.getCode() : request.getStatus().getCode());
        question.setOptions(writeJson(request.getOptions()));
        question.setAnswer(writeJson(request.getAnswers()));
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException("题目选项或答案序列化失败");
        }
    }

    private String questionFingerprint(QuestionRequestDTO request) {
        String options = request.getOptions() == null ? "" : request.getOptions().stream()
                .sorted(Comparator.comparing(QuestionRequestDTO.QuestionOptionRequest::getSortOrder))
                .map(option -> normalize(option.getOptionKey()) + "=" + normalize(option.getOptionContent()))
                .collect(Collectors.joining("|"));
        String answers = request.getAnswers() == null ? "" : request.getAnswers().stream()
                .sorted(Comparator.comparing(QuestionRequestDTO.QuestionAnswerRequest::getSortOrder))
                .map(answer -> normalize(answer.getAnswerContent()))
                .collect(Collectors.joining("|"));
        return request.getType() + "#" + normalize(request.getContent()) + "#" + options + "#" + answers;
    }

    private String questionFingerprint(Question question) {
        String options = parseOptions(question).stream()
                .sorted(Comparator.comparing(QuestionResponseDTO.OptionResponse::getSortOrder))
                .map(option -> normalize(option.getOptionKey()) + "=" + normalize(option.getOptionContent()))
                .collect(Collectors.joining("|"));
        String answers = parseAnswers(question).stream()
                .sorted(Comparator.comparing(QuestionResponseDTO.AnswerResponse::getSortOrder))
                .map(answer -> normalize(answer.getAnswerContent()))
                .collect(Collectors.joining("|"));
        return QuestionType.fromStorageValue(question.getQuestionType()) + "#" + normalize(question.getTitle()) + "#" + options + "#" + answers;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private QuestionResponseDTO.OptionResponse toOptionResponse(Question question, QuestionRequestDTO.QuestionOptionRequest item) {
        QuestionResponseDTO.OptionResponse response = new QuestionResponseDTO.OptionResponse();
        response.setId(syntheticChildId(question.getId(), item.getSortOrder()));
        response.setOptionKey(item.getOptionKey());
        response.setOptionContent(item.getOptionContent());
        response.setSortOrder(item.getSortOrder());
        return response;
    }

    private QuestionResponseDTO.AnswerResponse toAnswerResponse(Question question, QuestionRequestDTO.QuestionAnswerRequest item) {
        QuestionResponseDTO.AnswerResponse response = new QuestionResponseDTO.AnswerResponse();
        response.setId(syntheticChildId(question.getId(), item.getSortOrder()));
        response.setAnswerContent(item.getAnswerContent());
        response.setSortOrder(item.getSortOrder());
        return response;
    }

    private Long syntheticChildId(Integer parentId, Integer sortOrder) {
        if (parentId == null || sortOrder == null) {
            return null;
        }
        return parentId.longValue() * 1000L + sortOrder;
    }

    private String optionKey(int index) {
        return String.valueOf((char) ('A' + index));
    }

    private Integer toNullableInt(Long value, String fieldName) {
        return value == null ? null : toInt(value, fieldName);
    }

    private Integer toInt(Long value, String fieldName) {
        if (value == null || value <= 0 || value > Integer.MAX_VALUE) {
            throw new BusinessException(fieldName + "不合法");
        }
        return value.intValue();
    }

    private Long toLong(Integer value) {
        return value == null ? null : value.longValue();
    }
}
