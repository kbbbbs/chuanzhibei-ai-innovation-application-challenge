package com.cupk.aiacademy.learning;

import com.cupk.aiacademy.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LearningExperienceController {
    private final LearningExperienceService service;
    private final CodeLabService codeLabService;
    private final LearningContentRepository contentRepository;

    public LearningExperienceController(LearningExperienceService service,
                                        CodeLabService codeLabService,
                                        LearningContentRepository contentRepository) {
        this.service = service;
        this.codeLabService = codeLabService;
        this.contentRepository = contentRepository;
    }

    @GetMapping("/courses/{courseId}/experience")
    public Result experience(@PathVariable Integer courseId, @RequestParam(required = false) Integer classId) {
        return Result.success(service.courseExperience(courseId, classId));
    }

    @GetMapping("/learning-content/templates")
    public Result templates() {
        return Result.success(contentRepository.templateLibrary());
    }

    @GetMapping("/learning-content/templates/{key}")
    public Result template(@PathVariable String key) {
        try {
            return Result.success(contentRepository.templateByKey(key));
        } catch (IllegalArgumentException e) {
            return Result.error("404", e.getMessage());
        }
    }

    @PostMapping("/code-lab/run")
    public Result runCode(@RequestBody CodeLabRunRequest request) {
        try {
            return Result.success(codeLabService.run(request));
        } catch (IllegalArgumentException e) {
            return Result.error("400", e.getMessage());
        } catch (IllegalStateException e) {
            return Result.error("500", e.getMessage());
        }
    }
}

