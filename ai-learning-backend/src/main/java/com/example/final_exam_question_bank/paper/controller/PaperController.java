package com.example.final_exam_question_bank.paper.controller;

import com.example.final_exam_question_bank.common.ApiResponse;
import com.example.final_exam_question_bank.paper.dto.PaperRequest;
import com.example.final_exam_question_bank.paper.dto.PaperResponse;
import com.example.final_exam_question_bank.paper.service.PaperService;
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
    public ApiResponse<PaperResponse> create(@Valid @RequestBody PaperRequest request) {
        return ApiResponse.success(paperService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<PaperResponse> update(@PathVariable Long id, @Valid @RequestBody PaperRequest request) {
        return ApiResponse.success(paperService.update(id, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<PaperResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(paperService.getById(id));
    }

    @GetMapping
    public ApiResponse<List<PaperResponse>> list() {
        return ApiResponse.success(paperService.list());
    }

    @DeleteMapping("/{id}/questions/{paperQuestionId}")
    public ApiResponse<Void> removeQuestion(@PathVariable Long id, @PathVariable Long paperQuestionId) {
        paperService.removePaperQuestion(id, paperQuestionId);
        return ApiResponse.success(null, "删除成功");
    }
}
