package com.cupk.aiacademy.rag;
import com.cupk.aiacademy.common.Result;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rag/legacy-knowledge-bases")
public class LegacyCourseLibraryController {
    private final LegacyCourseLibraryService service;
    public LegacyCourseLibraryController(LegacyCourseLibraryService service) { this.service = service; }
    @GetMapping public Result list() { return Result.success(service.list()); }
    public record Assignment(Integer courseId, String createCourseTitle) { }
    @PostMapping("/{id}/preview") public Result preview(@PathVariable String id, @RequestBody Assignment form) { return Result.success(service.preview(id, form.courseId())); }
    @PostMapping("/{id}/assign") public Result assign(@PathVariable String id, @RequestBody Assignment form) { return Result.success(service.assign(id, form.courseId(), form.createCourseTitle())); }
    @PostMapping("/{id}/rollback") public Result rollback(@PathVariable String id) { return Result.success(service.rollback(id)); }
}
