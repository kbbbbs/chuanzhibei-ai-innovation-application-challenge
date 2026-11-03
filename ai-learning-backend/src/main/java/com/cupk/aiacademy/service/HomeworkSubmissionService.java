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
import com.cupk.aiacademy.mapper.HomeworkAnswerMapper;
import com.cupk.aiacademy.mapper.HomeworkPaperQuestionMapper;
import com.cupk.aiacademy.mapper.HomeworkRecordMapper;
import com.cupk.aiacademy.mapper.HomeworkSessionMapper;
import com.cupk.aiacademy.pojo.HomeworkAnswer;
import com.cupk.aiacademy.pojo.HomeworkPaperQuestion;
import com.cupk.aiacademy.pojo.HomeworkRecord;
import com.cupk.aiacademy.pojo.HomeworkSession;
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
public class HomeworkSubmissionService {
    private final HomeworkAssessmentService homeworkAssessmentService;
    private final QuestionService questionService;
    private final HomeworkSessionMapper homeworkSessionMapper;
    private final HomeworkRecordMapper recordMapper;
    private final HomeworkAnswerMapper answerMapper;
    private final HomeworkPaperQuestionMapper paperQuestionMapper;
    private final PermissionService permissionService;
    private final ObjectMapper objectMapper;

    public HomeworkSubmissionService(HomeworkAssessmentService homeworkAssessmentService,
                             QuestionService questionService,
                             HomeworkSessionMapper homeworkSessionMapper,
                             HomeworkRecordMapper recordMapper,
                             HomeworkAnswerMapper answerMapper,
                             HomeworkPaperQuestionMapper paperQuestionMapper,
                             PermissionService permissionService,
                             ObjectMapper objectMapper) {
        this.homeworkAssessmentService = homeworkAssessmentService;
        this.questionService = questionService;
        this.homeworkSessionMapper = homeworkSessionMapper;
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
        HomeworkSession exam = homeworkAssessmentService.getEntityById(examId);
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
        HomeworkRecord existing = findExistingRecord(exam.getId(), candidateId, studentId);
        if (existing != null) {
            SubmissionStatus status = SubmissionStatus.fromCode(existing.getStatus());
            if (status == SubmissionStatus.IN_PROGRESS) {
                return toResponse(existing);
            }
            throw new BusinessException("您已经完成该考试，不能重复参加");
        }

        HomeworkRecord record = new HomeworkRecord();
        record.setHomeworkSessionId(exam.getId());
        record.setStudentId(toNullableInt(studentId, "学生ID"));
        record.setCandidateId(candidateId);
        record.setStartTime(now);
        record.setObjectiveScore(BigDecimal.ZERO);
        record.setSubjectiveScore(BigDecimal.ZERO);
        record.setTotalScore(BigDecimal.ZERO);
        record.setStatus(SubmissionStatus.IN_PROGRESS.getCode());
        record.setCreateTime(now);
        record.setUpdateTime(now);
        try {
            recordMapper.insert(record);
        } catch (DuplicateKeyException duplicate) {
            HomeworkRecord concurrentRecord = findExistingRecord(exam.getId(), candidateId, studentId);
            if (concurrentRecord != null && SubmissionStatus.fromCode(concurrentRecord.getStatus()) == SubmissionStatus.IN_PROGRESS) {
                return toResponse(concurrentRecord);
            }
            throw duplicate;
        }

        for (HomeworkPaperQuestion paperQuestion : listPaperQuestions(exam.getPaperId())) {
            Question question = questionService.getEntityById(paperQuestion.getQuestionId().longValue());
            HomeworkAnswer answer = new HomeworkAnswer();
            answer.setHomeworkRecordId(record.getId());
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
        HomeworkRecord record = getEntityForUpdate(submissionId);
        requireStudentRecord(record);
        if (SubmissionStatus.fromCode(record.getStatus()) != SubmissionStatus.IN_PROGRESS) {
            throw new BusinessException("当前答卷不允许继续作答");
        }
        checkDuration(record, true);
        if (request == null || request.getAnswers() == null || request.getAnswers().isEmpty()) {
            throw new BusinessException("答案不能为空");
        }
        for (SubmissionRequestsDTO.AnswerItem item : request.getAnswers()) {
            HomeworkAnswer answer = findAnswer(record.getId(), toInt(item.getPaperQuestionId(), "试卷题目ID"));
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
        HomeworkRecord record = getEntityForUpdate(submissionId);
        requireStudentRecord(record);
        if (SubmissionStatus.fromCode(record.getStatus()) != SubmissionStatus.IN_PROGRESS) {
            throw new BusinessException("当前答卷状态不允许交卷");
        }
        boolean timeout = checkDuration(record, false);
        BigDecimal objectiveScore = BigDecimal.ZERO;
        boolean hasSubjective = false;
        for (HomeworkAnswer answer : listAnswers(record.getId())) {
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
    public SubmissionResponseDTO manualGrade(Long submissionId, SubmissionRequestsDTO.ManualGradeRequest request) {
        HomeworkRecord record = getEntityForUpdate(submissionId);
        requireTeacherRecord(record);
        if (request == null || request.getGrades() == null || request.getGrades().isEmpty()) {
            throw new BusinessException("批改结果不能为空");
        }
        for (SubmissionRequestsDTO.ManualGradeItem item : request.getGrades()) {
            HomeworkAnswer answer = answerMapper.selectById(toInt(item.getSubmissionAnswerId(), "答题记录ID"));
            if (answer == null) {
                throw new BusinessException("答题记录不存在: " + item.getSubmissionAnswerId());
            }
            if (!Objects.equals(answer.getHomeworkRecordId(), record.getId())) {
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
        List<HomeworkAnswer> answers = listAnswers(record.getId());
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
        HomeworkRecord record = getEntityById(submissionId);
        requireReadableRecord(record);
        return toResponse(record);
    }

    @Transactional(readOnly = true)
    public SubmissionResponseDTO getGradingSubmissionById(Long submissionId) {
        HomeworkRecord record = getEntityById(submissionId);
        HomeworkSession exam = homeworkAssessmentService.getEntityById(toLong(record.getHomeworkSessionId()));
        permissionService.requireHomeworkAssessmentTeacher(exam);
        return toGradingResponse(record);
    }

    @Transactional(readOnly = true)
    public StudentSubmissionResponseDTO getStudentSubmissionById(Long submissionId) {
        HomeworkRecord record = getEntityById(submissionId);
        requireReadableRecord(record);
        return toStudentResponse(record);
    }

    @Transactional(readOnly = true)
    public List<SubmissionResponseDTO> list(Long examId, String candidateId) {
        return list(examId, candidateId, null);
    }

    @Transactional(readOnly = true)
    public List<SubmissionResponseDTO> list(Long examId, String candidateId, Long studentId) {
        QueryWrapper<HomeworkRecord> query = new QueryWrapper<>();
        var user = permissionService.currentUser();
        if (permissionService.isStudent(user)) {
            query.eq("student_id", user.getUserId());
        } else if (permissionService.isTeacher(user)) {
            if (examId != null) {
                HomeworkSession exam = homeworkAssessmentService.getEntityById(examId);
                permissionService.requireHomeworkAssessmentTeacher(exam);
                query.eq("homework_session_id", toInt(examId, "考试ID"));
            } else {
                QueryWrapper<HomeworkSession> examQuery = new QueryWrapper<>();
                examQuery.eq("teacher_id", user.getUserId());
                List<Integer> examIds = homeworkSessionMapper.selectList(examQuery).stream()
                        .map(HomeworkSession::getId)
                        .filter(id -> id != null && id > 0)
                        .toList();
                if (examIds.isEmpty()) {
                    return List.of();
                }
                query.in("homework_session_id", examIds);
            }
        } else if (examId != null) {
            query.eq("homework_session_id", toInt(examId, "考试ID"));
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
    private HomeworkRecord getEntityForUpdate(Long submissionId) {
        HomeworkRecord record = recordMapper.selectOne(new QueryWrapper<HomeworkRecord>()
                .eq("id", toInt(submissionId, "答卷ID"))
                .last("FOR UPDATE"));
        if (record == null) {
            throw new BusinessException("答卷不存在: " + submissionId);
        }
        return record;
    }

    public HomeworkRecord getEntityById(Long submissionId) {
        HomeworkRecord record = recordMapper.selectById(toInt(submissionId, "答卷ID"));
        if (record == null) {
            throw new BusinessException("答卷不存在: " + submissionId);
        }
        return record;
    }

    public SubmissionResponseDTO toResponse(HomeworkRecord record) {
        return toResponse(record, false);
    }

    private SubmissionResponseDTO toGradingResponse(HomeworkRecord record) {
        return toResponse(record, true);
    }

    private SubmissionResponseDTO toResponse(HomeworkRecord record, boolean includeReferenceAnswer) {
        HomeworkSession exam = homeworkSessionMapper.selectById(record.getHomeworkSessionId());
        SubmissionResponseDTO response = new SubmissionResponseDTO();
        response.setId(toLong(record.getId()));
        response.setExamId(toLong(record.getHomeworkSessionId()));
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
                .map(answer -> toAnswerResponse(answer, includeReferenceAnswer))
                .toList());
        return response;
    }

    public StudentSubmissionResponseDTO toStudentResponse(HomeworkRecord record) {
        HomeworkSession exam = homeworkSessionMapper.selectById(record.getHomeworkSessionId());
        StudentSubmissionResponseDTO response = new StudentSubmissionResponseDTO();
        response.setId(toLong(record.getId()));
        response.setExamId(toLong(record.getHomeworkSessionId()));
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

    private SubmissionResponseDTO.SubmissionAnswerResponse toAnswerResponse(HomeworkAnswer answer, boolean includeReferenceAnswer) {
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

    private StudentSubmissionResponseDTO.StudentAnswerResponse toStudentAnswerResponse(HomeworkAnswer answer) {
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

    private String snapshotOf(Question question, HomeworkPaperQuestion paperQuestion) {
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

    private QuestionSnapshot snapshotOf(HomeworkAnswer answer) {
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
    private QuestionSnapshot legacySnapshotOf(HomeworkAnswer answer) {
        Question question = questionService.getEntityById(toLong(answer.getQuestionId()));
        HomeworkPaperQuestion paperQuestion = answer.getPaperQuestionId() == null
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

    private HomeworkRecord findExistingRecord(Integer examId, String candidateId, Long studentId) {
        QueryWrapper<HomeworkRecord> query = new QueryWrapper<>();
        query.eq("homework_session_id", examId);
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

    private List<HomeworkPaperQuestion> listPaperQuestions(Integer paperId) {
        QueryWrapper<HomeworkPaperQuestion> query = new QueryWrapper<>();
        query.eq("paper_id", paperId).orderByAsc("sort_order").orderByAsc("id");
        return paperQuestionMapper.selectList(query);
    }

    private List<HomeworkAnswer> listAnswers(Integer recordId) {
        QueryWrapper<HomeworkAnswer> query = new QueryWrapper<>();
        query.eq("homework_record_id", recordId).orderByAsc("paper_question_id").orderByAsc("id");
        return answerMapper.selectList(query);
    }

    private HomeworkAnswer findAnswer(Integer recordId, Integer paperQuestionId) {
        QueryWrapper<HomeworkAnswer> query = new QueryWrapper<>();
        query.eq("homework_record_id", recordId).eq("paper_question_id", paperQuestionId);
        HomeworkAnswer answer = answerMapper.selectOne(query);
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

    private boolean checkDuration(HomeworkRecord record, boolean rejectOnTimeout) {
        HomeworkSession exam = homeworkSessionMapper.selectById(record.getHomeworkSessionId());
        if (record.getStartTime() == null || exam == null || exam.getDuration() == null || exam.getDuration() <= 0) {
            return false;
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

    private String timeoutComment(HomeworkRecord record, LocalDateTime now) {
        HomeworkSession exam = homeworkSessionMapper.selectById(record.getHomeworkSessionId());
        long elapsed = Duration.between(record.getStartTime(), now).toMinutes();
        return "超时自动交卷，用时：" + elapsed + "分钟，限时：" + exam.getDuration() + "分钟";
    }

    private void requireStudentRecord(HomeworkRecord record) {
        permissionService.requireStudent();
        Integer currentUserId = permissionService.currentUser().getUserId();
        if (record.getStudentId() == null || !record.getStudentId().equals(currentUserId)) {
            throw new com.cupk.aiacademy.common.ForbiddenException("只能操作自己的答卷");
        }
    }

    private void requireTeacherRecord(HomeworkRecord record) {
        HomeworkSession exam = homeworkSessionMapper.selectById(record.getHomeworkSessionId());
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }
        permissionService.requireHomeworkAssessmentTeacher(exam);
    }

    private void requireReadableRecord(HomeworkRecord record) {
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

    private BigDecimal scoreOf(HomeworkPaperQuestion paperQuestion) {
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
