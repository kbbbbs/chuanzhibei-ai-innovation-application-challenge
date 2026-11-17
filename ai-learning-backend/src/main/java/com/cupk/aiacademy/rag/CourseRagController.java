package com.cupk.aiacademy.rag;

import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.common.UserContext;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rag/courses")
public class CourseRagController {
    private final CourseRagService courses;
    public CourseRagController(CourseRagService courses) { this.courses = courses; }

    @GetMapping("/{courseId}/documents")
    public Result documents(@PathVariable Integer courseId) {
        Integer user = UserContext.getCurrentUserId();
        if (user == null) throw new RagAccessException("401","请先登录");
        return Result.success(courses.documents(courseId,user));
    }
}
