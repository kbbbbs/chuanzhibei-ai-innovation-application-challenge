package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.ApiResponse;
import com.cupk.aiacademy.dto.ExamRequestDTO;
import com.cupk.aiacademy.dto.ExamResponseDTO;
import com.cupk.aiacademy.service.ExamService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/exams")
public class ExamController {
    private final ExamService examService;

    public ExamController(ExamService examService) {
        this.examService = examService;
    }

    @PostMapping
    public ApiResponse<ExamResponseDTO> create(@Valid @RequestBody ExamRequestDTO request) {
        return ApiResponse.success(examService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ExamResponseDTO> update(@PathVariable Long id, @Valid @RequestBody ExamRequestDTO request) {
        return ApiResponse.success(examService.update(id, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<ExamResponseDTO> getById(@PathVariable Long id) {
        return ApiResponse.success(examService.getById(id));
    }

    @GetMapping
    public ApiResponse<List<ExamResponseDTO>> list(@RequestParam(required = false) Long teacherId,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) Long classId) {
        return ApiResponse.success(examService.list(teacherId, keyword, classId));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<ExamResponseDTO> publish(@PathVariable Long id) {
        return ApiResponse.success(examService.publish(id));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<ExamResponseDTO> close(@PathVariable Long id) {
        return ApiResponse.success(examService.close(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        examService.delete(id);
        return ApiResponse.success(null, "删除成功");
    }
}
