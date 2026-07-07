package com.cupk.aiacademy.comment;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.ForbiddenException;
import com.cupk.aiacademy.dto.LessonCommentCreateRequest;
import com.cupk.aiacademy.dto.LessonCommentVO;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.LessonCommentMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.Chapter;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.Lesson;
import com.cupk.aiacademy.pojo.LessonComment;
import com.cupk.aiacademy.pojo.User;
import com.cupk.aiacademy.service.PermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LessonCommentService {
    private final LessonCommentMapper commentMapper;
    private final LessonMapper lessonMapper;
    private final ChapterMapper chapterMapper;
    private final CourseMapper courseMapper;
    private final UserMapper userMapper;
    private final PermissionService permissionService;

    public LessonCommentService(LessonCommentMapper commentMapper,
                                LessonMapper lessonMapper,
                                ChapterMapper chapterMapper,
                                CourseMapper courseMapper,
                                UserMapper userMapper,
                                PermissionService permissionService) {
        this.commentMapper = commentMapper;
        this.lessonMapper = lessonMapper;
        this.chapterMapper = chapterMapper;
        this.courseMapper = courseMapper;
        this.userMapper = userMapper;
        this.permissionService = permissionService;
    }

    @Transactional(readOnly = true)
    public Page<LessonCommentVO> listComments(Integer lessonId, Integer pageNum, Integer pageSize) {
        Lesson lesson = getLesson(lessonId);
        Course course = getCourseByLesson(lesson);
        requireLessonAccess(course);

        Page<LessonComment> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize));
        QueryWrapper<LessonComment> query = new QueryWrapper<>();
        query.eq("lesson_id", lesson.getId())
                .eq("deleted", 0)
                .isNull("parent_id")
                .orderByDesc("created_at")
                .orderByDesc("id");
        commentMapper.selectPage(page, query);

        AuthUtils.LoginUser currentUser = permissionService.currentUser();
        Map<Integer, User> users = loadUsers(page);
        Page<LessonCommentVO> voPage = new Page<>(page.getCurrent(), page.getSize());
        voPage.setTotal(page.getTotal());
        voPage.setRecords(page.getRecords().stream()
                .map(comment -> toVO(comment, users.get(comment.getUserId()), currentUser, course))
                .toList());
        return voPage;
    }

    @Transactional
    public LessonCommentVO createComment(Integer lessonId, LessonCommentCreateRequest request) {
        Lesson lesson = getLesson(lessonId);
        Course course = getCourseByLesson(lesson);
        requireLessonAccess(course);

        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.isBlank()) {
            throw new BusinessException("评论内容不能为空");
        }
        if (content.length() > 1000) {
            throw new BusinessException("评论内容不能超过1000个字符");
        }

        AuthUtils.LoginUser user = permissionService.currentUser();
        LocalDateTime now = LocalDateTime.now();
        LessonComment comment = new LessonComment();
        comment.setLessonId(lesson.getId());
        comment.setUserId(user.getUserId());
        comment.setParentId(null);
        comment.setContent(content);
        comment.setCreatedAt(now);
        comment.setUpdatedAt(now);
        comment.setDeleted(0);
        commentMapper.insert(comment);

        return toVO(comment, userMapper.selectById(user.getUserId()), user, course);
    }

    @Transactional
    public void deleteComment(Long commentId) {
        if (commentId == null || commentId <= 0) {
            throw new BusinessException("评论不能为空");
        }
        LessonComment comment = commentMapper.selectById(commentId);
        if (comment == null || Objects.equals(comment.getDeleted(), 1)) {
            throw new BusinessException("评论不存在");
        }

        Lesson lesson = getLesson(comment.getLessonId());
        Course course = getCourseByLesson(lesson);
        AuthUtils.LoginUser user = permissionService.currentUser();
        if (!canDelete(comment, user, course)) {
            throw new ForbiddenException("无权删除该评论");
        }

        comment.setDeleted(1);
        comment.setUpdatedAt(LocalDateTime.now());
        commentMapper.updateById(comment);
    }

    private Map<Integer, User> loadUsers(Page<LessonComment> page) {
        Set<Integer> userIds = page.getRecords().stream()
                .map(LessonComment::getUserId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left));
    }

    private LessonCommentVO toVO(LessonComment comment, User author, AuthUtils.LoginUser currentUser, Course course) {
        LessonCommentVO vo = LessonCommentVO.from(comment);
        String authorName = "用户" + comment.getUserId();
        if (author != null) {
            if (author.getNickname() != null && !author.getNickname().isBlank()) {
                authorName = author.getNickname();
            } else if (author.getUsername() != null && !author.getUsername().isBlank()) {
                authorName = author.getUsername();
            }
            vo.setAuthorAvatar(author.getAvatar());
        }
        vo.setAuthorName(authorName);
        vo.setCanDelete(canDelete(comment, currentUser, course));
        return vo;
    }

    private boolean canDelete(LessonComment comment, AuthUtils.LoginUser user, Course course) {
        if (permissionService.isAdmin(user)) {
            return true;
        }
        if (Objects.equals(comment.getUserId(), user.getUserId())) {
            return true;
        }
        return permissionService.isTeacher(user) && Objects.equals(course.getTeacherId(), user.getUserId());
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

    private Course getCourseByLesson(Lesson lesson) {
        Chapter chapter = chapterMapper.selectById(lesson.getChapterId());
        if (chapter == null) {
            throw new BusinessException("章节不存在");
        }
        Course course = courseMapper.selectById(chapter.getCourseId());
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        return course;
    }

    private void requireLessonAccess(Course course) {
        AuthUtils.LoginUser user = permissionService.currentUser();
        if (permissionService.isAdmin(user)) {
            return;
        }
        if (permissionService.isTeacher(user) && Objects.equals(course.getTeacherId(), user.getUserId())) {
            return;
        }
        if (permissionService.isStudent(user) && permissionService.studentCourseIds(user.getUserId()).contains(course.getId())) {
            return;
        }
        throw new ForbiddenException("无权访问该课时评论");
    }

    private long fixPageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private long fixPageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 50);
    }
}
