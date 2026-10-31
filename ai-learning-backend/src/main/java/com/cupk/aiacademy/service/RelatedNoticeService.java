package com.cupk.aiacademy.service;

import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.NoticeMapper;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.ExamSession;
import com.cupk.aiacademy.pojo.Notice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class RelatedNoticeService {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int MAX_TITLE_LENGTH = 100;

    private final NoticeMapper noticeMapper;
    private final PermissionService permissionService;

    public RelatedNoticeService(NoticeMapper noticeMapper, PermissionService permissionService) {
        this.noticeMapper = noticeMapper;
        this.permissionService = permissionService;
    }

    @Transactional
    public Notice publishCourseNotice(Course course) {
        if (course == null || course.getId() == null) {
            throw new BusinessException("课程信息不完整，无法同步发布通知");
        }
        String title = truncate("新课程发布：" + safeText(course.getTitle(), "未命名课程"));
        StringBuilder content = new StringBuilder()
                .append("课程《")
                .append(safeText(course.getTitle(), "未命名课程"))
                .append("》已发布，现可进入平台查看学习内容。")
                .append("\n课程ID：")
                .append(course.getId());
        if (course.getDescription() != null && !course.getDescription().isBlank()) {
            content.append("\n课程简介：").append(course.getDescription().trim());
        }
        return insertNotice(null, title, content.toString(), "course");
    }

    @Transactional
    public Notice publishExamNotice(ExamSession exam) {
        if (exam == null || exam.getId() == null || exam.getClassId() == null) {
            throw new BusinessException("考试信息不完整，无法同步发布通知");
        }
        String examName = safeText(exam.getTitle(), "未命名考试");
        StringBuilder content = new StringBuilder()
                .append("考试《")
                .append(examName)
                .append("》已发布。")
                .append("\n开始时间：")
                .append(formatTime(exam.getStartTime()))
                .append("\n结束时间：")
                .append(formatTime(exam.getEndTime()));
        if (exam.getDuration() != null) {
            content.append("\n作答时长：").append(exam.getDuration()).append(" 分钟");
        }
        return insertNotice(exam.getClassId(), truncate("考试发布：" + examName), content.toString(), "exam");
    }

    private Notice insertNotice(Integer classId, String title, String content, String noticeType) {
        Notice notice = new Notice();
        notice.setPublisherId(permissionService.currentUser().getUserId());
        notice.setClassId(classId);
        notice.setTitle(title);
        notice.setContent(content);
        notice.setNoticeType(noticeType);
        notice.setStatus(1);
        notice.setCreateTime(LocalDateTime.now());
        if (noticeMapper.insert(notice) <= 0) {
            throw new BusinessException("关联通知发布失败");
        }
        return notice;
    }

    private String formatTime(LocalDateTime value) {
        return value == null ? "待定" : value.format(TIME_FORMATTER);
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String truncate(String value) {
        return value.length() <= MAX_TITLE_LENGTH ? value : value.substring(0, MAX_TITLE_LENGTH);
    }
}
