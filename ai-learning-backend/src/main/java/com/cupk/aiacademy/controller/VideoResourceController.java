package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.VideoUrlSubmitRequest;
import com.cupk.aiacademy.videoresource.VideoResourceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VideoResourceController {
    private final VideoResourceService videoResourceService;

    public VideoResourceController(VideoResourceService videoResourceService) {
        this.videoResourceService = videoResourceService;
    }

    @PostMapping("/video-resources/url")
    public Result submitUrl(@Valid @RequestBody VideoUrlSubmitRequest request) {
        return Result.success(videoResourceService.submitUrl(request.getLessonId(), request.getUrl()));
    }

    @GetMapping("/video-resources/{id}")
    public Result getById(@PathVariable Long id) {
        return Result.success(videoResourceService.getResource(id));
    }

    @PostMapping("/video-resources/{id}/retry")
    public Result retry(@PathVariable Long id) {
        return Result.success(videoResourceService.retry(id));
    }
}
