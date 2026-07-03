package com.cupk.aiacademy.service;

import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.Difficulty;
import com.cupk.aiacademy.common.QuestionType;
import com.cupk.aiacademy.common.RecordStatus;
import com.cupk.aiacademy.dto.QuestionRequestdto;
import com.cupk.aiacademy.dto.QuestionResponsedto;
import com.cupk.aiacademy.pojo.Question;
import com.cupk.aiacademy.pojo.QuestionAnswer;
import com.cupk.aiacademy.pojo.QuestionOption;
import com.cupk.aiacademy.repository.QuestionRepository;
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
    public QuestionResponsedto create(QuestionRequestdto request) {
        Question question = new Question();
        fillQuestion(question, request);
        return toResponse(questionRepository.save(question));
    }

    @Transactional
    public QuestionResponsedto update(Long id, QuestionRequestdto request) {
        Question question = getEntityById(id);
        question.getOptions().clear();
        question.getAnswers().clear();
        fillQuestion(question, request);
        return toResponse(question);
    }

    @Transactional(readOnly = true)
    public QuestionResponsedto getById(Long id) {
        return toResponse(getEntityById(id));
    }

    @Transactional(readOnly = true)
    public Page<QuestionResponsedto> list(QuestionType type, Difficulty difficulty, RecordStatus status, int page, int size) {
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

    private void fillQuestion(Question question, QuestionRequestdto request) {
        question.setContent(request.getContent());
        question.setType(request.getType());
        question.setDifficulty(request.getDifficulty());
        question.setScore(request.getScore());
        question.setKnowledgePoint(request.getKnowledgePoint());
        question.setAnalysis(request.getAnalysis());
        question.setStatus(request.getStatus());

        if (request.getOptions() != null) {
            for (QuestionRequestdto.QuestionOptionRequest optionRequest : request.getOptions()) {
                QuestionOption option = new QuestionOption();
                option.setQuestion(question);
                option.setOptionKey(optionRequest.getOptionKey());
                option.setOptionContent(optionRequest.getOptionContent());
                option.setSortOrder(optionRequest.getSortOrder());
                question.getOptions().add(option);
            }
        }

        for (QuestionRequestdto.QuestionAnswerRequest answerRequest : request.getAnswers()) {
            QuestionAnswer answer = new QuestionAnswer();
            answer.setQuestion(question);
            answer.setAnswerContent(answerRequest.getAnswerContent());
            answer.setSortOrder(answerRequest.getSortOrder());
            question.getAnswers().add(answer);
        }
    }

    private QuestionResponsedto toResponse(Question question) {
        QuestionResponsedto response = new QuestionResponsedto();
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
                    QuestionResponsedto.OptionResponse item = new QuestionResponsedto.OptionResponse();
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
                    QuestionResponsedto.AnswerResponse item = new QuestionResponsedto.AnswerResponse();
                    item.setId(answer.getId());
                    item.setAnswerContent(answer.getAnswerContent());
                    item.setSortOrder(answer.getSortOrder());
                    return item;
                })
                .toList());
        return response;
    }
}
