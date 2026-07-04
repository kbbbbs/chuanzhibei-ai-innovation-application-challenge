package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.service.ArxivSearchService;
import com.cupk.aiacademy.service.OpenAlexSearchService;
import com.cupk.aiacademy.service.PaperLocalService;
import com.cupk.aiacademy.service.PaperRecommendService;
import com.cupk.aiacademy.service.PaperSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/paper")
public class PaperSearchController {

    @Autowired
    private PaperSearchService paperSearchService;

    @Autowired
    private PaperLocalService paperLocalService;

    @Autowired
    private ArxivSearchService arxivSearchService;

    @Autowired
    private OpenAlexSearchService openAlexSearchService;

    @Autowired
    private PaperRecommendService paperRecommendService;

    @Autowired
    private CourseMapper courseMapper;

    @GetMapping("/search")
    public Result search(@RequestParam String keyword,
                         @RequestParam(defaultValue = "local") String source,
                         @RequestParam(defaultValue = "1") Integer page,
                         @RequestParam(defaultValue = "10") Integer size) {
        return switch (source) {
            case "arxiv" -> arxivSearchService.search(keyword, page, size);
            case "openalex" -> openAlexSearchService.search(keyword, page, size);
            case "semantic_scholar" -> paperSearchService.searchPapers(keyword, page, size);
            default -> paperLocalService.search(keyword, page, size);
        };
    }

    @GetMapping("/detail/{paperId}")
    public Result detail(@PathVariable String paperId) {
        return paperLocalService.getDetail(paperId);
    }

    @PostMapping("/import")
    public Result importPapers(@RequestParam String keyword,
                                @RequestParam(defaultValue = "20") Integer maxResults) {
        return paperLocalService.importFromArxiv(keyword, maxResults);
    }

    @GetMapping("/course-directions")
    public Result courseDirections(@RequestParam String keyword) {
        return paperLocalService.getCourseDirections(keyword);
    }

    @GetMapping("/recommend")
    public Result recommend(@RequestParam(required = false) Integer studentId,
                            @RequestParam(required = false) Integer teacherId) {
        List<Course> courses;
        if (studentId != null && studentId > 0) {
            courses = courseMapper.selectCoursesByStudentId(studentId);
        } else if (teacherId != null && teacherId > 0) {
            courses = courseMapper.selectCoursesByTeacherId(teacherId);
        } else {
            return Result.success();
        }
        return paperRecommendService.recommend(courses, 5, 20);
    }
}
