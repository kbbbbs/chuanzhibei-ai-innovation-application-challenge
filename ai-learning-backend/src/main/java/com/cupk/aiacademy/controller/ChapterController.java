package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.ApiResponse;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.pojo.Chapter;
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
    public ApiResponse<Page<Chapter>> pages(@RequestParam(required = false) Integer courseId,
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
        return ApiResponse.success(page);
    }

    @GetMapping("/courses/{courseId}/chapters")
    public ApiResponse<Object> listByCourse(@PathVariable Integer courseId) {
        if (courseMapper.selectById(courseId) == null) {
            throw new BusinessException("课程不存在");
        }
        QueryWrapper<Chapter> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("course_id", courseId).orderByAsc("sort_order").orderByAsc("id");
        return ApiResponse.success(chapterMapper.selectList(queryWrapper));
    }

    @GetMapping("/chapters/{id}")
    public ApiResponse<Chapter> selectById(@PathVariable Integer id) {
        Chapter chapter = chapterMapper.selectById(id);
        if (chapter == null) {
            throw new BusinessException("章节不存在");
        }
        return ApiResponse.success(chapter);
    }

    @PostMapping("/chapters")
    public ApiResponse<Void> add(@RequestBody Chapter chapter) {
        checkChapter(chapter);
        LocalDateTime now = LocalDateTime.now();
        chapter.setTitle(chapter.getTitle().trim());
        fixSortOrder(chapter);
        chapter.setCreatedAt(now);
        chapter.setUpdatedAt(now);
        int row = chapterMapper.insert(chapter);
        if (row <= 0) {
            throw new BusinessException("添加失败");
        }
        return ApiResponse.success(null);
    }

    @PutMapping("/chapters/{id}")
    public ApiResponse<Void> update(@PathVariable Integer id, @RequestBody Chapter chapter) {
        if (chapterMapper.selectById(id) == null) {
            throw new BusinessException("章节不存在");
        }
        checkChapter(chapter);
        chapter.setId(id);
        chapter.setTitle(chapter.getTitle().trim());
        fixSortOrder(chapter);
        chapter.setUpdatedAt(LocalDateTime.now());
        int row = chapterMapper.updateById(chapter);
        if (row <= 0) {
            throw new BusinessException("更新失败");
        }
        return ApiResponse.success(null);
    }

    @DeleteMapping("/chapters/{id}")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        if (chapterMapper.selectById(id) == null) {
            throw new BusinessException("章节不存在");
        }
        int row = chapterMapper.deleteById(id);
        if (row <= 0) {
            throw new BusinessException("删除失败");
        }
        return ApiResponse.success(null);
    }

    private void checkChapter(Chapter chapter) {
        if (chapter == null) {
            throw new BusinessException("章节信息不能为空");
        }
        if (chapter.getCourseId() == null || courseMapper.selectById(chapter.getCourseId()) == null) {
            throw new BusinessException("课程不存在");
        }
        if (chapter.getTitle() == null || chapter.getTitle().trim().isEmpty()) {
            throw new BusinessException("章节名称不能为空");
        }
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
