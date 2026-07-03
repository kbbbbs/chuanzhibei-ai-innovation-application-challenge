// 文件: src/main/java/com/cupk/aiacademy/service/question/QuestionService.java
package com.cupk.aiacademy.service.question;

import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.Difficulty;
import com.cupk.aiacademy.common.QuestionType;
import com.cupk.aiacademy.common.RecordStatus;
import com.cupk.aiacademy.dto.question.QuestionRequest;
import com.cupk.aiacademy.dto.question.QuestionResponse;
import com.cupk.aiacademy.pojo.question.Question;
import com.cupk.aiacademy.pojo.question.QuestionAnswer;
import com.cupk.aiacademy.pojo.question.QuestionOption;
import com.cupk.aiacademy.repository.question.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionRepository questionRepository;

    @Transactional
    public QuestionResponse create(QuestionRequest request) {
        Question question = new Question();
        fillQuestion(question, request);
        return toResponse(questionRepository.save(question));
    }

    @Transactional
    public QuestionResponse update(Long id, QuestionRequest request) {
        Question question = getEntityById(id);
        question.getOptions().clear();
        question.getAnswers().clear();
        fillQuestion(question, request);
        return toResponse(question);
    }

    @Transactional(readOnly = true)
    public QuestionResponse getById(Long id) {
        return toResponse(getEntityById(id));
    }

    @Transactional(readOnly = true)
    public Page<QuestionResponse> list(QuestionType type, Difficulty difficulty, RecordStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<Question> questionPage;
        if (type != null && difficulty != null && status != null) {
            questionPage = questionRepository.findByTypeAndDifficultyAndStatus(type, difficulty, status, pageable);
        } else if (type != null && difficulty != null) {
            questionPage = questionRepository.findByTypeAndDifficulty(type, difficulty, pageable);
        } else if (type != null) {
            questionPage = questionRepository.findByType(type, pageable);
        } else if (difficulty != null) {
            questionPage = questionRepository.findByDifficulty(difficulty, pageable);
        } else if (status != null) {
            questionPage = questionRepository.findByStatus(status, pageable);
        } else {
            questionPage = questionRepository.findAll(pageable);
        }
        return questionPage.map(this::toResponse);
    }

    public void delete(Long id) {
        questionRepository.delete(getEntityById(id));
    }

    public Question getEntityById(Long id) {
        return questionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("题目不存在: " + id));
    }

    private void fillQuestion(Question question, QuestionRequest request) {
        question.setContent(request.getContent());
        question.setType(request.getType());
        question.setDifficulty(request.getDifficulty());
        question.setScore(request.getScore());
        question.setKnowledgePoint(request.getKnowledgePoint());
        question.setAnalysis(request.getAnalysis());
        question.setStatus(request.getStatus());

        if (request.getOptions() != null) {
            for (QuestionRequest.QuestionOptionRequest optionRequest : request.getOptions()) {
                QuestionOption option = new QuestionOption();
                option.setQuestion(question);
                option.setOptionKey(optionRequest.getOptionKey());
                option.setOptionContent(optionRequest.getOptionContent());
                option.setSortOrder(optionRequest.getSortOrder());
                question.getOptions().add(option);
            }
        }

        for (QuestionRequest.QuestionAnswerRequest answerRequest : request.getAnswers()) {
            QuestionAnswer answer = new QuestionAnswer();
            answer.setQuestion(question);
            answer.setAnswerContent(answerRequest.getAnswerContent());
            answer.setSortOrder(answerRequest.getSortOrder());
            question.getAnswers().add(answer);
        }
    }

    private QuestionResponse toResponse(Question question) {
        QuestionResponse response = new QuestionResponse();
        response.setId(question.getId());
        response.setContent(question.getContent());
        response.setType(question.getType());
        response.setDifficulty(question.getDifficulty());
        response.setScore(question.getScore());
        response.setKnowledgePoint(question.getKnowledgePoint());
        response.setAnalysis(question.getAnalysis());
        response.setStatus(question.getStatus());
        response.setCreatedAt(question.getCreatedAt());
        response.setUpdatedAt(question.getUpdatedAt());
        response.setOptions(question.getOptions().stream()
                .sorted(Comparator.comparing(QuestionOption::getSortOrder))
                .map(option -> {
                    QuestionResponse.OptionResponse item = new QuestionResponse.OptionResponse();
                    item.setId(option.getId());
                    item.setOptionKey(option.getOptionKey());
                    item.setOptionContent(option.getOptionContent());
                    item.setSortOrder(option.getSortOrder());
                    return item;
                })
                .toList());
        response.setAnswers(question.getAnswers().stream()
                .sorted(Comparator.comparing(QuestionAnswer::getSortOrder))
                .map(answer -> {
                    QuestionResponse.AnswerResponse item = new QuestionResponse.AnswerResponse();
                    item.setId(answer.getId());
                    item.setAnswerContent(answer.getAnswerContent());
                    item.setSortOrder(answer.getSortOrder());
                    return item;
                })
                .toList());
        return response;
    }
}
