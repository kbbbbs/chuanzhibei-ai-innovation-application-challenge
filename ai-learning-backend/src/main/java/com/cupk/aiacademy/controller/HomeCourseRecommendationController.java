package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.HomeCourseRecommendationRequest;
import com.cupk.aiacademy.service.HomeCourseRecommendationService;
import com.cupk.aiacademy.service.PermissionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/home-course-recommendations")
public class HomeCourseRecommendationController {
    private final HomeCourseRecommendationService recommendationService;
    private final PermissionService permissionService;

    public HomeCourseRecommendationController(HomeCourseRecommendationService recommendationService,
                                              PermissionService permissionService) {
        this.recommendationService = recommendationService;
        this.permissionService = permissionService;
    }

    @GetMapping
    public Result listPublished() {
        return Result.success(recommendationService.listPublished());
    }

    @PostMapping("/{courseId}/join")
    public Result joinPublicCourse(@PathVariable Integer courseId) {
        return Result.success(recommendationService.joinPublicCourse(courseId));
    }

    @GetMapping("/admin")
    public Result listForAdmin() {
        permissionService.requireAdmin();
        return Result.success(recommendationService.listForAdmin());
    }

    @PostMapping
    public Result create(@Valid @RequestBody HomeCourseRecommendationRequest request) {
        permissionService.requireAdmin();
        return Result.success(recommendationService.create(request));
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable Integer id,
                         @Valid @RequestBody HomeCourseRecommendationRequest request) {
        permissionService.requireAdmin();
        return Result.success(recommendationService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id) {
        permissionService.requireAdmin();
        recommendationService.delete(id);
        return Result.success();
    }
}
