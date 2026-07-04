package com.cupk.aiacademy.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.Difficulty;
import com.cupk.aiacademy.common.QuestionType;
import com.cupk.aiacademy.common.RecordStatus;
import com.cupk.aiacademy.dto.QuestionRequestDTO;
import com.cupk.aiacademy.dto.QuestionResponseDTO;
import com.cupk.aiacademy.mapper.QuestionMapper;
import com.cupk.aiacademy.pojo.Question;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

    public QuestionService(QuestionMapper questionMapper, ObjectMapper objectMapper) {
        this.questionMapper = questionMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public QuestionResponseDTO create(QuestionRequestDTO request) {
        validateQuestionRules(request);
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
        fillQuestion(question, request);
        question.setUpdateTime(LocalDateTime.now());
        questionMapper.updateById(question);
        return toResponse(question);
    }

    @Transactional(readOnly = true)
    public QuestionResponseDTO getById(Long id) {
        return toResponse(getEntityById(id));
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
        if (status != null) {
            query.eq("status", status.getCode());
        }
        if (courseId != null && courseId > 0) {
            query.eq("course_id", courseId);
        }
        if (teacherId != null && teacherId > 0) {
            query.eq("teacher_id", teacherId);
        }
        query.orderByDesc("id");
        questionMapper.selectPage(mpPage, query);
        List<QuestionResponseDTO> records = mpPage.getRecords().stream().map(this::toResponse).toList();
        return new PageImpl<>(records, PageRequest.of(pageIndex, pageSize), mpPage.getTotal());
    }

    @Transactional
    public void delete(Long id) {
        Question question = getEntityById(id);
        questionMapper.deleteById(question.getId());
    }

    public Question getEntityById(Long id) {
        Question question = questionMapper.selectById(toInt(id, "题目ID"));
        if (question == null) {
            throw new BusinessException("题目不存在: " + id);
        }
        return question;
    }

    public QuestionResponseDTO toResponse(Question question) {
        QuestionResponseDTO response = new QuestionResponseDTO();
        response.setId(toLong(question.getId()));
        response.setCourseId(toLong(question.getCourseId()));
        response.setTeacherId(toLong(question.getTeacherId()));
        response.setContent(question.getTitle());
        response.setType(QuestionType.fromStorageValue(question.getQuestionType()));
        response.setDifficulty(Difficulty.fromCode(question.getDifficulty()));
        response.setScore(question.getScore());
        response.setKnowledgePoint(question.getKnowledgePoint());
        response.setAnalysis(question.getAnalysis());
        response.setStatus(RecordStatus.fromCode(question.getStatus()));
        response.setCreatedAt(question.getCreateTime());
        response.setUpdatedAt(question.getUpdateTime());
        response.setOptions(parseOptions(question));
        response.setAnswers(parseAnswers(question));
        return response;
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
        question.setScore(request.getScore() == null ? BigDecimal.ZERO : request.getScore());
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
