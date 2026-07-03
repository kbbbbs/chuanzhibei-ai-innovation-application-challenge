package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.ApiResponse;
import com.cupk.aiacademy.dto.PaperRequestdto;
import com.cupk.aiacademy.dto.PaperResponsedto;
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
    public ApiResponse<PaperResponsedto> create(@Valid @RequestBody PaperRequestdto request) {
        return ApiResponse.success(paperService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<PaperResponsedto> update(@PathVariable Long id, @Valid @RequestBody PaperRequestdto request) {
        return ApiResponse.success(paperService.update(id, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<PaperResponsedto> getById(@PathVariable Long id) {
        return ApiResponse.success(paperService.getById(id));
    }

    @GetMapping
    public ApiResponse<List<PaperResponsedto>> list() {
        return ApiResponse.success(paperService.list());
    }

    @DeleteMapping("/{id}/questions/{paperQuestionId}")
    public ApiResponse<Void> removeQuestion(@PathVariable Long id, @PathVariable Long paperQuestionId) {
        paperService.removePaperQuestion(id, paperQuestionId);
        return ApiResponse.success(null, "删除成功");
    }
}
