package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.ApiResponse;
import com.cupk.aiacademy.dto.PaperRequestDTO;
import com.cupk.aiacademy.dto.PaperResponseDTO;
import com.cupk.aiacademy.service.PaperService;
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
@RequestMapping("/api/papers")
public class PaperController {
    private final PaperService paperService;

    public PaperController(PaperService paperService) {
        this.paperService = paperService;
    }

    @PostMapping
    public ApiResponse<PaperResponseDTO> create(@Valid @RequestBody PaperRequestDTO request) {
        return ApiResponse.success(paperService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<PaperResponseDTO> update(@PathVariable Long id, @Valid @RequestBody PaperRequestDTO request) {
        return ApiResponse.success(paperService.update(id, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<PaperResponseDTO> getById(@PathVariable Long id) {
        return ApiResponse.success(paperService.getById(id));
    }

    @GetMapping
    public ApiResponse<List<PaperResponseDTO>> list(@RequestParam(required = false) Long teacherId) {
        return ApiResponse.success(paperService.list(teacherId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        paperService.delete(id);
        return ApiResponse.success(null, "删除成功");
    }

    @DeleteMapping("/{id}/questions/{paperQuestionId}")
    public ApiResponse<Void> removeQuestion(@PathVariable Long id, @PathVariable Long paperQuestionId) {
        paperService.removePaperQuestion(id, paperQuestionId);
        return ApiResponse.success(null, "删除成功");
    }
}
