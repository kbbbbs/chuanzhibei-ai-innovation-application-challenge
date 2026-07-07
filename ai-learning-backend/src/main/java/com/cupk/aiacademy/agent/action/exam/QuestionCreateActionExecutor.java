package com.cupk.aiacademy.agent.action.exam;

import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.Difficulty;
import com.cupk.aiacademy.common.QuestionType;
import com.cupk.aiacademy.common.RecordStatus;
import com.cupk.aiacademy.dto.QuestionRequestDTO;
import com.cupk.aiacademy.dto.QuestionResponseDTO;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.service.QuestionService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class QuestionCreateActionExecutor implements AgentActionExecutor {
    private static final String ACTION_CODE = "question.create";

    private final QuestionService questionService;
    private final CourseMapper courseMapper;
    private final UserMapper userMapper;

    public QuestionCreateActionExecutor(QuestionService questionService,
                                        CourseMapper courseMapper,
                                        UserMapper userMapper) {
        this.questionService = questionService;
        this.courseMapper = courseMapper;
        this.userMapper = userMapper;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "创建试题";
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireTeacherOrAdmin(user);

        Map<String, Object> parameters = context.parameters();
        Integer courseId = requiredInteger(parameters, "courseId", "course_id");
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }

        Integer teacherId = optionalInteger(parameters, "teacherId", "teacher_id");
        if (teacherId == null) {
            teacherId = isAdmin(user) && course.getTeacherId() != null ? course.getTeacherId() : user.getUserId();
        }
        if (!isAdmin(user) && !teacherId.equals(user.getUserId())) {
            throw new BusinessException("只能以当前登录教师身份创建试题");
        }
        if (course.getTeacherId() != null && !course.getTeacherId().equals(teacherId)) {
            throw new BusinessException("题目教师必须是课程负责教师");
        }
        if (userMapper.selectById(teacherId) == null) {
            throw new BusinessException("教师用户不存在");
        }
        if (!isAdmin(user) && (course.getTeacherId() == null || !course.getTeacherId().equals(user.getUserId()))) {
            throw new BusinessException("只能给当前登录教师负责课程创建试题");
        }

        QuestionRequestDTO request = buildRequest(parameters, courseId, teacherId);
        QuestionResponseDTO response = questionService.create(request);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("questionId", response.getId());
        data.put("courseId", response.getCourseId());
        data.put("teacherId", response.getTeacherId());
        data.put("type", response.getType() == null ? null : response.getType().name());
        data.put("difficulty", response.getDifficulty() == null ? null : response.getDifficulty().name());
        data.put("score", response.getScore());
        data.put("status", response.getStatus() == null ? null : response.getStatus().name());
        data.put("content", response.getContent());
        data.put("answerCount", response.getAnswers() == null ? 0 : response.getAnswers().size());
        data.put("optionCount", response.getOptions() == null ? 0 : response.getOptions().size());
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "试题已创建",
                data,
                LocalDateTime.now()
        );
    }

    private QuestionRequestDTO buildRequest(Map<String, Object> parameters, Integer courseId, Integer teacherId) {
        QuestionRequestDTO request = new QuestionRequestDTO();
        request.setCourseId(courseId.longValue());
        request.setTeacherId(teacherId.longValue());
        request.setContent(requiredString(parameters, 500, "content", "title", "questionContent", "question_content"));
        QuestionType type = requiredQuestionType(parameters);
        request.setType(type);
        request.setDifficulty(optionalDifficulty(parameters));
        request.setScore(optionalScore(parameters));
        request.setKnowledgePoint(optionalString(parameters, 200, "knowledgePoint", "knowledge_point"));
        request.setAnalysis(optionalString(parameters, 1000, "analysis", "explanation"));
        request.setStatus(optionalStatus(parameters));
        request.setOptions(parseOptions(parameters, type));
        request.setAnswers(parseAnswers(parameters));
        return request;
    }

    private List<QuestionRequestDTO.QuestionOptionRequest> parseOptions(Map<String, Object> parameters,
                                                                        QuestionType type) {
        Object value = firstValue(parameters, "options", "choices");
        List<QuestionRequestDTO.QuestionOptionRequest> options = new ArrayList<>();
        if (value instanceof List<?> rawList) {
            int index = 0;
            for (Object rawItem : rawList) {
                index++;
                if (rawItem instanceof Map<?, ?> rawMap) {
                    options.add(optionRequest(
                            optionalMapString(rawMap, "optionKey", "key", "label"),
                            requiredMapString(rawMap, "optionContent", "content", "text", "value"),
                            optionalMapInteger(rawMap, "sortOrder", "sort_order", "order"),
                            index
                    ));
                } else {
                    options.add(optionRequest(null, String.valueOf(rawItem), null, index));
                }
            }
        } else if (value instanceof Map<?, ?> rawMap) {
            int index = 0;
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                index++;
                options.add(optionRequest(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()), null, index));
            }
        }
        addOptionFromFlatParameter(options, parameters, "A", "optionA", "option_a");
        addOptionFromFlatParameter(options, parameters, "B", "optionB", "option_b");
        addOptionFromFlatParameter(options, parameters, "C", "optionC", "option_c");
        addOptionFromFlatParameter(options, parameters, "D", "optionD", "option_d");

        if (options.isEmpty() && type == QuestionType.TRUE_FALSE) {
            options.add(optionRequest("A", "正确", 1, 1));
            options.add(optionRequest("B", "错误", 2, 2));
        }
        return options.isEmpty() ? null : options;
    }

    private List<QuestionRequestDTO.QuestionAnswerRequest> parseAnswers(Map<String, Object> parameters) {
        Object value = firstValue(parameters, "answers", "answer", "correctAnswers", "correct_answers");
        if (value == null || String.valueOf(value).isBlank()) {
            throw new BusinessException("answers 不能为空");
        }
        List<QuestionRequestDTO.QuestionAnswerRequest> answers = new ArrayList<>();
        if (value instanceof List<?> rawList) {
            int index = 0;
            for (Object rawItem : rawList) {
                index++;
                if (rawItem instanceof Map<?, ?> rawMap) {
                    answers.add(answerRequest(
                            requiredMapString(rawMap, "answerContent", "content", "text", "value"),
                            optionalMapInteger(rawMap, "sortOrder", "sort_order", "order"),
                            index
                    ));
                } else {
                    answers.add(answerRequest(String.valueOf(rawItem), null, index));
                }
            }
            return answers;
        }
        String text = String.valueOf(value).trim();
        String[] parts = text.contains(",") ? text.split(",") : text.split("，");
        for (int i = 0; i < parts.length; i++) {
            String answer = parts[i].trim();
            if (!answer.isEmpty()) {
                answers.add(answerRequest(answer, null, i + 1));
            }
        }
        if (answers.isEmpty()) {
            throw new BusinessException("answers 不能为空");
        }
        return answers;
    }

    private void addOptionFromFlatParameter(List<QuestionRequestDTO.QuestionOptionRequest> options,
                                            Map<String, Object> parameters,
                                            String key,
                                            String... aliases) {
        Object value = firstValue(parameters, aliases);
        if (value != null && !String.valueOf(value).isBlank()) {
            options.add(optionRequest(key, String.valueOf(value), null, options.size() + 1));
        }
    }

    private QuestionRequestDTO.QuestionOptionRequest optionRequest(String optionKey,
                                                                   String optionContent,
                                                                   Integer sortOrder,
                                                                   int fallbackOrder) {
        QuestionRequestDTO.QuestionOptionRequest option = new QuestionRequestDTO.QuestionOptionRequest();
        option.setOptionKey(isBlank(optionKey) ? optionKey(fallbackOrder) : optionKey.trim());
        option.setOptionContent(requiredText(optionContent, "optionContent"));
        option.setSortOrder(sortOrder == null || sortOrder <= 0 ? fallbackOrder : sortOrder);
        return option;
    }

    private QuestionRequestDTO.QuestionAnswerRequest answerRequest(String answerContent,
                                                                   Integer sortOrder,
                                                                   int fallbackOrder) {
        QuestionRequestDTO.QuestionAnswerRequest answer = new QuestionRequestDTO.QuestionAnswerRequest();
        answer.setAnswerContent(requiredText(answerContent, "answerContent"));
        answer.setSortOrder(sortOrder == null || sortOrder <= 0 ? fallbackOrder : sortOrder);
        return answer;
    }

    private QuestionType requiredQuestionType(Map<String, Object> parameters) {
        Object value = firstValue(parameters, "type", "questionType", "question_type");
        if (value == null || String.valueOf(value).isBlank()) {
            throw new BusinessException("type 不能为空");
        }
        String normalized = String.valueOf(value).trim().toLowerCase();
        return switch (normalized) {
            case "single", "single_choice", "singlechoice", "单选", "单选题" -> QuestionType.SINGLE_CHOICE;
            case "multiple", "multiple_choice", "multiplechoice", "多选", "多选题" -> QuestionType.MULTIPLE_CHOICE;
            case "judge", "true_false", "truefalse", "判断", "判断题" -> QuestionType.TRUE_FALSE;
            case "blank", "fill_blank", "fillblank", "填空", "填空题" -> QuestionType.FILL_BLANK;
            case "short_answer", "shortanswer", "subjective", "简答", "简答题", "主观题" -> QuestionType.SHORT_ANSWER;
            default -> {
                for (QuestionType type : QuestionType.values()) {
                    if (type.name().equalsIgnoreCase(normalized) || type.getStorageValue().equals(normalized)) {
                        yield type;
                    }
                }
                throw new BusinessException("type 不合法");
            }
        };
    }

    private Difficulty optionalDifficulty(Map<String, Object> parameters) {
        Object value = firstValue(parameters, "difficulty", "level");
        if (value == null || String.valueOf(value).isBlank()) {
            return Difficulty.MEDIUM;
        }
        if (value instanceof Number number) {
            return Difficulty.fromCode(number.intValue());
        }
        String normalized = String.valueOf(value).trim().toLowerCase();
        return switch (normalized) {
            case "easy", "简单", "容易", "1" -> Difficulty.EASY;
            case "medium", "normal", "中等", "普通", "2" -> Difficulty.MEDIUM;
            case "hard", "困难", "难", "3" -> Difficulty.HARD;
            default -> throw new BusinessException("difficulty 不合法");
        };
    }

    private RecordStatus optionalStatus(Map<String, Object> parameters) {
        Object value = firstValue(parameters, "status");
        if (value == null || String.valueOf(value).isBlank()) {
            return RecordStatus.ACTIVE;
        }
        if (value instanceof Number number) {
            return RecordStatus.fromCode(number.intValue());
        }
        String normalized = String.valueOf(value).trim().toLowerCase();
        return switch (normalized) {
            case "draft", "草稿", "0" -> RecordStatus.DRAFT;
            case "active", "enabled", "启用", "正常", "1" -> RecordStatus.ACTIVE;
            case "disabled", "停用", "禁用", "2" -> RecordStatus.DISABLED;
            default -> throw new BusinessException("status 不合法");
        };
    }

    private BigDecimal optionalScore(Map<String, Object> parameters) {
        Object value = firstValue(parameters, "score", "points");
        if (value == null || String.valueOf(value).isBlank()) {
            return BigDecimal.valueOf(5);
        }
        BigDecimal score;
        try {
            score = new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("score 必须是数字");
        }
        if (score.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("score 必须大于 0");
        }
        return score;
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || (!isTeacher(user) && !isAdmin(user))) {
            throw new BusinessException("只有教师或管理员可以创建试题");
        }
    }

    private boolean isTeacher(AuthUtils.LoginUser user) {
        return user != null && "teacher".equals(user.getRoleCode());
    }

    private boolean isAdmin(AuthUtils.LoginUser user) {
        return user != null && "admin".equals(user.getRoleCode());
    }

    private Integer requiredInteger(Map<String, Object> parameters, String... keys) {
        Integer value = optionalInteger(parameters, keys);
        if (value == null) {
            throw new BusinessException(keys[0] + " 不能为空");
        }
        return value;
    }

    private Integer optionalInteger(Map<String, Object> parameters, String... keys) {
        Object value = firstValue(parameters, keys);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        if (value instanceof Number number) {
            int parsed = number.intValue();
            if (parsed <= 0) {
                throw new BusinessException(keys[0] + " 不合法");
            }
            return parsed;
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(value).trim());
            if (parsed <= 0) {
                throw new BusinessException(keys[0] + " 不合法");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new BusinessException(keys[0] + " 必须是整数");
        }
    }

    private String requiredString(Map<String, Object> parameters, int maxLength, String... keys) {
        String value = optionalString(parameters, maxLength, keys);
        if (value == null) {
            throw new BusinessException(keys[0] + " 不能为空");
        }
        return value;
    }

    private String optionalString(Map<String, Object> parameters, int maxLength, String... keys) {
        Object value = firstValue(parameters, keys);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.length() > maxLength) {
            throw new BusinessException(keys[0] + " 长度不能超过 " + maxLength);
        }
        return text;
    }

    private Object firstValue(Map<String, Object> parameters, String... keys) {
        if (parameters == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (parameters.containsKey(key)) {
                return parameters.get(key);
            }
        }
        return null;
    }

    private String requiredMapString(Map<?, ?> map, String... keys) {
        String value = optionalMapString(map, keys);
        if (value == null) {
            throw new BusinessException(keys[0] + " 不能为空");
        }
        return value;
    }

    private String optionalMapString(Map<?, ?> map, String... keys) {
        Object value = firstMapValue(map, keys);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return String.valueOf(value).trim();
    }

    private Integer optionalMapInteger(Map<?, ?> map, String... keys) {
        Object value = firstMapValue(map, keys);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(keys[0] + " 必须是整数");
        }
    }

    private Object firstMapValue(Map<?, ?> map, String... keys) {
        if (map == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private String requiredText(String value, String fieldName) {
        if (isBlank(value)) {
            throw new BusinessException(fieldName + " 不能为空");
        }
        return value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String optionKey(int order) {
        return String.valueOf((char) ('A' + Math.max(0, order - 1)));
    }
}
