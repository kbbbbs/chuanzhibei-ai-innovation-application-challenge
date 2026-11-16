package com.cupk.aiacademy.rag.graph;

import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.rag.RagAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rag/graphs")
public class GraphController {
    private final GraphService service;
    private final GraphExtractionService extraction;
    public GraphController(GraphService service, GraphExtractionService extraction) { this.service = service; this.extraction = extraction; }
    public record PublishForm(int revision) { }

    @GetMapping public Result list(@RequestParam Integer courseId) { return Result.success(service.list(courseId)); }
    @PostMapping public Result create(@RequestBody GraphService.CreateForm form) { return Result.success(service.create(form)); }
    @GetMapping("/{id}") public Result get(@PathVariable String id, @RequestParam(defaultValue = "false") boolean draft) { return Result.success(service.get(id, draft)); }
    @GetMapping("/{id}/sources") public Result sources(@PathVariable String id) { return Result.success(service.sources(id)); }
    @PatchMapping("/{id}/draft") public Result draft(@PathVariable String id, @RequestBody GraphService.DraftForm form) { return Result.success(service.saveDraft(id, form)); }
    @PostMapping("/{id}/publish") public Result publish(@PathVariable String id, @RequestBody PublishForm form) { return Result.success(service.publish(id, form.revision())); }
    @PostMapping("/{id}/extract") public Result extract(@PathVariable String id, @RequestBody PublishForm form) { return Result.success(extraction.extract(id, form.revision())); }
    @GetMapping("/{id}/jobs/{jobId}") public Result job(@PathVariable String id, @PathVariable String jobId) { return Result.success(service.job(id, jobId)); }
    @PostMapping("/{id}/jobs/{jobId}/retry") public Result retry(@PathVariable String id, @PathVariable String jobId) { return Result.success(service.retry(id, jobId)); }
    @GetMapping("/{id}/evidence/{evidenceId}") public Result evidence(@PathVariable String id, @PathVariable String evidenceId) { return Result.success(service.evidence(id, evidenceId)); }
    @GetMapping("/{id}/entities") public Result entities(@PathVariable String id, @RequestParam(defaultValue = "") String keyword) { return Result.success(service.entities(id, keyword)); }
    @GetMapping("/{id}/entities/{entityId}/neighbors") public Result neighbors(@PathVariable String id, @PathVariable String entityId,
            @RequestParam(defaultValue = "1") int depth, @RequestParam(defaultValue = "") String relationType) { return Result.success(service.neighbors(id, entityId, depth, relationType)); }
    @GetMapping("/{id}/paths") public Result paths(@PathVariable String id, @RequestParam String from, @RequestParam String to,
            @RequestParam(defaultValue = "false") boolean learningOnly) { return Result.success(service.paths(id, from, to, learningOnly)); }

    @ExceptionHandler(RagAccessException.class)
    public ResponseEntity<Result> accessFailure(RagAccessException failure) {
        return ResponseEntity.status(Integer.parseInt(failure.code())).body(Result.error(failure.code(), failure.getMessage()));
    }
}
