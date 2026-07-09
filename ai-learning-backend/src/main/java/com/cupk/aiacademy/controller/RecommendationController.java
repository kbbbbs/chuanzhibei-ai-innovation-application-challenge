package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.service.RecommendationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {
    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/my")
    public Result myRecommendations(@RequestParam(defaultValue = "all") String type,
                                    @RequestParam(defaultValue = "24") Integer limit) {
        return Result.success(recommendationService.recommendForCurrentUser(type, limit));
    }
}
