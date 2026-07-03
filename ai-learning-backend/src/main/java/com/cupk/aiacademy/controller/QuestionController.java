package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.ApiResponse;
import com.cupk.aiacademy.common.Difficulty;
import com.cupk.aiacademy.common.QuestionType;
import com.cupk.aiacademy.common.RecordStatus;
import com.cupk.aiacademy.dto.QuestionRequestDTO;
import com.cupk.aiacademy.dto.QuestionResponseDTO;
import com.cupk.aiacademy.service.QuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;

    @PostMapping
    public ApiResponse<QuestionResponseDTO> create(@Valid @RequestBody QuestionRequestDTO request) {
        return ApiResponse.success(questionService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<QuestionResponseDTO> update(@PathVariable Long id, @Valid @RequestBody QuestionRequestDTO request) {
        return ApiResponse.success(questionService.update(id, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<QuestionResponseDTO> getById(@PathVariable Long id) {
        return ApiResponse.success(questionService.getById(id));
    }

    @GetMapping
    public ApiResponse<Page<QuestionResponseDTO>> list(
            @RequestParam(required = false) QuestionType type,
            @RequestParam(required = false) Difficulty difficulty,
            @RequestParam(required = false) RecordStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(questionService.list(type, difficulty, status, page, size));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        questionService.delete(id);
        return ApiResponse.success(null, "删除成功");
    }
}
