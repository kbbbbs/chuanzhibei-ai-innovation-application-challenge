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
public class QuestionUpdateActionExecutor implements AgentActionExecutor {
    private static final String ACTION_CODE = "question.update";

    private final QuestionService questionService;
    private final CourseMapper courseMapper;
    private final UserMapper userMapper;

    public QuestionUpdateActionExecutor(QuestionService questionService,
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
        return "修改试题";
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireTeacherOrAdmin(user);

        Map<String, Object> parameters = context.parameters();
        Long questionId = requiredLong(parameters, "questionId", "question_id", "id");
        requireAtLeastOneUpdateField(parameters);

        QuestionResponseDTO oldQuestion = questionService.getById(questionId);
        Integer targetCourseId = optionalInteger(parameters, "courseId", "course_id");
        if (targetCourseId == null) {
            targetCourseId = toInteger(oldQuestion.getCourseId(), "课程ID");
        }
        Integer targetTeacherId = optionalInteger(parameters, "teacherId", "teacher_id");
        if (targetTeacherId == null) {
            targetTeacherId = toInteger(oldQuestion.getTeacherId(), "教师ID");
        }

        Course targetCourse = requireCourse(targetCourseId);
        validatePermission(user, oldQuestion, targetCourse, targetTeacherId);
        if (userMapper.selectById(targetTeacherId) == null) {
            throw new BusinessException("教师用户不存在");
        }

        QuestionRequestDTO request = buildRequest(parameters, oldQuestion, targetCourseId, targetTeacherId);
        QuestionResponseDTO newQuestion = questionService.update(questionId, request);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("questionId", newQuestion.getId());
        data.put("oldCourseId", oldQuestion.getCourseId());
        data.put("newCourseId", newQuestion.getCourseId());
        data.put("oldTeacherId", oldQuestion.getTeacherId());
        data.put("newTeacherId", newQuestion.getTeacherId());
        data.put("oldType", enumName(oldQuestion.getType()));
        data.put("newType", enumName(newQuestion.getType()));
        data.put("oldDifficulty", enumName(oldQuestion.getDifficulty()));
        data.put("newDifficulty", enumName(newQuestion.getDifficulty()));
        data.put("oldStatus", enumName(oldQuestion.getStatus()));
        data.put("newStatus", enumName(newQuestion.getStatus()));
        data.put("oldContent", oldQuestion.getContent());
        data.put("newContent", newQuestion.getContent());
        data.put("answerCount", newQuestion.getAnswers() == null ? 0 : newQuestion.getAnswers().size());
        data.put("optionCount", newQuestion.getOptions() == null ? 0 : newQuestion.getOptions().size());
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "试题已修改",
                data,
                LocalDateTime.now()
        );
    }

    private QuestionRequestDTO buildRequest(Map<String, Object> parameters,
                                            QuestionResponseDTO oldQuestion,
                                            Integer courseId,
                                            Integer teacherId) {
        QuestionRequestDTO request = new QuestionRequestDTO();
        request.setCourseId(courseId.longValue());
        request.setTeacherId(teacherId.longValue());
        request.setContent(hasAnyKey(parameters, "content", "title", "questionContent", "question_content")
                ? requiredString(parameters, 500, "content", "title", "questionContent", "question_content")
                : oldQuestion.getContent());
        QuestionType type = hasAnyKey(parameters, "type", "questionType", "question_type")
                ? requiredQuestionType(parameters)
                : oldQuestion.getType();
        request.setType(type);
        request.setDifficulty(hasAnyKey(parameters, "difficulty", "level")
                ? optionalDifficulty(parameters)
                : oldQuestion.getDifficulty());
        request.setKnowledgePoint(hasAnyKey(parameters, "knowledgePoint", "knowledge_point")
                ? optionalString(parameters, 200, "knowledgePoint", "knowledge_point")
                : oldQuestion.getKnowledgePoint());
        request.setAnalysis(hasAnyKey(parameters, "analysis", "explanation")
                ? optionalString(parameters, 1000, "analysis", "explanation")
                : oldQuestion.getAnalysis());
        request.setStatus(hasAnyKey(parameters, "status") ? optionalStatus(parameters) : oldQuestion.getStatus());
        request.setOptions(hasOptionParameters(parameters) ? parseOptions(parameters, type) : copyOptions(oldQuestion));
        request.setAnswers(hasAnyKey(parameters, "answers", "answer", "correctAnswers", "correct_answers")
                ? parseAnswers(parameters)
                : copyAnswers(oldQuestion));
        return request;
    }

    private void validatePermission(AuthUtils.LoginUser user,
                                    QuestionResponseDTO oldQuestion,
                                    Course targetCourse,
                                    Integer targetTeacherId) {
        if (!isAdmin(user)) {
            Integer oldTeacherId = toInteger(oldQuestion.getTeacherId(), "教师ID");
            if (oldTeacherId == null || !oldTeacherId.equals(user.getUserId())) {
                throw new BusinessException("只能修改当前登录教师负责的试题");
            }
            if (!targetTeacherId.equals(user.getUserId())) {
                throw new BusinessException("只能以当前登录教师身份修改试题");
            }
            if (targetCourse.getTeacherId() == null || !targetCourse.getTeacherId().equals(user.getUserId())) {
                throw new BusinessException("只能把试题归属到当前登录教师负责的课程");
            }
        }
        if (targetCourse.getTeacherId() != null && !targetCourse.getTeacherId().equals(targetTeacherId)) {
            throw new BusinessException("题目教师必须是课程负责教师");
        }
    }

    private Course requireCourse(Integer courseId) {
        if (courseId == null || courseId <= 0) {
            throw new BusinessException("课程不能为空");
        }
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        return course;
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

    private List<QuestionRequestDTO.QuestionOptionRequest> copyOptions(QuestionResponseDTO oldQuestion) {
        if (oldQuestion.getOptions() == null || oldQuestion.getOptions().isEmpty()) {
            return null;
        }
        return oldQuestion.getOptions().stream()
                .map(item -> optionRequest(item.getOptionKey(), item.getOptionContent(), item.getSortOrder(), item.getSortOrder()))
                .toList();
    }

    private List<QuestionRequestDTO.QuestionAnswerRequest> copyAnswers(QuestionResponseDTO oldQuestion) {
        if (oldQuestion.getAnswers() == null || oldQuestion.getAnswers().isEmpty()) {
            return List.of();
        }
        return oldQuestion.getAnswers().stream()
                .map(item -> answerRequest(item.getAnswerContent(), item.getSortOrder(), item.getSortOrder()))
                .toList();
    }

    private void requireAtLeastOneUpdateField(Map<String, Object> parameters) {
        if (!hasAnyKey(parameters, "courseId", "course_id", "teacherId", "teacher_id", "type", "questionType",
                "question_type", "content", "title", "questionContent", "question_content", "options", "choices",
                "optionA", "option_a", "optionB", "option_b", "optionC", "option_c", "optionD", "option_d",
                "answers", "answer", "correctAnswers", "correct_answers", "difficulty", "level", "score", "points",
                "knowledgePoint", "knowledge_point", "analysis", "explanation", "status")) {
            throw new BusinessException("至少需要提供一个可修改的试题字段");
        }
    }

    private boolean hasOptionParameters(Map<String, Object> parameters) {
        return hasAnyKey(parameters, "options", "choices", "optionA", "option_a", "optionB", "option_b",
                "optionC", "option_c", "optionD", "option_d");
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
        try {
            BigDecimal score = new BigDecimal(String.valueOf(value).trim());
            if (score.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("score 必须大于 0");
            }
            return score;
        } catch (NumberFormatException e) {
            throw new BusinessException("score 必须是数字");
        }
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || (!isTeacher(user) && !isAdmin(user))) {
            throw new BusinessException("只有教师或管理员可以修改试题");
        }
    }

    private boolean isTeacher(AuthUtils.LoginUser user) {
        return user != null && "teacher".equals(user.getRoleCode());
    }

    private boolean isAdmin(AuthUtils.LoginUser user) {
        return user != null && "admin".equals(user.getRoleCode());
    }

    private Long requiredLong(Map<String, Object> parameters, String... keys) {
        Object value = firstValue(parameters, keys);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new BusinessException(keys[0] + " 不能为空");
        }
        try {
            long parsed = value instanceof Number number
                    ? number.longValue()
                    : Long.parseLong(String.valueOf(value).trim());
            if (parsed <= 0) {
                throw new BusinessException(keys[0] + " 不合法");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new BusinessException(keys[0] + " 必须是整数");
        }
    }

    private Integer optionalInteger(Map<String, Object> parameters, String... keys) {
        Object value = firstValue(parameters, keys);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            int parsed = value instanceof Number number
                    ? number.intValue()
                    : Integer.parseInt(String.valueOf(value).trim());
            if (parsed <= 0) {
                throw new BusinessException(keys[0] + " 不合法");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new BusinessException(keys[0] + " 必须是整数");
        }
    }

    private Integer toInteger(Long value, String fieldName) {
        if (value == null || value <= 0 || value > Integer.MAX_VALUE) {
            throw new BusinessException(fieldName + "不合法");
        }
        return value.intValue();
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
        try {
            return value instanceof Number number
                    ? number.intValue()
                    : Integer.parseInt(String.valueOf(value).trim());
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

    private boolean hasAnyKey(Map<String, Object> parameters, String... keys) {
        if (parameters == null || keys == null) {
            return false;
        }
        for (String key : keys) {
            if (parameters.containsKey(key)) {
                return true;
            }
        }
        return false;
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

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
