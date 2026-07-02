package com.example.final_exam_question_bank.paper.service;

import com.example.final_exam_question_bank.common.BusinessException;
import com.example.final_exam_question_bank.paper.dto.PaperRequest;
import com.example.final_exam_question_bank.paper.dto.PaperResponse;
import com.example.final_exam_question_bank.paper.entity.Paper;
import com.example.final_exam_question_bank.paper.entity.PaperQuestion;
import com.example.final_exam_question_bank.paper.repository.PaperQuestionRepository;
import com.example.final_exam_question_bank.paper.repository.PaperRepository;
import com.example.final_exam_question_bank.question.entity.Question;
import com.example.final_exam_question_bank.question.service.QuestionService;
import com.example.final_exam_question_bank.submission.repository.SubmissionAnswerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaperService {

    private final PaperRepository paperRepository;
    private final PaperQuestionRepository paperQuestionRepository;
    private final QuestionService questionService;
    private final SubmissionAnswerRepository submissionAnswerRepository;

    @Transactional
    public PaperResponse create(PaperRequest request) {
        Paper paper = new Paper();
        fillPaper(paper, request);
        return toResponse(paperRepository.save(paper));
    }

    @Transactional
    public PaperResponse update(Long id, PaperRequest request) {
        Paper paper = getEntityById(id);
        paper.getPaperQuestions().clear();
        fillPaper(paper, request);
        return toResponse(paper);
    }

    @Transactional(readOnly = true)
    public PaperResponse getById(Long id) {
        return toResponse(getEntityById(id));
    }

    @Transactional(readOnly = true)
    public List<PaperResponse> list() {
        return paperRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public void removePaperQuestion(Long paperId, Long paperQuestionId) {
        Paper paper = getEntityById(paperId);
        PaperQuestion paperQuestion = paperQuestionRepository.findById(paperQuestionId)
                .orElseThrow(() -> new BusinessException("试卷题目不存在: " + paperQuestionId));
        if (!paperQuestion.getPaper().getId().equals(paper.getId())) {
            throw new BusinessException("试卷题目不属于当前试卷");
        }
        if (submissionAnswerRepository.existsByPaperQuestionId(paperQuestionId)) {
            throw new BusinessException("该试卷题目已被答卷使用，不能删除");
        }
        paper.getPaperQuestions().removeIf(item -> item.getId().equals(paperQuestionId));
        paperQuestionRepository.delete(paperQuestion);
        recalculateTotalScore(paper);
    }

    public Paper getEntityById(Long id) {
        return paperRepository.findById(id)
                .orElseThrow(() -> new BusinessException("试卷不存在: " + id));
    }

    private void fillPaper(Paper paper, PaperRequest request) {
        paper.setName(request.getName());
        paper.setDescription(request.getDescription());
        paper.setDurationMinutes(request.getDurationMinutes());
        paper.setStatus(request.getStatus());
        paper.setTotalScore(BigDecimal.ZERO);

        BigDecimal totalScore = BigDecimal.ZERO;
        for (PaperRequest.PaperQuestionRequest item : request.getQuestions()) {
            Question question = questionService.getEntityById(item.getQuestionId());
            PaperQuestion paperQuestion = new PaperQuestion();
            paperQuestion.setPaper(paper);
            paperQuestion.setQuestion(question);
            paperQuestion.setSortOrder(item.getSortOrder());
            paperQuestion.setScore(item.getScore());
            paperQuestion.setRequiredAnswer(item.getRequiredAnswer());
            paper.getPaperQuestions().add(paperQuestion);
            totalScore = totalScore.add(item.getScore());
        }
        paper.setTotalScore(totalScore);
    }

    private void recalculateTotalScore(Paper paper) {
        BigDecimal totalScore = paper.getPaperQuestions().stream()
                .map(PaperQuestion::getScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        paper.setTotalScore(totalScore);
    }

    private PaperResponse toResponse(Paper paper) {
        PaperResponse response = new PaperResponse();
        response.setId(paper.getId());
        response.setName(paper.getName());
        response.setDescription(paper.getDescription());
        response.setTotalScore(paper.getTotalScore());
        response.setDurationMinutes(paper.getDurationMinutes());
        response.setStatus(paper.getStatus());
        response.setCreatedAt(paper.getCreatedAt());
        response.setUpdatedAt(paper.getUpdatedAt());
        response.setPaperQuestions(paper.getPaperQuestions().stream()
                .sorted(Comparator.comparing(PaperQuestion::getSortOrder))
                .map(item -> {
                    PaperResponse.PaperQuestionResponse pq = new PaperResponse.PaperQuestionResponse();
                    pq.setId(item.getId());
                    pq.setQuestionId(item.getQuestion().getId());
                    pq.setQuestionContent(item.getQuestion().getContent());
                    pq.setSortOrder(item.getSortOrder());
                    pq.setScore(item.getScore());
                    pq.setRequiredAnswer(item.getRequiredAnswer());
                    return pq;
                })
                .toList());
        return response;
    }
}
