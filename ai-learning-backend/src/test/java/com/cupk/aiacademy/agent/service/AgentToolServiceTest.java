package com.cupk.aiacademy.agent.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.mapper.ClassStudentMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.HomeworkMapper;
import com.cupk.aiacademy.mapper.HomeworkSubmitMapper;
import com.cupk.aiacademy.mapper.LearningProgressMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.LearningProgress;
import com.cupk.aiacademy.pojo.Lesson;
import com.cupk.aiacademy.service.PermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentToolServiceTest {
    @Mock
    private CourseMapper courseMapper;

    @Mock
    private HomeworkMapper homeworkMapper;

    @Mock
    private HomeworkSubmitMapper homeworkSubmitMapper;

    @Mock
    private LearningProgressMapper learningProgressMapper;

    @Mock
    private LessonMapper lessonMapper;

    @Mock
    private PermissionService permissionService;

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void courseSearchReturnsVisibleStudentCourses() {
        AgentToolService service = newService();
        loginAsStudent(10);
        when(permissionService.isStudent(any())).thenReturn(true);
        when(permissionService.studentCourseIds(10)).thenReturn(List.of(101));
        doAnswer(invocation -> {
            Page<Course> page = invocation.getArgument(0);
            page.setRecords(List.of(course(101, "Java 基础")));
            return page;
        }).when(courseMapper).selectPage(any(Page.class), any());

        String context = service.buildToolContext(List.of("course_search"), "帮我查 Java 课程", 5, 1000);

        assertTrue(context.contains("[course_search]"));
        assertTrue(context.contains("课程ID=101"));
        assertTrue(context.contains("Java 基础"));
    }

    @Test
    void learningPlanSummarizesStudentProgress() {
        AgentToolService service = newService();
        loginAsStudent(10);
        when(permissionService.isStudent(any())).thenReturn(true);
        doAnswer(invocation -> {
            Page<LearningProgress> page = invocation.getArgument(0);
            page.setRecords(List.of(
                    progress(1, 40),
                    progress(2, 80)
            ));
            return page;
        }).when(learningProgressMapper).selectPage(any(Page.class), any());
        when(lessonMapper.selectById(1)).thenReturn(lesson(1, "递归入门"));

        String context = service.buildToolContext(List.of("learning_plan"), "生成学习计划", 5, 1000);

        assertTrue(context.contains("[learning_plan]"));
        assertTrue(context.contains("平均进度=60%"));
        assertTrue(context.contains("递归入门(40%)"));
        assertTrue(context.contains("每天安排 30-45 分钟学习"));
    }

    private AgentToolService newService() {
        return new AgentToolService(courseMapper, homeworkMapper, homeworkSubmitMapper,
                learningProgressMapper, lessonMapper, permissionService);
    }

    private void loginAsStudent(Integer userId) {
        AuthUtils.LoginUser user = new AuthUtils.LoginUser();
        user.setUserId(userId);
        user.setUsername("student" + userId);
        user.setRoleCode("student");
        UserContext.set(user);
    }

    private Course course(Integer id, String title) {
        Course course = new Course();
        course.setId(id);
        course.setTitle(title);
        course.setTeacher("张老师");
        course.setDifficulty("入门");
        course.setDescription("Java 课程基础内容");
        course.setStatus(1);
        return course;
    }

    private LearningProgress progress(Integer lessonId, Integer progress) {
        LearningProgress learningProgress = new LearningProgress();
        learningProgress.setStudentId(10);
        learningProgress.setLessonId(lessonId);
        learningProgress.setProgress(progress);
        learningProgress.setLearnedSeconds(progress * 60);
        learningProgress.setLastLearnTime(LocalDateTime.now());
        return learningProgress;
    }

    private Lesson lesson(Integer id, String title) {
        Lesson lesson = new Lesson();
        lesson.setId(id);
        lesson.setTitle(title);
        return lesson;
    }
}
