package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.ApiResponse;
import com.cupk.aiacademy.dto.ExamRequestDTO;
import com.cupk.aiacademy.dto.ExamResponseDTO;
import com.cupk.aiacademy.service.ExamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/exams")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;

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
    public ApiResponse<List<ExamResponseDTO>> list() {
        return ApiResponse.success(examService.list());
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<ExamResponseDTO> publish(@PathVariable Long id) {
        return ApiResponse.success(examService.publish(id));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<ExamResponseDTO> close(@PathVariable Long id) {
        return ApiResponse.success(examService.close(id));
    }
}
