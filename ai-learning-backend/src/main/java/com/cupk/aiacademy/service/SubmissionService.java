package com.cupk.aiacademy.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
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
    private final PermissionService permissionService;
    private final ObjectMapper objectMapper;

    public SubmissionService(ExamService examService,
                             QuestionService questionService,
                             ExamSessionMapper examSessionMapper,
                             ExamRecordMapper recordMapper,
                             ExamAnswerMapper answerMapper,
                             ExamPaperQuestionMapper paperQuestionMapper,
                             PermissionService permissionService,
                             ObjectMapper objectMapper) {
        this.examService = examService;
        this.questionService = questionService;
        this.examSessionMapper = examSessionMapper;
        this.recordMapper = recordMapper;
        this.answerMapper = answerMapper;
        this.paperQuestionMapper = paperQuestionMapper;
        this.permissionService = permissionService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SubmissionResponseDTO start(Long examId, String candidateId) {
        return start(examId, null, null);
    }

    @Transactional
    public SubmissionResponseDTO start(Long examId, String candidateId, Long studentId) {
        permissionService.requireStudent();
        Long currentStudentId = permissionService.currentUser().getUserId().longValue();
        studentId = currentStudentId;
        candidateId = String.valueOf(currentStudentId);
        ExamSession exam = examService.getEntityById(examId);
        if (ExamStatus.fromCode(exam.getStatus()) != ExamStatus.PUBLISHED) {
            throw new BusinessException("考试未发布，不能开始答题");
        }
        if (exam.getClassId() == null) {
            throw new BusinessException("考试未绑定教学班");
        }
        permissionService.requireStudentInClass(exam.getClassId(), currentStudentId.intValue());
        LocalDateTime now = LocalDateTime.now();
        if (exam.getStartTime() != null && now.isBefore(exam.getStartTime())) {
            throw new BusinessException("考试尚未开始，当前时间：" + now + "，开始时间：" + exam.getStartTime());
        }
        if (exam.getEndTime() != null && !now.isBefore(exam.getEndTime())) {
            throw new BusinessException("考试已结束，当前时间：" + now + "，结束时间：" + exam.getEndTime());
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
        record.setScreenSwitchCount(0);
        record.setCheated(0);
        record.setCreateTime(now);
        record.setUpdateTime(now);
        try {
            recordMapper.insert(record);
        } catch (DuplicateKeyException duplicate) {
            ExamRecord concurrentRecord = findExistingRecord(exam.getId(), candidateId, studentId);
            if (concurrentRecord != null && SubmissionStatus.fromCode(concurrentRecord.getStatus()) == SubmissionStatus.IN_PROGRESS) {
                return toResponse(concurrentRecord);
            }
            throw duplicate;
        }

        for (ExamPaperQuestion paperQuestion : listPaperQuestions(exam.getPaperId())) {
            Question question = questionService.getEntityById(paperQuestion.getQuestionId().longValue());
            ExamAnswer answer = new ExamAnswer();
            answer.setExamRecordId(record.getId());
            answer.setPaperQuestionId(paperQuestion.getId());
            answer.setQuestionId(paperQuestion.getQuestionId());
            answer.setCorrectAnswer(joinStandardAnswers(question));
            answer.setQuestionSnapshot(snapshotOf(question, paperQuestion));
            answer.setScore(BigDecimal.ZERO);
            answer.setGradeStatus(JudgeStatus.NOT_ANSWERED.getCode());
            answerMapper.insert(answer);
        }
        return toResponse(record);
    }

    @Transactional
    public SubmissionResponseDTO saveAnswers(Long submissionId, SubmissionRequestsDTO.SaveAnswerRequest request) {
        ExamRecord record = getEntityForUpdate(submissionId);
        requireStudentRecord(record);
        if (SubmissionStatus.fromCode(record.getStatus()) != SubmissionStatus.IN_PROGRESS) {
            throw new BusinessException("当前答卷不允许继续作答");
        }
        checkDuration(record, true);
        applyAnswers(record, request);
        return toResponse(record);
    }

    private void applyAnswers(ExamRecord record, SubmissionRequestsDTO.SaveAnswerRequest request) {
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
    }

    @Transactional
    public SubmissionResponseDTO submit(Long submissionId) {
        return submit(submissionId, null);
    }

    @Transactional
    public SubmissionResponseDTO submit(Long submissionId, SubmissionRequestsDTO.SaveAnswerRequest request) {
        ExamRecord record = getEntityForUpdate(submissionId);
        requireStudentRecord(record);
        if (SubmissionStatus.fromCode(record.getStatus()) != SubmissionStatus.IN_PROGRESS) {
            throw new BusinessException("当前答卷状态不允许交卷");
        }
        checkDuration(record, true);
        if (request != null) {
            applyAnswers(record, request);
        }
        return finalizeSubmission(record, false);
    }

    @Transactional
    public SubmissionResponseDTO autoSubmit(Long submissionId, SubmissionRequestsDTO.SaveAnswerRequest request) {
        ExamRecord record = getEntityForUpdate(submissionId);
        requireStudentRecord(record);
        if (SubmissionStatus.fromCode(record.getStatus()) != SubmissionStatus.IN_PROGRESS) {
            return toResponse(record);
        }
        if (request != null) {
            applyAnswers(record, request);
        }
        return finalizeSubmission(record, checkDuration(record, false));
    }

    private SubmissionResponseDTO finalizeSubmission(ExamRecord record, boolean timeout) {
        BigDecimal objectiveScore = BigDecimal.ZERO;
        boolean hasSubjective = false;
        for (ExamAnswer answer : listAnswers(record.getId())) {
            QuestionSnapshot snapshot = snapshotOf(answer);
            QuestionType type = snapshot.questionType();
            if (type == QuestionType.SHORT_ANSWER || type == QuestionType.FILE_UPLOAD) {
                answer.setGradeStatus(JudgeStatus.PENDING_MANUAL_REVIEW.getCode());
                answer.setScore(BigDecimal.ZERO);
                hasSubjective = true;
                answerMapper.updateById(answer);
                continue;
            }
            boolean correct = judgeObjective(snapshot, answer.getStudentAnswer());
            answer.setIsCorrect(correct ? 1 : 0);
            if (correct) {
                BigDecimal score = snapshot.maxScore();
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
    public SubmissionResponseDTO reportScreenSwitch(Long submissionId) {
        ExamRecord record = getEntityForUpdate(submissionId);
        requireStudentRecord(record);
        if (SubmissionStatus.fromCode(record.getStatus()) != SubmissionStatus.IN_PROGRESS) {
            return toResponse(record);
        }
        if (checkDuration(record, false)) {
            return toResponse(record);
        }

        UpdateWrapper<ExamRecord> increment = new UpdateWrapper<>();
        increment.eq("id", record.getId())
                .eq("status", SubmissionStatus.IN_PROGRESS.getCode())
                .and(wrapper -> wrapper.eq("cheated", 0).or().isNull("cheated"))
                .setSql("screen_switch_count = COALESCE(screen_switch_count, 0) + 1")
                .set("update_time", LocalDateTime.now());
        recordMapper.update(null, increment);

        record = getEntityById(submissionId);
        ExamSession exam = examSessionMapper.selectById(record.getExamSessionId());
        int limit = exam == null || exam.getMaxScreenSwitches() == null ? 3 : exam.getMaxScreenSwitches();
        int count = record.getScreenSwitchCount() == null ? 0 : record.getScreenSwitchCount();
        if (count > limit && (record.getCheated() == null || record.getCheated() == 0)) {
            LocalDateTime now = LocalDateTime.now();
            record.setCheated(1);
            record.setObjectiveScore(BigDecimal.ZERO);
            record.setSubjectiveScore(BigDecimal.ZERO);
            record.setTotalScore(new BigDecimal("-1"));
            record.setSubmitTime(now);
            record.setStatus(SubmissionStatus.GRADED.getCode());
            record.setComment("切屏次数超过上限（" + count + "/" + limit + "），系统判定作弊");
            record.setUpdateTime(now);
            recordMapper.updateById(record);
        }
        return toResponse(record);
    }

    @Transactional
    public SubmissionResponseDTO manualGrade(Long submissionId, SubmissionRequestsDTO.ManualGradeRequest request) {
        ExamRecord record = getEntityForUpdate(submissionId);
        requireTeacherRecord(record);
        if (record.getCheated() != null && record.getCheated() == 1) {
            throw new BusinessException("作弊答卷不能修改成绩");
        }
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
            QuestionSnapshot snapshot = snapshotOf(answer);
            if (!isManualQuestion(snapshot)) {
                throw new BusinessException("只能批改简答题或文件上传题");
            }
            BigDecimal maxScore = snapshot.maxScore();
            if (item.getScore().compareTo(maxScore) > 0) {
                throw new BusinessException("主观题得分不能超过题目分值。题目分值：" + maxScore + "，给定分数：" + item.getScore());
            }
            answer.setScore(item.getScore());
            answer.setComment(item.getComment());
            answer.setIsCorrect(null);
            answer.setGradeStatus(JudgeStatus.MANUALLY_GRADED.getCode());
            answer.setGraderId(permissionService.currentUser().getUserId());
            answer.setGradeTime(LocalDateTime.now());
            answerMapper.updateById(answer);
        }
        List<ExamAnswer> answers = listAnswers(record.getId());
        BigDecimal subjectiveScore = answers.stream()
                .filter(answer -> isManualQuestion(snapshotOf(answer)))
                .map(answer -> answer.getScore() == null ? BigDecimal.ZERO : answer.getScore())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        record.setSubjectiveScore(subjectiveScore);
        record.setTotalScore((record.getObjectiveScore() == null ? BigDecimal.ZERO : record.getObjectiveScore()).add(subjectiveScore));
        boolean hasPendingManualReview = answers.stream()
                .filter(answer -> isManualQuestion(snapshotOf(answer)))
                .anyMatch(answer -> JudgeStatus.fromCode(answer.getGradeStatus()) != JudgeStatus.MANUALLY_GRADED);
        record.setStatus((hasPendingManualReview
                ? SubmissionStatus.PENDING_MANUAL_REVIEW
                : SubmissionStatus.GRADED).getCode());
        record.setUpdateTime(LocalDateTime.now());
        recordMapper.updateById(record);
        return toResponse(record);
    }

    @Transactional(readOnly = true)
    public SubmissionResponseDTO getById(Long submissionId) {
        ExamRecord record = getEntityById(submissionId);
        requireReadableRecord(record);
        return toResponse(record);
    }

    @Transactional(readOnly = true)
    public SubmissionResponseDTO getGradingSubmissionById(Long submissionId) {
        ExamRecord record = getEntityById(submissionId);
        ExamSession exam = examService.getEntityById(toLong(record.getExamSessionId()));
        permissionService.requireExamTeacher(exam);
        return toGradingResponse(record);
    }

    @Transactional(readOnly = true)
    public StudentSubmissionResponseDTO getStudentSubmissionById(Long submissionId) {
        ExamRecord record = getEntityById(submissionId);
        requireReadableRecord(record);
        return toStudentResponse(record);
    }

    @Transactional(readOnly = true)
    public List<SubmissionResponseDTO> list(Long examId, String candidateId) {
        return list(examId, candidateId, null);
    }

    @Transactional(readOnly = true)
    public List<SubmissionResponseDTO> list(Long examId, String candidateId, Long studentId) {
        QueryWrapper<ExamRecord> query = new QueryWrapper<>();
        var user = permissionService.currentUser();
        if (permissionService.isStudent(user)) {
            query.eq("student_id", user.getUserId());
        } else if (permissionService.isTeacher(user)) {
            if (examId != null) {
                ExamSession exam = examService.getEntityById(examId);
                permissionService.requireExamTeacher(exam);
                query.eq("exam_session_id", toInt(examId, "考试ID"));
            } else {
                QueryWrapper<ExamSession> examQuery = new QueryWrapper<>();
                examQuery.eq("teacher_id", user.getUserId());
                List<Integer> examIds = examSessionMapper.selectList(examQuery).stream()
                        .map(ExamSession::getId)
                        .filter(id -> id != null && id > 0)
                        .toList();
                if (examIds.isEmpty()) {
                    return List.of();
                }
                query.in("exam_session_id", examIds);
            }
        } else if (examId != null) {
            query.eq("exam_session_id", toInt(examId, "考试ID"));
        }
        if (!permissionService.isStudent(user) && !isBlank(candidateId)) {
            query.eq("candidate_id", candidateId);
        }
        if (!permissionService.isStudent(user) && studentId != null) {
            query.eq("student_id", toInt(studentId, "学生ID"));
        }
        query.orderByDesc("id");
        return recordMapper.selectList(query).stream().map(this::toResponse).toList();
    }

    /** Serialize answer/status/score writes for one submission until the caller's transaction commits. */
    private ExamRecord getEntityForUpdate(Long submissionId) {
        ExamRecord record = recordMapper.selectOne(new QueryWrapper<ExamRecord>()
                .eq("id", toInt(submissionId, "答卷ID"))
                .last("FOR UPDATE"));
        if (record == null) {
            throw new BusinessException("答卷不存在: " + submissionId);
        }
        return record;
    }

    public ExamRecord getEntityById(Long submissionId) {
        ExamRecord record = recordMapper.selectById(toInt(submissionId, "答卷ID"));
        if (record == null) {
            throw new BusinessException("答卷不存在: " + submissionId);
        }
        return record;
    }

    public SubmissionResponseDTO toResponse(ExamRecord record) {
        return toResponse(record, false);
    }

    private SubmissionResponseDTO toGradingResponse(ExamRecord record) {
        return toResponse(record, true);
    }

    private SubmissionResponseDTO toResponse(ExamRecord record, boolean includeReferenceAnswer) {
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
        response.setScreenSwitchCount(record.getScreenSwitchCount() == null ? 0 : record.getScreenSwitchCount());
        response.setMaxScreenSwitches(exam == null || exam.getMaxScreenSwitches() == null ? 3 : exam.getMaxScreenSwitches());
        response.setCheated(record.getCheated() != null && record.getCheated() == 1);
        response.setAnswers(listAnswers(record.getId()).stream()
                .map(answer -> toAnswerResponse(answer, includeReferenceAnswer))
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
        response.setScreenSwitchCount(record.getScreenSwitchCount() == null ? 0 : record.getScreenSwitchCount());
        response.setMaxScreenSwitches(exam == null || exam.getMaxScreenSwitches() == null ? 3 : exam.getMaxScreenSwitches());
        response.setCheated(record.getCheated() != null && record.getCheated() == 1);
        response.setAnswers(listAnswers(record.getId()).stream()
                .map(this::toStudentAnswerResponse)
                .toList());
        return response;
    }

    private SubmissionResponseDTO.SubmissionAnswerResponse toAnswerResponse(ExamAnswer answer, boolean includeReferenceAnswer) {
        QuestionSnapshot snapshot = snapshotOf(answer);
        SubmissionResponseDTO.SubmissionAnswerResponse response = new SubmissionResponseDTO.SubmissionAnswerResponse();
        response.setId(toLong(answer.getId()));
        response.setPaperQuestionId(toLong(answer.getPaperQuestionId()));
        response.setQuestionId(toLong(answer.getQuestionId()));
        response.setQuestionContent(snapshot.stem());
        response.setQuestionType(snapshot.questionType());
        response.setAnswerContent(answer.getStudentAnswer());
        response.setMaxScore(snapshot.maxScore());
        if (includeReferenceAnswer) {
            response.setReferenceAnswer(String.join(", ", snapshot.correctAnswers()));
        }
        response.setCorrect(answer.getIsCorrect() == null ? null : answer.getIsCorrect() == 1);
        response.setScore(answer.getScore());
        response.setJudgeStatus(JudgeStatus.fromCode(answer.getGradeStatus()));
        response.setComment(answer.getComment());
        return response;
    }

    private StudentSubmissionResponseDTO.StudentAnswerResponse toStudentAnswerResponse(ExamAnswer answer) {
        QuestionSnapshot snapshot = snapshotOf(answer);
        StudentSubmissionResponseDTO.StudentAnswerResponse response = new StudentSubmissionResponseDTO.StudentAnswerResponse();
        response.setId(toLong(answer.getId()));
        response.setPaperQuestionId(toLong(answer.getPaperQuestionId()));
        response.setQuestionId(toLong(answer.getQuestionId()));
        response.setQuestionContent(snapshot.stem());
        response.setQuestionType(snapshot.questionType());
        response.setScore(snapshot.maxScore());
        response.setSortOrder(snapshot.sortOrder());
        response.setRequiredAnswer(snapshot.requiredAnswer());
        response.setOptions(snapshot.options().stream().map(option -> {
            StudentSubmissionResponseDTO.OptionItem item = new StudentSubmissionResponseDTO.OptionItem();
            item.setOptionKey(option.optionKey());
            item.setOptionContent(option.optionContent());
            item.setSortOrder(option.sortOrder());
            return item;
        }).toList());
        response.setAnswerContent(answer.getStudentAnswer());
        response.setCorrect(answer.getIsCorrect() == null ? null : answer.getIsCorrect() == 1);
        response.setObtainedScore(answer.getScore());
        response.setJudgeStatus(JudgeStatus.fromCode(answer.getGradeStatus()));
        response.setComment(answer.getComment());
        return response;
    }

    private boolean isManualQuestion(QuestionSnapshot snapshot) {
        return snapshot.questionType() == QuestionType.SHORT_ANSWER || snapshot.questionType() == QuestionType.FILE_UPLOAD;
    }

    private String snapshotOf(Question question, ExamPaperQuestion paperQuestion) {
        QuestionSnapshot snapshot = new QuestionSnapshot(
                QuestionType.fromStorageValue(question.getQuestionType()),
                question.getTitle(),
                questionService.parseOptions(question).stream()
                        .sorted(Comparator.comparing(QuestionResponseDTO.OptionResponse::getSortOrder))
                        .map(option -> new SnapshotOption(option.getOptionKey(), option.getOptionContent(), option.getSortOrder()))
                        .toList(),
                question.getAnalysis(),
                scoreOf(paperQuestion),
                questionService.parseAnswers(question).stream()
                        .sorted(Comparator.comparing(QuestionResponseDTO.AnswerResponse::getSortOrder))
                        .map(QuestionResponseDTO.AnswerResponse::getAnswerContent)
                        .toList(),
                paperQuestion.getSortOrder(),
                paperQuestion.getRequiredAnswer() == null || paperQuestion.getRequiredAnswer() == 1
        );
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception exception) {
            throw new BusinessException("无法创建题目快照: " + exception.getMessage());
        }
    }

    private QuestionSnapshot snapshotOf(ExamAnswer answer) {
        if (isBlank(answer.getQuestionSnapshot())) {
            return legacySnapshotOf(answer);
        }
        try {
            return objectMapper.readValue(answer.getQuestionSnapshot(), QuestionSnapshot.class);
        } catch (Exception exception) {
            throw new BusinessException("答题记录的题目快照无效: " + answer.getId());
        }
    }

    /**
     * Historical answer rows predate immutable question snapshots. Build a read-only
     * compatibility view from the remaining question and paper-question records.
     */
    private QuestionSnapshot legacySnapshotOf(ExamAnswer answer) {
        Question question = questionService.getEntityById(toLong(answer.getQuestionId()));
        ExamPaperQuestion paperQuestion = answer.getPaperQuestionId() == null
                ? null
                : paperQuestionMapper.selectById(answer.getPaperQuestionId());
        return new QuestionSnapshot(
                QuestionType.fromStorageValue(question.getQuestionType()),
                question.getTitle(),
                questionService.parseOptions(question).stream()
                        .sorted(Comparator.comparing(QuestionResponseDTO.OptionResponse::getSortOrder))
                        .map(option -> new SnapshotOption(option.getOptionKey(), option.getOptionContent(), option.getSortOrder()))
                        .toList(),
                question.getAnalysis(),
                paperQuestion == null
                        ? (question.getScore() == null ? BigDecimal.ZERO : question.getScore())
                        : scoreOf(paperQuestion),
                questionService.parseAnswers(question).stream()
                        .sorted(Comparator.comparing(QuestionResponseDTO.AnswerResponse::getSortOrder))
                        .map(QuestionResponseDTO.AnswerResponse::getAnswerContent)
                        .toList(),
                paperQuestion == null ? null : paperQuestion.getSortOrder(),
                paperQuestion == null || paperQuestion.getRequiredAnswer() == null || paperQuestion.getRequiredAnswer() == 1
        );
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
        if (!isBlank(item.getAttachmentUrl())) {
            return item.getAttachmentUrl().trim();
        }
        return item.getAnswerContent();
    }

    private boolean judgeObjective(QuestionSnapshot snapshot, String submittedAnswer) {
        if (isBlank(submittedAnswer)) {
            return false;
        }
        List<String> standardAnswers = snapshot.correctAnswers();
        if (standardAnswers.isEmpty()) {
            return false;
        }
        QuestionType type = snapshot.questionType();
        return switch (type) {
            case SINGLE_CHOICE, TRUE_FALSE -> normalize(submittedAnswer).equals(normalize(standardAnswers.get(0)));
            case MULTIPLE_CHOICE -> normalizeMulti(submittedAnswer).equals(normalizeMulti(String.join(",", standardAnswers)));
            case FILL_BLANK -> standardAnswers.stream().anyMatch(answer -> normalizeBlank(answer).equals(normalizeBlank(submittedAnswer)));
            case SHORT_ANSWER, FILE_UPLOAD -> false;
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
        if (ExamStatus.fromCode(exam.getStatus()) != ExamStatus.PUBLISHED) {
            if (rejectOnTimeout) {
                throw new BusinessException("考试已结束，无法继续作答或交卷");
            }
            return true;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime durationCutoff = record.getStartTime().plusMinutes(exam.getDuration());
        LocalDateTime cutoff = exam.getEndTime() == null || durationCutoff.isBefore(exam.getEndTime())
                ? durationCutoff
                : exam.getEndTime();
        Duration elapsed = Duration.between(record.getStartTime(), now);
        boolean timeout = !now.isBefore(cutoff);
        if (timeout && rejectOnTimeout) {
            throw new BusinessException("考试时间已截止，无法保存答案。截止时间：" + cutoff + "，当前时间：" + now);
        }
        return timeout;
    }

    private String timeoutComment(ExamRecord record, LocalDateTime now) {
        ExamSession exam = examSessionMapper.selectById(record.getExamSessionId());
        long elapsed = Duration.between(record.getStartTime(), now).toMinutes();
        return "超时自动交卷，用时：" + elapsed + "分钟，限时：" + exam.getDuration() + "分钟";
    }

    private void requireStudentRecord(ExamRecord record) {
        permissionService.requireStudent();
        Integer currentUserId = permissionService.currentUser().getUserId();
        if (record.getStudentId() == null || !record.getStudentId().equals(currentUserId)) {
            throw new com.cupk.aiacademy.common.ForbiddenException("只能操作自己的答卷");
        }
    }

    private void requireTeacherRecord(ExamRecord record) {
        ExamSession exam = examSessionMapper.selectById(record.getExamSessionId());
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }
        permissionService.requireExamTeacher(exam);
    }

    private void requireReadableRecord(ExamRecord record) {
        var user = permissionService.currentUser();
        if (permissionService.isStudent(user)) {
            requireStudentRecord(record);
            return;
        }
        requireTeacherRecord(record);
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

    private BigDecimal scoreOf(ExamPaperQuestion paperQuestion) {
        return paperQuestion == null || paperQuestion.getScore() == null ? BigDecimal.ZERO : paperQuestion.getScore();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record QuestionSnapshot(
            QuestionType questionType,
            String stem,
            List<SnapshotOption> options,
            String analysis,
            BigDecimal maxScore,
            List<String> correctAnswers,
            Integer sortOrder,
            boolean requiredAnswer
    ) {
    }

    private record SnapshotOption(String optionKey, String optionContent, Integer sortOrder) {
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
