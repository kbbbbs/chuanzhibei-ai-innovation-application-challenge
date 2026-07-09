package com.cupk.aiacademy.agent.action.course;

import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionRiskLevel;
import com.cupk.aiacademy.agent.tool.course.CourseResourceRemovePlanTool;
import com.cupk.aiacademy.agent.tool.course.CourseStatusPlanTool;
import com.cupk.aiacademy.agent.tool.course.LessonStatusPlanTool;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.CourseCategoryMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.ClassStudentMapper;
import com.cupk.aiacademy.agent.query.AgentWriteEntityLocator;
import com.cupk.aiacademy.pojo.Chapter;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.CourseCategory;
import com.cupk.aiacademy.pojo.Lesson;
import com.cupk.aiacademy.pojo.User;
import com.cupk.aiacademy.pojo.TeachingClass;
import com.cupk.aiacademy.pojo.ClassStudent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseActionExecutorTest {
    @Mock
    private CourseMapper courseMapper;

    @Mock
    private CourseCategoryMapper categoryMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ChapterMapper chapterMapper;

    @Mock
    private LessonMapper lessonMapper;

    @Mock
    private TeachingClassMapper teachingClassMapper;

    @Mock
    private ClassStudentMapper classStudentMapper;

    private AgentWriteEntityLocator entityLocator;

    @org.junit.jupiter.api.BeforeEach
    void setUpLocator() {
        entityLocator = new AgentWriteEntityLocator(courseMapper, null, null);
    }

    @Test
    void courseCreateInsertsCourseForTeacher() {
        when(categoryMapper.selectById(2)).thenReturn(new CourseCategory());
        when(userMapper.selectById(7)).thenReturn(new User());
        when(courseMapper.insert(any(Course.class))).thenAnswer(invocation -> {
            Course course = invocation.getArgument(0);
            course.setId(11);
            return 1;
        });
        CourseCreateActionExecutor executor = new CourseCreateActionExecutor(courseMapper, categoryMapper, userMapper);

        AgentActionExecutionResult result = executor.execute(new AgentActionExecutionContext(
                loginAs("teacher", 7),
                Map.of(
                        "title", "Java 基础",
                        "categoryId", "2",
                        "difficulty", "beginner",
                        "rating", "4.5"
                ),
                "127.0.0.1"
        ));

        assertTrue(result.success());
        assertEquals("course.create", result.actionCode());
        assertEquals(11, result.data().get("courseId"));

        ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class);
        verify(courseMapper).insert(captor.capture());
        Course course = captor.getValue();
        assertEquals("Java 基础", course.getTitle());
        assertEquals(2, course.getCategoryId());
        assertEquals(7, course.getTeacherId());
        assertEquals("beginner", course.getDifficulty());
        assertEquals(new BigDecimal("4.5"), course.getRating());
        assertEquals(0, course.getStudentCount());
        assertEquals(0, course.getViewCount());
        assertEquals(1, course.getStatus());
    }

    @Test
    void courseCreateRejectsStudentRole() {
        CourseCreateActionExecutor executor = new CourseCreateActionExecutor(courseMapper, categoryMapper, userMapper);

        BusinessException exception = assertThrows(BusinessException.class, () -> executor.execute(
                new AgentActionExecutionContext(
                        loginAs("student", 9),
                        Map.of("title", "Java 基础", "categoryId", 2),
                        ""
                )
        ));

        assertEquals("只有教师或管理员可以创建课程", exception.getMessage());
        verify(courseMapper, never()).insert(any(Course.class));
    }

    @Test
    void courseUpdateUpdatesAllowedFieldsForOwnerTeacher() {
        when(courseMapper.selectById(11)).thenReturn(course(11, 7));
        when(categoryMapper.selectById(3)).thenReturn(new CourseCategory());
        when(courseMapper.updateById(any(Course.class))).thenReturn(1);
        CourseUpdateActionExecutor executor = new CourseUpdateActionExecutor(courseMapper, categoryMapper, entityLocator);

        AgentActionExecutionResult result = executor.execute(new AgentActionExecutionContext(
                loginAs("teacher", 7),
                Map.of(
                        "courseId", "11",
                        "title", "Java 进阶",
                        "categoryId", "3",
                        "difficulty", "advanced",
                        "rating", "4.8"
                ),
                ""
        ));

        assertTrue(result.success());
        assertEquals("course.update", result.actionCode());
        assertEquals("Java 基础", result.data().get("oldTitle"));
        assertEquals("Java 进阶", result.data().get("newTitle"));

        ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class);
        verify(courseMapper).updateById(captor.capture());
        Course course = captor.getValue();
        assertEquals(11, course.getId());
        assertEquals("Java 进阶", course.getTitle());
        assertEquals(3, course.getCategoryId());
        assertEquals(7, course.getTeacherId());
        assertEquals("advanced", course.getDifficulty());
        assertEquals(new BigDecimal("4.8"), course.getRating());
        assertEquals(1, course.getStatus());
    }

    @Test
    void courseUpdateResolvesCourseByOriginalTitle() {
        Course matchedCourse = course(11, 7);
        matchedCourse.setTitle("我不想吃肉");
        when(courseMapper.selectList(any())).thenReturn(List.of(matchedCourse));
        when(courseMapper.updateById(any(Course.class))).thenReturn(1);
        CourseUpdateActionExecutor executor = new CourseUpdateActionExecutor(courseMapper, categoryMapper, entityLocator);

        AgentActionExecutionResult result = executor.execute(new AgentActionExecutionContext(
                loginAs("teacher", 7),
                Map.of(
                        "originalTitle", "我不想吃肉",
                        "title", "我想吃肉"
                ),
                ""
        ));

        assertTrue(result.success());
        assertEquals(11, result.data().get("courseId"));
        assertEquals("我不想吃肉", result.data().get("oldTitle"));
        assertEquals("我想吃肉", result.data().get("newTitle"));

        ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class);
        verify(courseMapper).updateById(captor.capture());
        assertEquals(11, captor.getValue().getId());
        assertEquals("我想吃肉", captor.getValue().getTitle());
    }

    @Test
    void courseUpdateRejectsOtherTeacherCourse() {
        when(courseMapper.selectById(11)).thenReturn(course(11, 8));
        CourseUpdateActionExecutor executor = new CourseUpdateActionExecutor(courseMapper, categoryMapper, entityLocator);

        BusinessException exception = assertThrows(BusinessException.class, () -> executor.execute(
                new AgentActionExecutionContext(
                        loginAs("teacher", 7),
                        Map.of("courseId", 11, "title", "Java 进阶"),
                        ""
                )
        ));

        assertEquals("只能修改当前登录教师负责的课程", exception.getMessage());
        verify(courseMapper, never()).updateById(any(Course.class));
    }

    @Test
    void courseStatusUpdatesStatusForOwnerTeacher() {
        when(courseMapper.selectById(11)).thenReturn(course(11, 7));
        when(courseMapper.updateById(any(Course.class))).thenReturn(1);
        CourseStatusActionExecutor executor = new CourseStatusActionExecutor(courseMapper,
                chapterMapper, lessonMapper, teachingClassMapper, classStudentMapper, entityLocator);

        AgentActionExecutionResult result = executor.execute(new AgentActionExecutionContext(
                loginAs("teacher", 7),
                Map.of("courseId", "11", "status", 0, "reason", "课程下架"),
                ""
        ));

        assertTrue(result.success());
        assertEquals(AgentActionRiskLevel.HIGH, executor.riskLevel());
        assertEquals(CourseStatusPlanTool.CONFIRMATION_TEXT, executor.confirmationText());
        assertEquals(1, result.data().get("oldStatus"));
        assertEquals(0, result.data().get("newStatus"));

        ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class);
        verify(courseMapper).updateById(captor.capture());
        Course course = captor.getValue();
        assertEquals(11, course.getId());
        assertEquals(0, course.getStatus());
    }

    @Test
    void courseStatusRejectsInvalidStatus() {
        CourseStatusActionExecutor executor = new CourseStatusActionExecutor(courseMapper,
                chapterMapper, lessonMapper, teachingClassMapper, classStudentMapper, entityLocator);

        BusinessException exception = assertThrows(BusinessException.class, () -> executor.execute(
                new AgentActionExecutionContext(
                        loginAs("teacher", 7),
                        Map.of("courseId", 11, "status", 9),
                        ""
                )
        ));

        assertEquals("课程状态只能是 0、1 或 2", exception.getMessage());
        verify(courseMapper, never()).updateById(any(Course.class));
    }

    @Test
    void courseStatusResolvesCourseByUniqueNaturalLanguageParameters() {
        Course matchedCourse = course(11, 7);
        matchedCourse.setTitle("我有点想吃肉");
        matchedCourse.setTeacher("王老师");
        matchedCourse.setDifficulty("入门");
        matchedCourse.setCategoryId(1);
        when(courseMapper.selectList(any())).thenReturn(List.of(matchedCourse));
        when(courseMapper.updateById(any(Course.class))).thenReturn(1);
        CourseStatusActionExecutor executor = new CourseStatusActionExecutor(courseMapper,
                chapterMapper, lessonMapper, teachingClassMapper, classStudentMapper, entityLocator);

        AgentActionExecutionResult result = executor.execute(new AgentActionExecutionContext(
                loginAs("admin", 1),
                Map.of(
                        "title", "我有点想吃肉",
                        "teacher", "王老师",
                        "difficulty", "入门",
                        "categoryId", 1,
                        "status", 0
                ),
                ""
        ));

        assertTrue(result.success());
        assertEquals(11, result.data().get("courseId"));
        assertEquals(0, result.data().get("newStatus"));

        ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class);
        verify(courseMapper).updateById(captor.capture());
        assertEquals(11, captor.getValue().getId());
        assertEquals(0, captor.getValue().getStatus());
    }

    @Test
    void courseCreateResolvesCategoryIdByName() {
        CourseCategory category = new CourseCategory();
        category.setId(3);
        category.setName("人工智能");
        when(categoryMapper.selectById(3)).thenReturn(category);
        when(categoryMapper.selectList(any())).thenReturn(List.of(category));
        when(userMapper.selectById(7)).thenReturn(new User());
        when(courseMapper.insert(any(Course.class))).thenAnswer(invocation -> {
            Course course = invocation.getArgument(0);
            course.setId(12);
            return 1;
        });
        CourseCreateActionExecutor executor = new CourseCreateActionExecutor(courseMapper, categoryMapper, userMapper);

        AgentActionExecutionResult result = executor.execute(new AgentActionExecutionContext(
                loginAs("teacher", 7),
                Map.of("title", "深度学习", "categoryName", "人工智能"),
                ""
        ));

        assertTrue(result.success());
        assertEquals(3, result.data().get("categoryId"));
        assertEquals("人工智能", result.data().get("categoryName"));
    }

    @Test
    void courseCreateRejectsAmbiguousCategoryName() {
        CourseCategory first = new CourseCategory();
        first.setId(3);
        first.setName("人工智能");
        CourseCategory second = new CourseCategory();
        second.setId(4);
        second.setName("人工智能");
        when(categoryMapper.selectList(any())).thenReturn(List.of(first, second));
        CourseCreateActionExecutor executor = new CourseCreateActionExecutor(courseMapper, categoryMapper, userMapper);

        BusinessException exception = assertThrows(BusinessException.class, () -> executor.execute(
                new AgentActionExecutionContext(
                        loginAs("teacher", 7),
                        Map.of("title", "深度学习", "categoryName", "人工智能"),
                        ""
                )
        ));

        assertTrue(exception.getMessage().contains("课程分类名称不唯一"));
        verify(courseMapper, never()).insert(any(Course.class));
    }

    @Test
    void courseUpdateResolvesCategoryIdByName() {
        when(courseMapper.selectById(11)).thenReturn(course(11, 7));
        CourseCategory category = new CourseCategory();
        category.setId(3);
        category.setName("人工智能");
        when(categoryMapper.selectById(3)).thenReturn(category);
        when(categoryMapper.selectList(any())).thenReturn(List.of(category));
        when(courseMapper.updateById(any(Course.class))).thenReturn(1);
        CourseUpdateActionExecutor executor = new CourseUpdateActionExecutor(courseMapper, categoryMapper, entityLocator);

        AgentActionExecutionResult result = executor.execute(new AgentActionExecutionContext(
                loginAs("teacher", 7),
                Map.of("courseId", "11", "categoryName", "人工智能"),
                ""
        ));

        assertTrue(result.success());
        assertEquals(3, result.data().get("newCategoryId"));
        assertEquals("人工智能", result.data().get("newCategoryName"));
    }

    @Test
    void courseStatusIncludesImpactPreviewWhenOffline() {
        when(courseMapper.selectById(11)).thenReturn(course(11, 7));
        Chapter chapter = new Chapter();
        chapter.setId(21);
        chapter.setCourseId(11);
        when(chapterMapper.selectCount(any())).thenReturn(3L);
        when(chapterMapper.selectList(any())).thenReturn(List.of(chapter));
        when(lessonMapper.selectCount(any())).thenReturn(8L);
        when(teachingClassMapper.selectCount(any())).thenReturn(2L);
        TeachingClass teachingClass = new TeachingClass();
        teachingClass.setId(51);
        teachingClass.setCourseId(11);
        when(teachingClassMapper.selectList(any())).thenReturn(List.of(teachingClass));
        when(classStudentMapper.selectCount(any())).thenReturn(40L);
        when(courseMapper.updateById(any(Course.class))).thenReturn(1);
        CourseStatusActionExecutor executor = new CourseStatusActionExecutor(courseMapper,
                chapterMapper, lessonMapper, teachingClassMapper, classStudentMapper, entityLocator);

        AgentActionExecutionResult result = executor.execute(new AgentActionExecutionContext(
                loginAs("teacher", 7),
                Map.of("courseId", "11", "status", 0, "reason", "课程下架"),
                ""
        ));

        assertTrue(result.success());
        Map<?, ?> preview = (Map<?, ?>) result.data().get("impactPreview");
        assertEquals(3, preview.get("affectedChapterCount"));
        assertEquals(8, preview.get("affectedLessonCount"));
        assertEquals(2, preview.get("affectedTeachingClassCount"));
        assertEquals(40, preview.get("affectedStudentCount"));
        assertEquals(Boolean.TRUE, preview.get("offline"));
    }

    @Test
    void chapterCreateInsertsChapterForOwnerTeacher() {
        when(courseMapper.selectById(11)).thenReturn(course(11, 7));
        when(chapterMapper.insert(any(Chapter.class))).thenAnswer(invocation -> {
            Chapter chapter = invocation.getArgument(0);
            chapter.setId(21);
            return 1;
        });
        ChapterCreateActionExecutor executor = new ChapterCreateActionExecutor(chapterMapper, courseMapper, entityLocator);

        AgentActionExecutionResult result = executor.execute(new AgentActionExecutionContext(
                loginAs("teacher", 7),
                Map.of(
                        "courseId", "11",
                        "title", "第一章 Java 入门",
                        "description", "安装环境和基础语法",
                        "sortOrder", "1"
                ),
                ""
        ));

        assertTrue(result.success());
        assertEquals("chapter.create", result.actionCode());
        assertEquals(21, result.data().get("chapterId"));

        ArgumentCaptor<Chapter> captor = ArgumentCaptor.forClass(Chapter.class);
        verify(chapterMapper).insert(captor.capture());
        Chapter chapter = captor.getValue();
        assertEquals(11, chapter.getCourseId());
        assertEquals("第一章 Java 入门", chapter.getTitle());
        assertEquals("安装环境和基础语法", chapter.getDescription());
        assertEquals(1, chapter.getSortOrder());
    }

    @Test
    void chapterCreateRejectsOtherTeacherCourse() {
        when(courseMapper.selectById(11)).thenReturn(course(11, 8));
        ChapterCreateActionExecutor executor = new ChapterCreateActionExecutor(chapterMapper, courseMapper, entityLocator);

        BusinessException exception = assertThrows(BusinessException.class, () -> executor.execute(
                new AgentActionExecutionContext(
                        loginAs("teacher", 7),
                        Map.of("courseId", 11, "title", "第一章 Java 入门"),
                        ""
                )
        ));

        assertEquals("只能在当前登录教师负责的课程下创建章节", exception.getMessage());
        verify(chapterMapper, never()).insert(any(Chapter.class));
    }

    @Test
    void chapterCreateResolvesCourseByTitle() {
        Course matchedCourse = course(11, 7);
        matchedCourse.setTitle("Java 入门");
        when(courseMapper.selectList(any())).thenReturn(List.of(matchedCourse));
        when(chapterMapper.insert(any(Chapter.class))).thenAnswer(invocation -> {
            Chapter chapter = invocation.getArgument(0);
            chapter.setId(22);
            return 1;
        });
        ChapterCreateActionExecutor executor = new ChapterCreateActionExecutor(chapterMapper, courseMapper, entityLocator);

        AgentActionExecutionResult result = executor.execute(new AgentActionExecutionContext(
                loginAs("teacher", 7),
                Map.of(
                        "courseTitle", "Java 入门",
                        "title", "人工输入测试章节"
                ),
                ""
        ));

        assertTrue(result.success());
        assertEquals(22, result.data().get("chapterId"));
        assertEquals(11, result.data().get("courseId"));

        ArgumentCaptor<Chapter> captor = ArgumentCaptor.forClass(Chapter.class);
        verify(chapterMapper).insert(captor.capture());
        assertEquals(11, captor.getValue().getCourseId());
        assertEquals("人工输入测试章节", captor.getValue().getTitle());
    }

    @Test
    void chapterCreateRejectsAmbiguousCourseTitle() {
        Course first = course(11, 7);
        first.setTitle("Java 入门");
        Course second = course(12, 7);
        second.setTitle("Java 入门进阶");
        when(courseMapper.selectList(any())).thenReturn(List.of(first, second));
        ChapterCreateActionExecutor executor = new ChapterCreateActionExecutor(chapterMapper, courseMapper, entityLocator);

        BusinessException exception = assertThrows(BusinessException.class, () -> executor.execute(
                new AgentActionExecutionContext(
                        loginAs("teacher", 7),
                        Map.of("courseTitle", "Java 入门", "title", "人工输入测试章节"),
                        ""
                )
        ));

        assertTrue(exception.getMessage().contains("匹配到多门课程"));
        assertTrue(exception.getMessage().contains("11=Java 入门"));
        assertTrue(exception.getMessage().contains("12=Java 入门进阶"));
        verify(chapterMapper, never()).insert(any(Chapter.class));
    }

    @Test
    void chapterUpdateUpdatesChapterForOwnerTeacher() {
        when(chapterMapper.selectById(21)).thenReturn(chapter(21, 11));
        when(courseMapper.selectById(11)).thenReturn(course(11, 7));
        when(chapterMapper.updateById(any(Chapter.class))).thenReturn(1);
        ChapterUpdateActionExecutor executor = new ChapterUpdateActionExecutor(chapterMapper, courseMapper);

        AgentActionExecutionResult result = executor.execute(new AgentActionExecutionContext(
                loginAs("teacher", 7),
                Map.of(
                        "chapterId", "21",
                        "title", "第一章 Java 基础",
                        "description", "修订后的章节说明",
                        "sortOrder", "2"
                ),
                ""
        ));

        assertTrue(result.success());
        assertEquals("chapter.update", result.actionCode());
        assertEquals("第一章 Java 入门", result.data().get("oldTitle"));
        assertEquals("第一章 Java 基础", result.data().get("newTitle"));

        ArgumentCaptor<Chapter> captor = ArgumentCaptor.forClass(Chapter.class);
        verify(chapterMapper).updateById(captor.capture());
        Chapter chapter = captor.getValue();
        assertEquals(21, chapter.getId());
        assertEquals(11, chapter.getCourseId());
        assertEquals("第一章 Java 基础", chapter.getTitle());
        assertEquals("修订后的章节说明", chapter.getDescription());
        assertEquals(2, chapter.getSortOrder());
    }

    @Test
    void chapterUpdateRejectsOtherTeacherCourse() {
        when(chapterMapper.selectById(21)).thenReturn(chapter(21, 11));
        when(courseMapper.selectById(11)).thenReturn(course(11, 8));
        ChapterUpdateActionExecutor executor = new ChapterUpdateActionExecutor(chapterMapper, courseMapper);

        BusinessException exception = assertThrows(BusinessException.class, () -> executor.execute(
                new AgentActionExecutionContext(
                        loginAs("teacher", 7),
                        Map.of("chapterId", 21, "title", "第一章 Java 基础"),
                        ""
                )
        ));

        assertEquals("只能修改当前登录教师负责课程下的章节", exception.getMessage());
        verify(chapterMapper, never()).updateById(any(Chapter.class));
    }

    @Test
    void chapterSortUpdatesAllItemsForOwnerTeacher() {
        when(courseMapper.selectById(11)).thenReturn(course(11, 7));
        when(chapterMapper.selectById(21)).thenReturn(chapter(21, 11, "第一章 Java 入门", 1));
        when(chapterMapper.selectById(22)).thenReturn(chapter(22, 11, "第二章 面向对象", 2));
        when(chapterMapper.updateById(any(Chapter.class))).thenReturn(1);
        ChapterSortActionExecutor executor = new ChapterSortActionExecutor(chapterMapper, courseMapper);

        AgentActionExecutionResult result = executor.execute(new AgentActionExecutionContext(
                loginAs("teacher", 7),
                Map.of(
                        "courseId", "11",
                        "items", java.util.List.of(
                                Map.of("chapterId", "21", "sortOrder", "2"),
                                Map.of("chapterId", "22", "sortOrder", "1")
                        )
                ),
                ""
        ));

        assertTrue(result.success());
        assertEquals("chapter.sort", result.actionCode());
        assertEquals(2, result.data().get("changedCount"));

        ArgumentCaptor<Chapter> captor = ArgumentCaptor.forClass(Chapter.class);
        verify(chapterMapper, org.mockito.Mockito.times(2)).updateById(captor.capture());
        assertEquals(21, captor.getAllValues().get(0).getId());
        assertEquals(2, captor.getAllValues().get(0).getSortOrder());
        assertEquals(22, captor.getAllValues().get(1).getId());
        assertEquals(1, captor.getAllValues().get(1).getSortOrder());
    }

    @Test
    void chapterSortRejectsDuplicateChapterIds() {
        when(courseMapper.selectById(11)).thenReturn(course(11, 7));
        ChapterSortActionExecutor executor = new ChapterSortActionExecutor(chapterMapper, courseMapper);

        BusinessException exception = assertThrows(BusinessException.class, () -> executor.execute(
                new AgentActionExecutionContext(
                        loginAs("teacher", 7),
                        Map.of(
                                "courseId", 11,
                                "items", java.util.List.of(
                                        Map.of("chapterId", 21, "sortOrder", 1),
                                        Map.of("chapterId", 21, "sortOrder", 2)
                                )
                        ),
                        ""
                )
        ));

        assertEquals("排序数据存在重复章节ID", exception.getMessage());
        verify(chapterMapper, never()).updateById(any(Chapter.class));
    }

    @Test
    void lessonCreateInsertsLessonForOwnerTeacher() {
        when(chapterMapper.selectById(31)).thenReturn(chapter(31, 11));
        when(courseMapper.selectById(11)).thenReturn(course(11, 7));
        when(lessonMapper.insert(any(Lesson.class))).thenAnswer(invocation -> {
            Lesson lesson = invocation.getArgument(0);
            lesson.setId(41);
            return 1;
        });
        LessonCreateActionExecutor executor = new LessonCreateActionExecutor(lessonMapper, chapterMapper, courseMapper);

        AgentActionExecutionResult result = executor.execute(new AgentActionExecutionContext(
                loginAs("teacher", 7),
                Map.of(
                        "chapterId", "31",
                        "title", "变量与数据类型",
                        "resourceType", "video",
                        "videoUrl", "https://example.com/java-variables.mp4",
                        "duration", "600",
                        "sortOrder", "1"
                ),
                ""
        ));

        assertTrue(result.success());
        assertEquals("lesson.create", result.actionCode());
        assertEquals(41, result.data().get("lessonId"));

        ArgumentCaptor<Lesson> captor = ArgumentCaptor.forClass(Lesson.class);
        verify(lessonMapper).insert(captor.capture());
        Lesson lesson = captor.getValue();
        assertEquals(31, lesson.getChapterId());
        assertEquals("变量与数据类型", lesson.getTitle());
        assertEquals("video", lesson.getResourceType());
        assertEquals("https://example.com/java-variables.mp4", lesson.getVideoUrl());
        assertEquals(600, lesson.getDuration());
        assertEquals(1, lesson.getSortOrder());
        assertEquals(0, lesson.getViewCount());
        assertEquals(1, lesson.getStatus());
    }

    @Test
    void lessonCreateRejectsOtherTeacherChapter() {
        when(chapterMapper.selectById(31)).thenReturn(chapter(31, 11));
        when(courseMapper.selectById(11)).thenReturn(course(11, 8));
        LessonCreateActionExecutor executor = new LessonCreateActionExecutor(lessonMapper, chapterMapper, courseMapper);

        BusinessException exception = assertThrows(BusinessException.class, () -> executor.execute(
                new AgentActionExecutionContext(
                        loginAs("teacher", 7),
                        Map.of("chapterId", 31, "title", "变量与数据类型"),
                        ""
                )
        ));

        assertEquals("只能在当前登录教师负责课程的章节下创建课时", exception.getMessage());
        verify(lessonMapper, never()).insert(any(Lesson.class));
    }

    @Test
    void lessonUpdateUpdatesLessonForOwnerTeacher() {
        when(lessonMapper.selectById(41)).thenReturn(lesson(41, 31));
        when(chapterMapper.selectById(31)).thenReturn(chapter(31, 11));
        when(courseMapper.selectById(11)).thenReturn(course(11, 7));
        when(lessonMapper.updateById(any(Lesson.class))).thenReturn(1);
        LessonUpdateActionExecutor executor = new LessonUpdateActionExecutor(lessonMapper, chapterMapper, courseMapper);

        AgentActionExecutionResult result = executor.execute(new AgentActionExecutionContext(
                loginAs("teacher", 7),
                Map.of(
                        "lessonId", "41",
                        "title", "变量、常量与数据类型",
                        "resourceUrl", "https://example.com/java-vars.pdf",
                        "duration", "720",
                        "status", "1"
                ),
                ""
        ));

        assertTrue(result.success());
        assertEquals("lesson.update", result.actionCode());
        assertEquals("变量与数据类型", result.data().get("oldTitle"));
        assertEquals("变量、常量与数据类型", result.data().get("newTitle"));

        ArgumentCaptor<Lesson> captor = ArgumentCaptor.forClass(Lesson.class);
        verify(lessonMapper).updateById(captor.capture());
        Lesson lesson = captor.getValue();
        assertEquals(41, lesson.getId());
        assertEquals(31, lesson.getChapterId());
        assertEquals("变量、常量与数据类型", lesson.getTitle());
        assertEquals("https://example.com/java-vars.pdf", lesson.getResourceUrl());
        assertEquals(720, lesson.getDuration());
        assertEquals(1, lesson.getStatus());
    }

    @Test
    void lessonUpdateRejectsOtherTeacherLesson() {
        when(lessonMapper.selectById(41)).thenReturn(lesson(41, 31));
        when(chapterMapper.selectById(31)).thenReturn(chapter(31, 11));
        when(courseMapper.selectById(11)).thenReturn(course(11, 8));
        LessonUpdateActionExecutor executor = new LessonUpdateActionExecutor(lessonMapper, chapterMapper, courseMapper);

        BusinessException exception = assertThrows(BusinessException.class, () -> executor.execute(
                new AgentActionExecutionContext(
                        loginAs("teacher", 7),
                        Map.of("lessonId", 41, "title", "变量、常量与数据类型"),
                        ""
                )
        ));

        assertEquals("只能修改当前登录教师负责课程下的课时", exception.getMessage());
        verify(lessonMapper, never()).updateById(any(Lesson.class));
    }

    @Test
    void courseResourceAttachUpdatesLessonResourceForOwnerTeacher() {
        when(lessonMapper.selectById(41)).thenReturn(lesson(41, 31));
        when(chapterMapper.selectById(31)).thenReturn(chapter(31, 11));
        when(courseMapper.selectById(11)).thenReturn(course(11, 7));
        when(lessonMapper.updateById(any(Lesson.class))).thenReturn(1);
        CourseResourceAttachActionExecutor executor =
                new CourseResourceAttachActionExecutor(lessonMapper, chapterMapper, courseMapper);

        AgentActionExecutionResult result = executor.execute(new AgentActionExecutionContext(
                loginAs("teacher", 7),
                Map.of(
                        "lessonId", "41",
                        "resourceType", "video",
                        "videoUrl", "https://example.com/java-vars-new.mp4",
                        "resourceUrl", "https://example.com/java-vars.pdf",
                        "reason", "补充课时资料"
                ),
                ""
        ));

        assertTrue(result.success());
        assertEquals("course.resource.attach", result.actionCode());
        assertEquals("video", result.data().get("newResourceType"));
        assertEquals("https://example.com/java-vars-new.mp4", result.data().get("newVideoUrl"));

        ArgumentCaptor<Lesson> captor = ArgumentCaptor.forClass(Lesson.class);
        verify(lessonMapper).updateById(captor.capture());
        Lesson lesson = captor.getValue();
        assertEquals(41, lesson.getId());
        assertEquals("video", lesson.getResourceType());
        assertEquals("https://example.com/java-vars-new.mp4", lesson.getVideoUrl());
        assertEquals("https://example.com/java-vars.pdf", lesson.getResourceUrl());
    }

    @Test
    void courseResourceAttachRejectsOtherTeacherLesson() {
        when(lessonMapper.selectById(41)).thenReturn(lesson(41, 31));
        when(chapterMapper.selectById(31)).thenReturn(chapter(31, 11));
        when(courseMapper.selectById(11)).thenReturn(course(11, 8));
        CourseResourceAttachActionExecutor executor =
                new CourseResourceAttachActionExecutor(lessonMapper, chapterMapper, courseMapper);

        BusinessException exception = assertThrows(BusinessException.class, () -> executor.execute(
                new AgentActionExecutionContext(
                        loginAs("teacher", 7),
                        Map.of("lessonId", 41, "videoUrl", "https://example.com/java-vars-new.mp4"),
                        ""
                )
        ));

        assertEquals("只能给当前登录教师负责课程下的课时挂载资源", exception.getMessage());
        verify(lessonMapper, never()).updateById(any(Lesson.class));
    }

    @Test
    void courseResourceRemoveClearsLessonResourceForOwnerTeacher() {
        when(lessonMapper.selectById(41)).thenReturn(lesson(41, 31));
        when(chapterMapper.selectById(31)).thenReturn(chapter(31, 11));
        when(courseMapper.selectById(11)).thenReturn(course(11, 7));
        when(lessonMapper.updateById(any(Lesson.class))).thenReturn(1);
        CourseResourceRemoveActionExecutor executor =
                new CourseResourceRemoveActionExecutor(lessonMapper, chapterMapper, courseMapper);

        AgentActionExecutionResult result = executor.execute(new AgentActionExecutionContext(
                loginAs("teacher", 7),
                Map.of("lessonId", "41", "reason", "旧资源下线"),
                ""
        ));

        assertTrue(result.success());
        assertEquals("course.resource.remove", result.actionCode());
        assertEquals(AgentActionRiskLevel.HIGH, executor.riskLevel());
        assertEquals(CourseResourceRemovePlanTool.CONFIRMATION_TEXT, executor.confirmationText());
        assertEquals(41, result.data().get("lessonId"));
        assertEquals(31, result.data().get("chapterId"));
        assertEquals(11, result.data().get("courseId"));

        ArgumentCaptor<Lesson> captor = ArgumentCaptor.forClass(Lesson.class);
        verify(lessonMapper).updateById(captor.capture());
        Lesson lesson = captor.getValue();
        assertEquals(41, lesson.getId());
        assertNull(lesson.getResourceType());
        assertNull(lesson.getResourceUrl());
        assertNull(lesson.getVideoUrl());
        assertNull(lesson.getContent());
    }

    @Test
    void courseResourceRemoveRejectsOtherTeacherLesson() {
        when(lessonMapper.selectById(41)).thenReturn(lesson(41, 31));
        when(chapterMapper.selectById(31)).thenReturn(chapter(31, 11));
        when(courseMapper.selectById(11)).thenReturn(course(11, 8));
        CourseResourceRemoveActionExecutor executor =
                new CourseResourceRemoveActionExecutor(lessonMapper, chapterMapper, courseMapper);

        BusinessException exception = assertThrows(BusinessException.class, () -> executor.execute(
                new AgentActionExecutionContext(
                        loginAs("teacher", 7),
                        Map.of("lessonId", 41),
                        ""
                )
        ));

        assertEquals("只能移除当前登录教师负责课程下的课时资源", exception.getMessage());
        verify(lessonMapper, never()).updateById(any(Lesson.class));
    }

    @Test
    void courseResourceRemoveRejectsUnsupportedField() {
        when(lessonMapper.selectById(41)).thenReturn(lesson(41, 31));
        when(chapterMapper.selectById(31)).thenReturn(chapter(31, 11));
        when(courseMapper.selectById(11)).thenReturn(course(11, 7));
        CourseResourceRemoveActionExecutor executor =
                new CourseResourceRemoveActionExecutor(lessonMapper, chapterMapper, courseMapper);

        BusinessException exception = assertThrows(BusinessException.class, () -> executor.execute(
                new AgentActionExecutionContext(
                        loginAs("teacher", 7),
                        Map.of("lessonId", 41, "removeFields", "duration"),
                        ""
                )
        ));

        assertEquals("不支持移除字段：duration", exception.getMessage());
        verify(lessonMapper, never()).updateById(any(Lesson.class));
    }

    @Test
    void lessonSortUpdatesAllItemsForOwnerTeacher() {
        when(chapterMapper.selectById(31)).thenReturn(chapter(31, 11));
        when(courseMapper.selectById(11)).thenReturn(course(11, 7));
        when(lessonMapper.selectById(41)).thenReturn(lesson(41, 31, "变量与数据类型", 1));
        when(lessonMapper.selectById(42)).thenReturn(lesson(42, 31, "运算符", 2));
        when(lessonMapper.updateById(any(Lesson.class))).thenReturn(1);
        LessonSortActionExecutor executor = new LessonSortActionExecutor(lessonMapper, chapterMapper, courseMapper);

        AgentActionExecutionResult result = executor.execute(new AgentActionExecutionContext(
                loginAs("teacher", 7),
                Map.of(
                        "chapterId", "31",
                        "items", java.util.List.of(
                                Map.of("lessonId", "41", "sortOrder", "2"),
                                Map.of("lessonId", "42", "sortOrder", "1")
                        )
                ),
                ""
        ));

        assertTrue(result.success());
        assertEquals("lesson.sort", result.actionCode());
        assertEquals(2, result.data().get("changedCount"));

        ArgumentCaptor<Lesson> captor = ArgumentCaptor.forClass(Lesson.class);
        verify(lessonMapper, org.mockito.Mockito.times(2)).updateById(captor.capture());
        assertEquals(41, captor.getAllValues().get(0).getId());
        assertEquals(2, captor.getAllValues().get(0).getSortOrder());
        assertEquals(42, captor.getAllValues().get(1).getId());
        assertEquals(1, captor.getAllValues().get(1).getSortOrder());
    }

    @Test
    void lessonSortRejectsDuplicateLessonIds() {
        when(chapterMapper.selectById(31)).thenReturn(chapter(31, 11));
        when(courseMapper.selectById(11)).thenReturn(course(11, 7));
        LessonSortActionExecutor executor = new LessonSortActionExecutor(lessonMapper, chapterMapper, courseMapper);

        BusinessException exception = assertThrows(BusinessException.class, () -> executor.execute(
                new AgentActionExecutionContext(
                        loginAs("teacher", 7),
                        Map.of(
                                "chapterId", 31,
                                "items", java.util.List.of(
                                        Map.of("lessonId", 41, "sortOrder", 1),
                                        Map.of("lessonId", 41, "sortOrder", 2)
                                )
                        ),
                        ""
                )
        ));

        assertEquals("排序数据存在重复课时ID", exception.getMessage());
        verify(lessonMapper, never()).updateById(any(Lesson.class));
    }

    @Test
    void lessonStatusUpdatesStatusForOwnerTeacher() {
        when(lessonMapper.selectById(41)).thenReturn(lesson(41, 31));
        when(chapterMapper.selectById(31)).thenReturn(chapter(31, 11));
        when(courseMapper.selectById(11)).thenReturn(course(11, 7));
        when(lessonMapper.updateById(any(Lesson.class))).thenReturn(1);
        LessonStatusActionExecutor executor = new LessonStatusActionExecutor(lessonMapper, chapterMapper, courseMapper);

        AgentActionExecutionResult result = executor.execute(new AgentActionExecutionContext(
                loginAs("teacher", 7),
                Map.of("lessonId", "41", "status", 0, "reason", "课时下架"),
                ""
        ));

        assertTrue(result.success());
        assertEquals("lesson.status", result.actionCode());
        assertEquals(AgentActionRiskLevel.HIGH, executor.riskLevel());
        assertEquals(LessonStatusPlanTool.CONFIRMATION_TEXT, executor.confirmationText());
        assertEquals(1, result.data().get("oldStatus"));
        assertEquals(0, result.data().get("newStatus"));

        ArgumentCaptor<Lesson> captor = ArgumentCaptor.forClass(Lesson.class);
        verify(lessonMapper).updateById(captor.capture());
        Lesson lesson = captor.getValue();
        assertEquals(41, lesson.getId());
        assertEquals(0, lesson.getStatus());
    }

    @Test
    void lessonStatusRejectsInvalidStatus() {
        LessonStatusActionExecutor executor = new LessonStatusActionExecutor(lessonMapper, chapterMapper, courseMapper);

        BusinessException exception = assertThrows(BusinessException.class, () -> executor.execute(
                new AgentActionExecutionContext(
                        loginAs("teacher", 7),
                        Map.of("lessonId", 41, "status", 9),
                        ""
                )
        ));

        assertEquals("课时状态只能是 0、1 或 2", exception.getMessage());
        verify(lessonMapper, never()).updateById(any(Lesson.class));
    }

    private AuthUtils.LoginUser loginAs(String roleCode, Integer userId) {
        AuthUtils.LoginUser user = new AuthUtils.LoginUser();
        user.setUserId(userId);
        user.setUsername(roleCode + userId);
        user.setRoleCode(roleCode);
        return user;
    }

    private Course course(Integer id, Integer teacherId) {
        Course course = new Course();
        course.setId(id);
        course.setTitle("Java 基础");
        course.setDescription("基础课程");
        course.setCategoryId(2);
        course.setTeacherId(teacherId);
        course.setDifficulty("beginner");
        course.setRating(new BigDecimal("4.5"));
        course.setStatus(1);
        return course;
    }

    private Chapter chapter(Integer id, Integer courseId) {
        return chapter(id, courseId, "第一章 Java 入门", 1);
    }

    private Chapter chapter(Integer id, Integer courseId, String title, Integer sortOrder) {
        Chapter chapter = new Chapter();
        chapter.setId(id);
        chapter.setCourseId(courseId);
        chapter.setTitle(title);
        chapter.setDescription("安装环境和基础语法");
        chapter.setSortOrder(sortOrder);
        return chapter;
    }

    private Lesson lesson(Integer id, Integer chapterId) {
        return lesson(id, chapterId, "变量与数据类型", 1);
    }

    private Lesson lesson(Integer id, Integer chapterId, String title, Integer sortOrder) {
        Lesson lesson = new Lesson();
        lesson.setId(id);
        lesson.setChapterId(chapterId);
        lesson.setTitle(title);
        lesson.setResourceType("video");
        lesson.setResourceUrl("https://example.com/java-vars-old.pdf");
        lesson.setVideoUrl("https://example.com/java-variables.mp4");
        lesson.setContent("变量基础");
        lesson.setDuration(600);
        lesson.setSortOrder(sortOrder);
        lesson.setViewCount(0);
        lesson.setStatus(1);
        return lesson;
    }
}
