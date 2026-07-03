package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.ApiResponse;
import com.cupk.aiacademy.dto.ExamRequestdto;
import com.cupk.aiacademy.dto.ExamResponsedto;
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
    public ApiResponse<ExamResponsedto> create(@Valid @RequestBody ExamRequestdto request) {
        return ApiResponse.success(examService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ExamResponsedto> update(@PathVariable Long id, @Valid @RequestBody ExamRequestdto request) {
        return ApiResponse.success(examService.update(id, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<ExamResponsedto> getById(@PathVariable Long id) {
        return ApiResponse.success(examService.getById(id));
    }

    @GetMapping
    public ApiResponse<List<ExamResponsedto>> list() {
        return ApiResponse.success(examService.list());
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<ExamResponsedto> publish(@PathVariable Long id) {
        return ApiResponse.success(examService.publish(id));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<ExamResponsedto> close(@PathVariable Long id) {
        return ApiResponse.success(examService.close(id));
    }
}
