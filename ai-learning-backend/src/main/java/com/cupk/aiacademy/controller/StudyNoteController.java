package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.StudyNoteSaveRequest;
import com.cupk.aiacademy.note.StudyNoteService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
public class StudyNoteController {
    private final StudyNoteService studyNoteService;

    public StudyNoteController(StudyNoteService studyNoteService) {
        this.studyNoteService = studyNoteService;
    }

    @GetMapping("/lessons/{lessonId}/note")
    public Result getNote(@PathVariable Integer lessonId) {
        return Result.success(studyNoteService.getMyNote(lessonId));
    }

    @PutMapping("/lessons/{lessonId}/note")
    public Result saveNote(@PathVariable Integer lessonId,
                           @Valid @RequestBody StudyNoteSaveRequest request) {
        return Result.success(studyNoteService.saveMyNote(lessonId, request));
    }

    @GetMapping(value = "/lessons/{lessonId}/note/download", produces = "text/markdown;charset=UTF-8")
    public ResponseEntity<byte[]> downloadNote(@PathVariable Integer lessonId) {
        String content = studyNoteService.getDownloadContent(lessonId);
        String fileName = studyNoteService.getDownloadFileName(lessonId);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "markdown", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(fileName, StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(content.getBytes(StandardCharsets.UTF_8));
    }
}
