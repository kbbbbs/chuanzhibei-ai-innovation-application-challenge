package com.example.final_exam_question_bank.exam.controller;

import com.example.final_exam_question_bank.common.ApiResponse;
import com.example.final_exam_question_bank.exam.dto.ExamRequest;
import com.example.final_exam_question_bank.exam.dto.ExamResponse;
import com.example.final_exam_question_bank.exam.service.ExamService;
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
    public ApiResponse<ExamResponse> create(@Valid @RequestBody ExamRequest request) {
        return ApiResponse.success(examService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ExamResponse> update(@PathVariable Long id, @Valid @RequestBody ExamRequest request) {
        return ApiResponse.success(examService.update(id, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<ExamResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(examService.getById(id));
    }

    @GetMapping
    public ApiResponse<List<ExamResponse>> list() {
        return ApiResponse.success(examService.list());
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<ExamResponse> publish(@PathVariable Long id) {
        return ApiResponse.success(examService.publish(id));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<ExamResponse> close(@PathVariable Long id) {
        return ApiResponse.success(examService.close(id));
    }
}
