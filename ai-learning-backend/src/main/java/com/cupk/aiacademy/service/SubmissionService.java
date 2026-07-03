package com.cupk.aiacademy.service;

import com.cupk.aiacademy.common.*;
import com.cupk.aiacademy.dto.StudentSubmissionResponseDTO;
import com.cupk.aiacademy.dto.SubmissionRequestsDTO;
import com.cupk.aiacademy.dto.SubmissionResponseDTO;
import com.cupk.aiacademy.pojo.Exam;
import com.cupk.aiacademy.pojo.PaperQuestion;
import com.cupk.aiacademy.pojo.Question;
import com.cupk.aiacademy.pojo.QuestionAnswer;
import com.cupk.aiacademy.pojo.QuestionOption;
import com.cupk.aiacademy.pojo.ExamSubmission;
import com.cupk.aiacademy.pojo.SubmissionAnswer;
import com.cupk.aiacademy.repository.PaperQuestionRepository;
import com.cupk.aiacademy.repository.ExamSubmissionRepository;
import com.cupk.aiacademy.repository.SubmissionAnswerRepository;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class SubmissionService {

    private final ExamService examService;
    private final PaperQuestionRepository paperQuestionRepository;
    private final ExamSubmissionRepository examSubmissionRepository;
    private final SubmissionAnswerRepository submissionAnswerRepository;

    @Transactional
    public SubmissionResponseDTO start(Long examId, String candidateId) {
        Exam exam = examService.getEntityById(examId);
        
        if (exam.getStatus() != ExamStatus.PUBLISHED) {
            throw new BusinessException("考试未发布，不能开始答题");
        }
        
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(exam.getStartTime())) {
            throw new BusinessException("考试尚未开始，当前时间：" + now + "，开始时间：" + exam.getStartTime());
        }
        if (now.isAfter(exam.getEndTime())) {
            throw new BusinessException("考试已结束，当前时间：" + now + "，结束时间：" + exam.getEndTime());
        }
        
        List<ExamSubmission> existingSubmissions = examSubmissionRepository.findByExamIdAndCandidateId(examId, candidateId);
        if (!existingSubmissions.isEmpty()) {
            ExamSubmission existing = existingSubmissions.getFirst();
            if (existing.getStatus() == SubmissionStatus.IN_PROGRESS) {
                return toResponse(existing);
            } else if (existing.getStatus() == SubmissionStatus.SUBMITTED || existing.getStatus() == SubmissionStatus.GRADED) {
                throw new BusinessException("您已经完成该考试，不能重复参加");
            }
        }
        
        ExamSubmission submission = new ExamSubmission();
        submission.setExam(exam);
        submission.setCandidateId(candidateId);
        submission.setStartedAt(now);
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
    public SubmissionResponseDTO saveAnswers(Long submissionId, SubmissionRequestsDTO.SaveAnswerRequest request) {
        ExamSubmission submission = getEntityById(submissionId);
        if (submission.getStatus() != SubmissionStatus.IN_PROGRESS) {
            throw new BusinessException("当前答卷不允许继续作答");
        }
        
        LocalDateTime now = LocalDateTime.now();
        Duration elapsed = Duration.between(submission.getStartedAt(), now);
        int durationMinutes = submission.getExam().getDurationMinutes();
        if (elapsed.toMinutes() > durationMinutes) {
            throw new BusinessException("考试时间已超时，无法保存答案。用时：" + elapsed.toMinutes() + "分钟，限时：" + durationMinutes + "分钟");
        }
        
        for (SubmissionRequestsDTO.AnswerItem item : request.getAnswers()) {
            SubmissionAnswer answer = submissionAnswerRepository.findBySubmissionIdAndPaperQuestionId(submissionId, item.getPaperQuestionId())
                    .orElseThrow(() -> new BusinessException("答题记录不存在: " + item.getPaperQuestionId()));
            
            String normalizedAnswer = normalizeAnswer(item);
            answer.setAnswerContent(normalizedAnswer);
            answer.setJudgeStatus(isBlank(normalizedAnswer) ? JudgeStatus.NOT_ANSWERED : JudgeStatus.ANSWERED);
        }
        return toResponse(submission);
    }

    @Transactional
    public SubmissionResponseDTO submit(Long submissionId) {
        ExamSubmission submission = getEntityById(submissionId);
        if (submission.getStatus() != SubmissionStatus.IN_PROGRESS) {
            throw new BusinessException("当前答卷状态不允许交卷");
        }

        LocalDateTime now = LocalDateTime.now();
        Duration elapsed = Duration.between(submission.getStartedAt(), now);
        int durationMinutes = submission.getExam().getDurationMinutes();
        if (elapsed.toMinutes() > durationMinutes) {
            submission.setComment("超时自动交卷，用时：" + elapsed.toMinutes() + "分钟，限时：" + durationMinutes + "分钟");
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

            boolean correct = judgeObjective(question, answer.getAnswerContent(), answer.getSelectedOptionKeys());
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
    public SubmissionResponseDTO manualGrade(Long submissionId, SubmissionRequestsDTO.ManualGradeRequest request) {
        ExamSubmission submission = getEntityById(submissionId);
        for (SubmissionRequestsDTO.ManualGradeItem item : request.getGrades()) {
            SubmissionAnswer answer = submissionAnswerRepository.findById(item.getSubmissionAnswerId())
                    .orElseThrow(() -> new BusinessException("答题记录不存在: " + item.getSubmissionAnswerId()));
            if (!Objects.equals(answer.getSubmission().getId(), submissionId)) {
                throw new BusinessException("答题记录不属于当前答卷");
            }
            
            BigDecimal maxScore = answer.getPaperQuestion().getScore();
            if (item.getScore().compareTo(maxScore) > 0) {
                throw new BusinessException("主观题得分不能超过题目分值。题目分值：" + maxScore + "，给定分数：" + item.getScore());
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
    public SubmissionResponseDTO getById(Long submissionId) {
        return toResponse(getEntityById(submissionId));
    }

    @Transactional(readOnly = true)
    public StudentSubmissionResponseDTO getStudentSubmissionById(Long submissionId) {
        ExamSubmission submission = getEntityById(submissionId);
        return toStudentResponse(submission);
    }

    @Transactional(readOnly = true)
    public List<SubmissionResponseDTO> list(Long examId, String candidateId) {
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

    private String normalizeAnswer(SubmissionRequestsDTO.AnswerItem item) {
        if (item.getSelectedOptionKeys() != null && !item.getSelectedOptionKeys().isEmpty()) {
            return String.join(",", item.getSelectedOptionKeys().stream()
                    .sorted()
                    .toList());
        }
        return item.getAnswerContent();
    }

    private boolean judgeObjective(Question question, String submittedAnswer, String selectedOptionKeys) {
        String actualAnswer = isBlank(selectedOptionKeys) ? submittedAnswer : selectedOptionKeys;
        if (isBlank(actualAnswer)) {
            return false;
        }
        List<String> standardAnswers = question.getAnswers().stream()
                .sorted(Comparator.comparing(QuestionAnswer::getSortOrder))
                .map(QuestionAnswer::getAnswerContent)
                .toList();

        return switch (question.getType()) {
            case SINGLE_CHOICE, TRUE_FALSE -> normalize(actualAnswer).equals(normalize(standardAnswers.getFirst()));
            case MULTIPLE_CHOICE -> normalizeMulti(actualAnswer).equals(normalizeMulti(String.join(",", standardAnswers)));
            case FILL_BLANK -> standardAnswers.stream().anyMatch(answer -> normalizeBlank(answer).equals(normalizeBlank(actualAnswer)));
            case SHORT_ANSWER -> false;
        };
    }

    private SubmissionResponseDTO toResponse(ExamSubmission submission) {
        SubmissionResponseDTO response = new SubmissionResponseDTO();
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
                    SubmissionResponseDTO.SubmissionAnswerResponse item = new SubmissionResponseDTO.SubmissionAnswerResponse();
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

    private StudentSubmissionResponseDTO toStudentResponse(ExamSubmission submission) {
        StudentSubmissionResponseDTO response = new StudentSubmissionResponseDTO();
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
                    StudentSubmissionResponseDTO.StudentAnswerResponse item = new StudentSubmissionResponseDTO.StudentAnswerResponse();
                    item.setId(answer.getId());
                    item.setPaperQuestionId(answer.getPaperQuestion().getId());
                    item.setQuestionId(answer.getPaperQuestion().getQuestion().getId());
                    item.setQuestionContent(answer.getPaperQuestion().getQuestion().getContent());
                    item.setQuestionType(answer.getPaperQuestion().getQuestion().getType());
                    item.setScore(answer.getPaperQuestion().getScore());
                    item.setSortOrder(answer.getPaperQuestion().getSortOrder());
                    item.setRequiredAnswer(answer.getPaperQuestion().getRequiredAnswer());
                    
                    if (answer.getPaperQuestion().getQuestion().getOptions() != null) {
                        item.setOptions(answer.getPaperQuestion().getQuestion().getOptions().stream()
                                .sorted(Comparator.comparing(QuestionOption::getSortOrder))
                                .map(option -> {
                                    StudentSubmissionResponseDTO.OptionItem optionItem = new StudentSubmissionResponseDTO.OptionItem();
                                    optionItem.setOptionKey(option.getOptionKey());
                                    optionItem.setOptionContent(option.getOptionContent());
                                    optionItem.setSortOrder(option.getSortOrder());
                                    return optionItem;
                                })
                                .toList());
                    }
                    
                    item.setAnswerContent(answer.getAnswerContent());
                    item.setCorrect(answer.getCorrect());
                    item.setObtainedScore(answer.getScore());
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
