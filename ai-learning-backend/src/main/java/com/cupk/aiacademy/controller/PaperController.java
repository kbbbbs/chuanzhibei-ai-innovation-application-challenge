package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.ApiResponse;
import com.cupk.aiacademy.dto.PaperRequestDTO;
import com.cupk.aiacademy.dto.PaperResponseDTO;
import com.cupk.aiacademy.service.PaperService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/papers")
@RequiredArgsConstructor
public class PaperController {

    private final PaperService paperService;

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
    public ApiResponse<List<PaperResponseDTO>> list() {
        return ApiResponse.success(paperService.list());
    }

    @DeleteMapping("/{id}/questions/{paperQuestionId}")
    public ApiResponse<Void> removeQuestion(@PathVariable Long id, @PathVariable Long paperQuestionId) {
        paperService.removePaperQuestion(id, paperQuestionId);
        return ApiResponse.success(null, "删除成功");
    }
}
