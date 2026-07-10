package com.cupk.aiacademy.agent.tool.course;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.agent.tool.api.AgentTool;
import com.cupk.aiacademy.agent.tool.api.AgentToolContext;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import com.cupk.aiacademy.agent.tool.support.AgentToolSupport;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.pojo.Chapter;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.Lesson;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CourseStructureSearchTool implements AgentTool {
    private final CourseMapper courseMapper;
    private final ChapterMapper chapterMapper;
    private final LessonMapper lessonMapper;
    private final AgentToolSupport support;

    public CourseStructureSearchTool(CourseMapper courseMapper,
                                     ChapterMapper chapterMapper,
                                     LessonMapper lessonMapper,
                                     AgentToolSupport support) {
        this.courseMapper = courseMapper;
        this.chapterMapper = chapterMapper;
        this.lessonMapper = lessonMapper;
        this.support = support;
    }

    @Override
    public String name() {
        return "course_structure_search";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("course_outline_search", "course_catalog_search");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        String courseTitle = context.stringArgument("courseTitle");
        QueryWrapper<Course> courseQuery = new QueryWrapper<>();
        if (!support.applyCourseVisibility(courseQuery)) {
            return AgentToolResult.read(name(), "当前账号没有可查询的课程范围。", Map.of("count", 0));
        }
        courseQuery.like("title", courseTitle).orderByDesc("id").last("LIMIT 5");
        List<Course> courses = courseMapper.selectList(courseQuery);
        if (courses == null || courses.isEmpty()) {
            return AgentToolResult.read(name(), "未查询到匹配课程，无法读取课程结构。", Map.of("count", 0));
        }

        int remaining = context.maxItems();
        List<String> lines = new ArrayList<>();
        for (Course course : courses) {
            if (remaining <= 0) {
                break;
            }
            lines.add("课程=" + support.value(course.getTitle()) + "；课程ID=" + support.value(course.getId()));
            QueryWrapper<Chapter> chapterQuery = new QueryWrapper<>();
            chapterQuery.eq("course_id", course.getId())
                    .eq("status", Chapter.STATUS_NORMAL)
                    .orderByAsc("sort_order").orderByAsc("id");
            List<Chapter> chapters = chapterMapper.selectList(chapterQuery);
            for (Chapter chapter : chapters == null ? List.<Chapter>of() : chapters) {
                if (remaining-- <= 0) {
                    break;
                }
                lines.add("章节=" + support.value(chapter.getTitle())
                        + "；章节ID=" + support.value(chapter.getId())
                        + "；排序=" + support.value(chapter.getSortOrder()));
                QueryWrapper<Lesson> lessonQuery = new QueryWrapper<>();
                lessonQuery.eq("chapter_id", chapter.getId())
                        .orderByAsc("sort_order").orderByAsc("id");
                List<Lesson> lessons = lessonMapper.selectList(lessonQuery);
                for (Lesson lesson : lessons == null ? List.<Lesson>of() : lessons) {
                    if (remaining-- <= 0) {
                        break;
                    }
                    lines.add("  课时=" + support.value(lesson.getTitle())
                            + "；课时ID=" + support.value(lesson.getId())
                            + "；资源类型=" + support.value(lesson.getResourceType())
                            + "；时长=" + support.value(lesson.getDuration()));
                }
            }
        }
        return AgentToolResult.read(name(), String.join("\n", lines),
                Map.of("courseCount", courses.size(), "itemLimit", context.maxItems()));
    }
}
