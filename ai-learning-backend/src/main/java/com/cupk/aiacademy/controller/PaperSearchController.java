package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.dto.PaperMarkUpdateRequest;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.service.ArxivSearchService;
import com.cupk.aiacademy.service.OpenAlexSearchService;
import com.cupk.aiacademy.service.PaperLocalService;
import com.cupk.aiacademy.service.PaperMarkGenerationService;
import com.cupk.aiacademy.service.PaperMarkService;
import com.cupk.aiacademy.service.PaperRecommendService;
import com.cupk.aiacademy.service.PaperSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private PaperMarkService paperMarkService;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private PaperMarkGenerationService paperMarkGenerationService;

    @GetMapping("/search")
    public Result search(@RequestParam String keyword,
                         @RequestParam(defaultValue = "local") String source,
                         @RequestParam(defaultValue = "1") Integer page,
                         @RequestParam(defaultValue = "10") Integer size,
                         @RequestParam(required = false) String author,
                         @RequestParam(required = false) String venue,
                         @RequestParam(required = false) Integer yearFrom,
                         @RequestParam(required = false) Integer yearTo,
                         @RequestParam(required = false) Integer minCitations,
                         @RequestParam(required = false, defaultValue = "relevance") String sortBy,
                         @RequestParam(required = false) Boolean openAccessOnly) {
        return switch (source) {
            case "arxiv" -> arxivSearchService.search(keyword, page, size, sortBy);
            case "openalex" -> openAlexSearchService.search(keyword, page, size, sortBy);
            case "semantic_scholar" -> paperSearchService.searchPapers(keyword, page, size, sortBy);
            default -> paperLocalService.search(keyword, page, size, author, venue, yearFrom, yearTo, minCitations, sortBy, openAccessOnly);
        };
    }

    @GetMapping("/detail/{paperId}")
    public Result detail(@PathVariable String paperId,
                         @RequestParam(required = false, defaultValue = "local") String source) {
        Result local = paperLocalService.getDetail(paperId);
        if ("200".equals(local.getCode())) {
            return local;
        }
        if ("semantic_scholar".equalsIgnoreCase(source)) {
            return paperSearchService.getPaperDetail(paperId);
        }
        return local;
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

    @GetMapping("/marks")
    public Result listMarks(@RequestParam(required = false) String keyword,
                            @RequestParam(defaultValue = "1") Integer page,
                            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(paperMarkService.listMarks(keyword, page, size));
    }

    @GetMapping("/marks/batch")
    public Result getMarks(@RequestParam String paperIds) {
        List<String> ids = Arrays.stream(paperIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        return Result.success(paperMarkService.getMarks(ids));
    }

    @GetMapping("/marks/{paperId}")
    public Result getMark(@PathVariable String paperId) {
        return Result.success(paperMarkService.getMark(paperId));
    }

    @PutMapping("/marks/{paperId}")
    public Result saveMark(@PathVariable String paperId,
                           @RequestBody(required = false) PaperMarkUpdateRequest request) {
        return Result.success(paperMarkService.saveMark(paperId, request));
    }

    @DeleteMapping("/marks/{paperId}")
    public Result deleteMark(@PathVariable String paperId) {
        paperMarkService.deleteMark(paperId);
        return Result.success();
    }

    @PostMapping("/generate-marks")
    public Result generateMarks() {
        Integer userId = UserContext.getCurrentUserId();
        String roleCode = UserContext.getCurrentRoleCode();
        if (userId == null) {
            return Result.error("请先登录");
        }
        return Result.success(paperMarkGenerationService.generateForUser(userId, roleCode));
    }

    @PostMapping("/generate-marks-batch")
    public Result generateMarksBatch() {
        String roleCode = UserContext.getCurrentRoleCode();
        if (!"admin".equals(roleCode)) {
            return Result.error("仅管理员可执行批量生成");
        }
        Map<String, Object> result = paperMarkGenerationService.batchGenerateAllUsers();
        return Result.success(result);
    }
}
