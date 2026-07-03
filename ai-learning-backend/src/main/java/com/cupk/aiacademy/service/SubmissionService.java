package com.cupk.aiacademy.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.ExamStatus;
import com.cupk.aiacademy.common.JudgeStatus;
import com.cupk.aiacademy.common.QuestionType;
import com.cupk.aiacademy.common.SubmissionStatus;
import com.cupk.aiacademy.dto.QuestionResponseDTO;
import com.cupk.aiacademy.dto.StudentSubmissionResponseDTO;
import com.cupk.aiacademy.dto.SubmissionRequestsDTO;
import com.cupk.aiacademy.dto.SubmissionResponseDTO;
import com.cupk.aiacademy.mapper.ExamAnswerMapper;
import com.cupk.aiacademy.mapper.ExamPaperQuestionMapper;
import com.cupk.aiacademy.mapper.ExamRecordMapper;
import com.cupk.aiacademy.mapper.ExamSessionMapper;
import com.cupk.aiacademy.pojo.ExamAnswer;
import com.cupk.aiacademy.pojo.ExamPaperQuestion;
import com.cupk.aiacademy.pojo.ExamRecord;
import com.cupk.aiacademy.pojo.ExamSession;
import com.cupk.aiacademy.pojo.Question;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class SubmissionService {
    private final ExamService examService;
    private final QuestionService questionService;
    private final ExamSessionMapper examSessionMapper;
    private final ExamRecordMapper recordMapper;
    private final ExamAnswerMapper answerMapper;
    private final ExamPaperQuestionMapper paperQuestionMapper;

    public SubmissionService(ExamService examService,
                             QuestionService questionService,
                             ExamSessionMapper examSessionMapper,
                             ExamRecordMapper recordMapper,
                             ExamAnswerMapper answerMapper,
                             ExamPaperQuestionMapper paperQuestionMapper) {
        this.examService = examService;
        this.questionService = questionService;
        this.examSessionMapper = examSessionMapper;
        this.recordMapper = recordMapper;
        this.answerMapper = answerMapper;
        this.paperQuestionMapper = paperQuestionMapper;
    }

    @Transactional
    public SubmissionResponseDTO start(Long examId, String candidateId) {
        return start(examId, candidateId, parseCandidateStudentId(candidateId));
    }

    @Transactional
    public SubmissionResponseDTO start(Long examId, String candidateId, Long studentId) {
        ExamSession exam = examService.getEntityById(examId);
        if (ExamStatus.fromCode(exam.getStatus()) != ExamStatus.PUBLISHED) {
            throw new BusinessException("考试未发布，不能开始答题");
        }
        LocalDateTime now = LocalDateTime.now();
        if (exam.getStartTime() != null && now.isBefore(exam.getStartTime())) {
            throw new BusinessException("考试尚未开始，当前时间：" + now + "，开始时间：" + exam.getStartTime());
        }
        if (exam.getEndTime() != null && now.isAfter(exam.getEndTime())) {
            throw new BusinessException("考试已结束，当前时间：" + now + "，结束时间：" + exam.getEndTime());
        }
        if (isBlank(candidateId) && studentId == null) {
            throw new BusinessException("考生不能为空");
        }
        ExamRecord existing = findExistingRecord(exam.getId(), candidateId, studentId);
        if (existing != null) {
            SubmissionStatus status = SubmissionStatus.fromCode(existing.getStatus());
            if (status == SubmissionStatus.IN_PROGRESS) {
                return toResponse(existing);
            }
            throw new BusinessException("您已经完成该考试，不能重复参加");
        }

        ExamRecord record = new ExamRecord();
        record.setExamSessionId(exam.getId());
        record.setStudentId(toNullableInt(studentId, "学生ID"));
        record.setCandidateId(candidateId);
        record.setStartTime(now);
        record.setObjectiveScore(BigDecimal.ZERO);
        record.setSubjectiveScore(BigDecimal.ZERO);
        record.setTotalScore(BigDecimal.ZERO);
        record.setStatus(SubmissionStatus.IN_PROGRESS.getCode());
        record.setCreateTime(now);
        record.setUpdateTime(now);
        recordMapper.insert(record);

        for (ExamPaperQuestion paperQuestion : listPaperQuestions(exam.getPaperId())) {
            Question question = questionService.getEntityById(paperQuestion.getQuestionId().longValue());
            ExamAnswer answer = new ExamAnswer();
            answer.setExamRecordId(record.getId());
            answer.setPaperQuestionId(paperQuestion.getId());
            answer.setQuestionId(paperQuestion.getQuestionId());
            answer.setCorrectAnswer(joinStandardAnswers(question));
            answer.setScore(BigDecimal.ZERO);
            answer.setGradeStatus(JudgeStatus.NOT_ANSWERED.getCode());
            answerMapper.insert(answer);
        }
        return toResponse(record);
    }

    @Transactional
    public SubmissionResponseDTO saveAnswers(Long submissionId, SubmissionRequestsDTO.SaveAnswerRequest request) {
        ExamRecord record = getEntityById(submissionId);
        if (SubmissionStatus.fromCode(record.getStatus()) != SubmissionStatus.IN_PROGRESS) {
            throw new BusinessException("当前答卷不允许继续作答");
        }
        checkDuration(record, true);
        if (request == null || request.getAnswers() == null || request.getAnswers().isEmpty()) {
            throw new BusinessException("答案不能为空");
        }
        for (SubmissionRequestsDTO.AnswerItem item : request.getAnswers()) {
            ExamAnswer answer = findAnswer(record.getId(), toInt(item.getPaperQuestionId(), "试卷题目ID"));
            String normalizedAnswer = normalizeAnswer(item);
            answer.setStudentAnswer(normalizedAnswer);
            answer.setIsCorrect(null);
            answer.setGradeStatus(isBlank(normalizedAnswer) ? JudgeStatus.NOT_ANSWERED.getCode() : JudgeStatus.ANSWERED.getCode());
            answerMapper.updateById(answer);
        }
        return toResponse(record);
    }

    @Transactional
    public SubmissionResponseDTO submit(Long submissionId) {
        ExamRecord record = getEntityById(submissionId);
        if (SubmissionStatus.fromCode(record.getStatus()) != SubmissionStatus.IN_PROGRESS) {
            throw new BusinessException("当前答卷状态不允许交卷");
        }
        boolean timeout = checkDuration(record, false);
        BigDecimal objectiveScore = BigDecimal.ZERO;
        boolean hasSubjective = false;
        for (ExamAnswer answer : listAnswers(record.getId())) {
            Question question = questionService.getEntityById(answer.getQuestionId().longValue());
            ExamPaperQuestion paperQuestion = paperQuestionMapper.selectById(answer.getPaperQuestionId());
            QuestionType type = QuestionType.fromStorageValue(question.getQuestionType());
            if (type == QuestionType.SHORT_ANSWER) {
                answer.setGradeStatus(JudgeStatus.PENDING_MANUAL_REVIEW.getCode());
                answer.setScore(BigDecimal.ZERO);
                hasSubjective = true;
                answerMapper.updateById(answer);
                continue;
            }
            boolean correct = judgeObjective(question, answer.getStudentAnswer());
            answer.setIsCorrect(correct ? 1 : 0);
            if (correct) {
                BigDecimal score = paperQuestion == null || paperQuestion.getScore() == null ? BigDecimal.ZERO : paperQuestion.getScore();
                answer.setScore(score);
                objectiveScore = objectiveScore.add(score);
            } else {
                answer.setScore(BigDecimal.ZERO);
            }
            answer.setGradeStatus(JudgeStatus.AUTO_CORRECT.getCode());
            answerMapper.updateById(answer);
        }
        LocalDateTime now = LocalDateTime.now();
        record.setObjectiveScore(objectiveScore);
        record.setSubmitTime(now);
        record.setTotalScore(objectiveScore.add(record.getSubjectiveScore() == null ? BigDecimal.ZERO : record.getSubjectiveScore()));
        record.setStatus(hasSubjective ? SubmissionStatus.PENDING_MANUAL_REVIEW.getCode() : SubmissionStatus.GRADED.getCode());
        record.setComment(timeout ? timeoutComment(record, now) : record.getComment());
        record.setUpdateTime(now);
        recordMapper.updateById(record);
        return toResponse(record);
    }

    @Transactional
    public SubmissionResponseDTO manualGrade(Long submissionId, SubmissionRequestsDTO.ManualGradeRequest request) {
        ExamRecord record = getEntityById(submissionId);
        if (request == null || request.getGrades() == null || request.getGrades().isEmpty()) {
            throw new BusinessException("批改结果不能为空");
        }
        for (SubmissionRequestsDTO.ManualGradeItem item : request.getGrades()) {
            ExamAnswer answer = answerMapper.selectById(toInt(item.getSubmissionAnswerId(), "答题记录ID"));
            if (answer == null) {
                throw new BusinessException("答题记录不存在: " + item.getSubmissionAnswerId());
            }
            if (!Objects.equals(answer.getExamRecordId(), record.getId())) {
                throw new BusinessException("答题记录不属于当前答卷");
            }
            ExamPaperQuestion paperQuestion = paperQuestionMapper.selectById(answer.getPaperQuestionId());
            BigDecimal maxScore = paperQuestion == null || paperQuestion.getScore() == null ? BigDecimal.ZERO : paperQuestion.getScore();
            if (item.getScore().compareTo(maxScore) > 0) {
                throw new BusinessException("主观题得分不能超过题目分值。题目分值：" + maxScore + "，给定分数：" + item.getScore());
            }
            answer.setScore(item.getScore());
            answer.setComment(item.getComment());
            answer.setIsCorrect(null);
            answer.setGradeStatus(JudgeStatus.MANUALLY_GRADED.getCode());
            answer.setGraderId(toNullableInt(request.getGraderId(), "批改教师ID"));
            answer.setGradeTime(LocalDateTime.now());
            answerMapper.updateById(answer);
        }
        BigDecimal subjectiveScore = listAnswers(record.getId()).stream()
                .filter(answer -> QuestionType.fromStorageValue(questionService.getEntityById(answer.getQuestionId().longValue()).getQuestionType()) == QuestionType.SHORT_ANSWER)
                .map(answer -> answer.getScore() == null ? BigDecimal.ZERO : answer.getScore())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        record.setSubjectiveScore(subjectiveScore);
        record.setTotalScore((record.getObjectiveScore() == null ? BigDecimal.ZERO : record.getObjectiveScore()).add(subjectiveScore));
        record.setStatus(SubmissionStatus.GRADED.getCode());
        record.setUpdateTime(LocalDateTime.now());
        recordMapper.updateById(record);
        return toResponse(record);
    }

    @Transactional(readOnly = true)
    public SubmissionResponseDTO getById(Long submissionId) {
        return toResponse(getEntityById(submissionId));
    }

    @Transactional(readOnly = true)
    public StudentSubmissionResponseDTO getStudentSubmissionById(Long submissionId) {
        return toStudentResponse(getEntityById(submissionId));
    }

    @Transactional(readOnly = true)
    public List<SubmissionResponseDTO> list(Long examId, String candidateId) {
        QueryWrapper<ExamRecord> query = new QueryWrapper<>();
        if (examId != null) {
            query.eq("exam_session_id", toInt(examId, "考试ID"));
        }
        if (!isBlank(candidateId)) {
            query.eq("candidate_id", candidateId);
        }
        query.orderByDesc("id");
        return recordMapper.selectList(query).stream().map(this::toResponse).toList();
    }

    public ExamRecord getEntityById(Long submissionId) {
        ExamRecord record = recordMapper.selectById(toInt(submissionId, "答卷ID"));
        if (record == null) {
            throw new BusinessException("答卷不存在: " + submissionId);
        }
        return record;
    }

    public SubmissionResponseDTO toResponse(ExamRecord record) {
        ExamSession exam = examSessionMapper.selectById(record.getExamSessionId());
        SubmissionResponseDTO response = new SubmissionResponseDTO();
        response.setId(toLong(record.getId()));
        response.setExamId(toLong(record.getExamSessionId()));
        response.setExamName(exam == null ? null : exam.getTitle());
        response.setCandidateId(record.getCandidateId());
        response.setStudentId(toLong(record.getStudentId()));
        response.setStartedAt(record.getStartTime());
        response.setSubmittedAt(record.getSubmitTime());
        response.setObjectiveScore(record.getObjectiveScore());
        response.setSubjectiveScore(record.getSubjectiveScore());
        response.setTotalScore(record.getTotalScore());
        response.setStatus(SubmissionStatus.fromCode(record.getStatus()));
        response.setAnswers(listAnswers(record.getId()).stream()
                .map(this::toAnswerResponse)
                .toList());
        return response;
    }

    public StudentSubmissionResponseDTO toStudentResponse(ExamRecord record) {
        ExamSession exam = examSessionMapper.selectById(record.getExamSessionId());
        StudentSubmissionResponseDTO response = new StudentSubmissionResponseDTO();
        response.setId(toLong(record.getId()));
        response.setExamId(toLong(record.getExamSessionId()));
        response.setExamName(exam == null ? null : exam.getTitle());
        response.setCandidateId(record.getCandidateId());
        response.setStudentId(toLong(record.getStudentId()));
        response.setStartedAt(record.getStartTime());
        response.setSubmittedAt(record.getSubmitTime());
        response.setObjectiveScore(record.getObjectiveScore());
        response.setSubjectiveScore(record.getSubjectiveScore());
        response.setTotalScore(record.getTotalScore());
        response.setStatus(SubmissionStatus.fromCode(record.getStatus()));
        response.setAnswers(listAnswers(record.getId()).stream()
                .map(this::toStudentAnswerResponse)
                .toList());
        return response;
    }

    private SubmissionResponseDTO.SubmissionAnswerResponse toAnswerResponse(ExamAnswer answer) {
        Question question = questionService.getEntityById(answer.getQuestionId().longValue());
        SubmissionResponseDTO.SubmissionAnswerResponse response = new SubmissionResponseDTO.SubmissionAnswerResponse();
        response.setId(toLong(answer.getId()));
        response.setPaperQuestionId(toLong(answer.getPaperQuestionId()));
        response.setQuestionId(toLong(answer.getQuestionId()));
        response.setQuestionContent(question.getTitle());
        response.setQuestionType(QuestionType.fromStorageValue(question.getQuestionType()));
        response.setAnswerContent(answer.getStudentAnswer());
        response.setCorrect(answer.getIsCorrect() == null ? null : answer.getIsCorrect() == 1);
        response.setScore(answer.getScore());
        response.setJudgeStatus(JudgeStatus.fromCode(answer.getGradeStatus()));
        response.setComment(answer.getComment());
        return response;
    }

    private StudentSubmissionResponseDTO.StudentAnswerResponse toStudentAnswerResponse(ExamAnswer answer) {
        Question question = questionService.getEntityById(answer.getQuestionId().longValue());
        ExamPaperQuestion paperQuestion = paperQuestionMapper.selectById(answer.getPaperQuestionId());
        StudentSubmissionResponseDTO.StudentAnswerResponse response = new StudentSubmissionResponseDTO.StudentAnswerResponse();
        response.setId(toLong(answer.getId()));
        response.setPaperQuestionId(toLong(answer.getPaperQuestionId()));
        response.setQuestionId(toLong(answer.getQuestionId()));
        response.setQuestionContent(question.getTitle());
        response.setQuestionType(QuestionType.fromStorageValue(question.getQuestionType()));
        response.setScore(paperQuestion == null ? BigDecimal.ZERO : paperQuestion.getScore());
        response.setSortOrder(paperQuestion == null ? null : paperQuestion.getSortOrder());
        response.setRequiredAnswer(paperQuestion == null || paperQuestion.getRequiredAnswer() == null || paperQuestion.getRequiredAnswer() == 1);
        response.setOptions(questionService.parseOptions(question).stream().map(option -> {
            StudentSubmissionResponseDTO.OptionItem item = new StudentSubmissionResponseDTO.OptionItem();
            item.setOptionKey(option.getOptionKey());
            item.setOptionContent(option.getOptionContent());
            item.setSortOrder(option.getSortOrder());
            return item;
        }).toList());
        response.setAnswerContent(answer.getStudentAnswer());
        response.setCorrect(answer.getIsCorrect() == null ? null : answer.getIsCorrect() == 1);
        response.setObtainedScore(answer.getScore());
        response.setJudgeStatus(JudgeStatus.fromCode(answer.getGradeStatus()));
        response.setComment(answer.getComment());
        return response;
    }

    private ExamRecord findExistingRecord(Integer examId, String candidateId, Long studentId) {
        QueryWrapper<ExamRecord> query = new QueryWrapper<>();
        query.eq("exam_session_id", examId);
        if (!isBlank(candidateId) && studentId != null) {
            query.and(wrapper -> wrapper.eq("candidate_id", candidateId).or().eq("student_id", toInt(studentId, "学生ID")));
        } else if (!isBlank(candidateId)) {
            query.eq("candidate_id", candidateId);
        } else if (studentId != null) {
            query.eq("student_id", toInt(studentId, "学生ID"));
        }
        query.orderByDesc("id").last("limit 1");
        return recordMapper.selectOne(query);
    }

    private List<ExamPaperQuestion> listPaperQuestions(Integer paperId) {
        QueryWrapper<ExamPaperQuestion> query = new QueryWrapper<>();
        query.eq("paper_id", paperId).orderByAsc("sort_order").orderByAsc("id");
        return paperQuestionMapper.selectList(query);
    }

    private List<ExamAnswer> listAnswers(Integer recordId) {
        QueryWrapper<ExamAnswer> query = new QueryWrapper<>();
        query.eq("exam_record_id", recordId).orderByAsc("paper_question_id").orderByAsc("id");
        return answerMapper.selectList(query);
    }

    private ExamAnswer findAnswer(Integer recordId, Integer paperQuestionId) {
        QueryWrapper<ExamAnswer> query = new QueryWrapper<>();
        query.eq("exam_record_id", recordId).eq("paper_question_id", paperQuestionId);
        ExamAnswer answer = answerMapper.selectOne(query);
        if (answer == null) {
            throw new BusinessException("答题记录不存在: " + paperQuestionId);
        }
        return answer;
    }

    private String normalizeAnswer(SubmissionRequestsDTO.AnswerItem item) {
        if (item.getSelectedOptionKeys() != null && !item.getSelectedOptionKeys().isEmpty()) {
            return item.getSelectedOptionKeys().stream()
                    .sorted()
                    .collect(Collectors.joining(","));
        }
        return item.getAnswerContent();
    }

    private boolean judgeObjective(Question question, String submittedAnswer) {
        if (isBlank(submittedAnswer)) {
            return false;
        }
        List<String> standardAnswers = questionService.parseAnswers(question).stream()
                .sorted(Comparator.comparing(QuestionResponseDTO.AnswerResponse::getSortOrder))
                .map(QuestionResponseDTO.AnswerResponse::getAnswerContent)
                .toList();
        if (standardAnswers.isEmpty()) {
            return false;
        }
        QuestionType type = QuestionType.fromStorageValue(question.getQuestionType());
        return switch (type) {
            case SINGLE_CHOICE, TRUE_FALSE -> normalize(submittedAnswer).equals(normalize(standardAnswers.get(0)));
            case MULTIPLE_CHOICE -> normalizeMulti(submittedAnswer).equals(normalizeMulti(String.join(",", standardAnswers)));
            case FILL_BLANK -> standardAnswers.stream().anyMatch(answer -> normalizeBlank(answer).equals(normalizeBlank(submittedAnswer)));
            case SHORT_ANSWER -> false;
        };
    }

    private String joinStandardAnswers(Question question) {
        return questionService.parseAnswers(question).stream()
                .sorted(Comparator.comparing(QuestionResponseDTO.AnswerResponse::getSortOrder))
                .map(QuestionResponseDTO.AnswerResponse::getAnswerContent)
                .collect(Collectors.joining(","));
    }

    private boolean checkDuration(ExamRecord record, boolean rejectOnTimeout) {
        ExamSession exam = examSessionMapper.selectById(record.getExamSessionId());
        if (record.getStartTime() == null || exam == null || exam.getDuration() == null || exam.getDuration() <= 0) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        Duration elapsed = Duration.between(record.getStartTime(), now);
        boolean timeout = elapsed.toMinutes() > exam.getDuration();
        if (timeout && rejectOnTimeout) {
            throw new BusinessException("考试时间已超时，无法保存答案。用时：" + elapsed.toMinutes() + "分钟，限时：" + exam.getDuration() + "分钟");
        }
        return timeout;
    }

    private String timeoutComment(ExamRecord record, LocalDateTime now) {
        ExamSession exam = examSessionMapper.selectById(record.getExamSessionId());
        long elapsed = Duration.between(record.getStartTime(), now).toMinutes();
        return "超时自动交卷，用时：" + elapsed + "分钟，限时：" + exam.getDuration() + "分钟";
    }

    private Long parseCandidateStudentId(String candidateId) {
        if (isBlank(candidateId)) {
            return null;
        }
        try {
            return Long.parseLong(candidateId);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeBlank(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeMulti(String value) {
        return List.of(value.split(","))
                .stream()
                .map(this::normalize)
                .filter(item -> !item.isBlank())
                .sorted()
                .collect(Collectors.joining(","));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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
