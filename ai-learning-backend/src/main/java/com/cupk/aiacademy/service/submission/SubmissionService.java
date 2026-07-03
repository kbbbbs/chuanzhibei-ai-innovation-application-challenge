// 文件: src/main/java/com/cupk/aiacademy/service/submission/SubmissionService.java
package com.cupk.aiacademy.service.submission;

import com.cupk.aiacademy.common.*;
import com.cupk.aiacademy.dto.submission.SubmissionRequests;
import com.cupk.aiacademy.dto.submission.SubmissionResponse;
import com.cupk.aiacademy.pojo.exam.Exam;
import com.cupk.aiacademy.pojo.paper.PaperQuestion;
import com.cupk.aiacademy.pojo.question.Question;
import com.cupk.aiacademy.pojo.question.QuestionAnswer;
import com.cupk.aiacademy.pojo.submission.ExamSubmission;
import com.cupk.aiacademy.pojo.submission.SubmissionAnswer;
import com.cupk.aiacademy.repository.exam.ExamRepository;
import com.cupk.aiacademy.repository.paper.PaperQuestionRepository;
import com.cupk.aiacademy.repository.submission.ExamSubmissionRepository;
import com.cupk.aiacademy.repository.submission.SubmissionAnswerRepository;
import com.cupk.aiacademy.service.exam.ExamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final ExamService examService;
    private final PaperQuestionRepository paperQuestionRepository;
    private final ExamSubmissionRepository examSubmissionRepository;
    private final SubmissionAnswerRepository submissionAnswerRepository;

    @Transactional
    public SubmissionResponse start(Long examId, SubmissionRequests.StartSubmissionRequest request) {
        Exam exam = examService.getEntityById(examId);
        if (exam.getStatus() != ExamStatus.PUBLISHED) {
            throw new BusinessException("考试未发布，不能开始答题");
        }
        ExamSubmission submission = new ExamSubmission();
        submission.setExam(exam);
        submission.setCandidateId(request.getCandidateId());
        submission.setStartedAt(LocalDateTime.now());
        submission.setObjectiveScore(BigDecimal.ZERO);
        submission.setSubjectiveScore(BigDecimal.ZERO);
        submission.setTotalScore(BigDecimal.ZERO);
        submission.setStatus(SubmissionStatus.IN_PROGRESS);

        List<PaperQuestion> paperQuestions = paperQuestionRepository.findByPaperIdOrderBySortOrderAsc(exam.getPaper().getId());
        for (PaperQuestion paperQuestion : paperQuestions) {
            SubmissionAnswer answer = new SubmissionAnswer();
            answer.setSubmission(submission);
            answer.setPaperQuestion(paperQuestion);
            answer.setScore(BigDecimal.ZERO);
            answer.setJudgeStatus(JudgeStatus.NOT_ANSWERED);
            submission.getAnswers().add(answer);
        }
        return toResponse(examSubmissionRepository.save(submission));
    }

    @Transactional
    public SubmissionResponse saveAnswers(Long submissionId, SubmissionRequests.SaveAnswerRequest request) {
        ExamSubmission submission = getEntityById(submissionId);
        if (submission.getStatus() != SubmissionStatus.IN_PROGRESS) {
            throw new BusinessException("当前答卷不允许继续作答");
        }
        for (SubmissionRequests.AnswerItem item : request.getAnswers()) {
            SubmissionAnswer answer = submissionAnswerRepository.findBySubmissionIdAndPaperQuestionId(submissionId, item.getPaperQuestionId())
                    .orElseThrow(() -> new BusinessException("答题记录不存在: " + item.getPaperQuestionId()));
            answer.setAnswerContent(item.getAnswerContent());
            answer.setJudgeStatus(isBlank(item.getAnswerContent()) ? JudgeStatus.NOT_ANSWERED : JudgeStatus.ANSWERED);
        }
        return toResponse(submission);
    }

    @Transactional
    public SubmissionResponse submit(Long submissionId) {
        ExamSubmission submission = getEntityById(submissionId);
        if (submission.getStatus() != SubmissionStatus.IN_PROGRESS) {
            throw new BusinessException("当前答卷状态不允许交卷");
        }

        BigDecimal objectiveScore = BigDecimal.ZERO;
        boolean hasSubjective = false;
        List<SubmissionAnswer> answers = submissionAnswerRepository.findBySubmissionId(submissionId);
        for (SubmissionAnswer answer : answers) {
            Question question = answer.getPaperQuestion().getQuestion();
            if (question.getType() == QuestionType.SHORT_ANSWER) {
                answer.setJudgeStatus(JudgeStatus.PENDING_MANUAL_REVIEW);
                hasSubjective = true;
                continue;
            }

            boolean correct = judgeObjective(question, answer.getAnswerContent());
            answer.setCorrect(correct);
            if (correct) {
                answer.setScore(answer.getPaperQuestion().getScore());
                objectiveScore = objectiveScore.add(answer.getScore());
            } else {
                answer.setScore(BigDecimal.ZERO);
            }
            answer.setJudgeStatus(JudgeStatus.AUTO_CORRECT);
        }

        submission.setObjectiveScore(objectiveScore);
        submission.setSubmittedAt(LocalDateTime.now());
        submission.setTotalScore(objectiveScore.add(submission.getSubjectiveScore()));
        submission.setStatus(hasSubjective ? SubmissionStatus.PENDING_MANUAL_REVIEW : SubmissionStatus.GRADED);
        return toResponse(submission);
    }

    @Transactional
    public SubmissionResponse manualGrade(Long submissionId, SubmissionRequests.ManualGradeRequest request) {
        ExamSubmission submission = getEntityById(submissionId);
        for (SubmissionRequests.ManualGradeItem item : request.getGrades()) {
            SubmissionAnswer answer = submissionAnswerRepository.findById(item.getSubmissionAnswerId())
                    .orElseThrow(() -> new BusinessException("答题记录不存在: " + item.getSubmissionAnswerId()));
            if (!Objects.equals(answer.getSubmission().getId(), submissionId)) {
                throw new BusinessException("答题记录不属于当前答卷");
            }
            answer.setScore(item.getScore());
            answer.setComment(item.getComment());
            answer.setCorrect(null);
            answer.setJudgeStatus(JudgeStatus.MANUALLY_GRADED);
        }

        BigDecimal subjectiveScore = submissionAnswerRepository.findBySubmissionId(submissionId).stream()
                .filter(answer -> answer.getPaperQuestion().getQuestion().getType() == QuestionType.SHORT_ANSWER)
                .map(SubmissionAnswer::getScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        submission.setSubjectiveScore(subjectiveScore);
        submission.setTotalScore(submission.getObjectiveScore().add(subjectiveScore));
        submission.setStatus(SubmissionStatus.GRADED);
        return toResponse(submission);
    }

    @Transactional(readOnly = true)
    public SubmissionResponse getById(Long submissionId) {
        return toResponse(getEntityById(submissionId));
    }

    @Transactional(readOnly = true)
    public List<SubmissionResponse> list(Long examId, String candidateId) {
        List<ExamSubmission> submissions;
        if (examId != null && candidateId != null) {
            submissions = examSubmissionRepository.findByExamIdAndCandidateId(examId, candidateId);
        } else if (examId != null) {
            submissions = examSubmissionRepository.findByExamId(examId);
        } else if (candidateId != null) {
            submissions = examSubmissionRepository.findByCandidateId(candidateId);
        } else {
            submissions = examSubmissionRepository.findAll();
        }
        return submissions.stream().map(this::toResponse).toList();
    }

    public ExamSubmission getEntityById(Long submissionId) {
        return examSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new BusinessException("答卷不存在: " + submissionId));
    }

    private boolean judgeObjective(Question question, String submittedAnswer) {
        if (isBlank(submittedAnswer)) {
            return false;
        }
        List<String> standardAnswers = question.getAnswers().stream()
                .sorted(Comparator.comparing(QuestionAnswer::getSortOrder))
                .map(QuestionAnswer::getAnswerContent)
                .toList();

        return switch (question.getType()) {
            case SINGLE_CHOICE, TRUE_FALSE -> normalize(submittedAnswer).equals(normalize(standardAnswers.getFirst()));
            case MULTIPLE_CHOICE -> normalizeMulti(submittedAnswer).equals(normalizeMulti(String.join(",", standardAnswers)));
            case FILL_BLANK -> standardAnswers.stream().anyMatch(answer -> normalizeBlank(answer).equals(normalizeBlank(submittedAnswer)));
            case SHORT_ANSWER -> false;
        };
    }

    private SubmissionResponse toResponse(ExamSubmission submission) {
        SubmissionResponse response = new SubmissionResponse();
        response.setId(submission.getId());
        response.setExamId(submission.getExam().getId());
        response.setExamName(submission.getExam().getName());
        response.setCandidateId(submission.getCandidateId());
        response.setStartedAt(submission.getStartedAt());
        response.setSubmittedAt(submission.getSubmittedAt());
        response.setObjectiveScore(submission.getObjectiveScore());
        response.setSubjectiveScore(submission.getSubjectiveScore());
        response.setTotalScore(submission.getTotalScore());
        response.setStatus(submission.getStatus());
        response.setAnswers(submission.getAnswers().stream()
                .sorted(Comparator.comparing(answer -> answer.getPaperQuestion().getSortOrder()))
                .map(answer -> {
                    SubmissionResponse.SubmissionAnswerResponse item = new SubmissionResponse.SubmissionAnswerResponse();
                    item.setId(answer.getId());
                    item.setPaperQuestionId(answer.getPaperQuestion().getId());
                    item.setQuestionId(answer.getPaperQuestion().getQuestion().getId());
                    item.setQuestionContent(answer.getPaperQuestion().getQuestion().getContent());
                    item.setQuestionType(answer.getPaperQuestion().getQuestion().getType());
                    item.setAnswerContent(answer.getAnswerContent());
                    item.setCorrect(answer.getCorrect());
                    item.setScore(answer.getScore());
                    item.setJudgeStatus(answer.getJudgeStatus());
                    item.setComment(answer.getComment());
                    return item;
                })
                .toList());
        return response;
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
}
