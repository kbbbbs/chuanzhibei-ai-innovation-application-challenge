package com.cupk.aiacademy.service;

import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.ExamStatus;
import com.cupk.aiacademy.dto.ExamRequestdto;
import com.cupk.aiacademy.dto.ExamResponsedto;
import com.cupk.aiacademy.pojo.Exam;
import com.cupk.aiacademy.pojo.Paper;
import com.cupk.aiacademy.repository.ExamRepository;
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
    public ExamResponsedto create(ExamRequestdto request) {
        Exam exam = new Exam();
        fillExam(exam, request);
        return toResponse(examRepository.save(exam));
    }

    @Transactional
    public ExamResponsedto update(Long id, ExamRequestdto request) {
        Exam exam = getEntityById(id);
        fillExam(exam, request);
        return toResponse(exam);
    }

    @Transactional(readOnly = true)
    public ExamResponsedto getById(Long id) {
        return toResponse(getEntityById(id));
    }

    @Transactional(readOnly = true)
    public List<ExamResponsedto> list() {
        return examRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public ExamResponsedto publish(Long id) {
        Exam exam = getEntityById(id);
        exam.setStatus(ExamStatus.PUBLISHED);
        return toResponse(exam);
    }

    @Transactional
    public ExamResponsedto close(Long id) {
        Exam exam = getEntityById(id);
        exam.setStatus(ExamStatus.CLOSED);
        return toResponse(exam);
    }

    public Exam getEntityById(Long id) {
        return examRepository.findById(id)
                .orElseThrow(() -> new BusinessException("考试不存在: " + id));
    }

    private void fillExam(Exam exam, ExamRequestdto request) {
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

    private ExamResponsedto toResponse(Exam exam) {
        ExamResponsedto response = new ExamResponsedto();
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
