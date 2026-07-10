package com.cupk.aiacademy.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.PaperStatus;
import com.cupk.aiacademy.common.PaperPreflightWarningException;
import com.cupk.aiacademy.common.QuestionType;
import com.cupk.aiacademy.dto.PaperRequestDTO;
import com.cupk.aiacademy.dto.PaperPreflightResponse;
import com.cupk.aiacademy.dto.PaperResponseDTO;
import com.cupk.aiacademy.dto.QuestionRequestDTO;
import com.cupk.aiacademy.mapper.ExamAnswerMapper;
import com.cupk.aiacademy.mapper.ExamPaperMapper;
import com.cupk.aiacademy.mapper.ExamPaperQuestionMapper;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.ExamAnswer;
import com.cupk.aiacademy.pojo.ExamPaper;
import com.cupk.aiacademy.pojo.ExamPaperQuestion;
import com.cupk.aiacademy.pojo.Question;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class PaperService {
    private final ExamPaperMapper paperMapper;
    private final ExamPaperQuestionMapper paperQuestionMapper;
    private final ExamAnswerMapper examAnswerMapper;
    private final QuestionService questionService;
    private final PermissionService permissionService;

    public PaperService(ExamPaperMapper paperMapper,
                        ExamPaperQuestionMapper paperQuestionMapper,
                        ExamAnswerMapper examAnswerMapper,
                        QuestionService questionService,
                        PermissionService permissionService) {
        this.paperMapper = paperMapper;
        this.paperQuestionMapper = paperQuestionMapper;
        this.examAnswerMapper = examAnswerMapper;
        this.questionService = questionService;
        this.permissionService = permissionService;
    }

    @Transactional
    public PaperResponseDTO create(PaperRequestDTO request) {
        requireWarningsAccepted(prepareAndPreflight(request), request);
        resolveInlineQuestions(request);
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
        ExamPaper paper = getEntityById(id);
        permissionService.requirePaperTeacher(paper);
        requireWarningsAccepted(prepareAndPreflight(request), request);
        PaperStatus status = PaperStatus.fromCode(paper.getStatus());
        if (status == PaperStatus.PUBLISHED || status == PaperStatus.ARCHIVED) {
            throw new BusinessException("已发布或已归档的试卷不允许修改");
        }
        resolveInlineQuestions(request);
        fillPaper(paper, request);
        paper.setUpdateTime(LocalDateTime.now());
        paperMapper.updateById(paper);
        deletePaperQuestions(paper.getId());
        insertPaperQuestions(paper, request.getQuestions());
        return toResponse(paper);
    }

    @Transactional(readOnly = true)
    public PaperPreflightResponse preflight(PaperRequestDTO request) {
        return prepareAndPreflight(request);
    }

    @Transactional(readOnly = true)
    public PaperResponseDTO getById(Long id) {
        ExamPaper paper = getEntityById(id);
        requireReadablePaper(paper);
        return toResponse(paper);
    }

    @Transactional(readOnly = true)
    public List<PaperResponseDTO> list(Long teacherId) {
        return list(teacherId, null, null);
    }

    @Transactional(readOnly = true)
    public List<PaperResponseDTO> list(Long teacherId, String keyword, Long courseId) {
        QueryWrapper<ExamPaper> query = new QueryWrapper<>();
        var user = permissionService.currentUser();
        if (permissionService.isTeacher(user)) {
            query.eq("teacher_id", user.getUserId());
        } else if (permissionService.isStudent(user)) {
            List<Integer> courseIds = permissionService.studentCourseIds(user.getUserId());
            if (courseIds.isEmpty()) {
                return List.of();
            }
            if (courseId != null && courseId > 0) {
                if (!courseIds.contains(courseId.intValue())) {
                    return List.of();
                }
                query.eq("course_id", courseId);
            } else {
                query.in("course_id", courseIds);
            }
            query.eq("status", PaperStatus.PUBLISHED.getCode());
        } else if (teacherId != null && teacherId > 0) {
            query.eq("teacher_id", teacherId);
        }
        if (courseId != null && courseId > 0 && !permissionService.isStudent(user)) {
            query.eq("course_id", courseId);
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            query.like("title", keyword.trim());
        }
        query.orderByDesc("id");
        return paperMapper.selectList(query).stream().map(this::toResponse).toList();
    }

    @Transactional
    public void delete(Long id) {
        ExamPaper paper = getEntityById(id);
        permissionService.requirePaperTeacher(paper);
        PaperStatus status = PaperStatus.fromCode(paper.getStatus());
        if (status == PaperStatus.PUBLISHED) {
            throw new BusinessException("已发布的试卷不允许删除，请先改为草稿状态");
        }
        deletePaperQuestions(paper.getId());
        paperMapper.deleteById(paper.getId());
    }

    @Transactional
    public PaperResponseDTO publish(Long id) {
        ExamPaper paper = getEntityById(id);
        permissionService.requirePaperTeacher(paper);
        if (PaperStatus.fromCode(paper.getStatus()) != PaperStatus.DRAFT) {
            throw new BusinessException("只有草稿状态的试卷才能启用");
        }
        paper.setStatus(PaperStatus.PUBLISHED.getCode());
        paper.setUpdateTime(LocalDateTime.now());
        paperMapper.updateById(paper);
        return toResponse(paper);
    }

    @Transactional
    public void removePaperQuestion(Long paperId, Long paperQuestionId) {
        ExamPaper paper = getEntityById(paperId);
        permissionService.requirePaperTeacher(paper);
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
        QueryWrapper<ExamAnswer> answerQuery = new QueryWrapper<>();
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

    private PaperPreflightResponse prepareAndPreflight(PaperRequestDTO request) {
        if (request == null) {
            throw new BusinessException("试卷信息不能为空");
        }
        prepareForWrite(request);
        if (request.getQuestions() == null || request.getQuestions().isEmpty()) {
            throw new BusinessException("试卷必须包含至少一道题目");
        }
        Set<Long> questionIds = new HashSet<>();
        Set<Integer> sortOrders = new HashSet<>();
        Set<QuestionType> presentTypes = EnumSet.noneOf(QuestionType.class);
        BigDecimal totalScore = BigDecimal.ZERO;
        for (PaperRequestDTO.PaperQuestionRequest item : request.getQuestions()) {
            if (item == null) {
                throw new BusinessException("试卷题目不能为空");
            }
            boolean hasQuestionId = item.getQuestionId() != null;
            boolean hasInlineQuestion = item.getInlineQuestion() != null;
            if (hasQuestionId == hasInlineQuestion) {
                throw new BusinessException("每道试卷题目必须且只能选择题库题目或自定义题目");
            }
            if (item.getScore() == null || item.getScore().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("试卷题目分值必须大于 0");
            }
            totalScore = totalScore.add(item.getScore());
            if (item.getSortOrder() == null || item.getSortOrder() <= 0 || !sortOrders.add(item.getSortOrder())) {
                throw new BusinessException("试卷中题目序号不能重复，序号：" + item.getSortOrder());
            }
            if (hasQuestionId && !questionIds.add(item.getQuestionId())) {
                throw new BusinessException("试卷中不能重复添加同一道题，题目ID：" + item.getQuestionId());
            }
            if (hasQuestionId) {
                Question question = questionService.getEntityById(item.getQuestionId());
                if (!Objects.equals(request.getCourseId().intValue(), question.getCourseId())) {
                    throw new BusinessException("题目不属于当前试卷课程，题目ID：" + item.getQuestionId());
                }
                if (!Objects.equals(request.getTeacherId().intValue(), question.getTeacherId())) {
                    throw new BusinessException("题目不属于当前试卷教师，题目ID：" + item.getQuestionId());
                }
                presentTypes.add(QuestionType.fromStorageValue(question.getQuestionType()));
            } else {
                QuestionRequestDTO inlineQuestion = item.getInlineQuestion();
                assignInlineQuestionOwnership(inlineQuestion, request);
                questionService.validateForPaperComposition(inlineQuestion);
                presentTypes.add(inlineQuestion.getType());
            }
        }
        return diagnostics(totalScore, presentTypes);
    }

    private PaperPreflightResponse diagnostics(BigDecimal totalScore, Set<QuestionType> presentTypes) {
        List<QuestionType> expectedTypes = Arrays.asList(QuestionType.values());
        List<QuestionType> missingTypes = expectedTypes.stream().filter(type -> !presentTypes.contains(type)).toList();
        List<String> warningCodes = new ArrayList<>();
        if (totalScore.compareTo(new BigDecimal("100")) != 0) {
            warningCodes.add("TOTAL_NOT_100");
        }
        if (!missingTypes.isEmpty()) {
            warningCodes.add("MISSING_TYPES");
        }
        PaperPreflightResponse response = new PaperPreflightResponse();
        response.setTotalScore(totalScore);
        response.setExpectedTypes(expectedTypes);
        response.setPresentTypes(expectedTypes.stream().filter(presentTypes::contains).toList());
        response.setMissingTypes(missingTypes);
        response.setWarningCodes(warningCodes);
        return response;
    }

    private void requireWarningsAccepted(PaperPreflightResponse diagnostics, PaperRequestDTO request) {
        if (diagnostics.hasWarnings() && !Boolean.TRUE.equals(request.getIgnoreWarnings())) {
            throw new PaperPreflightWarningException(diagnostics);
        }
    }

    private void resolveInlineQuestions(PaperRequestDTO request) {
        for (PaperRequestDTO.PaperQuestionRequest item : request.getQuestions()) {
            if (item.getInlineQuestion() == null) {
                continue;
            }
            Question existingQuestion = questionService.findEquivalentQuestion(item.getInlineQuestion());
            item.setQuestionId(existingQuestion == null
                    ? questionService.create(item.getInlineQuestion()).getId()
                    : existingQuestion.getId().longValue());
            item.setInlineQuestion(null);
        }
    }

    private void assignInlineQuestionOwnership(QuestionRequestDTO inlineQuestion, PaperRequestDTO paper) {
        inlineQuestion.setCourseId(paper.getCourseId());
        inlineQuestion.setTeacherId(paper.getTeacherId());
        inlineQuestion.setStatus(com.cupk.aiacademy.common.RecordStatus.ACTIVE);
    }

    private void prepareForWrite(PaperRequestDTO request) {
        permissionService.requireTeacherOrAdmin();
        if (request.getCourseId() == null) {
            throw new BusinessException("课程不能为空");
        }
        Course course = permissionService.getCourse(toInt(request.getCourseId(), "课程ID"));
        var user = permissionService.currentUser();
        if (permissionService.isTeacher(user)) {
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
    }

    private void requireReadablePaper(ExamPaper paper) {
        var user = permissionService.currentUser();
        if (permissionService.isAdmin(user)) {
            return;
        }
        if (permissionService.isTeacher(user)) {
            permissionService.requirePaperTeacher(paper);
            return;
        }
        if (PaperStatus.fromCode(paper.getStatus()) != PaperStatus.PUBLISHED) {
            throw new com.cupk.aiacademy.common.ForbiddenException("无权查看该试卷");
        }
        List<Integer> courseIds = permissionService.studentCourseIds(user.getUserId());
        if (paper.getCourseId() == null || !courseIds.contains(paper.getCourseId())) {
            throw new com.cupk.aiacademy.common.ForbiddenException("无权查看该试卷");
        }
    }

    private void deletePaperQuestions(Integer paperId) {
        QueryWrapper<ExamPaperQuestion> deleteQuery = new QueryWrapper<>();
        deleteQuery.eq("paper_id", paperId);
        paperQuestionMapper.delete(deleteQuery);
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
        Question question = null;
        try {
            question = questionService.getEntityById(toLong(item.getQuestionId()));
        } catch (BusinessException ignored) {
            // Historical data may contain a paper-question row whose source question was deleted
            // before referential checks existed. Keep the paper list readable and make the
            // broken reference explicit instead of failing the whole page.
        }
        PaperResponseDTO.PaperQuestionResponse response = new PaperResponseDTO.PaperQuestionResponse();
        response.setId(toLong(item.getId()));
        response.setQuestionId(toLong(item.getQuestionId()));
        response.setQuestionContent(question == null ? "题目已删除（ID: " + item.getQuestionId() + "）" : question.getTitle());
        response.setSortOrder(item.getSortOrder());
        response.setScore(item.getScore());
        response.setRequiredAnswer(item.getRequiredAnswer() == null || item.getRequiredAnswer() == 1);
        return response;
    }

    private void recalculateTotalScore(ExamPaper paper) {
        BigDecimal totalScore = listPaperQuestions(paper.getId()).stream()
                .map(ExamPaperQuestion::getScore)
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
