package com.cupk.aiacademy.workflow;

import com.cupk.aiacademy.common.Difficulty;
import com.cupk.aiacademy.common.ExamStatus;
import com.cupk.aiacademy.common.PaperStatus;
import com.cupk.aiacademy.common.QuestionType;
import com.cupk.aiacademy.common.RecordStatus;
import com.cupk.aiacademy.common.SubmissionStatus;
import com.cupk.aiacademy.dto.ExamRequestDTO;
import com.cupk.aiacademy.dto.PaperRequestDTO;
import com.cupk.aiacademy.dto.QuestionRequestDTO;
import com.cupk.aiacademy.dto.SubmissionRequestsDTO;
import com.cupk.aiacademy.dto.SubmissionResponseDTO;
import com.cupk.aiacademy.service.ExamService;
import com.cupk.aiacademy.service.PaperService;
import com.cupk.aiacademy.service.QuestionService;
import com.cupk.aiacademy.service.SubmissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class ExamWorkflowIntegrationTest {
    @Autowired
    private QuestionService questionService;

    @Autowired
    private PaperService paperService;

    @Autowired
    private ExamService examService;

    @Autowired
    private SubmissionService submissionService;

    @Test
    void shouldCompleteObjectiveAndSubjectiveFlow() {
        var single = questionService.create(buildSingleChoiceQuestion());
        var shortAnswer = questionService.create(buildShortAnswerQuestion());

        PaperRequestDTO paperRequest = new PaperRequestDTO();
        paperRequest.setName("Java 期末试卷");
        paperRequest.setDescription("期末考试");
        paperRequest.setDurationMinutes(120);
        paperRequest.setStatus(PaperStatus.PUBLISHED);

        PaperRequestDTO.PaperQuestionRequest singleItem = new PaperRequestDTO.PaperQuestionRequest();
        singleItem.setQuestionId(single.getId());
        singleItem.setSortOrder(1);
        singleItem.setScore(new BigDecimal("5.00"));
        singleItem.setRequiredAnswer(true);

        PaperRequestDTO.PaperQuestionRequest shortItem = new PaperRequestDTO.PaperQuestionRequest();
        shortItem.setQuestionId(shortAnswer.getId());
        shortItem.setSortOrder(2);
        shortItem.setScore(new BigDecimal("10.00"));
        shortItem.setRequiredAnswer(true);

        paperRequest.setQuestions(List.of(singleItem, shortItem));
        var paper = paperService.create(paperRequest);
        assertEquals(new BigDecimal("15.00"), paper.getTotalScore());

        ExamRequestDTO examRequest = new ExamRequestDTO();
        examRequest.setName("Java 期末考试");
        examRequest.setPaperId(paper.getId());
        examRequest.setDescription("第一场");
        examRequest.setStartTime(LocalDateTime.now().minusMinutes(10));
        examRequest.setEndTime(LocalDateTime.now().plusHours(2));
        examRequest.setDurationMinutes(120);
        examRequest.setStatus(ExamStatus.DRAFT);

        var exam = examService.create(examRequest);
        examService.publish(exam.getId());

        var submission = submissionService.start(exam.getId(), "student-001");
        assertEquals(2, submission.getAnswers().size());

        SubmissionRequestsDTO.SaveAnswerRequest answerRequest = new SubmissionRequestsDTO.SaveAnswerRequest();
        SubmissionRequestsDTO.AnswerItem answer1 = new SubmissionRequestsDTO.AnswerItem();
        answer1.setPaperQuestionId(submission.getAnswers().get(0).getPaperQuestionId());
        answer1.setSelectedOptionKeys(List.of("A"));

        SubmissionRequestsDTO.AnswerItem answer2 = new SubmissionRequestsDTO.AnswerItem();
        answer2.setPaperQuestionId(submission.getAnswers().get(1).getPaperQuestionId());
        answer2.setAnswerContent("JVM 负责管理 Java 运行环境");

        answerRequest.setAnswers(List.of(answer1, answer2));
        submissionService.saveAnswers(submission.getId(), answerRequest);

        SubmissionResponseDTO submitted = submissionService.submit(submission.getId());
        assertEquals(new BigDecimal("5.00"), submitted.getObjectiveScore());
        assertEquals(SubmissionStatus.PENDING_MANUAL_REVIEW, submitted.getStatus());

        SubmissionRequestsDTO.ManualGradeRequest gradeRequest = new SubmissionRequestsDTO.ManualGradeRequest();
        SubmissionRequestsDTO.ManualGradeItem gradeItem = new SubmissionRequestsDTO.ManualGradeItem();
        gradeItem.setSubmissionAnswerId(submitted.getAnswers().get(1).getId());
        gradeItem.setScore(new BigDecimal("9.00"));
        gradeItem.setComment("回答完整");
        gradeRequest.setGrades(List.of(gradeItem));

        SubmissionResponseDTO graded = submissionService.manualGrade(submission.getId(), gradeRequest);
        assertEquals(new BigDecimal("9.00"), graded.getSubjectiveScore());
        assertEquals(new BigDecimal("14.00"), graded.getTotalScore());
        assertEquals(SubmissionStatus.GRADED, graded.getStatus());

        assertThrows(RuntimeException.class, () -> submissionService.start(exam.getId(), "student-001"));
    }

    @Test
    void publishAndCloseMustKeepOriginalStateRules() {
        var exam = createExam(ExamStatus.DRAFT);

        examService.publish(exam.getId());
        assertThrows(RuntimeException.class, () -> examService.publish(exam.getId()));

        examService.close(exam.getId());
        assertThrows(RuntimeException.class, () -> examService.close(exam.getId()));
    }

    @Test
    void startShouldPreventDuplicateByCandidateOrStudentIdentity() {
        var exam = createExam(ExamStatus.DRAFT);
        examService.publish(exam.getId());

        var submission = submissionService.start(exam.getId(), null, 1001L);
        submissionService.submit(submission.getId());

        assertThrows(RuntimeException.class, () -> submissionService.start(exam.getId(), "1001"));
    }

    private com.cupk.aiacademy.dto.ExamResponseDTO createExam(ExamStatus status) {
        var single = questionService.create(buildSingleChoiceQuestion());

        PaperRequestDTO paperRequest = new PaperRequestDTO();
        paperRequest.setName("Java 单选试卷");
        paperRequest.setDescription("状态规则测试");
        paperRequest.setDurationMinutes(60);
        paperRequest.setStatus(PaperStatus.PUBLISHED);

        PaperRequestDTO.PaperQuestionRequest item = new PaperRequestDTO.PaperQuestionRequest();
        item.setQuestionId(single.getId());
        item.setSortOrder(1);
        item.setScore(new BigDecimal("5.00"));
        item.setRequiredAnswer(true);
        paperRequest.setQuestions(List.of(item));

        var paper = paperService.create(paperRequest);

        ExamRequestDTO examRequest = new ExamRequestDTO();
        examRequest.setName("Java 状态规则考试");
        examRequest.setPaperId(paper.getId());
        examRequest.setDescription("状态规则测试");
        examRequest.setStartTime(LocalDateTime.now().minusMinutes(10));
        examRequest.setEndTime(LocalDateTime.now().plusHours(2));
        examRequest.setDurationMinutes(60);
        examRequest.setStatus(status);
        return examService.create(examRequest);
    }

    private QuestionRequestDTO buildSingleChoiceQuestion() {
        QuestionRequestDTO request = new QuestionRequestDTO();
        request.setContent("Java 的编译结果是？");
        request.setType(QuestionType.SINGLE_CHOICE);
        request.setDifficulty(Difficulty.EASY);
        request.setScore(new BigDecimal("5.00"));
        request.setKnowledgePoint("Java 基础");
        request.setAnalysis("编译后生成字节码");
        request.setStatus(RecordStatus.ACTIVE);

        QuestionRequestDTO.QuestionOptionRequest optionA = new QuestionRequestDTO.QuestionOptionRequest();
        optionA.setOptionKey("A");
        optionA.setOptionContent("字节码");
        optionA.setSortOrder(1);

        QuestionRequestDTO.QuestionOptionRequest optionB = new QuestionRequestDTO.QuestionOptionRequest();
        optionB.setOptionKey("B");
        optionB.setOptionContent("机器码");
        optionB.setSortOrder(2);

        request.setOptions(List.of(optionA, optionB));

        QuestionRequestDTO.QuestionAnswerRequest answer = new QuestionRequestDTO.QuestionAnswerRequest();
        answer.setAnswerContent("A");
        answer.setSortOrder(1);
        request.setAnswers(List.of(answer));
        return request;
    }

    private QuestionRequestDTO buildShortAnswerQuestion() {
        QuestionRequestDTO request = new QuestionRequestDTO();
        request.setContent("简述 JVM 的作用");
        request.setType(QuestionType.SHORT_ANSWER);
        request.setDifficulty(Difficulty.MEDIUM);
        request.setScore(new BigDecimal("10.00"));
        request.setKnowledgePoint("JVM");
        request.setAnalysis("开放题");
        request.setStatus(RecordStatus.ACTIVE);

        QuestionRequestDTO.QuestionAnswerRequest answer = new QuestionRequestDTO.QuestionAnswerRequest();
        answer.setAnswerContent("参考答案");
        answer.setSortOrder(1);
        request.setAnswers(List.of(answer));
        return request;
    }
}
