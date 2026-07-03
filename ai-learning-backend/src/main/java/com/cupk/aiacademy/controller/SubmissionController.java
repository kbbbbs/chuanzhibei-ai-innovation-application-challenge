package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.ApiResponse;
import com.cupk.aiacademy.dto.SubmissionRequestsdto;
import com.cupk.aiacademy.dto.SubmissionResponsedto;
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
    public ApiResponse<SubmissionResponsedto> start(
            @PathVariable Long id,
            @Valid @RequestBody SubmissionRequestsdto.StartSubmissionRequest request) {
        return ApiResponse.success(submissionService.start(id, request));
    }

    @PostMapping("/api/submissions/{id}/answers")
    public ApiResponse<SubmissionResponsedto> saveAnswers(
            @PathVariable Long id,
            @Valid @RequestBody SubmissionRequestsdto.SaveAnswerRequest request) {
        return ApiResponse.success(submissionService.saveAnswers(id, request));
    }

    @PostMapping("/api/submissions/{id}/submit")
    public ApiResponse<SubmissionResponsedto> submit(@PathVariable Long id) {
        return ApiResponse.success(submissionService.submit(id));
    }

    @GetMapping("/api/submissions/{id}")
    public ApiResponse<SubmissionResponsedto> getById(@PathVariable Long id) {
        return ApiResponse.success(submissionService.getById(id));
    }

    @GetMapping("/api/submissions")
    public ApiResponse<List<SubmissionResponsedto>> list(
            @RequestParam(required = false) Long examId,
            @RequestParam(required = false) String candidateId) {
        return ApiResponse.success(submissionService.list(examId, candidateId));
    }

    @PostMapping("/api/submissions/{id}/grade-subjective")
    public ApiResponse<SubmissionResponsedto> manualGrade(
            @PathVariable Long id,
            @Valid @RequestBody SubmissionRequestsdto.ManualGradeRequest request) {
        return ApiResponse.success(submissionService.manualGrade(id, request));
    }
}
