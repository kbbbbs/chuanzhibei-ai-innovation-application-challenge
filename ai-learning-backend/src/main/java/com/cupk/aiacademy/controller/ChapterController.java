package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.ChapterDto;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.pojo.Chapter;
import com.cupk.aiacademy.service.PermissionService;
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

    @Autowired
    private PermissionService permissionService;

    @GetMapping("/chapters")
    public Result pages(@RequestParam(required = false) Integer courseId,
                        @RequestParam(defaultValue = "") String title,
                        @RequestParam(defaultValue = "1") Integer pageNum,
                        @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<Chapter> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize));
        QueryWrapper<Chapter> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", Chapter.STATUS_NORMAL);
        if (courseId != null && courseId > 0) {
            permissionService.requireCourseContentRead(courseId);
            queryWrapper.eq("course_id", courseId);
        } else {
            var user = permissionService.currentUser();
            if (permissionService.isTeacher(user)) queryWrapper.inSql("course_id", "SELECT id FROM t_course WHERE teacher_id=" + user.getUserId());
            else if (permissionService.isStudent(user)) {
                var courseIds = permissionService.studentCourseIds(user.getUserId());
                if (courseIds.isEmpty()) return Result.success(page);
                queryWrapper.in("course_id", courseIds);
            } else throw new com.cupk.aiacademy.common.ForbiddenException("无权访问课程内容");
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
        permissionService.requireCourseContentRead(courseId);
        if (courseMapper.selectById(courseId) == null) {
            return Result.error("404", "课程不存在");
        }
        QueryWrapper<Chapter> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("course_id", courseId)
                .eq("status", Chapter.STATUS_NORMAL)
                .orderByAsc("sort_order").orderByAsc("id");
        return Result.success(chapterMapper.selectList(queryWrapper));
    }

    @GetMapping("/chapters/{id}")
    public Result selectById(@PathVariable Integer id) {
        Chapter chapter = chapterMapper.selectById(id);
        if (isActiveChapter(chapter)) permissionService.requireCourseContentRead(chapter.getCourseId());
        return isActiveChapter(chapter) ? Result.success(chapter) : Result.error("404", "章节不存在");
    }

    @PostMapping("/chapters")
    public Result add(@Valid @RequestBody ChapterDto request) {
        Chapter chapter = request.toEntity();
        permissionService.requireCourseContentTeacher(chapter.getCourseId());
        Result checkResult = checkChapter(chapter);
        if (checkResult != null) {
            return checkResult;
        }
        LocalDateTime now = LocalDateTime.now();
        chapter.setTitle(chapter.getTitle().trim());
        fixSortOrder(chapter);
        if (chapter.getSortOrder() == null) {
            // A new chapter without a position goes after the existing ones instead of sharing position 1.
            chapter.setSortOrder(chapterMapper.selectList(new QueryWrapper<Chapter>().eq("course_id", chapter.getCourseId())
                    .eq("status", Chapter.STATUS_NORMAL)).stream().map(Chapter::getSortOrder)
                    .filter(java.util.Objects::nonNull).max(Integer::compare).orElse(0) + 1);
        }
        chapter.setStatus(Chapter.STATUS_NORMAL);
        chapter.setCreatedAt(now);
        chapter.setUpdatedAt(now);
        int row = chapterMapper.insert(chapter);
        return row > 0 ? Result.success() : Result.error();
    }

    @PutMapping("/chapters/{id}")
    public Result update(@PathVariable Integer id, @Valid @RequestBody ChapterDto request) {
        Chapter chapter = request.toEntity();
        Chapter existing = chapterMapper.selectById(id);
        if (!isActiveChapter(existing)) {
            return Result.error("404", "章节不存在");
        }
        permissionService.requireCourseContentTeacher(existing.getCourseId());
        permissionService.requireCourseContentTeacher(chapter.getCourseId());
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
        Chapter existing = chapterMapper.selectById(id);
        if (!isActiveChapter(existing)) {
            return Result.error("404", "章节不存在");
        }
        permissionService.requireCourseContentTeacher(existing.getCourseId());
        Chapter chapter = new Chapter();
        chapter.setId(id);
        chapter.setStatus(Chapter.STATUS_DELETED);
        chapter.setUpdatedAt(LocalDateTime.now());
        int row = chapterMapper.updateById(chapter);
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

    private boolean isActiveChapter(Chapter chapter) {
        return chapter != null && Chapter.STATUS_NORMAL == defaultStatus(chapter.getStatus());
    }

    private int defaultStatus(Integer status) {
        return status == null ? Chapter.STATUS_NORMAL : status;
    }

    private int fixPageNum(Integer pageNum) {
        return pageNum == null || pageNum <= 0 ? 1 : pageNum;
    }

    private int fixPageSize(Integer pageSize) {
        return pageSize == null || pageSize <= 0 ? 10 : pageSize;
    }
}
