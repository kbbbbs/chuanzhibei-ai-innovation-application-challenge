package com.cupk.aiacademy.rag.graph;

import com.cupk.aiacademy.common.Result;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/courses/{courseId}/knowledge-graph")
public class CourseKnowledgeGraphController {
    private final CourseKnowledgeGraphService graphs;

    public CourseKnowledgeGraphController(CourseKnowledgeGraphService graphs) {
        this.graphs = graphs;
    }

    @GetMapping
    public Result read(
            @PathVariable int courseId, @RequestParam(required = false) Integer chapterId) {
        return Result.success(graphs.read(courseId, chapterId));
    }

    @GetMapping("/scope-options")
    public Result options(@PathVariable int courseId) {
        return Result.success(graphs.scopeOptions(courseId));
    }

    @PostMapping("/generate")
    public Result generate(@PathVariable int courseId, @RequestBody Map<String, Object> input) {
        return Result.success(
                graphs.generate(
                        courseId,
                        Objects.toString(input.get("requestId"), null),
                        input.get("chapterId") == null
                                ? null
                                : Integer.valueOf(input.get("chapterId").toString()),
                        input.get("documentIds") instanceof List<?> documents
                                ? documents.stream().map(Object::toString).toList()
                                : List.of()));
    }

    @GetMapping("/neighbors")
    public Result neighbors(
            @PathVariable int courseId,
            @RequestParam String entityId,
            @RequestParam(defaultValue = "1") int depth,
            @RequestParam(defaultValue = "") String relationType,
            @RequestParam(required = false) Integer chapterId) {
        return Result.success(graphs.neighbors(courseId, entityId, depth, relationType, chapterId));
    }

    @GetMapping("/paths")
    public Result paths(
            @PathVariable int courseId,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) Integer chapterId) {
        return Result.success(graphs.paths(courseId, from, to, chapterId));
    }

    @PostMapping("/jobs/{jobId}/cancel")
    public Result cancel(@PathVariable int courseId, @PathVariable String jobId) {
        return Result.success(graphs.cancel(courseId, jobId));
    }
}
