package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.ChapterDto;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.pojo.Chapter;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
public class ChapterController {
    @Autowired
    private ChapterMapper chapterMapper;

    @Autowired
    private CourseMapper courseMapper;

    @GetMapping("/chapters")
    public Result pages(@RequestParam(required = false) Integer courseId,
                        @RequestParam(defaultValue = "") String title,
                        @RequestParam(defaultValue = "1") Integer pageNum,
                        @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<Chapter> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize));
        QueryWrapper<Chapter> queryWrapper = new QueryWrapper<>();
        if (courseId != null && courseId > 0) {
            queryWrapper.eq("course_id", courseId);
        }
        if (title != null && !title.trim().isEmpty()) {
            queryWrapper.like("title", title.trim());
        }
        queryWrapper.orderByAsc("course_id").orderByAsc("sort_order").orderByAsc("id");
        chapterMapper.selectPage(page, queryWrapper);
        return Result.success(page);
    }

    @GetMapping("/courses/{courseId}/chapters")
    public Result listByCourse(@PathVariable Integer courseId) {
        if (courseMapper.selectById(courseId) == null) {
            return Result.error("404", "课程不存在");
        }
        QueryWrapper<Chapter> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("course_id", courseId).orderByAsc("sort_order").orderByAsc("id");
        return Result.success(chapterMapper.selectList(queryWrapper));
    }

    @GetMapping("/chapters/{id}")
    public Result selectById(@PathVariable Integer id) {
        Chapter chapter = chapterMapper.selectById(id);
        return chapter == null ? Result.error("404", "章节不存在") : Result.success(chapter);
    }

    @PostMapping("/chapters")
    public Result add(@Valid @RequestBody ChapterDto request) {
        Chapter chapter = request.toEntity();
        Result checkResult = checkChapter(chapter);
        if (checkResult != null) {
            return checkResult;
        }
        LocalDateTime now = LocalDateTime.now();
        chapter.setTitle(chapter.getTitle().trim());
        fixSortOrder(chapter);
        chapter.setCreatedAt(now);
        chapter.setUpdatedAt(now);
        int row = chapterMapper.insert(chapter);
        return row > 0 ? Result.success() : Result.error();
    }

    @PutMapping("/chapters/{id}")
    public Result update(@PathVariable Integer id, @Valid @RequestBody ChapterDto request) {
        Chapter chapter = request.toEntity();
        if (chapterMapper.selectById(id) == null) {
            return Result.error("404", "章节不存在");
        }
        Result checkResult = checkChapter(chapter);
        if (checkResult != null) {
            return checkResult;
        }
        chapter.setId(id);
        chapter.setTitle(chapter.getTitle().trim());
        fixSortOrder(chapter);
        chapter.setUpdatedAt(LocalDateTime.now());
        int row = chapterMapper.updateById(chapter);
        return row > 0 ? Result.success() : Result.error();
    }

    @DeleteMapping("/chapters/{id}")
    public Result delete(@PathVariable Integer id) {
        if (chapterMapper.selectById(id) == null) {
            return Result.error("404", "章节不存在");
        }
        int row = chapterMapper.deleteById(id);
        return row > 0 ? Result.success() : Result.error();
    }

    private Result checkChapter(Chapter chapter) {
        if (chapter == null) {
            return Result.error("400", "章节信息不能为空");
        }
        if (chapter.getCourseId() == null || courseMapper.selectById(chapter.getCourseId()) == null) {
            return Result.error("404", "课程不存在");
        }
        if (chapter.getTitle() == null || chapter.getTitle().trim().isEmpty()) {
            return Result.error("400", "章节名称不能为空");
        }
        return null;
    }

    private void fixSortOrder(Chapter chapter) {
        if (chapter.getSortOrder() == null || chapter.getSortOrder() <= 0) {
            chapter.setSortOrder(null);
        }
    }

    private int fixPageNum(Integer pageNum) {
        return pageNum == null || pageNum <= 0 ? 1 : pageNum;
    }

    private int fixPageSize(Integer pageSize) {
        return pageSize == null || pageSize <= 0 ? 10 : pageSize;
    }
}
