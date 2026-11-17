package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.LearningProgressDto;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.LearningProgressMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.Chapter;
import com.cupk.aiacademy.pojo.LearningProgress;
import com.cupk.aiacademy.pojo.Lesson;
import com.cupk.aiacademy.pojo.TeachingClass;
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
@RequestMapping("/learning-progress")
public class LearningProgressController {
    @Autowired
    private LearningProgressMapper progressMapper;

    @Autowired
    private TeachingClassMapper classMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private LessonMapper lessonMapper;

    @Autowired
    private ChapterMapper chapterMapper;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private AuthUtils authUtils;

    @GetMapping
    public Result pages(@RequestParam(required = false) Integer classId,
                        @RequestParam(required = false) Integer studentId,
                        @RequestParam(required = false) Integer lessonId,
                        @RequestParam(defaultValue = "1") Integer pageNum,
                        @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<LearningProgress> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize));
        QueryWrapper<LearningProgress> queryWrapper = new QueryWrapper<>();
        AuthUtils.LoginUser user = permissionService.currentUser();
        if (authUtils.isStudent(user)) {
            queryWrapper.eq("student_id", user.getUserId());
            if (classId != null && classId > 0) {
                queryWrapper.eq("class_id", classId);
            }
        } else if (authUtils.isTeacher(user)) {
            if (classId != null && classId > 0) {
                permissionService.requireClassTeacher(classId);
                queryWrapper.eq("class_id", classId);
            } else {
                java.util.List<Integer> classIds = permissionService.teacherClassIds(user.getUserId());
                if (classIds.isEmpty()) {
                    return Result.success(page);
                }
                queryWrapper.in("class_id", classIds);
            }
            if (studentId != null && studentId > 0) {
                queryWrapper.eq("student_id", studentId);
            }
        } else {
            if (classId != null && classId > 0) {
                queryWrapper.eq("class_id", classId);
            }
            if (studentId != null && studentId > 0) {
                queryWrapper.eq("student_id", studentId);
            }
        }
        if (lessonId != null && lessonId > 0) {
            queryWrapper.eq("lesson_id", lessonId);
        }
        queryWrapper.orderByDesc("id");
        progressMapper.selectPage(page, queryWrapper);
        return Result.success(page);
    }

    @GetMapping("/{id}")
    public Result selectById(@PathVariable Integer id) {
        LearningProgress progress = progressMapper.selectById(id);
        if (progress == null) {
            return Result.error("404", "学习进度不存在");
        }
        checkProgressReadPermission(progress);
        return Result.success(progress);
    }

    @PostMapping
    public Result add(@RequestBody LearningProgressDto request) {
        LearningProgress progress = request == null ? null : request.toEntity();
        if (progress != null) {
            applyProgressOwnerForCreate(progress);
        }
        Result checkResult = checkProgress(progress);
        if (checkResult != null) {
            return checkResult;
        }
        stampProgressForCreate(progress);
        int row = progressMapper.insert(progress);
        return row > 0 ? Result.success() : Result.error();
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable Integer id, @RequestBody LearningProgressDto request) {
        LearningProgress progress = request == null ? null : request.toEntity();
        Result checkResult = checkProgress(progress);
        if (checkResult != null) {
            return checkResult;
        }
        LearningProgress old = progressMapper.selectById(id);
        if (old == null) {
            return Result.error("404", "学习进度不存在");
        }
        if (authUtils.isStudent(permissionService.currentUser())) {
            if (!permissionService.currentUser().getUserId().equals(old.getStudentId())) {
                return Result.error("403", "只能修改自己的学习进度");
            }
            preserveProgressIdentity(progress, old);
            Result identityCheck = checkProgress(progress);
            if (identityCheck != null) return identityCheck;
        } else {
            permissionService.requireAdmin();
        }
        stampProgressForUpdate(progress, id);
        int row = progressMapper.updateById(progress);
        return row > 0 ? Result.success() : Result.error();
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id) {
        LearningProgress progress = progressMapper.selectById(id);
        if (progress == null) {
            return Result.error("404", "学习进度不存在");
        }
        if (authUtils.isStudent(permissionService.currentUser())) {
            if (!permissionService.currentUser().getUserId().equals(progress.getStudentId())) {
                return Result.error("403", "只能删除自己的学习进度");
            }
        } else {
            permissionService.requireAdmin();
        }
        int row = progressMapper.deleteById(id);
        return row > 0 ? Result.success() : Result.error();
    }

    private void applyProgressOwnerForCreate(LearningProgress progress) {
        AuthUtils.LoginUser user = permissionService.currentUser();
        if (authUtils.isStudent(user)) {
            progress.setStudentId(user.getUserId());
        } else {
            permissionService.requireAdmin();
        }
    }

    private void preserveProgressIdentity(LearningProgress progress, LearningProgress old) {
        progress.setClassId(old.getClassId());
        progress.setStudentId(old.getStudentId());
        progress.setLessonId(old.getLessonId());
    }

    private void stampProgressForCreate(LearningProgress progress) {
        LocalDateTime now = LocalDateTime.now();
        progress.setCreateTime(now);
        progress.setUpdateTime(now);
    }

    private void stampProgressForUpdate(LearningProgress progress, Integer id) {
        progress.setId(id);
        progress.setUpdateTime(LocalDateTime.now());
    }

    private Result checkProgress(LearningProgress progress) {
        if (progress == null) {
            return Result.error("400", "学习进度信息不能为空");
        }
        if (progress.getClassId() == null || progress.getClassId() <= 0) {
            return Result.error("400", "教学班不能为空");
        }
        if (progress.getStudentId() == null || progress.getStudentId() <= 0) {
            return Result.error("400", "学生用户不能为空");
        }
        if (progress.getLessonId() == null || progress.getLessonId() <= 0) {
            return Result.error("400", "课时不能为空");
        }
        TeachingClass teachingClass = classMapper.selectById(progress.getClassId());
        if (teachingClass == null) {
            return Result.error("404", "教学班不存在");
        }
        if (userMapper.selectById(progress.getStudentId()) == null) {
            return Result.error("404", "学生用户不存在");
        }
        Lesson lesson = lessonMapper.selectById(progress.getLessonId());
        if (lesson == null) {
            return Result.error("404", "课时不存在");
        }
        Chapter chapter = chapterMapper.selectById(lesson.getChapterId());
        if (chapter == null || !teachingClass.getCourseId().equals(chapter.getCourseId())) {
            return Result.error("400", "课时不属于教学班课程");
        }
        permissionService.requireStudentLearningClass(progress.getClassId(), chapter.getCourseId(), progress.getStudentId());
        if (progress.getProgress() != null && (progress.getProgress() < 0 || progress.getProgress() > 100)) {
            return Result.error("400", "学习进度应在0到100之间");
        }
        return null;
    }

    private void checkProgressReadPermission(LearningProgress progress) {
        AuthUtils.LoginUser user = permissionService.currentUser();
        if (authUtils.isAdmin(user)) {
            return;
        }
        if (authUtils.isStudent(user)) {
            if (!user.getUserId().equals(progress.getStudentId())) {
                throw new com.cupk.aiacademy.common.ForbiddenException("只能查看自己的学习进度");
            }
            return;
        }
        permissionService.requireClassTeacher(progress.getClassId());
    }

    private int fixPageNum(Integer pageNum) {
        return pageNum == null || pageNum <= 0 ? 1 : pageNum;
    }

    private int fixPageSize(Integer pageSize) {
        return pageSize == null || pageSize <= 0 ? 10 : pageSize;
    }
}
