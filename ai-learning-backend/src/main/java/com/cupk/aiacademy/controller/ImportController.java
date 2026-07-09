package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.ApiResponse;
import com.cupk.aiacademy.dto.imports.ImportConfirmRequest;
import com.cupk.aiacademy.dto.imports.ImportConfirmResponse;
import com.cupk.aiacademy.dto.imports.ImportPreviewResponse;
import com.cupk.aiacademy.service.ImportService;
import com.cupk.aiacademy.service.imports.TabularFileSupport.ExportFile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/imports")
public class ImportController {
    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @GetMapping("/{type}/template")
    public ResponseEntity<byte[]> template(@PathVariable String type) {
        byte[] file = importService.template(type);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(type + "-import-template.xlsx")
                        .build()
                        .toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file);
    }

    @PostMapping("/questions/preview")
    public ApiResponse<ImportPreviewResponse> previewQuestions(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(importService.preview("questions", file));
    }

    @PostMapping("/papers/preview")
    public ApiResponse<ImportPreviewResponse> previewPapers(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(importService.preview("papers", file));
    }

    @PostMapping("/exams/preview")
    public ApiResponse<ImportPreviewResponse> previewExams(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(importService.preview("exams", file));
    }

    @PostMapping("/homeworks/preview")
    public ApiResponse<ImportPreviewResponse> previewHomeworks(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(importService.preview("homeworks", file));
    }

    @PostMapping("/{type}/confirm")
    public ApiResponse<ImportConfirmResponse> confirm(@PathVariable String type, @RequestBody ImportConfirmRequest request) {
        return ApiResponse.success(importService.confirm(type, request));
    }

    @GetMapping("/{type}/export")
    public ResponseEntity<byte[]> export(@PathVariable String type,
                                         @RequestParam(defaultValue = "xlsx") String format,
                                         @RequestParam Map<String, String> params) {
        ExportFile file = importService.exportData(type, format, params);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName())
                        .build()
                        .toString())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.bytes());
    }
}
