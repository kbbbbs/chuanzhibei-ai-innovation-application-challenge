package com.cupk.aiacademy.analysis;

import com.cupk.aiacademy.common.Result;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/learning-analysis")
public class LearningAnalysisController {
    private final LearningAnalysisReportService reports;
    private final LearningAnalysisTemplateService templates;
    public LearningAnalysisController(LearningAnalysisReportService reports, LearningAnalysisTemplateService templates) {
        this.reports = reports; this.templates = templates;
    }
    @GetMapping("/templates") public Result templates() { return Result.success(templates.list()); }
    @PostMapping("/templates") public Result upload(@RequestParam("file") MultipartFile file) throws IOException {
        return Result.success(templates.upload(file.getOriginalFilename(),file.getBytes()));
    }
    @GetMapping("/templates/{id}/word") public ResponseEntity<byte[]> template(@PathVariable String id) {
        return download(templates.bytes(id),"学情报告模板.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }
    @PostMapping("/reports") public Result create(@Valid @RequestBody LearningAnalysisReportRequest request) { return Result.success(reports.create(request)); }
    @GetMapping("/reports") public Result list(@RequestParam int courseId) { return Result.success(reports.list(courseId)); }
    @GetMapping("/reports/{id}") public Result detail(@PathVariable String id) { return Result.success(reports.detail(id)); }
    @GetMapping("/reports/{id}/word") public ResponseEntity<byte[]> word(@PathVariable String id) {
        return download(reports.word(id),"课程学情分析报告.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }
    @GetMapping("/reports/{id}/csv") public ResponseEntity<byte[]> csv(@PathVariable String id) {
        return download(reports.csv(id),"课程学情分析数据.csv","text/csv;charset=UTF-8");
    }
    private ResponseEntity<byte[]> download(byte[] bytes, String name, String type) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(name,StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(type)).body(bytes);
    }
}
