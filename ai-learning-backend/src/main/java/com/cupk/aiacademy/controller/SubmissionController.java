package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.ApiResponse;
import com.cupk.aiacademy.dto.StudentSubmissionResponseDTO;
import com.cupk.aiacademy.dto.SubmissionRequestsDTO;
import com.cupk.aiacademy.dto.SubmissionResponseDTO;
import com.cupk.aiacademy.service.SubmissionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SubmissionController {
    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @PostMapping("/api/exams/{id}/submissions/start")
    public ApiResponse<SubmissionResponseDTO> start(
            @PathVariable Long id,
            @RequestParam String candidateId) {
        return ApiResponse.success(submissionService.start(id, candidateId));
    }

    @PostMapping("/api/submissions/{id}/answers")
    public ApiResponse<SubmissionResponseDTO> saveAnswers(
            @PathVariable Long id,
            @Valid @RequestBody SubmissionRequestsDTO.SaveAnswerRequest request) {
        return ApiResponse.success(submissionService.saveAnswers(id, request));
    }

    @PostMapping("/api/submissions/{id}/submit")
    public ApiResponse<SubmissionResponseDTO> submit(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) SubmissionRequestsDTO.SaveAnswerRequest request) {
        return ApiResponse.success(submissionService.submit(id, request));
    }

    @PostMapping("/api/submissions/{id}/auto-submit")
    public ApiResponse<SubmissionResponseDTO> autoSubmit(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) SubmissionRequestsDTO.SaveAnswerRequest request) {
        return ApiResponse.success(submissionService.autoSubmit(id, request));
    }

    @PostMapping("/api/submissions/{id}/screen-switch")
    public ApiResponse<SubmissionResponseDTO> reportScreenSwitch(@PathVariable Long id) {
        return ApiResponse.success(submissionService.reportScreenSwitch(id));
    }

    @GetMapping("/api/submissions/{id}")
    public ApiResponse<SubmissionResponseDTO> getById(@PathVariable Long id) {
        return ApiResponse.success(submissionService.getById(id));
    }

    @GetMapping("/api/submissions/{id}/grading")
    public ApiResponse<SubmissionResponseDTO> getGradingById(@PathVariable Long id) {
        return ApiResponse.success(submissionService.getGradingSubmissionById(id));
    }

    @GetMapping("/api/submissions/{id}/student")
    public ApiResponse<StudentSubmissionResponseDTO> getStudentById(@PathVariable Long id) {
        return ApiResponse.success(submissionService.getStudentSubmissionById(id));
    }

    @GetMapping("/api/submissions")
    public ApiResponse<List<SubmissionResponseDTO>> list(
            @RequestParam(required = false) Long examId,
            @RequestParam(required = false) String candidateId,
            @RequestParam(required = false) Long studentId) {
        return ApiResponse.success(submissionService.list(examId, candidateId, studentId));
    }

    @PostMapping("/api/submissions/{id}/grade-subjective")
    public ApiResponse<SubmissionResponseDTO> manualGrade(
            @PathVariable Long id,
            @Valid @RequestBody SubmissionRequestsDTO.ManualGradeRequest request) {
        return ApiResponse.success(submissionService.manualGrade(id, request));
    }
}
