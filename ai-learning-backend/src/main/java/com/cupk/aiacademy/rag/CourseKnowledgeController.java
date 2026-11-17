package com.cupk.aiacademy.rag;

import com.cupk.aiacademy.common.Result;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/rag/courses/{courseId}")
public class CourseKnowledgeController {
    private final CourseKnowledgeService service;
    public CourseKnowledgeController(CourseKnowledgeService service) { this.service = service; }
    @GetMapping("/knowledge-base") public Result base(@PathVariable Integer courseId) { return Result.success(service.ensureCourseBase(courseId)); }
    @GetMapping("/document-access") public Result access(@PathVariable Integer courseId) { return Result.success(service.documentAccess(courseId)); }
    public record AccessForm(List<String> documentIds, boolean readExcerpt, boolean downloadAllowed) { }
    @PutMapping("/document-access") public Result access(@PathVariable Integer courseId, @RequestBody AccessForm form) {
        return Result.success(service.updateDocumentAccess(courseId, form.documentIds(), form.readExcerpt(), form.downloadAllowed()));
    }
}
