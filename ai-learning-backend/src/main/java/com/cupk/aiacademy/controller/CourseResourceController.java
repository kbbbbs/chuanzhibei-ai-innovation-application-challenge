package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.ClassStudentMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.LearningProgressMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.pojo.Chapter;
import com.cupk.aiacademy.pojo.ClassStudent;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.LearningProgress;
import com.cupk.aiacademy.pojo.Lesson;
import com.cupk.aiacademy.pojo.TeachingClass;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class CourseResourceController {
    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private ChapterMapper chapterMapper;

    @Autowired
    private LessonMapper lessonMapper;

    @Autowired
    private TeachingClassMapper teachingClassMapper;

    @Autowired
    private ClassStudentMapper classStudentMapper;

    @Autowired
    private LearningProgressMapper learningProgressMapper;

    @Autowired
    private AuthUtils authUtils;

    @GetMapping("/courses/{id}/resource-tree")
    public Result resourceTree(@PathVariable Integer id) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            return Result.error("404", "课程不存在");
        }

        QueryWrapper<Chapter> chapterQuery = new QueryWrapper<>();
        chapterQuery.eq("course_id", id).orderByAsc("sort_order").orderByAsc("id");
        List<Chapter> chapters = chapterMapper.selectList(chapterQuery);
        if (chapters.isEmpty()) {
            course.setChapters(chapters);
            return Result.success(course);
        }

        List<Integer> chapterIds = chapters.stream().map(Chapter::getId).collect(Collectors.toList());
        QueryWrapper<Lesson> lessonQuery = new QueryWrapper<>();
        lessonQuery.in("chapter_id", chapterIds)
                .orderByAsc("chapter_id")
                .orderByAsc("sort_order")
                .orderByAsc("id");
        List<Lesson> lessons = lessonMapper.selectList(lessonQuery);
        Map<Integer, List<Lesson>> lessonMap = lessons.stream()
                .collect(Collectors.groupingBy(Lesson::getChapterId, HashMap::new, Collectors.toList()));
        for (Chapter chapter : chapters) {
            chapter.setLessons(lessonMap.getOrDefault(chapter.getId(), new ArrayList<>()));
        }
        course.setChapters(chapters);
        return Result.success(course);
    }

    @PutMapping("/courses/{courseId}/chapters/sort")
    @Transactional
    public Result sortChapters(@PathVariable Integer courseId,
                               @RequestBody List<SortItem> items,
                               @RequestHeader(value = "Authorization", required = false) String authorization) {
        AuthUtils.LoginUser loginUser = parseLoginUser(authorization);
        if (loginUser == null) {
            return Result.error("401", "登录状态已失效");
        }

        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            return Result.error("404", "课程不存在");
        }
        Result permissionResult = checkCourseManagePermission(course, loginUser);
        if (permissionResult != null) {
            return permissionResult;
        }

        Result itemResult = checkSortItems(items);
        if (itemResult != null) {
            return itemResult;
        }

        QueryWrapper<Chapter> query = new QueryWrapper<>();
        query.eq("course_id", courseId);
        List<Chapter> chapters = chapterMapper.selectList(query);
        Set<Integer> chapterIds = chapters.stream().map(Chapter::getId).collect(Collectors.toSet());
        for (SortItem item : items) {
            if (!chapterIds.contains(item.getId())) {
                return Result.error("400", "章节不属于该课程");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        for (SortItem item : items) {
            Chapter chapter = new Chapter();
            chapter.setId(item.getId());
            chapter.setSortOrder(item.getSortOrder());
            chapter.setUpdatedAt(now);
            chapterMapper.updateById(chapter);
        }
        return Result.success();
    }

    @PutMapping("/chapters/{chapterId}/lessons/sort")
    @Transactional
    public Result sortLessons(@PathVariable Integer chapterId,
                              @RequestBody List<SortItem> items,
                              @RequestHeader(value = "Authorization", required = false) String authorization) {
        AuthUtils.LoginUser loginUser = parseLoginUser(authorization);
        if (loginUser == null) {
            return Result.error("401", "登录状态已失效");
        }

        Chapter chapter = chapterMapper.selectById(chapterId);
        if (chapter == null) {
            return Result.error("404", "章节不存在");
        }
        Course course = courseMapper.selectById(chapter.getCourseId());
        if (course == null) {
            return Result.error("404", "课程不存在");
        }
        Result permissionResult = checkCourseManagePermission(course, loginUser);
        if (permissionResult != null) {
            return permissionResult;
        }

        Result itemResult = checkSortItems(items);
        if (itemResult != null) {
            return itemResult;
        }

        QueryWrapper<Lesson> query = new QueryWrapper<>();
        query.eq("chapter_id", chapterId);
        List<Lesson> lessons = lessonMapper.selectList(query);
        Set<Integer> lessonIds = lessons.stream().map(Lesson::getId).collect(Collectors.toSet());
        for (SortItem item : items) {
            if (!lessonIds.contains(item.getId())) {
                return Result.error("400", "课时不属于该章节");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        for (SortItem item : items) {
            Lesson lesson = new Lesson();
            lesson.setId(item.getId());
            lesson.setSortOrder(item.getSortOrder());
            lesson.setUpdatedAt(now);
            lessonMapper.updateById(lesson);
        }
        return Result.success();
    }

    @PutMapping("/lessons/{lessonId}/progress")
    @Transactional
    public Result upsertLessonProgress(@PathVariable Integer lessonId,
                                       @RequestBody LessonProgressForm form,
                                       @RequestHeader(value = "Authorization", required = false) String authorization) {
        AuthUtils.LoginUser loginUser = parseLoginUser(authorization);
        if (loginUser == null) {
            return Result.error("401", "登录状态已失效");
        }
        if (!authUtils.isStudent(loginUser)) {
            return Result.error("403", "只有学生可以更新学习进度");
        }
        if (form == null || form.getClassId() == null || form.getClassId() <= 0) {
            return Result.error("400", "教学班不能为空");
        }
        Integer progressValue = form.getProgress() == null ? 0 : form.getProgress();
        Integer learnedSeconds = form.getLearnedSeconds() == null ? 0 : form.getLearnedSeconds();
        Integer lastPosition = form.getLastPosition() == null ? 0 : form.getLastPosition();
        if (progressValue < 0 || progressValue > 100) {
            return Result.error("400", "学习进度应在0到100之间");
        }
        if (learnedSeconds < 0 || lastPosition < 0) {
            return Result.error("400", "学习时长和播放位置不能小于0");
        }

        TeachingClass teachingClass = teachingClassMapper.selectById(form.getClassId());
        if (teachingClass == null) {
            return Result.error("404", "教学班不存在");
        }
        Lesson lesson = lessonMapper.selectById(lessonId);
        if (lesson == null) {
            return Result.error("404", "课时不存在");
        }
        Chapter chapter = chapterMapper.selectById(lesson.getChapterId());
        if (chapter == null || !teachingClass.getCourseId().equals(chapter.getCourseId())) {
            return Result.error("400", "课时不属于该教学班课程");
        }

        QueryWrapper<ClassStudent> classStudentQuery = new QueryWrapper<>();
        classStudentQuery.eq("class_id", form.getClassId())
                .eq("student_id", loginUser.getUserId())
                .eq("status", 1);
        if (classStudentMapper.selectCount(classStudentQuery) <= 0) {
            return Result.error("403", "学生未加入该教学班");
        }

        QueryWrapper<LearningProgress> progressQuery = new QueryWrapper<>();
        progressQuery.eq("class_id", form.getClassId())
                .eq("student_id", loginUser.getUserId())
                .eq("lesson_id", lessonId)
                .orderByDesc("id");
        List<LearningProgress> oldProgressList = learningProgressMapper.selectList(progressQuery);

        LocalDateTime now = LocalDateTime.now();
        LearningProgress progress;
        if (oldProgressList.isEmpty()) {
            progress = new LearningProgress();
            progress.setClassId(form.getClassId());
            progress.setStudentId(loginUser.getUserId());
            progress.setLessonId(lessonId);
            progress.setCreateTime(now);
        } else {
            progress = oldProgressList.get(0);
        }
        progress.setProgress(progressValue);
        progress.setLearnedSeconds(learnedSeconds);
        progress.setLastPosition(lastPosition);
        progress.setLastLearnTime(now);
        progress.setUpdateTime(now);

        if (progress.getId() == null) {
            learningProgressMapper.insert(progress);
        } else {
            learningProgressMapper.updateById(progress);
        }
        return Result.success(progress);
    }

    private AuthUtils.LoginUser parseLoginUser(String authorization) {
        try {
            return authUtils.requireLogin(authorization);
        } catch (Exception e) {
            return null;
        }
    }

    private Result checkCourseManagePermission(Course course, AuthUtils.LoginUser loginUser) {
        if (authUtils.isAdmin(loginUser)) {
            return null;
        }
        if (authUtils.isTeacher(loginUser) && loginUser.getUserId().equals(course.getTeacherId())) {
            return null;
        }
        return Result.error("403", "无权维护该课程资源");
    }

    private Result checkSortItems(List<SortItem> items) {
        if (items == null || items.isEmpty()) {
            return Result.error("400", "排序数据不能为空");
        }
        Set<Integer> ids = new HashSet<>();
        for (SortItem item : items) {
            if (item == null || item.getId() == null || item.getId() <= 0) {
                return Result.error("400", "排序数据的ID不能为空");
            }
            if (item.getSortOrder() == null || item.getSortOrder() <= 0) {
                return Result.error("400", "排序值必须大于0");
            }
            if (!ids.add(item.getId())) {
                return Result.error("400", "排序数据存在重复ID");
            }
        }
        return null;
    }

    public static class SortItem {
        private Integer id;
        private Integer sortOrder;

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public Integer getSortOrder() {
            return sortOrder;
        }

        public void setSortOrder(Integer sortOrder) {
            this.sortOrder = sortOrder;
        }
    }

    public static class LessonProgressForm {
        private Integer classId;
        private Integer progress;
        private Integer learnedSeconds;
        private Integer lastPosition;

        public Integer getClassId() {
            return classId;
        }

        public void setClassId(Integer classId) {
            this.classId = classId;
        }

        public Integer getProgress() {
            return progress;
        }

        public void setProgress(Integer progress) {
            this.progress = progress;
        }

        public Integer getLearnedSeconds() {
            return learnedSeconds;
        }

        public void setLearnedSeconds(Integer learnedSeconds) {
            this.learnedSeconds = learnedSeconds;
        }

        public Integer getLastPosition() {
            return lastPosition;
        }

        public void setLastPosition(Integer lastPosition) {
            this.lastPosition = lastPosition;
        }
    }
}
