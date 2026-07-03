package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.ApiResponse;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.pojo.Lesson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
public class LessonController {
    @Autowired
    private LessonMapper lessonMapper;

    @Autowired
    private ChapterMapper chapterMapper;

    @GetMapping("/lessons")
    public ApiResponse<Page<Lesson>> pages(@RequestParam(required = false) Integer chapterId,
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
        return ApiResponse.success(page);
    }

    @GetMapping("/chapters/{chapterId}/lessons")
    public ApiResponse<Object> listByChapter(@PathVariable Integer chapterId) {
        if (chapterMapper.selectById(chapterId) == null) {
            throw new BusinessException("章节不存在");
        }
        QueryWrapper<Lesson> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("chapter_id", chapterId).orderByAsc("sort_order").orderByAsc("id");
        return ApiResponse.success(lessonMapper.selectList(queryWrapper));
    }

    @GetMapping("/lessons/{id}")
    public ApiResponse<Lesson> selectById(@PathVariable Integer id) {
        Lesson lesson = lessonMapper.selectById(id);
        if (lesson == null) {
            throw new BusinessException("课时不存在");
        }
        return ApiResponse.success(lesson);
    }

    @PostMapping("/lessons")
    public ApiResponse<Void> add(@RequestBody Lesson lesson) {
        checkLesson(lesson);
        LocalDateTime now = LocalDateTime.now();
        lesson.setTitle(lesson.getTitle().trim());
        fixSortOrder(lesson);
        lesson.setCreatedAt(now);
        lesson.setUpdatedAt(now);
        int row = lessonMapper.insert(lesson);
        if (row <= 0) {
            throw new BusinessException("添加失败");
        }
        return ApiResponse.success(null);
    }

    @PutMapping("/lessons/{id}")
    public ApiResponse<Void> update(@PathVariable Integer id, @RequestBody Lesson lesson) {
        if (lessonMapper.selectById(id) == null) {
            throw new BusinessException("课时不存在");
        }
        checkLesson(lesson);
        lesson.setId(id);
        lesson.setTitle(lesson.getTitle().trim());
        fixSortOrder(lesson);
        lesson.setUpdatedAt(LocalDateTime.now());
        int row = lessonMapper.updateById(lesson);
        if (row <= 0) {
            throw new BusinessException("更新失败");
        }
        return ApiResponse.success(null);
    }

    @DeleteMapping("/lessons/{id}")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        if (lessonMapper.selectById(id) == null) {
            throw new BusinessException("课时不存在");
        }
        int row = lessonMapper.deleteById(id);
        if (row <= 0) {
            throw new BusinessException("删除失败");
        }
        return ApiResponse.success(null);
    }

    private void checkLesson(Lesson lesson) {
        if (lesson == null) {
            throw new BusinessException("课时信息不能为空");
        }
        if (lesson.getChapterId() == null || chapterMapper.selectById(lesson.getChapterId()) == null) {
            throw new BusinessException("章节不存在");
        }
        if (lesson.getTitle() == null || lesson.getTitle().trim().isEmpty()) {
            throw new BusinessException("课时名称不能为空");
        }
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
