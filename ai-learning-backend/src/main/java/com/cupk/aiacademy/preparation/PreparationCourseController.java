package com.cupk.aiacademy.preparation;

import com.cupk.aiacademy.common.Result;
import org.springframework.web.bind.annotation.*;

@RestController
public class PreparationCourseController {
    private final PreparationService preparations;
    private final PreparationCourseWorkflow workflow;
    public PreparationCourseController(PreparationService preparations, PreparationCourseWorkflow workflow) {
        this.preparations = preparations; this.workflow = workflow;
    }
    public record Binding(Integer courseId) { }

    @PostMapping("/api/preparations/{id}/bind-course")
    public Result bind(@PathVariable Long id, @RequestBody Binding form) {
        return Result.success(preparations.bindCourse(id, form.courseId()));
    }
    @GetMapping("/api/preparations/{id}/apply-preview")
    public Result preview(@PathVariable Long id) { return Result.success(workflow.preview(id)); }

    @PostMapping("/api/preparations/{id}/apply")
    public Result apply(@PathVariable Long id, @RequestBody PreparationCourseWorkflow.ApplyRequest request) {
        return Result.success(workflow.apply(id, request));
    }
    @PostMapping("/api/preparations/{id}/rollback")
    public Result rollback(@PathVariable Long id) { return Result.success(workflow.rollback(id)); }

    @PostMapping("/courses/{courseId}/import-materials")
    public Result importPages(@PathVariable Integer courseId, @RequestBody PreparationCourseWorkflow.ImportRequest request) {
        return Result.success(workflow.importPages(courseId, request));
    }
}
