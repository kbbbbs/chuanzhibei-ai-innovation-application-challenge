package com.cupk.aiacademy.questiongen;

import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.GenerateQuestionsFromKbRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/questions/generation-tasks")
public class QuestionGenerationTaskController {
    private final QuestionGenerationTaskService tasks;

    public QuestionGenerationTaskController(QuestionGenerationTaskService tasks) { this.tasks = tasks; }

    @PostMapping
    public Result start(@RequestBody GenerateQuestionsFromKbRequest request) { return Result.success(tasks.start(request)); }

    @GetMapping
    public Result list(@RequestParam String knowledgeBaseId) { return Result.success(tasks.list(knowledgeBaseId)); }

    @GetMapping("/{id}")
    public Result get(@PathVariable String id) { return Result.success(tasks.get(id)); }

    @PutMapping("/{id}/questions")
    public Result update(@PathVariable String id, @RequestBody List<Map<String, Object>> questions) {
        return Result.success(tasks.updateDrafts(id, questions));
    }

    @PostMapping("/{id}/resume")
    public Result resume(@PathVariable String id) { return Result.success(tasks.resume(id)); }

    @PostMapping("/{id}/save")
    public Result save(@PathVariable String id) { return Result.success(tasks.save(id)); }

    @PostMapping("/{id}/discard")
    public Result discard(@PathVariable String id) { return Result.success(tasks.discard(id)); }
}
