package com.example.final_exam_question_bank.workflow;

import com.example.final_exam_question_bank.common.*;
import com.example.final_exam_question_bank.exam.dto.ExamRequest;
import com.example.final_exam_question_bank.exam.entity.Exam;
import com.example.final_exam_question_bank.exam.service.ExamService;
import com.example.final_exam_question_bank.paper.dto.PaperRequest;
import com.example.final_exam_question_bank.paper.entity.Paper;
import com.example.final_exam_question_bank.paper.service.PaperService;
import com.example.final_exam_question_bank.question.dto.QuestionRequest;
import com.example.final_exam_question_bank.question.entity.Question;
import com.example.final_exam_question_bank.question.service.QuestionService;
import com.example.final_exam_question_bank.submission.dto.SubmissionRequests;
import com.example.final_exam_question_bank.submission.entity.ExamSubmission;
import com.example.final_exam_question_bank.submission.service.SubmissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        Question single = questionService.create(buildSingleChoiceQuestion());
        Question shortAnswer = questionService.create(buildShortAnswerQuestion());

        PaperRequest paperRequest = new PaperRequest();
        paperRequest.setName("Java 期末试卷");
        paperRequest.setDescription("期末考试");
        paperRequest.setDurationMinutes(120);
        paperRequest.setStatus(PaperStatus.PUBLISHED);

        PaperRequest.PaperQuestionRequest singleItem = new PaperRequest.PaperQuestionRequest();
        singleItem.setQuestionId(single.getId());
        singleItem.setSortOrder(1);
        singleItem.setScore(new BigDecimal("5"));
        singleItem.setRequiredAnswer(true);

        PaperRequest.PaperQuestionRequest shortItem = new PaperRequest.PaperQuestionRequest();
        shortItem.setQuestionId(shortAnswer.getId());
        shortItem.setSortOrder(2);
        shortItem.setScore(new BigDecimal("10"));
        shortItem.setRequiredAnswer(true);

        paperRequest.setQuestions(List.of(singleItem, shortItem));
        Paper paper = paperService.create(paperRequest);

        ExamRequest examRequest = new ExamRequest();
        examRequest.setName("Java 期末考试");
        examRequest.setPaperId(paper.getId());
        examRequest.setDescription("第一场");
        examRequest.setStartTime(LocalDateTime.now().minusMinutes(10));
        examRequest.setEndTime(LocalDateTime.now().plusHours(2));
        examRequest.setDurationMinutes(120);
        examRequest.setStatus(ExamStatus.PUBLISHED);
        Exam exam = examService.create(examRequest);
        examService.publish(exam.getId());

        SubmissionRequests.StartSubmissionRequest startRequest = new SubmissionRequests.StartSubmissionRequest();
        startRequest.setCandidateId("student-001");
        ExamSubmission submission = submissionService.start(exam.getId(), startRequest);

        SubmissionRequests.SaveAnswerRequest answerRequest = new SubmissionRequests.SaveAnswerRequest();
        SubmissionRequests.AnswerItem answer1 = new SubmissionRequests.AnswerItem();
        answer1.setPaperQuestionId(submission.getAnswers().get(0).getPaperQuestion().getId());
        answer1.setAnswerContent("A");
        SubmissionRequests.AnswerItem answer2 = new SubmissionRequests.AnswerItem();
        answer2.setPaperQuestionId(submission.getAnswers().get(1).getPaperQuestion().getId());
        answer2.setAnswerContent("JVM 负责管理 Java 运行环境");
        answerRequest.setAnswers(List.of(answer1, answer2));
        submissionService.saveAnswers(submission.getId(), answerRequest);

        ExamSubmission submitted = submissionService.submit(submission.getId());
        assertEquals(new BigDecimal("5.00"), submitted.getObjectiveScore());
        assertEquals(SubmissionStatus.PENDING_MANUAL_REVIEW, submitted.getStatus());
    }

    private QuestionRequest buildSingleChoiceQuestion() {
        QuestionRequest request = new QuestionRequest();
        request.setContent("Java 的编译结果是？");
        request.setType(QuestionType.SINGLE_CHOICE);
        request.setDifficulty(Difficulty.EASY);
        request.setScore(new BigDecimal("5"));
        request.setKnowledgePoint("Java 基础");
        request.setAnalysis("编译后生成字节码");
        request.setStatus(RecordStatus.ACTIVE);

        QuestionRequest.QuestionOptionRequest optionA = new QuestionRequest.QuestionOptionRequest();
        optionA.setOptionKey("A");
        optionA.setOptionContent("字节码");
        optionA.setSortOrder(1);
        QuestionRequest.QuestionOptionRequest optionB = new QuestionRequest.QuestionOptionRequest();
        optionB.setOptionKey("B");
        optionB.setOptionContent("机器码");
        optionB.setSortOrder(2);
        request.setOptions(List.of(optionA, optionB));

        QuestionRequest.QuestionAnswerRequest answer = new QuestionRequest.QuestionAnswerRequest();
        answer.setAnswerContent("A");
        answer.setSortOrder(1);
        request.setAnswers(List.of(answer));
        return request;
    }

    private QuestionRequest buildShortAnswerQuestion() {
        QuestionRequest request = new QuestionRequest();
        request.setContent("简述 JVM 的作用");
        request.setType(QuestionType.SHORT_ANSWER);
        request.setDifficulty(Difficulty.MEDIUM);
        request.setScore(new BigDecimal("10"));
        request.setKnowledgePoint("JVM");
        request.setAnalysis("开放题");
        request.setStatus(RecordStatus.ACTIVE);

        QuestionRequest.QuestionAnswerRequest answer = new QuestionRequest.QuestionAnswerRequest();
        answer.setAnswerContent("参考答案");
        answer.setSortOrder(1);
        request.setAnswers(List.of(answer));
        return request;
    }
}
