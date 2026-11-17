package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.LessonDto;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.pojo.Chapter;
import com.cupk.aiacademy.pojo.Lesson;
import com.cupk.aiacademy.service.CourseResourceService;
import com.cupk.aiacademy.service.PermissionService;
import com.cupk.aiacademy.videoresource.VideoResourceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
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
    private PermissionService permissionService;

    @Autowired
    private VideoResourceService videoResourceService;

    @Autowired
    private AuthUtils authUtils;

    @Autowired
    private ObjectMapper objectMapper;

    @GetMapping("/lessons")
    public Result pages(@RequestParam(required = false) Integer chapterId,
                        @RequestParam(defaultValue = "") String title,
                        @RequestParam(defaultValue = "1") Integer pageNum,
                        @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<Lesson> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize));
        QueryWrapper<Lesson> queryWrapper = new QueryWrapper<>();
        queryWrapper.inSql("chapter_id", "SELECT id FROM t_chapter WHERE status = 1");
        if (chapterId != null && chapterId > 0) {
            requireChapterAccess(chapterId, false);
            queryWrapper.eq("chapter_id", chapterId);
        } else {
            var user = permissionService.currentUser();
            if (permissionService.isTeacher(user)) queryWrapper.inSql("chapter_id", "SELECT ch.id FROM t_chapter ch JOIN t_course c ON c.id=ch.course_id WHERE c.teacher_id=" + user.getUserId());
            else if (permissionService.isStudent(user)) {
                var courseIds = permissionService.studentCourseIds(user.getUserId());
                if (courseIds.isEmpty()) return Result.success(page);
                String ids = courseIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                queryWrapper.inSql("chapter_id", "SELECT id FROM t_chapter WHERE course_id IN (" + ids + ")");
            } else throw new com.cupk.aiacademy.common.ForbiddenException("无权访问课程内容");
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
        requireChapterAccess(chapterId, false);
        if (!isActiveChapter(chapterMapper.selectById(chapterId))) {
            return Result.error("404", "章节不存在");
        }
        QueryWrapper<Lesson> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("chapter_id", chapterId).orderByAsc("sort_order").orderByAsc("id");
        return Result.success(lessonMapper.selectList(queryWrapper));
    }

    @GetMapping("/lessons/{id}")
    public Result selectById(@PathVariable Integer id) {
        Lesson lesson = lessonMapper.selectById(id);
        if (lesson != null) requireChapterAccess(lesson.getChapterId(), false);
        return lesson == null || !isActiveChapter(chapterMapper.selectById(lesson.getChapterId()))
                ? Result.error("404", "课时不存在")
                : Result.success(lesson);
    }

    @GetMapping("/lessons/{lessonId}/playback")
    public Result playback(@PathVariable Integer lessonId) {
        return Result.success(videoResourceService.getLessonPlayback(lessonId));
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
    public Result add(@Valid @RequestBody LessonDto request) {
        Lesson lesson = request.toEntity();
        requireChapterAccess(lesson.getChapterId(), true);
        Result checkResult = checkLesson(lesson);
        if (checkResult != null) {
            return checkResult;
        }
        LocalDateTime now = LocalDateTime.now();
        lesson.setTitle(lesson.getTitle().trim());
        normalizeMediaFields(lesson);
        fixSortOrder(lesson);
        if (lesson.getSortOrder() == null) lesson.setSortOrder(nextLessonPosition(lesson.getChapterId()));
        lesson.setCreatedAt(now);
        lesson.setUpdatedAt(now);
        int row = lessonMapper.insert(lesson);
        return row > 0 ? Result.success(lesson) : Result.error();
    }

    @PutMapping("/lessons/{id}")
    public Result update(@PathVariable Integer id, @Valid @RequestBody LessonDto request) {
        Lesson lesson = request.toEntity();
        Lesson existing = lessonMapper.selectById(id);
        if (existing == null) {
            return Result.error("404", "课时不存在");
        }
        requireChapterAccess(existing.getChapterId(), true);
        requireChapterAccess(lesson.getChapterId(), true);
        Result checkResult = checkLesson(lesson);
        if (checkResult != null) {
            return checkResult;
        }
        lesson.setId(id);
        lesson.setTitle(lesson.getTitle().trim());
        normalizeMediaFields(lesson);
        fixSortOrder(lesson);
        lesson.setUpdatedAt(LocalDateTime.now());
        int row = lessonMapper.updateById(lesson);
        // updateById skips nulls, so a lesson that stops being a video must drop its binding explicitly;
        // otherwise the old download status and player keep showing.
        if (row > 0 && existing.getVideoResourceId() != null && !isVideoType(lesson.getResourceType())) {
            lessonMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Lesson>()
                    .eq("id", id).set("video_resource_id", null).set("video_url", ""));
        }
        return row > 0 ? Result.success(lessonMapper.selectById(id)) : Result.error();
    }

    @DeleteMapping("/lessons/{id}")
    public Result delete(@PathVariable Integer id) {
        Lesson existing = lessonMapper.selectById(id);
        if (existing == null) {
            return Result.error("404", "课时不存在");
        }
        requireChapterAccess(existing.getChapterId(), true);
        int row = lessonMapper.deleteById(id);
        return row > 0 ? Result.success() : Result.error();
    }

    /** Types the course workspace, course import, preparation publishing and the learning page understand. */
    private static final java.util.Set<String> RESOURCE_TYPES = java.util.Set.of("markdown", "article",
            "interactive_json_builtin", "interactive_json_upload", "video_url", "video_link", "video_file",
            "ppt", "pptx", "pdf", "file_link");
    private static final java.util.Set<String> TEXT_TYPES = java.util.Set.of("markdown", "article");

    private static boolean isVideoType(String type) {
        return "video_url".equals(type) || "video_link".equals(type) || "video_file".equals(type);
    }

    private Integer nextLessonPosition(Integer chapterId) {
        return lessonMapper.selectList(new QueryWrapper<Lesson>().eq("chapter_id", chapterId)).stream()
                .map(Lesson::getSortOrder).filter(java.util.Objects::nonNull).max(Integer::compare).orElse(0) + 1;
    }

    private void requireChapterAccess(Integer chapterId, boolean write) {
        Chapter chapter = chapterId == null ? null : chapterMapper.selectById(chapterId);
        if (!isActiveChapter(chapter)) throw new com.cupk.aiacademy.common.BusinessException("章节不存在");
        if (write) permissionService.requireCourseContentTeacher(chapter.getCourseId());
        else permissionService.requireCourseContentRead(chapter.getCourseId());
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
        if (lesson.getChapterId() == null || !isActiveChapter(chapterMapper.selectById(lesson.getChapterId()))) {
            return Result.error("404", "章节不存在");
        }
        if (lesson.getTitle() == null || lesson.getTitle().trim().isEmpty()) {
            return Result.error("400", "课时名称不能为空");
        }
        if (lesson.getResourceType() != null && !RESOURCE_TYPES.contains(lesson.getResourceType())) {
            return Result.error("400", "不支持的课时资源类型：" + lesson.getResourceType());
        }
        if (TEXT_TYPES.contains(lesson.getResourceType()) && (lesson.getContent() == null || lesson.getContent().isBlank())) {
            return Result.error("400", "请填写课时正文");
        }
        if (isInteractiveJsonType(lesson.getResourceType()) && !isValidLessonPageJson(lesson.getContent())) {
            return Result.error("400", "互动课本 JSON 需要包含 blocks 数组");
        }
        return null;
    }

    private void normalizeMediaFields(Lesson lesson) {
        String resourceType = lesson.getResourceType();
        if ("video_file".equals(resourceType)) {
            lesson.setVideoUrl(lesson.getResourceUrl());
            lesson.setVideoResourceId(null);
            return;
        }
        if ("video_url".equals(resourceType)) {
            lesson.setVideoUrl("");
            lesson.setVideoResourceId(null);
            return;
        }
        lesson.setVideoUrl("");
        lesson.setVideoResourceId(null);
    }

    private void fixSortOrder(Lesson lesson) {
        if (lesson.getSortOrder() == null || lesson.getSortOrder() <= 0) {
            lesson.setSortOrder(null);
        }
    }

    private boolean isInteractiveJsonType(String resourceType) {
        return "interactive_json_builtin".equals(resourceType) || "interactive_json_upload".equals(resourceType);
    }

    private boolean isValidLessonPageJson(String content) {
        if (content == null || content.isBlank()) return false;
        try {
            JsonNode node = objectMapper.readTree(content);
            JsonNode blocks = node.isArray() ? node : node.get("blocks");
            return blocks != null && blocks.isArray() && !blocks.isEmpty();
        } catch (JsonProcessingException e) {
            return false;
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
