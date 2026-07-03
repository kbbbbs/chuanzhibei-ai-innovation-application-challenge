package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.ApiResponse;
import com.cupk.aiacademy.dto.SubmissionRequests;
import com.cupk.aiacademy.dto.SubmissionResponse;
import com.cupk.aiacademy.service.SubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    @PostMapping("/api/exams/{id}/submissions/start")
    public ApiResponse<SubmissionResponse> start(
            @PathVariable Long id,
            @Valid @RequestBody SubmissionRequests.StartSubmissionRequest request) {
        return ApiResponse.success(submissionService.start(id, request));
    }

    @PostMapping("/api/submissions/{id}/answers")
    public ApiResponse<SubmissionResponse> saveAnswers(
            @PathVariable Long id,
            @Valid @RequestBody SubmissionRequests.SaveAnswerRequest request) {
        return ApiResponse.success(submissionService.saveAnswers(id, request));
    }

    @PostMapping("/api/submissions/{id}/submit")
    public ApiResponse<SubmissionResponse> submit(@PathVariable Long id) {
        return ApiResponse.success(submissionService.submit(id));
    }

    @GetMapping("/api/submissions/{id}")
    public ApiResponse<SubmissionResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(submissionService.getById(id));
    }

    @GetMapping("/api/submissions")
    public ApiResponse<List<SubmissionResponse>> list(
            @RequestParam(required = false) Long examId,
            @RequestParam(required = false) String candidateId) {
        return ApiResponse.success(submissionService.list(examId, candidateId));
    }

    @PostMapping("/api/submissions/{id}/grade-subjective")
    public ApiResponse<SubmissionResponse> manualGrade(
            @PathVariable Long id,
            @Valid @RequestBody SubmissionRequests.ManualGradeRequest request) {
        return ApiResponse.success(submissionService.manualGrade(id, request));
    }
}
