package com.cupk.aiacademy.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.ClassStudentMapper;
import com.cupk.aiacademy.mapper.CourseCategoryMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.ExamPaperMapper;
import com.cupk.aiacademy.mapper.GradeRecordMapper;
import com.cupk.aiacademy.mapper.LearningProgressMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.pojo.Chapter;
import com.cupk.aiacademy.pojo.ClassStudent;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.ExamPaper;
import com.cupk.aiacademy.pojo.GradeRecord;
import com.cupk.aiacademy.pojo.LearningProgress;
import com.cupk.aiacademy.pojo.Lesson;
import com.cupk.aiacademy.pojo.TeachingClass;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class CourseResourceService {
    private final CourseCategoryMapper categoryMapper;
    private final CourseMapper courseMapper;
    private final ChapterMapper chapterMapper;
    private final LessonMapper lessonMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final ClassStudentMapper classStudentMapper;
    private final GradeRecordMapper gradeRecordMapper;
    private final ExamPaperMapper examPaperMapper;
    private final LearningProgressMapper learningProgressMapper;
    private final AuthUtils authUtils;

    public CourseResourceService(CourseCategoryMapper categoryMapper,
                                 CourseMapper courseMapper,
                                 ChapterMapper chapterMapper,
                                 LessonMapper lessonMapper,
                                 TeachingClassMapper teachingClassMapper,
                                 ClassStudentMapper classStudentMapper,
                                 GradeRecordMapper gradeRecordMapper,
                                 ExamPaperMapper examPaperMapper,
                                 LearningProgressMapper learningProgressMapper,
                                 AuthUtils authUtils) {
        this.categoryMapper = categoryMapper;
        this.courseMapper = courseMapper;
        this.chapterMapper = chapterMapper;
        this.lessonMapper = lessonMapper;
        this.teachingClassMapper = teachingClassMapper;
        this.classStudentMapper = classStudentMapper;
        this.gradeRecordMapper = gradeRecordMapper;
        this.examPaperMapper = examPaperMapper;
        this.learningProgressMapper = learningProgressMapper;
        this.authUtils = authUtils;
    }

    @Transactional(readOnly = true)
    public Page<Course> listCoursesByCategory(Integer categoryId, Integer status, Integer pageNum, Integer pageSize) {
        if (categoryId == null || categoryId <= 0) {
            throw new BusinessException("课程分类不能为空");
        }
        if (categoryMapper.selectById(categoryId) == null) {
            throw new BusinessException("课程分类不存在");
        }
        Page<Course> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize, 10));
        QueryWrapper<Course> query = new QueryWrapper<>();
        query.eq("category_id", categoryId);
        if (status != null) {
            query.eq("status", status);
        }
        query.orderByDesc("id");
        courseMapper.selectPage(page, query);
        return page;
    }

    @Transactional(readOnly = true)
    public Page<Course> listMyCourses(AuthUtils.LoginUser loginUser, Integer pageNum, Integer pageSize) {
        Page<Course> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize, 10));
        QueryWrapper<Course> query = new QueryWrapper<>();
        if (authUtils.isStudent(loginUser)) {
            List<Integer> courseIds = findStudentCourseIds(loginUser.getUserId(), null);
            if (courseIds.isEmpty()) {
                return page;
            }
            query.in("id", courseIds);
        } else if (authUtils.isTeacher(loginUser)) {
            query.eq("teacher_id", loginUser.getUserId());
        } else if (!authUtils.isAdmin(loginUser)) {
            throw new BusinessException("用户角色不合法");
        }
        query.orderByDesc("id");
        courseMapper.selectPage(page, query);
        return page;
    }

    @Transactional(readOnly = true)
    public Page<TeachingClass> listMyTeachingClasses(AuthUtils.LoginUser loginUser, Integer status, Integer pageNum, Integer pageSize) {
        Page<TeachingClass> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize, 10));
        QueryWrapper<TeachingClass> query = new QueryWrapper<>();
        if (authUtils.isStudent(loginUser)) {
            List<Integer> classIds = findStudentClassIds(loginUser.getUserId(), true);
            if (classIds.isEmpty()) {
                return page;
            }
            query.in("id", classIds);
        } else if (authUtils.isTeacher(loginUser)) {
            query.eq("teacher_id", loginUser.getUserId());
        } else if (!authUtils.isAdmin(loginUser)) {
            throw new BusinessException("用户角色不合法");
        }
        if (status != null) {
            query.eq("status", status);
        }
        query.orderByDesc("id");
        teachingClassMapper.selectPage(page, query);
        return page;
    }

    @Transactional(readOnly = true)
    public Page<GradeRecord> listMyGradeRecords(Integer courseId,
                                                 AuthUtils.LoginUser loginUser,
                                                 String sourceType,
                                                 Integer pageNum,
                                                 Integer pageSize) {
        Course course = getCourse(courseId);
        Page<GradeRecord> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize, 10));
        QueryWrapper<GradeRecord> query = new QueryWrapper<>();
        query.eq("course_id", course.getId());
        if (authUtils.isStudent(loginUser)) {
            query.eq("student_id", loginUser.getUserId());
        } else if (authUtils.isTeacher(loginUser)) {
            requireCourseTeacher(course, loginUser);
        } else if (!authUtils.isAdmin(loginUser)) {
            throw new BusinessException("用户角色不合法");
        }
        if (sourceType != null && !sourceType.trim().isEmpty()) {
            query.eq("source_type", sourceType.trim());
        }
        query.orderByDesc("id");
        gradeRecordMapper.selectPage(page, query);
        return page;
    }

    @Transactional(readOnly = true)
    public Page<ExamPaper> listCourseExamPapers(Integer courseId, Integer status, Integer pageNum, Integer pageSize) {
        getCourse(courseId);
        Page<ExamPaper> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize, 10));
        QueryWrapper<ExamPaper> query = new QueryWrapper<>();
        query.eq("course_id", courseId);
        if (status != null) {
            query.eq("status", status);
        }
        query.orderByDesc("id");
        examPaperMapper.selectPage(page, query);
        return page;
    }

    @Transactional(readOnly = true)
    public Page<LearningProgress> listLessonProgress(Integer lessonId,
                                                      Integer classId,
                                                      AuthUtils.LoginUser loginUser,
                                                      Integer pageNum,
                                                      Integer pageSize) {
        Lesson lesson = getLesson(lessonId);
        Chapter chapter = getChapter(lesson.getChapterId());
        Page<LearningProgress> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize, 10));
        QueryWrapper<LearningProgress> query = new QueryWrapper<>();
        query.eq("lesson_id", lessonId);
        if (classId != null && classId > 0) {
            TeachingClass teachingClass = getTeachingClass(classId);
            if (!teachingClass.getCourseId().equals(chapter.getCourseId())) {
                throw new BusinessException("课时不属于该教学班课程");
            }
            query.eq("class_id", classId);
        }
        if (authUtils.isStudent(loginUser)) {
            query.eq("student_id", loginUser.getUserId());
        } else if (authUtils.isTeacher(loginUser)) {
            Course course = getCourse(chapter.getCourseId());
            requireCourseTeacher(course, loginUser);
        } else if (!authUtils.isAdmin(loginUser)) {
            throw new BusinessException("用户角色不合法");
        }
        query.orderByDesc("id");
        learningProgressMapper.selectPage(page, query);
        return page;
    }

    @Transactional(readOnly = true)
    public List<LearningProgress> listMyCourseLearningProgress(Integer courseId, Integer classId, AuthUtils.LoginUser loginUser) {
        if (!authUtils.isStudent(loginUser)) {
            throw new BusinessException("只有学生可以查看自己的课程学习进度");
        }
        Course course = getCourse(courseId);
        if (classId != null && classId > 0) {
            TeachingClass teachingClass = getTeachingClass(classId);
            if (!teachingClass.getCourseId().equals(course.getId())) {
                throw new BusinessException("教学班不属于该课程");
            }
            requireStudentInClass(classId, loginUser.getUserId());
        } else if (!findStudentCourseIds(loginUser.getUserId(), course.getId()).contains(course.getId())) {
            throw new BusinessException("学生未加入该课程教学班");
        }

        List<Integer> lessonIds = findCourseLessonIds(course.getId());
        if (lessonIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper<LearningProgress> query = new QueryWrapper<>();
        query.eq("student_id", loginUser.getUserId()).in("lesson_id", lessonIds);
        if (classId != null && classId > 0) {
            query.eq("class_id", classId);
        }
        query.orderByAsc("lesson_id").orderByDesc("id");
        return learningProgressMapper.selectList(query);
    }

    @Transactional
    public LearningProgress upsertLessonProgress(Integer lessonId,
                                                  Integer classId,
                                                  Integer progressValue,
                                                  Integer learnedSeconds,
                                                  Integer lastPosition,
                                                  AuthUtils.LoginUser loginUser) {
        if (!authUtils.isStudent(loginUser)) {
            throw new BusinessException("只有学生可以更新学习进度");
        }
        if (classId == null || classId <= 0) {
            throw new BusinessException("教学班不能为空");
        }
        int progress = progressValue == null ? 0 : progressValue;
        int learned = learnedSeconds == null ? 0 : learnedSeconds;
        int position = lastPosition == null ? 0 : lastPosition;
        if (progress < 0 || progress > 100) {
            throw new BusinessException("学习进度应在0到100之间");
        }
        if (learned < 0 || position < 0) {
            throw new BusinessException("学习时长和播放位置不能小于0");
        }

        TeachingClass teachingClass = getTeachingClass(classId);
        Lesson lesson = getLesson(lessonId);
        Chapter chapter = getChapter(lesson.getChapterId());
        if (!teachingClass.getCourseId().equals(chapter.getCourseId())) {
            throw new BusinessException("课时不属于该教学班课程");
        }
        requireStudentInClass(classId, loginUser.getUserId());

        QueryWrapper<LearningProgress> query = new QueryWrapper<>();
        query.eq("class_id", classId)
                .eq("student_id", loginUser.getUserId())
                .eq("lesson_id", lessonId)
                .orderByDesc("id");
        List<LearningProgress> oldProgressList = learningProgressMapper.selectList(query);

        LocalDateTime now = LocalDateTime.now();
        LearningProgress learningProgress;
        if (oldProgressList.isEmpty()) {
            learningProgress = new LearningProgress();
            learningProgress.setClassId(classId);
            learningProgress.setStudentId(loginUser.getUserId());
            learningProgress.setLessonId(lessonId);
            learningProgress.setCreateTime(now);
        } else {
            learningProgress = oldProgressList.get(0);
        }
        learningProgress.setProgress(progress);
        learningProgress.setLearnedSeconds(learned);
        learningProgress.setLastPosition(position);
        learningProgress.setLastLearnTime(now);
        learningProgress.setUpdateTime(now);

        if (learningProgress.getId() == null) {
            learningProgressMapper.insert(learningProgress);
        } else {
            learningProgressMapper.updateById(learningProgress);
        }
        return learningProgress;
    }

    private Course getCourse(Integer courseId) {
        if (courseId == null || courseId <= 0) {
            throw new BusinessException("课程不能为空");
        }
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        return course;
    }

    private Chapter getChapter(Integer chapterId) {
        Chapter chapter = chapterMapper.selectById(chapterId);
        if (chapter == null) {
            throw new BusinessException("章节不存在");
        }
        return chapter;
    }

    private Lesson getLesson(Integer lessonId) {
        if (lessonId == null || lessonId <= 0) {
            throw new BusinessException("课时不能为空");
        }
        Lesson lesson = lessonMapper.selectById(lessonId);
        if (lesson == null) {
            throw new BusinessException("课时不存在");
        }
        return lesson;
    }

    private TeachingClass getTeachingClass(Integer classId) {
        TeachingClass teachingClass = teachingClassMapper.selectById(classId);
        if (teachingClass == null) {
            throw new BusinessException("教学班不存在");
        }
        return teachingClass;
    }

    private void requireCourseTeacher(Course course, AuthUtils.LoginUser loginUser) {
        if (!loginUser.getUserId().equals(course.getTeacherId())) {
            throw new BusinessException("无权访问该课程资源");
        }
    }

    private void requireStudentInClass(Integer classId, Integer studentId) {
        QueryWrapper<ClassStudent> query = new QueryWrapper<>();
        query.eq("class_id", classId)
                .eq("student_id", studentId)
                .eq("status", 1);
        if (classStudentMapper.selectCount(query) <= 0) {
            throw new BusinessException("学生未加入该教学班");
        }
    }

    private List<Integer> findStudentClassIds(Integer studentId, boolean onlyActive) {
        QueryWrapper<ClassStudent> query = new QueryWrapper<>();
        query.eq("student_id", studentId);
        if (onlyActive) {
            query.eq("status", 1);
        }
        query.orderByDesc("id");
        return classStudentMapper.selectList(query).stream()
                .map(ClassStudent::getClassId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    private List<Integer> findStudentCourseIds(Integer studentId, Integer courseId) {
        List<Integer> classIds = findStudentClassIds(studentId, true);
        if (classIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper<TeachingClass> query = new QueryWrapper<>();
        query.in("id", classIds);
        if (courseId != null && courseId > 0) {
            query.eq("course_id", courseId);
        }
        return teachingClassMapper.selectList(query).stream()
                .map(TeachingClass::getCourseId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    private List<Integer> findCourseLessonIds(Integer courseId) {
        QueryWrapper<Chapter> chapterQuery = new QueryWrapper<>();
        chapterQuery.eq("course_id", courseId);
        List<Integer> chapterIds = chapterMapper.selectList(chapterQuery).stream()
                .map(Chapter::getId)
                .filter(id -> id != null && id > 0)
                .toList();
        if (chapterIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper<Lesson> lessonQuery = new QueryWrapper<>();
        lessonQuery.in("chapter_id", chapterIds);
        return new ArrayList<>(lessonMapper.selectList(lessonQuery).stream()
                .map(Lesson::getId)
                .filter(id -> id != null && id > 0)
                .toList());
    }

    private int fixPageNum(Integer pageNum) {
        return pageNum == null || pageNum <= 0 ? 1 : pageNum;
    }

    private int fixPageSize(Integer pageSize, int defaultSize) {
        return pageSize == null || pageSize <= 0 ? defaultSize : pageSize;
    }
}
