package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.pojo.Lesson;
import com.cupk.aiacademy.service.CourseResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
public class LessonController {
    @Autowired
    private LessonMapper lessonMapper;

    @Autowired
    private ChapterMapper chapterMapper;

    @Autowired
    private CourseResourceService courseResourceService;

    @Autowired
    private AuthUtils authUtils;

    @GetMapping("/lessons")
    public Result pages(@RequestParam(required = false) Integer chapterId,
                        @RequestParam(defaultValue = "") String title,
                        @RequestParam(defaultValue = "1") Integer pageNum,
                        @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<Lesson> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize));
        QueryWrapper<Lesson> queryWrapper = new QueryWrapper<>();
        if (chapterId != null && chapterId > 0) {
            queryWrapper.eq("chapter_id", chapterId);
        }
        if (title != null && !title.trim().isEmpty()) {
            queryWrapper.like("title", title.trim());
        }
        queryWrapper.orderByAsc("chapter_id").orderByAsc("sort_order").orderByAsc("id");
        lessonMapper.selectPage(page, queryWrapper);
        return Result.success(page);
    }

    @GetMapping("/chapters/{chapterId}/lessons")
    public Result listByChapter(@PathVariable Integer chapterId) {
        if (chapterMapper.selectById(chapterId) == null) {
            return Result.error("404", "章节不存在");
        }
        QueryWrapper<Lesson> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("chapter_id", chapterId).orderByAsc("sort_order").orderByAsc("id");
        return Result.success(lessonMapper.selectList(queryWrapper));
    }

    @GetMapping("/lessons/{id}")
    public Result selectById(@PathVariable Integer id) {
        Lesson lesson = lessonMapper.selectById(id);
        return lesson == null ? Result.error("404", "课时不存在") : Result.success(lesson);
    }

    @GetMapping("/lessons/{lessonId}/my-progress")
    public Result myProgress(@PathVariable Integer lessonId,
                             @RequestHeader(value = "Authorization", required = false) String authorization,
                             @RequestParam(required = false) Integer classId,
                             @RequestParam(defaultValue = "1") Integer pageNum,
                             @RequestParam(defaultValue = "10") Integer pageSize) {
        AuthUtils.LoginUser loginUser = parseLoginUser(authorization);
        if (loginUser == null) {
            return Result.error("401", "登录状态已失效");
        }
        return Result.success(courseResourceService.listLessonProgress(lessonId, classId, loginUser, pageNum, pageSize));
    }

    @PostMapping("/lessons")
    public Result add(@RequestBody Lesson lesson) {
        Result checkResult = checkLesson(lesson);
        if (checkResult != null) {
            return checkResult;
        }
        LocalDateTime now = LocalDateTime.now();
        lesson.setTitle(lesson.getTitle().trim());
        fixSortOrder(lesson);
        lesson.setCreatedAt(now);
        lesson.setUpdatedAt(now);
        int row = lessonMapper.insert(lesson);
        return row > 0 ? Result.success() : Result.error();
    }

    @PutMapping("/lessons/{id}")
    public Result update(@PathVariable Integer id, @RequestBody Lesson lesson) {
        if (lessonMapper.selectById(id) == null) {
            return Result.error("404", "课时不存在");
        }
        Result checkResult = checkLesson(lesson);
        if (checkResult != null) {
            return checkResult;
        }
        lesson.setId(id);
        lesson.setTitle(lesson.getTitle().trim());
        fixSortOrder(lesson);
        lesson.setUpdatedAt(LocalDateTime.now());
        int row = lessonMapper.updateById(lesson);
        return row > 0 ? Result.success() : Result.error();
    }

    @DeleteMapping("/lessons/{id}")
    public Result delete(@PathVariable Integer id) {
        if (lessonMapper.selectById(id) == null) {
            return Result.error("404", "课时不存在");
        }
        int row = lessonMapper.deleteById(id);
        return row > 0 ? Result.success() : Result.error();
    }

    private AuthUtils.LoginUser parseLoginUser(String authorization) {
        try {
            return authUtils.requireLogin(authorization);
        } catch (Exception e) {
            return null;
        }
    }

    private Result checkLesson(Lesson lesson) {
        if (lesson == null) {
            return Result.error("400", "课时信息不能为空");
        }
        if (lesson.getChapterId() == null || chapterMapper.selectById(lesson.getChapterId()) == null) {
            return Result.error("404", "章节不存在");
        }
        if (lesson.getTitle() == null || lesson.getTitle().trim().isEmpty()) {
            return Result.error("400", "课时名称不能为空");
        }
        return null;
    }

    private void fixSortOrder(Lesson lesson) {
        if (lesson.getSortOrder() == null || lesson.getSortOrder() <= 0) {
            lesson.setSortOrder(null);
        }
    }

    private int fixPageNum(Integer pageNum) {
        return pageNum == null || pageNum <= 0 ? 1 : pageNum;
    }

    private int fixPageSize(Integer pageSize) {
        return pageSize == null || pageSize <= 0 ? 10 : pageSize;
    }
}
