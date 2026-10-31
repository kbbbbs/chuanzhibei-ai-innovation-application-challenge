package com.cupk.aiacademy.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.HomeworkDto;
import com.cupk.aiacademy.mapper.HomeworkMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.pojo.Homework;
import com.cupk.aiacademy.pojo.TeachingClass;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class HomeworkService {
    private final HomeworkMapper homeworkMapper;
    private final TeachingClassMapper classMapper;
    private final PermissionService permissionService;
    private final AuthUtils authUtils;

    public HomeworkService(HomeworkMapper homeworkMapper,
                           TeachingClassMapper classMapper,
                           PermissionService permissionService,
                           AuthUtils authUtils) {
        this.homeworkMapper = homeworkMapper;
        this.classMapper = classMapper;
        this.permissionService = permissionService;
        this.authUtils = authUtils;
    }

    @Transactional
    public Result create(HomeworkDto request) {
        permissionService.requireTeacherOrAdmin();
        Homework homework = request.toEntity();
        applyHomeworkTeacher(homework);
        Result checkResult = checkHomework(homework);
        if (checkResult != null) {
            return checkResult;
        }
        stampHomeworkForCreate(homework);
        int row = homeworkMapper.insert(homework);
        return row > 0 ? Result.success(homework) : Result.error();
    }

    @Transactional
    public Result update(Integer id, HomeworkDto request) {
        Homework homework = homeworkMapper.selectById(id);
        if (homework == null) {
            return Result.error("404", "作业不存在");
        }
        permissionService.requireHomeworkTeacher(homework);
        homework = request.toEntity();
        applyHomeworkTeacher(homework);
        Result checkResult = checkHomework(homework);
        if (checkResult != null) {
            return checkResult;
        }
        stampHomeworkForUpdate(homework, id);
        int row = homeworkMapper.updateById(homework);
        return row > 0 ? Result.success(homework) : Result.error();
    }

    @Transactional(readOnly = true)
    public List<Homework> listForExport(Integer classId, Integer teacherId, String title, Integer status) {
        permissionService.requireTeacherOrAdmin();
        QueryWrapper<Homework> query = new QueryWrapper<>();
        AuthUtils.LoginUser user = permissionService.currentUser();
        if (authUtils.isTeacher(user)) {
            if (classId != null && classId > 0) {
                permissionService.requireClassTeacher(classId);
                query.eq("class_id", classId);
            } else {
                List<Integer> classIds = permissionService.teacherClassIds(user.getUserId());
                if (classIds.isEmpty()) {
                    return List.of();
                }
                query.in("class_id", classIds);
            }
            query.eq("teacher_id", user.getUserId());
        } else {
            if (classId != null && classId > 0) {
                query.eq("class_id", classId);
            }
            if (teacherId != null && teacherId > 0) {
                query.eq("teacher_id", teacherId);
            }
        }
        if (title != null && !title.trim().isEmpty()) {
            query.like("title", title.trim());
        }
        if (status != null) {
            query.eq("status", status);
        }
        query.orderByDesc("id");
        return homeworkMapper.selectList(query);
    }

    private Result checkHomework(Homework homework) {
        if (homework == null) {
            return Result.error("400", "作业信息不能为空");
        }
        TeachingClass teachingClass = homework.getClassId() == null ? null : classMapper.selectById(homework.getClassId());
        if (teachingClass == null) {
            return Result.error("404", "教学班不存在");
        }
        if (homework.getTeacherId() == null || homework.getTeacherId() <= 0) {
            return Result.error("400", "教师用户不能为空");
        }
        try {
            permissionService.requireUserRole(homework.getTeacherId(), "teacher", "教师用户不存在或角色不正确");
        } catch (RuntimeException e) {
            return Result.error("404", e.getMessage());
        }
        if (!homework.getTeacherId().equals(teachingClass.getTeacherId())) {
            return Result.error("400", "作业教师必须与教学班教师一致");
        }
        if (homework.getTitle() == null || homework.getTitle().trim().isEmpty()) {
            return Result.error("400", "作业标题不能为空");
        }
        return null;
    }

    private void stampHomeworkForCreate(Homework homework) {
        LocalDateTime now = LocalDateTime.now();
        homework.setTitle(homework.getTitle().trim());
        homework.setCreateTime(now);
        homework.setUpdateTime(now);
    }

    private void stampHomeworkForUpdate(Homework homework, Integer id) {
        homework.setId(id);
        homework.setTitle(homework.getTitle().trim());
        homework.setUpdateTime(LocalDateTime.now());
    }

    private void applyHomeworkTeacher(Homework homework) {
        AuthUtils.LoginUser user = permissionService.currentUser();
        if (authUtils.isTeacher(user)) {
            homework.setTeacherId(user.getUserId());
            return;
        }
        if (homework.getClassId() == null || (homework.getTeacherId() != null && homework.getTeacherId() > 0)) {
            return;
        }
        TeachingClass teachingClass = classMapper.selectById(homework.getClassId());
        if (teachingClass != null) {
            homework.setTeacherId(teachingClass.getTeacherId());
        }
    }
}
