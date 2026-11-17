package com.cupk.aiacademy.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.ForbiddenException;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.mapper.ClassStudentMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.ClassStudent;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.ExamPaper;
import com.cupk.aiacademy.pojo.ExamSession;
import com.cupk.aiacademy.pojo.Homework;
import com.cupk.aiacademy.pojo.HomeworkPaper;
import com.cupk.aiacademy.pojo.HomeworkSession;
import com.cupk.aiacademy.pojo.TeachingClass;
import com.cupk.aiacademy.pojo.User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class PermissionService {
    private final AuthUtils authUtils;
    private final UserMapper userMapper;
    private final CourseMapper courseMapper;
    private final TeachingClassMapper classMapper;
    private final ClassStudentMapper classStudentMapper;

    public PermissionService(AuthUtils authUtils,
                             UserMapper userMapper,
                             CourseMapper courseMapper,
                             TeachingClassMapper classMapper,
                             ClassStudentMapper classStudentMapper) {
        this.authUtils = authUtils;
        this.userMapper = userMapper;
        this.courseMapper = courseMapper;
        this.classMapper = classMapper;
        this.classStudentMapper = classStudentMapper;
    }

    public AuthUtils.LoginUser currentUser() {
        AuthUtils.LoginUser user = UserContext.get();
        if (user == null) {
            throw new BusinessException("请先登录");
        }
        return user;
    }

    /** Re-resolve a persisted actor for background work; a numeric identifier is not a role grant. */
    public AuthUtils.LoginUser resolveActiveUser(Integer userId) {
        if (userId == null || userId <= 0) throw new ForbiddenException("用户身份无效");
        User user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus()))
            throw new ForbiddenException("用户不存在或已停用");
        String role = authUtils.findAssignedRoleCode(userId);
        if (!List.of("teacher", "student", "admin").contains(role == null ? "" : role))
            throw new ForbiddenException("用户未分配有效角色");
        var actor = new AuthUtils.LoginUser();
        actor.setUserId(userId);actor.setUsername(user.getUsername());actor.setRoleCode(role);
        return actor;
    }

    /** Course directory visibility and administrative maintenance are not private-material rights. */
    public boolean canReadCourseMaterials(AuthUtils.LoginUser actor, Integer courseId) {
        if (actor == null || courseId == null || courseId <= 0) return false;
        AuthUtils.LoginUser active;
        try { active = resolveActiveUser(actor.getUserId()); }
        catch (ForbiddenException denied) { return false; }
        if (!Objects.equals(active.getRoleCode(), actor.getRoleCode())) return false;
        Course course = courseMapper.selectById(courseId);
        if (course == null) return false;
        if ("teacher".equals(active.getRoleCode())) return Objects.equals(course.getTeacherId(), active.getUserId());
        if (!"student".equals(active.getRoleCode()) || !Integer.valueOf(1).equals(course.getStatus())) return false;
        List<Integer> classIds = studentClassIds(active.getUserId());
        if (classIds.isEmpty()) return false;
        QueryWrapper<TeachingClass> query = new QueryWrapper<>();
        query.in("id", classIds).eq("course_id", courseId).eq("status", 1);
        return classMapper.selectList(query).stream().anyMatch(teachingClass ->
                Objects.equals(teachingClass.getCourseId(), courseId) && Integer.valueOf(1).equals(teachingClass.getStatus()));
    }

    public boolean isAdmin() {
        return authUtils.isAdmin(currentUser());
    }

    public boolean isAdmin(AuthUtils.LoginUser user) {
        return authUtils.isAdmin(user);
    }

    public boolean isTeacher() {
        return authUtils.isTeacher(currentUser());
    }

    public boolean isTeacher(AuthUtils.LoginUser user) {
        return authUtils.isTeacher(user);
    }

    public boolean isStudent() {
        return authUtils.isStudent(currentUser());
    }

    public boolean isStudent(AuthUtils.LoginUser user) {
        return authUtils.isStudent(user);
    }

    public void requireAdmin() {
        if (!authUtils.isAdmin(currentUser())) {
            throw new ForbiddenException("仅管理员可操作");
        }
    }

    public void requireTeacherOrAdmin() {
        AuthUtils.LoginUser user = currentUser();
        if (!authUtils.isAdmin(user) && !authUtils.isTeacher(user)) {
            throw new ForbiddenException("仅教师或管理员可操作");
        }
    }

    public void requireStudent() {
        if (!authUtils.isStudent(currentUser())) {
            throw new ForbiddenException("仅学生可操作");
        }
    }

    public void requireSelfStudent(Integer studentId) {
        AuthUtils.LoginUser user = currentUser();
        if (authUtils.isStudent(user) && !Objects.equals(user.getUserId(), studentId)) {
            throw new ForbiddenException("只能操作自己的数据");
        }
    }

    public Course getCourse(Integer courseId) {
        if (courseId == null || courseId <= 0) {
            throw new BusinessException("课程不能为空");
        }
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        return course;
    }

    public TeachingClass getTeachingClass(Integer classId) {
        if (classId == null || classId <= 0) {
            throw new BusinessException("教学班不能为空");
        }
        TeachingClass teachingClass = classMapper.selectById(classId);
        if (teachingClass == null) {
            throw new BusinessException("教学班不存在");
        }
        return teachingClass;
    }

    public void requireCourseTeacher(Integer courseId) {
        requireCourseTeacher(getCourse(courseId));
    }

    public void requireCourseTeacher(Course course) {
        AuthUtils.LoginUser user = currentUser();
        if (authUtils.isAdmin(user)) {
            return;
        }
        if (authUtils.isTeacher(user) && Objects.equals(course.getTeacherId(), user.getUserId())) {
            return;
        }
        throw new ForbiddenException("无权维护该课程");
    }

    public void requireClassTeacher(Integer classId) {
        requireClassTeacher(getTeachingClass(classId));
    }

    public void requireClassTeacher(TeachingClass teachingClass) {
        AuthUtils.LoginUser user = currentUser();
        if (authUtils.isAdmin(user)) {
            return;
        }
        if (authUtils.isTeacher(user) && Objects.equals(teachingClass.getTeacherId(), user.getUserId())) {
            return;
        }
        throw new ForbiddenException("无权维护该教学班");
    }

    public void requirePaperTeacher(ExamPaper paper) {
        AuthUtils.LoginUser user = currentUser();
        if (authUtils.isAdmin(user)) {
            return;
        }
        if (authUtils.isTeacher(user) && Objects.equals(paper.getTeacherId(), user.getUserId())) {
            return;
        }
        throw new ForbiddenException("无权维护该试卷");
    }

    public void requireHomeworkPaperTeacher(HomeworkPaper paper) {
        AuthUtils.LoginUser user = currentUser();
        if (authUtils.isAdmin(user)) {
            return;
        }
        if (authUtils.isTeacher(user) && Objects.equals(paper.getTeacherId(), user.getUserId())) {
            return;
        }
        throw new ForbiddenException("无权维护该作业试卷");
    }

    public void requireExamTeacher(ExamSession exam) {
        AuthUtils.LoginUser user = currentUser();
        if (authUtils.isAdmin(user)) {
            return;
        }
        if (authUtils.isTeacher(user) && Objects.equals(exam.getTeacherId(), user.getUserId())) {
            return;
        }
        throw new ForbiddenException("无权维护该考试");
    }

    public void requireHomeworkAssessmentTeacher(HomeworkSession homework) {
        AuthUtils.LoginUser user = currentUser();
        if (authUtils.isAdmin(user)) {
            return;
        }
        if (authUtils.isTeacher(user) && Objects.equals(homework.getTeacherId(), user.getUserId())) {
            return;
        }
        throw new ForbiddenException("无权维护该作业");
    }

    public void requireHomeworkTeacher(Homework homework) {
        AuthUtils.LoginUser user = currentUser();
        if (authUtils.isAdmin(user)) {
            return;
        }
        if (authUtils.isTeacher(user) && Objects.equals(homework.getTeacherId(), user.getUserId())) {
            return;
        }
        throw new ForbiddenException("无权维护该作业");
    }

    public void requireStudentInClass(Integer classId, Integer studentId) {
        if (!isStudentInClass(classId, studentId)) {
            throw new ForbiddenException("学生未加入该教学班");
        }
    }

    public boolean isStudentInClass(Integer classId, Integer studentId) {
        if (classId == null || studentId == null) {
            return false;
        }
        QueryWrapper<ClassStudent> query = new QueryWrapper<>();
        query.eq("class_id", classId)
                .eq("student_id", studentId)
                .eq("status", 1);
        return classStudentMapper.selectCount(query) > 0;
    }

    public List<Integer> studentClassIds(Integer studentId) {
        QueryWrapper<ClassStudent> query = new QueryWrapper<>();
        query.eq("student_id", studentId).eq("status", 1).orderByDesc("id");
        List<Integer> joined = classStudentMapper.selectList(query).stream()
                .map(ClassStudent::getClassId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (joined.isEmpty()) return List.of();
        QueryWrapper<TeachingClass> active = new QueryWrapper<>();
        active.in("id", joined).eq("status", 1);
        return classMapper.selectList(active).stream()
                .filter(teachingClass -> Integer.valueOf(1).equals(teachingClass.getStatus()))
                .map(TeachingClass::getId)
                .distinct()
                .toList();
    }

    public List<Integer> studentCourseIds(Integer studentId) {
        List<Integer> classIds = studentClassIds(studentId);
        if (classIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper<TeachingClass> query = new QueryWrapper<>();
        query.in("id", classIds).eq("status", 1);
        List<Integer> joinedCourses = classMapper.selectList(query).stream()
                .filter(teachingClass -> Integer.valueOf(1).equals(teachingClass.getStatus()))
                .map(TeachingClass::getCourseId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (joinedCourses.isEmpty()) return List.of();
        QueryWrapper<Course> published = new QueryWrapper<>();
        published.in("id", joinedCourses).eq("status", 1);
        return courseMapper.selectList(published).stream()
                .filter(course -> Integer.valueOf(1).equals(course.getStatus()))
                .map(Course::getId)
                .distinct()
                .toList();
    }

    public List<Integer> teacherClassIds(Integer teacherId) {
        QueryWrapper<TeachingClass> query = new QueryWrapper<>();
        query.eq("teacher_id", teacherId);
        return classMapper.selectList(query).stream()
                .map(TeachingClass::getId)
                .filter(id -> id != null && id > 0)
                .toList();
    }

    public List<Integer> teacherStudentIds(Integer teacherId) {
        List<Integer> classIds = teacherClassIds(teacherId);
        if (classIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper<ClassStudent> query = new QueryWrapper<>();
        query.in("class_id", classIds).eq("status", 1);
        return classStudentMapper.selectList(query).stream()
                .map(ClassStudent::getStudentId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    public boolean hasRole(Integer userId, String roleCode) {
        return roleCode != null && roleCode.equals(authUtils.findRoleCode(userId));
    }

    public void requireUserRole(Integer userId, String roleCode, String message) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(message);
        }
        User user = userMapper.selectById(userId);
        if (user == null || !hasRole(userId, roleCode)) {
            throw new BusinessException(message);
        }
    }
}
