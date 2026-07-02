package com.example.final_exam_question_bank.exam.service;

import com.example.final_exam_question_bank.common.BusinessException;
import com.example.final_exam_question_bank.common.ExamStatus;
import com.example.final_exam_question_bank.exam.dto.ExamRequest;
import com.example.final_exam_question_bank.exam.dto.ExamResponse;
import com.example.final_exam_question_bank.exam.entity.Exam;
import com.example.final_exam_question_bank.exam.repository.ExamRepository;
import com.example.final_exam_question_bank.paper.entity.Paper;
import com.example.final_exam_question_bank.paper.service.PaperService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamRepository examRepository;
    private final PaperService paperService;

    @Transactional
    public ExamResponse create(ExamRequest request) {
        Exam exam = new Exam();
        fillExam(exam, request);
        return toResponse(examRepository.save(exam));
    }

    @Transactional
    public ExamResponse update(Long id, ExamRequest request) {
        Exam exam = getEntityById(id);
        fillExam(exam, request);
        return toResponse(exam);
    }

    @Transactional(readOnly = true)
    public ExamResponse getById(Long id) {
        return toResponse(getEntityById(id));
    }

    @Transactional(readOnly = true)
    public List<ExamResponse> list() {
        return examRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public ExamResponse publish(Long id) {
        Exam exam = getEntityById(id);
        exam.setStatus(ExamStatus.PUBLISHED);
        return toResponse(exam);
    }

    @Transactional
    public ExamResponse close(Long id) {
        Exam exam = getEntityById(id);
        exam.setStatus(ExamStatus.CLOSED);
        return toResponse(exam);
    }

    public Exam getEntityById(Long id) {
        return examRepository.findById(id)
                .orElseThrow(() -> new BusinessException("考试不存在: " + id));
    }

    private void fillExam(Exam exam, ExamRequest request) {
        if (request.getEndTime().isBefore(request.getStartTime())) {
            throw new BusinessException("考试结束时间不能早于开始时间");
        }
        Paper paper = paperService.getEntityById(request.getPaperId());
        exam.setName(request.getName());
        exam.setPaper(paper);
        exam.setDescription(request.getDescription());
        exam.setStartTime(request.getStartTime());
        exam.setEndTime(request.getEndTime());
        exam.setDurationMinutes(request.getDurationMinutes());
        exam.setStatus(request.getStatus());
    }

    private ExamResponse toResponse(Exam exam) {
        ExamResponse response = new ExamResponse();
        response.setId(exam.getId());
        response.setName(exam.getName());
        response.setPaperId(exam.getPaper().getId());
        response.setPaperName(exam.getPaper().getName());
        response.setDescription(exam.getDescription());
        response.setStartTime(exam.getStartTime());
        response.setEndTime(exam.getEndTime());
        response.setDurationMinutes(exam.getDurationMinutes());
        response.setStatus(exam.getStatus());
        response.setCreatedAt(exam.getCreatedAt());
        response.setUpdatedAt(exam.getUpdatedAt());
        return response;
    }
}
