package com.cupk.aiacademy.service;

import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.ExamStatus;
import com.cupk.aiacademy.common.PaperStatus;
import com.cupk.aiacademy.dto.ExamRequestDTO;
import com.cupk.aiacademy.dto.ExamResponseDTO;
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
    public ExamResponseDTO create(ExamRequestDTO request) {
        Paper paper = paperService.getEntityById(request.getPaperId());
        if (paper.getStatus() != PaperStatus.PUBLISHED) {
            throw new BusinessException("只能使用已发布的试卷创建考试，当前试卷状态：" + paper.getStatus());
        }
        
        Exam exam = new Exam();
        fillExam(exam, request);
        return toResponse(examRepository.save(exam));
    }

    @Transactional
    public ExamResponseDTO update(Long id, ExamRequestDTO request) {
        Exam exam = getEntityById(id);
        if (exam.getStatus() == ExamStatus.PUBLISHED || exam.getStatus() == ExamStatus.CLOSED) {
            throw new BusinessException("已发布或已关闭的考试不允许修改");
        }
        
        Paper paper = paperService.getEntityById(request.getPaperId());
        if (paper.getStatus() != PaperStatus.PUBLISHED) {
            throw new BusinessException("只能使用已发布的试卷，当前试卷状态：" + paper.getStatus());
        }
        
        fillExam(exam, request);
        return toResponse(exam);
    }

    @Transactional(readOnly = true)
    public ExamResponseDTO getById(Long id) {
        return toResponse(getEntityById(id));
    }

    @Transactional(readOnly = true)
    public List<ExamResponseDTO> list() {
        return examRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public ExamResponseDTO publish(Long id) {
        Exam exam = getEntityById(id);
        if (exam.getStatus() != ExamStatus.DRAFT) {
            throw new BusinessException("只有草稿状态的考试才能发布，当前状态：" + exam.getStatus());
        }
        exam.setStatus(ExamStatus.PUBLISHED);
        return toResponse(exam);
    }

    @Transactional
    public ExamResponseDTO close(Long id) {
        Exam exam = getEntityById(id);
        if (exam.getStatus() != ExamStatus.PUBLISHED) {
            throw new BusinessException("只有已发布的考试才能关闭，当前状态：" + exam.getStatus());
        }
        exam.setStatus(ExamStatus.CLOSED);
        return toResponse(exam);
    }

    public Exam getEntityById(Long id) {
        return examRepository.findById(id)
                .orElseThrow(() -> new BusinessException("考试不存在: " + id));
    }

    private void fillExam(Exam exam, ExamRequestDTO request) {
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

    private ExamResponseDTO toResponse(Exam exam) {
        ExamResponseDTO response = new ExamResponseDTO();
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
