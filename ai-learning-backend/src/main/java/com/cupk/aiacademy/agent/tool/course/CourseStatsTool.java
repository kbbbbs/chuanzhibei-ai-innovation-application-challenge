package com.cupk.aiacademy.agent.tool.course;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.agent.tool.api.AgentTool;
import com.cupk.aiacademy.agent.tool.api.AgentToolContext;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import com.cupk.aiacademy.agent.tool.support.AgentToolSupport;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.HomeworkMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.pojo.Chapter;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.Homework;
import com.cupk.aiacademy.pojo.Lesson;
import com.cupk.aiacademy.pojo.TeachingClass;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CourseStatsTool implements AgentTool {
    private final CourseMapper courseMapper;
    private final ChapterMapper chapterMapper;
    private final LessonMapper lessonMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final HomeworkMapper homeworkMapper;
    private final AgentToolSupport support;

    public CourseStatsTool(CourseMapper courseMapper,
                           ChapterMapper chapterMapper,
                           LessonMapper lessonMapper,
                           TeachingClassMapper teachingClassMapper,
                           HomeworkMapper homeworkMapper,
                           AgentToolSupport support) {
        this.courseMapper = courseMapper;
        this.chapterMapper = chapterMapper;
        this.lessonMapper = lessonMapper;
        this.teachingClassMapper = teachingClassMapper;
        this.homeworkMapper = homeworkMapper;
        this.support = support;
    }

    @Override
    public String name() {
        return "course_stats";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("course_statistics", "course_content_stats");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        String courseTitle = context.stringArgument("courseTitle");
        QueryWrapper<Course> query = new QueryWrapper<>();
        if (!support.applyCourseVisibility(query)) {
            return AgentToolResult.read(name(), "当前账号没有可查询的课程范围。", Map.of("count", 0));
        }
        query.like("title", courseTitle).orderByDesc("id").last("LIMIT " + context.maxItems());
        List<Course> courses = courseMapper.selectList(query);
        if (courses == null || courses.isEmpty()) {
            return AgentToolResult.read(name(), "未查询到匹配课程，无法统计。", Map.of("count", 0));
        }

        List<String> lines = new ArrayList<>();
        for (Course course : courses) {
            List<Integer> chapterIds = chapterIds(course.getId());
            List<Integer> classIds = classIds(course.getId());
            lines.add("课程=" + support.value(course.getTitle())
                    + "；课程ID=" + support.value(course.getId())
                    + "；章节数=" + chapterIds.size()
                    + "；课时数=" + countLessons(chapterIds)
                    + "；教学班数=" + classIds.size()
                    + "；作业数=" + countHomeworks(classIds));
        }
        return AgentToolResult.read(name(), String.join("\n", lines), Map.of("count", courses.size()));
    }

    private List<Integer> chapterIds(Integer courseId) {
        QueryWrapper<Chapter> query = new QueryWrapper<>();
        query.eq("course_id", courseId).eq("status", Chapter.STATUS_NORMAL);
        List<Chapter> chapters = chapterMapper.selectList(query);
        return chapters == null ? List.of() : chapters.stream().map(Chapter::getId).toList();
    }

    private int countLessons(List<Integer> chapterIds) {
        if (chapterIds.isEmpty()) {
            return 0;
        }
        QueryWrapper<Lesson> query = new QueryWrapper<>();
        query.in("chapter_id", chapterIds);
        return Math.toIntExact(lessonMapper.selectCount(query));
    }

    private List<Integer> classIds(Integer courseId) {
        QueryWrapper<TeachingClass> query = new QueryWrapper<>();
        query.eq("course_id", courseId);
        List<TeachingClass> classes = teachingClassMapper.selectList(query);
        return classes == null ? List.of() : classes.stream().map(TeachingClass::getId).toList();
    }

    private int countHomeworks(List<Integer> classIds) {
        if (classIds.isEmpty()) {
            return 0;
        }
        QueryWrapper<Homework> query = new QueryWrapper<>();
        query.in("class_id", classIds);
        return Math.toIntExact(homeworkMapper.selectCount(query));
    }
}
