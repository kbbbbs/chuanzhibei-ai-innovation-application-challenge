package com.cupk.aiacademy.workflow;

import com.cupk.aiacademy.common.*;
import com.cupk.aiacademy.dto.ExamRequestDTO;
import com.cupk.aiacademy.dto.PaperRequestDTO;
import com.cupk.aiacademy.dto.QuestionRequestDTO;
import com.cupk.aiacademy.dto.SubmissionRequestsDTO;
import com.cupk.aiacademy.service.ExamService;
import com.cupk.aiacademy.service.PaperService;
import com.cupk.aiacademy.service.QuestionService;
import com.cupk.aiacademy.service.SubmissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
    @Rollback(false)
    void shouldCompleteObjectiveAndSubjectiveFlow() {
        // 1. 创建题目
        var single = questionService.create(buildSingleChoiceQuestion());
        var shortAnswer = questionService.create(buildShortAnswerQuestion());

        // 2. 创建试卷
        PaperRequestDTO paperRequestDTO = new PaperRequestDTO();
        paperRequestDTO.setName("Java 期末试卷");
        paperRequestDTO.setDescription("期末考试");
        paperRequestDTO.setDurationMinutes(120);
        paperRequestDTO.setStatus(PaperStatus.PUBLISHED);

        PaperRequestDTO.PaperQuestionRequest singleItem = new PaperRequestDTO.PaperQuestionRequest();
        singleItem.setQuestionId(single.getId());
        singleItem.setSortOrder(1);
        singleItem.setScore(new BigDecimal("5"));
        singleItem.setRequiredAnswer(true);

        PaperRequestDTO.PaperQuestionRequest shortItem = new PaperRequestDTO.PaperQuestionRequest();
        shortItem.setQuestionId(shortAnswer.getId());
        shortItem.setSortOrder(2);
        shortItem.setScore(new BigDecimal("10"));
        shortItem.setRequiredAnswer(true);

        paperRequestDTO.setQuestions(List.of(singleItem, shortItem));
        var paper = paperService.create(paperRequestDTO);

        // 3. 创建并发布考试
        ExamRequestDTO examRequestDTO = new ExamRequestDTO();
        examRequestDTO.setName("Java 期末考试");
        examRequestDTO.setPaperId(paper.getId());
        examRequestDTO.setDescription("第一场");
        examRequestDTO.setStartTime(LocalDateTime.now().minusMinutes(10));
        examRequestDTO.setEndTime(LocalDateTime.now().plusHours(2));
        examRequestDTO.setDurationMinutes(120);
        examRequestDTO.setStatus(ExamStatus.DRAFT);
        var exam = examService.create(examRequestDTO);
        examService.publish(exam.getId());

        // 4. 学生开始考试
        var submission = submissionService.start(exam.getId(), "student-001");

        // 5. 保存答案（使用 selectedOptionKeys）
        SubmissionRequestsDTO.SaveAnswerRequest answerRequest = new SubmissionRequestsDTO.SaveAnswerRequest();
        SubmissionRequestsDTO.AnswerItem answer1 = new SubmissionRequestsDTO.AnswerItem();
        answer1.setPaperQuestionId(submission.getAnswers().get(0).getPaperQuestionId());
        answer1.setSelectedOptionKeys(List.of("A"));

        SubmissionRequestsDTO.AnswerItem answer2 = new SubmissionRequestsDTO.AnswerItem();
        answer2.setPaperQuestionId(submission.getAnswers().get(1).getPaperQuestionId());
        answer2.setAnswerContent("JVM 负责管理 Java 运行环境");

        answerRequest.setAnswers(List.of(answer1, answer2));
        submissionService.saveAnswers(submission.getId(), answerRequest);

        // 6. 交卷并验证自动判分
        var submitted = submissionService.submit(submission.getId());
        assertEquals(new BigDecimal("5.00"), submitted.getObjectiveScore());
        assertEquals(SubmissionStatus.PENDING_MANUAL_REVIEW, submitted.getStatus());
    }

    private QuestionRequestDTO buildSingleChoiceQuestion() {
        QuestionRequestDTO request = new QuestionRequestDTO();
        request.setContent("Java 的编译结果是？");
        request.setType(QuestionType.SINGLE_CHOICE);
        request.setDifficulty(Difficulty.EASY);
        request.setScore(new BigDecimal("5"));
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
        request.setScore(new BigDecimal("10"));
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
