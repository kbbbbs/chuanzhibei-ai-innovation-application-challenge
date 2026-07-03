package com.cupk.aiacademy.service;

import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.PaperStatus;
import com.cupk.aiacademy.dto.PaperRequestDTO;
import com.cupk.aiacademy.dto.PaperResponseDTO;
import com.cupk.aiacademy.pojo.Paper;
import com.cupk.aiacademy.pojo.PaperQuestion;
import com.cupk.aiacademy.pojo.Question;
import com.cupk.aiacademy.repository.PaperQuestionRepository;
import com.cupk.aiacademy.repository.PaperRepository;
import com.cupk.aiacademy.repository.SubmissionAnswerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PaperService {

    private final PaperRepository paperRepository;
    private final PaperQuestionRepository paperQuestionRepository;
    private final QuestionService questionService;
    private final SubmissionAnswerRepository submissionAnswerRepository;

    @Transactional
    public PaperResponseDTO create(PaperRequestDTO request) {
        validatePaperQuestions(request.getQuestions());
        
        Paper paper = new Paper();
        fillPaper(paper, request);
        return toResponse(paperRepository.save(paper));
    }

    @Transactional
    public PaperResponseDTO update(Long id, PaperRequestDTO request) {
        Paper paper = getEntityById(id);
        if (paper.getStatus() == PaperStatus.PUBLISHED || paper.getStatus() == PaperStatus.ARCHIVED) {
            throw new BusinessException("已发布或已归档的试卷不允许修改");
        }
        
        validatePaperQuestions(request.getQuestions());
        
        paper.getPaperQuestions().clear();
        fillPaper(paper, request);
        return toResponse(paper);
    }

    @Transactional(readOnly = true)
    public PaperResponseDTO getById(Long id) {
        return toResponse(getEntityById(id));
    }

    @Transactional(readOnly = true)
    public List<PaperResponseDTO> list() {
        return paperRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public void removePaperQuestion(Long paperId, Long paperQuestionId) {
        Paper paper = getEntityById(paperId);
        if (paper.getStatus() == PaperStatus.PUBLISHED || paper.getStatus() == PaperStatus.ARCHIVED) {
            throw new BusinessException("已发布或已归档的试卷不允许删除题目");
        }
        
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

    private void validatePaperQuestions(List<PaperRequestDTO.PaperQuestionRequest> questions) {
        if (questions == null || questions.isEmpty()) {
            throw new BusinessException("试卷必须包含至少一道题目");
        }
        
        Set<Long> questionIds = new HashSet<>();
        Set<Integer> sortOrders = new HashSet<>();
        
        for (PaperRequestDTO.PaperQuestionRequest item : questions) {
            if (questionIds.contains(item.getQuestionId())) {
                throw new BusinessException("试卷中不能重复添加同一道题，题目ID：" + item.getQuestionId());
            }
            questionIds.add(item.getQuestionId());
            
            if (sortOrders.contains(item.getSortOrder())) {
                throw new BusinessException("试卷中题目序号不能重复，序号：" + item.getSortOrder());
            }
            sortOrders.add(item.getSortOrder());
        }
    }

    private void fillPaper(Paper paper, PaperRequestDTO request) {
        paper.setName(request.getName());
        paper.setDescription(request.getDescription());
        paper.setDurationMinutes(request.getDurationMinutes());
        paper.setStatus(request.getStatus());
        paper.setTotalScore(BigDecimal.ZERO);

        BigDecimal totalScore = BigDecimal.ZERO;
        for (PaperRequestDTO.PaperQuestionRequest item : request.getQuestions()) {
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

    private PaperResponseDTO toResponse(Paper paper) {
        PaperResponseDTO response = new PaperResponseDTO();
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
                    PaperResponseDTO.PaperQuestionResponse pq = new PaperResponseDTO.PaperQuestionResponse();
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
