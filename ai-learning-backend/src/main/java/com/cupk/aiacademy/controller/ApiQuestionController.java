package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.ApiResponse;
import com.cupk.aiacademy.common.Difficulty;
import com.cupk.aiacademy.common.QuestionType;
import com.cupk.aiacademy.common.RecordStatus;
import com.cupk.aiacademy.dto.QuestionRequestDTO;
import com.cupk.aiacademy.dto.QuestionResponseDTO;
import com.cupk.aiacademy.service.QuestionService;
import com.cupk.aiacademy.service.PermissionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/questions")
public class ApiQuestionController {
    private final QuestionService questionService;
    private final PermissionService permissionService;

    public ApiQuestionController(QuestionService questionService, PermissionService permissionService) {
        this.questionService = questionService;
        this.permissionService = permissionService;
    }

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
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(questionService.list(type, difficulty, status, courseId, teacherId, page, size));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        questionService.delete(id);
        return ApiResponse.success(null, "删除成功");
    }

    @PostMapping("/extract")
    public ApiResponse<List<QuestionRequestDTO>> extract(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long courseId) {
        permissionService.requireTeacherOrAdmin();
        var course = permissionService.getCourse(courseId.intValue());
        var user = permissionService.currentUser();
        Integer teacherId;
        if ("teacher".equals(user.getRoleCode())) {
            permissionService.requireCourseTeacher(course);
            teacherId = user.getUserId();
        } else {
            teacherId = course.getTeacherId();
        }
        return ApiResponse.success(questionService.extractQuestions(file, courseId, teacherId.longValue()));
    }

    @PostMapping("/batch")
    public ApiResponse<Map<String, Object>> batchCreate(@RequestBody List<QuestionRequestDTO> requests) {
        List<Map<String, Object>> items = questionService.batchCreate(requests);
        long successCount = items.stream().filter(m -> Boolean.TRUE.equals(m.get("success"))).count();
        long failCount = items.size() - successCount;
        Map<String, Object> summary = new java.util.HashMap<>();
        summary.put("total", items.size());
        summary.put("success", successCount);
        summary.put("fail", failCount);
        summary.put("items", items);
        return ApiResponse.success(summary);
    }
}
