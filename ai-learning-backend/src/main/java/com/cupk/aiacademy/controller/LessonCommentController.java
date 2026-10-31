package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.comment.LessonCommentService;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.LessonCommentCreateRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LessonCommentController {
    private final LessonCommentService lessonCommentService;

    public LessonCommentController(LessonCommentService lessonCommentService) {
        this.lessonCommentService = lessonCommentService;
    }

    @GetMapping("/lessons/{lessonId}/comments")
    public Result listComments(@PathVariable Integer lessonId,
                               @RequestParam(defaultValue = "1") Integer pageNum,
                               @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(lessonCommentService.listComments(lessonId, pageNum, pageSize));
    }

    @PostMapping("/lessons/{lessonId}/comments")
    public Result createComment(@PathVariable Integer lessonId,
                                @Valid @RequestBody LessonCommentCreateRequest request) {
        return Result.success(lessonCommentService.createComment(lessonId, request));
    }

    @DeleteMapping("/lesson-comments/{commentId}")
    public Result deleteComment(@PathVariable Long commentId) {
        lessonCommentService.deleteComment(commentId);
        return Result.success();
    }
}
