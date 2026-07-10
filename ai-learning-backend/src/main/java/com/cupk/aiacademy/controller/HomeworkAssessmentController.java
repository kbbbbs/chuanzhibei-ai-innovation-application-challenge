package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.ApiResponse;
import com.cupk.aiacademy.dto.ExamRequestDTO;
import com.cupk.aiacademy.dto.ExamResponseDTO;
import com.cupk.aiacademy.dto.StudentSubmissionResponseDTO;
import com.cupk.aiacademy.dto.SubmissionRequestsDTO;
import com.cupk.aiacademy.dto.SubmissionResponseDTO;
import com.cupk.aiacademy.service.HomeworkAssessmentService;
import com.cupk.aiacademy.service.HomeworkSubmissionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class HomeworkAssessmentController {
    private final HomeworkAssessmentService homeworkAssessmentService;
    private final HomeworkSubmissionService homeworkSubmissionService;

    public HomeworkAssessmentController(HomeworkAssessmentService homeworkAssessmentService,
                                        HomeworkSubmissionService homeworkSubmissionService) {
        this.homeworkAssessmentService = homeworkAssessmentService;
        this.homeworkSubmissionService = homeworkSubmissionService;
    }

    @PostMapping("/api/homeworks")
    public ApiResponse<ExamResponseDTO> create(@Valid @RequestBody ExamRequestDTO request) {
        return ApiResponse.success(homeworkAssessmentService.create(request));
    }

    @PutMapping("/api/homeworks/{id}")
    public ApiResponse<ExamResponseDTO> update(@PathVariable Long id, @Valid @RequestBody ExamRequestDTO request) {
        return ApiResponse.success(homeworkAssessmentService.update(id, request));
    }

    @GetMapping("/api/homeworks/{id}")
    public ApiResponse<ExamResponseDTO> getById(@PathVariable Long id) {
        return ApiResponse.success(homeworkAssessmentService.getById(id));
    }

    @GetMapping("/api/homeworks")
    public ApiResponse<List<ExamResponseDTO>> list(@RequestParam(required = false) Long teacherId,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) Long classId) {
        return ApiResponse.success(homeworkAssessmentService.list(teacherId, keyword, classId));
    }

    @PostMapping("/api/homeworks/{id}/publish")
    public ApiResponse<ExamResponseDTO> publish(@PathVariable Long id) {
        return ApiResponse.success(homeworkAssessmentService.publish(id));
    }

    @PostMapping("/api/homeworks/{id}/close")
    public ApiResponse<ExamResponseDTO> close(@PathVariable Long id) {
        return ApiResponse.success(homeworkAssessmentService.close(id));
    }

    @DeleteMapping("/api/homeworks/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        homeworkAssessmentService.delete(id);
        return ApiResponse.success(null, "删除成功");
    }

    @PostMapping("/api/homeworks/{id}/submissions/start")
    public ApiResponse<SubmissionResponseDTO> start(@PathVariable Long id, @RequestParam String candidateId) {
        return ApiResponse.success(homeworkSubmissionService.start(id, candidateId));
    }

    @GetMapping("/api/homework-submissions")
    public ApiResponse<List<SubmissionResponseDTO>> listSubmissions(@RequestParam(required = false) Long homeworkId,
                                                                      @RequestParam(required = false) String candidateId,
                                                                      @RequestParam(required = false) Long studentId) {
        return ApiResponse.success(homeworkSubmissionService.list(homeworkId, candidateId, studentId));
    }

    @GetMapping("/api/homework-submissions/{id}")
    public ApiResponse<SubmissionResponseDTO> getSubmission(@PathVariable Long id) {
        return ApiResponse.success(homeworkSubmissionService.getById(id));
    }

    @GetMapping("/api/homework-submissions/{id}/grading")
    public ApiResponse<SubmissionResponseDTO> getGradingSubmission(@PathVariable Long id) {
        return ApiResponse.success(homeworkSubmissionService.getGradingSubmissionById(id));
    }

    @GetMapping("/api/homework-submissions/{id}/student")
    public ApiResponse<StudentSubmissionResponseDTO> getStudentSubmission(@PathVariable Long id) {
        return ApiResponse.success(homeworkSubmissionService.getStudentSubmissionById(id));
    }

    @PostMapping("/api/homework-submissions/{id}/answers")
    public ApiResponse<SubmissionResponseDTO> saveAnswers(@PathVariable Long id,
                                                           @Valid @RequestBody SubmissionRequestsDTO.SaveAnswerRequest request) {
        return ApiResponse.success(homeworkSubmissionService.saveAnswers(id, request));
    }

    @PostMapping("/api/homework-submissions/{id}/submit")
    public ApiResponse<SubmissionResponseDTO> submit(@PathVariable Long id) {
        return ApiResponse.success(homeworkSubmissionService.submit(id));
    }

    @PostMapping("/api/homework-submissions/{id}/grade-subjective")
    public ApiResponse<SubmissionResponseDTO> manualGrade(@PathVariable Long id,
                                                            @Valid @RequestBody SubmissionRequestsDTO.ManualGradeRequest request) {
        return ApiResponse.success(homeworkSubmissionService.manualGrade(id, request));
    }
}
