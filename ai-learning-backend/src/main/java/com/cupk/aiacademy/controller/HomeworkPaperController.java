package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.ApiResponse;
import com.cupk.aiacademy.dto.PaperPreflightResponse;
import com.cupk.aiacademy.dto.PaperRequestDTO;
import com.cupk.aiacademy.dto.PaperResponseDTO;
import com.cupk.aiacademy.service.HomeworkPaperService;
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
@RequestMapping("/api/homework-papers")
public class HomeworkPaperController {
    private final HomeworkPaperService homeworkPaperService;

    public HomeworkPaperController(HomeworkPaperService homeworkPaperService) {
        this.homeworkPaperService = homeworkPaperService;
    }

    @PostMapping
    public ApiResponse<PaperResponseDTO> create(@Valid @RequestBody PaperRequestDTO request) {
        return ApiResponse.success(homeworkPaperService.create(request));
    }

    @PostMapping("/preflight")
    public ApiResponse<PaperPreflightResponse> preflight(@Valid @RequestBody PaperRequestDTO request) {
        return ApiResponse.success(homeworkPaperService.preflight(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<PaperResponseDTO> update(@PathVariable Long id, @Valid @RequestBody PaperRequestDTO request) {
        return ApiResponse.success(homeworkPaperService.update(id, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<PaperResponseDTO> getById(@PathVariable Long id) {
        return ApiResponse.success(homeworkPaperService.getById(id));
    }

    @GetMapping
    public ApiResponse<List<PaperResponseDTO>> list(@RequestParam(required = false) Long teacherId,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) Long courseId) {
        return ApiResponse.success(homeworkPaperService.list(teacherId, keyword, courseId));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<PaperResponseDTO> publish(@PathVariable Long id) {
        return ApiResponse.success(homeworkPaperService.publish(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        homeworkPaperService.delete(id);
        return ApiResponse.success(null, "删除成功");
    }

    @DeleteMapping("/{id}/questions/{paperQuestionId}")
    public ApiResponse<Void> removeQuestion(@PathVariable Long id, @PathVariable Long paperQuestionId) {
        homeworkPaperService.removePaperQuestion(id, paperQuestionId);
        return ApiResponse.success(null, "删除成功");
    }
}
