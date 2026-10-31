package com.cupk.aiacademy.agent.query;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.NoticeMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.Notice;
import com.cupk.aiacademy.pojo.TeachingClass;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 写操作实体定位器：把 EntityResolver 的模糊匹配能力回流到写操作执行器。
 * 与查询层 NaturalLanguageEntityResolver 的区别：这里强制唯一锁定，
 * 多候选时抛出带候选列表的 BusinessException，让用户补充 ID 消歧，
 * 而不是直接返回多结果。
 */
@Component
public class AgentWriteEntityLocator {
    private final CourseMapper courseMapper;
    private final NoticeMapper noticeMapper;
    private final TeachingClassMapper teachingClassMapper;

    public AgentWriteEntityLocator(CourseMapper courseMapper,
                                   NoticeMapper noticeMapper,
                                   TeachingClassMapper teachingClassMapper) {
        this.courseMapper = courseMapper;
        this.noticeMapper = noticeMapper;
        this.teachingClassMapper = teachingClassMapper;
    }

    public Course locateCourse(Integer courseId, String titleLocator, String teacher,
                               String difficulty, Integer categoryId) {
        if (courseId != null) {
            Course course = courseMapper.selectById(courseId);
            if (course == null) {
                throw new BusinessException("课程不存在");
            }
            return course;
        }
        QueryWrapper<Course> query = new QueryWrapper<>();
        boolean hasLocator = false;
        hasLocator = appendLike(query, "title", titleLocator, hasLocator);
        hasLocator = appendEquals(query, "teacher", teacher, hasLocator);
        hasLocator = appendEquals(query, "difficulty", difficulty, hasLocator);
        if (categoryId != null) {
            query.eq("category_id", categoryId);
            hasLocator = true;
        }
        if (!hasLocator) {
            throw new BusinessException("请提供 courseId，或提供原课程名称、教师、难度、分类ID等唯一定位条件");
        }
        query.last("LIMIT 10");
        List<Course> courses = courseMapper.selectList(query);
        return uniqueCourse(courses, titleLocator);
    }

    public Notice locateNotice(Integer noticeId, String titleLocator) {
        if (noticeId != null) {
            Notice notice = noticeMapper.selectById(noticeId);
            if (notice == null) {
                throw new BusinessException("通知不存在");
            }
            return notice;
        }
        if (titleLocator == null || titleLocator.isBlank()) {
            throw new BusinessException("请提供 noticeId，或提供原通知标题等唯一定位条件");
        }
        QueryWrapper<Notice> query = new QueryWrapper<>();
        query.like("title", titleLocator.trim()).last("LIMIT 10");
        List<Notice> notices = noticeMapper.selectList(query);
        return uniqueNotice(notices, titleLocator);
    }

    private Course uniqueCourse(List<Course> courses, String titleLocator) {
        if (courses == null || courses.isEmpty()) {
            throw new BusinessException("未找到匹配课程");
        }
        if (courses.size() == 1) {
            return courses.get(0);
        }
        throw new BusinessException("匹配到多门课程，请补充 courseId 后重试：" + courseCandidates(courses));
    }

    private Notice uniqueNotice(List<Notice> notices, String titleLocator) {
        if (notices == null || notices.isEmpty()) {
            throw new BusinessException("未找到匹配通知");
        }
        if (notices.size() == 1) {
            return notices.get(0);
        }
        throw new BusinessException("匹配到多条通知，请补充 noticeId 后重试：" + noticeCandidates(notices));
    }

    private boolean appendLike(QueryWrapper<?> query, String column, String value, boolean hasLocator) {
        if (value == null || value.isBlank()) {
            return hasLocator;
        }
        query.like(column, value.trim());
        return true;
    }

    private boolean appendEquals(QueryWrapper<?> query, String column, String value, boolean hasLocator) {
        if (value == null || value.isBlank()) {
            return hasLocator;
        }
        query.eq(column, value.trim());
        return true;
    }

    private String courseCandidates(List<Course> courses) {
        List<String> parts = new ArrayList<>();
        for (Course course : courses) {
            parts.add(course.getId() + "=" + (course.getTitle() == null ? "" : course.getTitle()));
        }
        return String.join("、", parts);
    }

    private String noticeCandidates(List<Notice> notices) {
        List<String> parts = new ArrayList<>();
        for (Notice notice : notices) {
            parts.add(notice.getId() + "=" + (notice.getTitle() == null ? "" : notice.getTitle()));
        }
        return String.join("、", parts);
    }
}