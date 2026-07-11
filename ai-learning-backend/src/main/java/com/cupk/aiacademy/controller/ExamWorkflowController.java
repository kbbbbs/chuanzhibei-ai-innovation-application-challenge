package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.SubmissionRequestsDTO;
import com.cupk.aiacademy.service.ExamService;
import com.cupk.aiacademy.service.SubmissionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExamWorkflowController {
    private final ExamService examService;
    private final SubmissionService submissionService;

    public ExamWorkflowController(ExamService examService, SubmissionService submissionService) {
        this.examService = examService;
        this.submissionService = submissionService;
    }

    @PostMapping("/exam-sessions/{id}/publish")
    public Result publish(@PathVariable Long id) {
        return Result.success(examService.publish(id));
    }

    @PostMapping("/exam-sessions/{id}/close")
    public Result close(@PathVariable Long id) {
        return Result.success(examService.close(id));
    }

    @PostMapping("/exam-sessions/{id}/records/start")
    public Result start(@PathVariable Long id,
                        @RequestParam(required = false) String candidateId,
                        @RequestParam(required = false) Long studentId) {
        return Result.success(submissionService.start(id, candidateId, studentId));
    }

    @PostMapping("/exam-records/{id}/answers")
    public Result saveAnswers(@PathVariable Long id, @Valid @RequestBody SubmissionRequestsDTO.SaveAnswerRequest request) {
        return Result.success(submissionService.saveAnswers(id, request));
    }

    @PostMapping("/exam-records/{id}/submit")
    public Result submit(@PathVariable Long id) {
        return Result.success(submissionService.submit(id));
    }

    @PostMapping("/exam-records/{id}/screen-switch")
    public Result reportScreenSwitch(@PathVariable Long id) {
        return Result.success(submissionService.reportScreenSwitch(id));
    }

    @GetMapping("/exam-records/{id}/student")
    public Result getStudentById(@PathVariable Long id) {
        return Result.success(submissionService.getStudentSubmissionById(id));
    }

    @PostMapping("/exam-records/{id}/grade-subjective")
    public Result manualGrade(@PathVariable Long id, @Valid @RequestBody SubmissionRequestsDTO.ManualGradeRequest request) {
        return Result.success(submissionService.manualGrade(id, request));
    }
}
