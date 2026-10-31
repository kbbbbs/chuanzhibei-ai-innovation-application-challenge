package com.cupk.aiacademy.preparation;

import com.cupk.aiacademy.common.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/preparations")
public class PreparationController {
    private final PreparationService preparationService;

    public PreparationController(PreparationService preparationService) {
        this.preparationService = preparationService;
    }

    @PostMapping
    public Result create(@Valid @RequestBody PreparationModels.Config config) {
        return Result.success(preparationService.create(config));
    }

    @GetMapping
    public Result list() {
        return Result.success(preparationService.list());
    }

    @GetMapping("/{id}")
    public Result get(@PathVariable Long id) {
        return Result.success(preparationService.get(id));
    }

    @PutMapping("/{id}/config")
    public Result updateConfig(@PathVariable Long id, @Valid @RequestBody PreparationModels.Config config) {
        return Result.success(preparationService.updateConfig(id, config));
    }

    @PostMapping(value = "/{id}/sources", consumes = "multipart/form-data")
    public Result uploadSources(@PathVariable Long id,
                                @RequestPart(value = "files", required = false) List<MultipartFile> files,
                                @RequestParam(value = "file", required = false) MultipartFile file) {
        if (files == null) files = new java.util.ArrayList<>();
        if (file != null && !file.isEmpty()) files.add(file);
        return Result.success(preparationService.uploadSources(id, files));
    }

    @PostMapping("/{id}/generate-outline")
    public Result generateOutline(@PathVariable Long id) {
        return Result.success(preparationService.startOutline(id), "章节骨架生成任务已启动");
    }

    @PutMapping("/{id}/outline")
    public Result saveOutline(@PathVariable Long id, @RequestBody PreparationModels.Outline outline) {
        return Result.success(preparationService.saveOutline(id, outline));
    }

    @PostMapping("/{id}/generate-lessons")
    public Result generateLessons(@PathVariable Long id) {
        return Result.success(preparationService.startLessons(id), "课时内容生成任务已启动");
    }

    @PutMapping("/{id}/lessons")
    public Result saveLessons(@PathVariable Long id, @RequestBody List<PreparationModels.LessonDraft> lessons) {
        return Result.success(preparationService.saveLessons(id, lessons));
    }

    @PostMapping("/{id}/generate-questions")
    public Result generateQuestions(@PathVariable Long id) {
        return Result.success(preparationService.startQuestions(id), "题目生成任务已启动");
    }

    @PutMapping("/{id}/questions")
    public Result saveQuestions(@PathVariable Long id,
                                @RequestBody List<PreparationModels.ChapterQuestions> questions) {
        return Result.success(preparationService.saveQuestions(id, questions));
    }

    @PostMapping("/{id}/publish")
    public Result publish(@PathVariable Long id) {
        return Result.success(preparationService.publish(id), "课程草稿发布成功");
    }
}
