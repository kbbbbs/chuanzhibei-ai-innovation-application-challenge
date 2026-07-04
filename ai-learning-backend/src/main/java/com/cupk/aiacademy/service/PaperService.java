package com.cupk.aiacademy.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.PaperStatus;
import com.cupk.aiacademy.dto.PaperRequestDTO;
import com.cupk.aiacademy.dto.PaperResponseDTO;
import com.cupk.aiacademy.mapper.ExamAnswerMapper;
import com.cupk.aiacademy.mapper.ExamPaperMapper;
import com.cupk.aiacademy.mapper.ExamPaperQuestionMapper;
import com.cupk.aiacademy.pojo.ExamPaper;
import com.cupk.aiacademy.pojo.ExamPaperQuestion;
import com.cupk.aiacademy.pojo.Question;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class PaperService {
    private final ExamPaperMapper paperMapper;
    private final ExamPaperQuestionMapper paperQuestionMapper;
    private final ExamAnswerMapper examAnswerMapper;
    private final QuestionService questionService;

    public PaperService(ExamPaperMapper paperMapper,
                        ExamPaperQuestionMapper paperQuestionMapper,
                        ExamAnswerMapper examAnswerMapper,
                        QuestionService questionService) {
        this.paperMapper = paperMapper;
        this.paperQuestionMapper = paperQuestionMapper;
        this.examAnswerMapper = examAnswerMapper;
        this.questionService = questionService;
    }

    @Transactional
    public PaperResponseDTO create(PaperRequestDTO request) {
        validateRequest(request);
        ExamPaper paper = new ExamPaper();
        fillPaper(paper, request);
        LocalDateTime now = LocalDateTime.now();
        paper.setCreateTime(now);
        paper.setUpdateTime(now);
        paperMapper.insert(paper);
        insertPaperQuestions(paper, request.getQuestions());
        return toResponse(paper);
    }

    @Transactional
    public PaperResponseDTO update(Long id, PaperRequestDTO request) {
        validateRequest(request);
        ExamPaper paper = getEntityById(id);
        PaperStatus status = PaperStatus.fromCode(paper.getStatus());
        if (status == PaperStatus.PUBLISHED || status == PaperStatus.ARCHIVED) {
            throw new BusinessException("已发布或已归档的试卷不允许修改");
        }
        fillPaper(paper, request);
        paper.setUpdateTime(LocalDateTime.now());
        paperMapper.updateById(paper);
        QueryWrapper<ExamPaperQuestion> deleteQuery = new QueryWrapper<>();
        deleteQuery.eq("paper_id", paper.getId());
        paperQuestionMapper.delete(deleteQuery);
        insertPaperQuestions(paper, request.getQuestions());
        return toResponse(paper);
    }

    @Transactional(readOnly = true)
    public PaperResponseDTO getById(Long id) {
        return toResponse(getEntityById(id));
    }

    @Transactional(readOnly = true)
    public List<PaperResponseDTO> list(Long teacherId) {
        QueryWrapper<ExamPaper> query = new QueryWrapper<>();
        if (teacherId != null && teacherId > 0) {
            query.eq("teacher_id", teacherId);
        }
        query.orderByDesc("id");
        return paperMapper.selectList(query).stream().map(this::toResponse).toList();
    }

    @Transactional
    public void delete(Long id) {
        ExamPaper paper = getEntityById(id);
        PaperStatus status = PaperStatus.fromCode(paper.getStatus());
        if (status == PaperStatus.PUBLISHED) {
            throw new BusinessException("已发布的试卷不允许删除，请先改为草稿状态");
        }
        QueryWrapper<ExamPaperQuestion> deleteQuery = new QueryWrapper<>();
        deleteQuery.eq("paper_id", paper.getId());
        paperQuestionMapper.delete(deleteQuery);
        paperMapper.deleteById(paper.getId());
    }

    @Transactional
    public void removePaperQuestion(Long paperId, Long paperQuestionId) {
        ExamPaper paper = getEntityById(paperId);
        PaperStatus status = PaperStatus.fromCode(paper.getStatus());
        if (status == PaperStatus.PUBLISHED || status == PaperStatus.ARCHIVED) {
            throw new BusinessException("已发布或已归档的试卷不允许删除题目");
        }
        ExamPaperQuestion paperQuestion = paperQuestionMapper.selectById(toInt(paperQuestionId, "试卷题目ID"));
        if (paperQuestion == null) {
            throw new BusinessException("试卷题目不存在: " + paperQuestionId);
        }
        if (!paperQuestion.getPaperId().equals(paper.getId())) {
            throw new BusinessException("试卷题目不属于当前试卷");
        }
        QueryWrapper<com.cupk.aiacademy.pojo.ExamAnswer> answerQuery = new QueryWrapper<>();
        answerQuery.eq("paper_question_id", paperQuestion.getId());
        if (examAnswerMapper.selectCount(answerQuery) > 0) {
            throw new BusinessException("该试卷题目已被答卷使用，不能删除");
        }
        paperQuestionMapper.deleteById(paperQuestion.getId());
        recalculateTotalScore(paper);
    }

    public ExamPaper getEntityById(Long id) {
        ExamPaper paper = paperMapper.selectById(toInt(id, "试卷ID"));
        if (paper == null) {
            throw new BusinessException("试卷不存在: " + id);
        }
        return paper;
    }

    public PaperResponseDTO toResponse(ExamPaper paper) {
        PaperResponseDTO response = new PaperResponseDTO();
        response.setId(toLong(paper.getId()));
        response.setCourseId(toLong(paper.getCourseId()));
        response.setTeacherId(toLong(paper.getTeacherId()));
        response.setName(paper.getTitle());
        response.setDescription(paper.getDescription());
        response.setTotalScore(paper.getTotalScore());
        response.setDurationMinutes(paper.getDurationMinutes());
        response.setStatus(PaperStatus.fromCode(paper.getStatus()));
        response.setCreatedAt(paper.getCreateTime());
        response.setUpdatedAt(paper.getUpdateTime());
        response.setPaperQuestions(listPaperQuestions(paper.getId()).stream()
                .map(this::toPaperQuestionResponse)
                .toList());
        return response;
    }

    private void validateRequest(PaperRequestDTO request) {
        if (request == null) {
            throw new BusinessException("试卷信息不能为空");
        }
        if (request.getQuestions() == null || request.getQuestions().isEmpty()) {
            throw new BusinessException("试卷必须包含至少一道题目");
        }
        Set<Long> questionIds = new HashSet<>();
        Set<Integer> sortOrders = new HashSet<>();
        for (PaperRequestDTO.PaperQuestionRequest item : request.getQuestions()) {
            if (item.getQuestionId() == null) {
                throw new BusinessException("试卷题目不能为空");
            }
            if (!questionIds.add(item.getQuestionId())) {
                throw new BusinessException("试卷中不能重复添加同一道题，题目ID：" + item.getQuestionId());
            }
            if (item.getSortOrder() == null || !sortOrders.add(item.getSortOrder())) {
                throw new BusinessException("试卷中题目序号不能重复，序号：" + item.getSortOrder());
            }
            questionService.getEntityById(item.getQuestionId());
        }
    }

    private void fillPaper(ExamPaper paper, PaperRequestDTO request) {
        paper.setCourseId(toNullableInt(request.getCourseId(), "课程ID"));
        paper.setTeacherId(toNullableInt(request.getTeacherId(), "教师ID"));
        paper.setTitle(request.getName());
        paper.setDescription(request.getDescription());
        paper.setDurationMinutes(request.getDurationMinutes());
        paper.setStatus(request.getStatus().getCode());
        paper.setTotalScore(totalScore(request.getQuestions()));
    }

    private void insertPaperQuestions(ExamPaper paper, List<PaperRequestDTO.PaperQuestionRequest> questions) {
        for (PaperRequestDTO.PaperQuestionRequest item : questions) {
            ExamPaperQuestion paperQuestion = new ExamPaperQuestion();
            paperQuestion.setPaperId(paper.getId());
            paperQuestion.setQuestionId(toInt(item.getQuestionId(), "题目ID"));
            paperQuestion.setSortOrder(item.getSortOrder());
            paperQuestion.setScore(item.getScore());
            paperQuestion.setRequiredAnswer(Boolean.TRUE.equals(item.getRequiredAnswer()) ? 1 : 0);
            paperQuestionMapper.insert(paperQuestion);
        }
    }

    private BigDecimal totalScore(List<PaperRequestDTO.PaperQuestionRequest> questions) {
        return questions.stream()
                .map(PaperRequestDTO.PaperQuestionRequest::getScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<ExamPaperQuestion> listPaperQuestions(Integer paperId) {
        QueryWrapper<ExamPaperQuestion> query = new QueryWrapper<>();
        query.eq("paper_id", paperId).orderByAsc("sort_order").orderByAsc("id");
        return paperQuestionMapper.selectList(query);
    }

    private PaperResponseDTO.PaperQuestionResponse toPaperQuestionResponse(ExamPaperQuestion item) {
        Question question = questionService.getEntityById(toLong(item.getQuestionId()));
        PaperResponseDTO.PaperQuestionResponse response = new PaperResponseDTO.PaperQuestionResponse();
        response.setId(toLong(item.getId()));
        response.setQuestionId(toLong(item.getQuestionId()));
        response.setQuestionContent(question.getTitle());
        response.setSortOrder(item.getSortOrder());
        response.setScore(item.getScore());
        response.setRequiredAnswer(item.getRequiredAnswer() == null || item.getRequiredAnswer() == 1);
        return response;
    }

    private void recalculateTotalScore(ExamPaper paper) {
        BigDecimal totalScore = listPaperQuestions(paper.getId()).stream()
                .map(ExamPaperQuestion::getScore)
                .sorted(Comparator.naturalOrder())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        paper.setTotalScore(totalScore);
        paper.setUpdateTime(LocalDateTime.now());
        paperMapper.updateById(paper);
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
