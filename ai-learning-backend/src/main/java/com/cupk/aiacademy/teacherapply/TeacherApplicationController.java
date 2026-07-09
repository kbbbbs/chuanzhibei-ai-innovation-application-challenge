package com.cupk.aiacademy.teacherapply;

import com.cupk.aiacademy.common.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/teacher-applications")
public class TeacherApplicationController {
    private final TeacherApplicationService service;

    public TeacherApplicationController(TeacherApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public Result apply(@Valid @RequestBody TeacherApplicationRequest request) {
        return Result.success(service.apply(request), "申请已提交，请等待管理员审核");
    }

    @GetMapping
    public Result list(@RequestParam(required = false) Integer status,
                       @RequestParam(defaultValue = "1") Integer pageNum,
                       @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(service.list(status, pageNum, pageSize));
    }

    @PostMapping("/{id}/review")
    public Result review(@PathVariable Integer id, @Valid @RequestBody TeacherApplicationReviewRequest request) {
        return Result.success(service.review(id, request));
    }
}
