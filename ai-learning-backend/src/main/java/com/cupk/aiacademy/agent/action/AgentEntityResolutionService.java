package com.cupk.aiacademy.agent.action;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.ClassStudentMapper;
import com.cupk.aiacademy.mapper.CourseCategoryMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.ExamPaperMapper;
import com.cupk.aiacademy.mapper.ExamPaperQuestionMapper;
import com.cupk.aiacademy.mapper.ExamSessionMapper;
import com.cupk.aiacademy.mapper.HomeworkMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.mapper.NoticeMapper;
import com.cupk.aiacademy.mapper.QuestionMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.Chapter;
import com.cupk.aiacademy.pojo.ClassStudent;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.CourseCategory;
import com.cupk.aiacademy.pojo.ExamPaper;
import com.cupk.aiacademy.pojo.ExamPaperQuestion;
import com.cupk.aiacademy.pojo.ExamSession;
import com.cupk.aiacademy.pojo.Homework;
import com.cupk.aiacademy.pojo.Lesson;
import com.cupk.aiacademy.pojo.Notice;
import com.cupk.aiacademy.pojo.Question;
import com.cupk.aiacademy.pojo.TeachingClass;
import com.cupk.aiacademy.pojo.User;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

@Service
public class AgentEntityResolutionService {
    private final CourseCategoryMapper courseCategoryMapper;
    private final CourseMapper courseMapper;
    private final ChapterMapper chapterMapper;
    private final LessonMapper lessonMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final ClassStudentMapper classStudentMapper;
    private final HomeworkMapper homeworkMapper;
    private final NoticeMapper noticeMapper;
    private final ExamPaperMapper examPaperMapper;
    private final ExamPaperQuestionMapper examPaperQuestionMapper;
    private final ExamSessionMapper examSessionMapper;
    private final QuestionMapper questionMapper;
    private final UserMapper userMapper;

    public AgentEntityResolutionService(CourseCategoryMapper courseCategoryMapper,
                                        CourseMapper courseMapper,
                                        ChapterMapper chapterMapper,
                                        LessonMapper lessonMapper,
                                        TeachingClassMapper teachingClassMapper,
                                        ClassStudentMapper classStudentMapper,
                                        HomeworkMapper homeworkMapper,
                                        NoticeMapper noticeMapper,
                                        ExamPaperMapper examPaperMapper,
                                        ExamPaperQuestionMapper examPaperQuestionMapper,
                                        ExamSessionMapper examSessionMapper,
                                        QuestionMapper questionMapper,
                                        UserMapper userMapper) {
        this.courseCategoryMapper = courseCategoryMapper;
        this.courseMapper = courseMapper;
        this.chapterMapper = chapterMapper;
        this.lessonMapper = lessonMapper;
        this.teachingClassMapper = teachingClassMapper;
        this.classStudentMapper = classStudentMapper;
        this.homeworkMapper = homeworkMapper;
        this.noticeMapper = noticeMapper;
        this.examPaperMapper = examPaperMapper;
        this.examPaperQuestionMapper = examPaperQuestionMapper;
        this.examSessionMapper = examSessionMapper;
        this.questionMapper = questionMapper;
        this.userMapper = userMapper;
    }

    public Map<String, Object> resolve(String actionCode, Map<String, Object> parameters) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        if (parameters != null) {
            resolved.putAll(parameters);
        }
        String action = normalizeActionCode(actionCode);

        resolveCategory(resolved);
        resolveUsers(resolved);

        switch (action) {
            case "course.update", "course.status", "course.delete" -> resolveCourse(resolved, "originalTitle", "courseTitle", "courseName", "title");
            case "chapter.create", "chapter.sort" -> resolveCourse(resolved, "courseTitle", "courseName");
            case "chapter.update", "chapter.delete" -> {
                resolveCourse(resolved, "courseTitle", "courseName");
                resolveChapter(resolved, "chapterTitle", "originalTitle");
            }
            case "lesson.create", "lesson.sort" -> {
                resolveCourse(resolved, "courseTitle", "courseName");
                resolveChapter(resolved, "chapterTitle");
            }
            case "lesson.update", "lesson.status", "course.resource.attach", "course.resource.remove" -> {
                resolveCourse(resolved, "courseTitle", "courseName");
                resolveChapter(resolved, "chapterTitle");
                resolveLesson(resolved, "lessonTitle", "originalTitle");
            }
            case "teaching.class.create" -> resolveCourse(resolved, "courseTitle", "courseName");
            case "teaching.class.update" -> {
                resolveCourse(resolved, "courseTitle", "courseName");
                resolveTeachingClass(resolved, "originalClassName");
            }
            case "teaching.class.status", "class.student.add", "class.student.remove", "class.student.status" -> {
                resolveTeachingClass(resolved, "className");
                resolveClassStudent(resolved);
            }
            case "homework.create" -> {
                resolveCourse(resolved, "courseTitle", "courseName");
                resolveTeachingClass(resolved, "className");
                resolveTeachingClassFromCourse(resolved);
            }
            case "homework.update", "homework.delete" -> {
                resolveCourse(resolved, "courseTitle", "courseName");
                resolveTeachingClass(resolved, "className");
                resolveTeachingClassFromCourse(resolved);
                resolveHomework(resolved, "homeworkTitle", "originalTitle");
            }
            case "notice.create" -> resolveTeachingClass(resolved, "className");
            case "notice.update" -> {
                resolveTeachingClass(resolved, "className");
                resolveNotice(resolved, "originalTitle");
            }
            case "notice.status" -> {
                resolveTeachingClass(resolved, "className");
                resolveNotice(resolved, "title", "noticeTitle");
            }
            case "exam.session.create" -> {
                resolveTeachingClass(resolved, "className");
                resolvePaper(resolved, "paperTitle");
            }
            case "exam.session.close" -> resolveExamSession(resolved, "examSessionTitle", "title");
            case "exam.paper.create" -> resolveCourse(resolved, "courseTitle", "courseName");
            case "exam.paper.update", "exam.paper.delete" -> {
                resolveCourse(resolved, "courseTitle", "courseName");
                resolvePaper(resolved, "paperTitle", "originalTitle");
            }
            case "exam.paper.question.add", "exam.paper.question.remove", "exam.paper.question.update" -> {
                resolveCourse(resolved, "courseTitle", "courseName");
                resolvePaper(resolved, "paperTitle");
                resolveQuestion(resolved, "questionTitle");
                resolvePaperQuestion(resolved);
            }
            case "question.create" -> resolveCourse(resolved, "courseTitle", "courseName");
            case "question.update", "question.delete" -> {
                resolveCourse(resolved, "courseTitle", "courseName");
                resolveQuestion(resolved, "questionTitle", "originalTitle");
            }
            case "grade.record.create" -> {
                resolveCourse(resolved, "courseTitle", "courseName");
                resolveTeachingClass(resolved, "className");
            }
            default -> {
                // no entity locator needed
            }
        }
        return resolved;
    }

    private void resolveCategory(Map<String, Object> parameters) {
        if (hasPositiveInteger(parameters, "categoryId")) {
            return;
        }
        String name = text(parameters, "categoryName", "category");
        if (isBlank(name)) {
            return;
        }
        Integer id = resolveByText(courseCategoryMapper, "课程分类", "name", name,
                CourseCategory::getId, CourseCategory::getName, null);
        parameters.put("categoryId", id);
    }

    private void resolveCourse(Map<String, Object> parameters, String... titleKeys) {
        if (hasPositiveInteger(parameters, "courseId")) {
            return;
        }
        String title = text(parameters, titleKeys);
        if (isBlank(title)) {
            return;
        }
        Integer categoryId = positiveInteger(parameters, "categoryId");
        Integer id = resolveByText(courseMapper, "课程", "title", title,
                Course::getId, Course::getTitle,
                query -> {
                    if (categoryId != null) {
                        query.eq("category_id", categoryId);
                    }
                });
        parameters.put("courseId", id);
    }

    private void resolveChapter(Map<String, Object> parameters, String... titleKeys) {
        if (hasPositiveInteger(parameters, "chapterId")) {
            return;
        }
        String title = text(parameters, titleKeys);
        if (isBlank(title)) {
            return;
        }
        Integer courseId = positiveInteger(parameters, "courseId");
        Integer id = resolveByText(chapterMapper, "章节", "title", title,
                Chapter::getId, Chapter::getTitle,
                query -> {
                    if (courseId != null) {
                        query.eq("course_id", courseId);
                    }
                });
        parameters.put("chapterId", id);
    }

    private void resolveLesson(Map<String, Object> parameters, String... titleKeys) {
        if (hasPositiveInteger(parameters, "lessonId")) {
            return;
        }
        String title = text(parameters, titleKeys);
        if (isBlank(title)) {
            return;
        }
        Integer chapterId = positiveInteger(parameters, "chapterId");
        Integer id = resolveByText(lessonMapper, "课时", "title", title,
                Lesson::getId, Lesson::getTitle,
                query -> {
                    if (chapterId != null) {
                        query.eq("chapter_id", chapterId);
                    }
                });
        parameters.put("lessonId", id);
    }

    private void resolveTeachingClass(Map<String, Object> parameters, String... titleKeys) {
        if (hasPositiveInteger(parameters, "classId")) {
            return;
        }
        String className = text(parameters, titleKeys);
        if (isBlank(className)) {
            return;
        }
        Integer courseId = positiveInteger(parameters, "courseId");
        Integer id = resolveByText(teachingClassMapper, "教学班", "class_name", className,
                TeachingClass::getId, TeachingClass::getClassName,
                query -> {
                    if (courseId != null) {
                        query.eq("course_id", courseId);
                    }
                });
        parameters.put("classId", id);
    }

    private void resolveTeachingClassFromCourse(Map<String, Object> parameters) {
        if (hasPositiveInteger(parameters, "classId")) {
            return;
        }
        Integer courseId = positiveInteger(parameters, "courseId");
        if (courseId == null) {
            return;
        }
        QueryWrapper<TeachingClass> query = new QueryWrapper<>();
        query.eq("course_id", courseId).last("limit 5");
        List<TeachingClass> rows = teachingClassMapper.selectList(query);
        if (rows == null || rows.isEmpty()) {
            throw new BusinessException("未找到课程对应的教学班：courseId=" + courseId
                    + "，请补充 classId 或 className");
        }
        if (rows.size() > 1) {
            String candidates = rows.stream()
                    .limit(5)
                    .map(row -> row.getId() + "(" + safeTeachingClassName(row) + ")")
                    .toList()
                    .toString();
            throw new BusinessException("课程下匹配到多个教学班：" + candidates
                    + "，请补充 classId 或 className");
        }
        TeachingClass teachingClass = rows.get(0);
        parameters.put("classId", teachingClass.getId());
        if (isBlank(text(parameters, "className")) && !isBlank(teachingClass.getClassName())) {
            parameters.put("className", teachingClass.getClassName());
        }
    }

    private void resolveClassStudent(Map<String, Object> parameters) {
        if (hasPositiveInteger(parameters, "classStudentId")) {
            return;
        }
        Integer classId = positiveInteger(parameters, "classId");
        Integer studentId = positiveInteger(parameters, "studentId");
        if (classId == null || studentId == null) {
            return;
        }
        QueryWrapper<ClassStudent> query = new QueryWrapper<>();
        query.eq("class_id", classId).eq("student_id", studentId);
        List<ClassStudent> rows = classStudentMapper.selectList(query);
        if (rows == null || rows.isEmpty()) {
            throw new BusinessException("未找到教学班学生关系：classId=" + classId + "，studentId=" + studentId);
        }
        if (rows.size() > 1) {
            throw new BusinessException("教学班学生关系匹配到多条记录，请补充 classStudentId");
        }
        parameters.put("classStudentId", rows.get(0).getId());
    }

    private void resolveHomework(Map<String, Object> parameters, String... titleKeys) {
        if (hasPositiveInteger(parameters, "homeworkId")) {
            return;
        }
        String title = text(parameters, titleKeys);
        if (isBlank(title)) {
            return;
        }
        Integer classId = positiveInteger(parameters, "classId");
        Integer id = resolveByText(homeworkMapper, "作业", "title", title,
                Homework::getId, Homework::getTitle,
                query -> {
                    if (classId != null) {
                        query.eq("class_id", classId);
                    }
                });
        parameters.put("homeworkId", id);
    }

    private void resolveNotice(Map<String, Object> parameters, String... titleKeys) {
        if (hasPositiveInteger(parameters, "noticeId")) {
            return;
        }
        String title = text(parameters, titleKeys);
        if (isBlank(title)) {
            return;
        }
        Integer classId = positiveInteger(parameters, "classId");
        Integer id = resolveByText(noticeMapper, "通知", "title", title,
                Notice::getId, Notice::getTitle,
                query -> {
                    if (classId != null) {
                        query.eq("class_id", classId);
                    }
                });
        parameters.put("noticeId", id);
    }

    private void resolvePaper(Map<String, Object> parameters, String... titleKeys) {
        if (hasPositiveInteger(parameters, "paperId")) {
            return;
        }
        String title = text(parameters, titleKeys);
        if (isBlank(title)) {
            return;
        }
        Integer courseId = positiveInteger(parameters, "courseId");
        Integer id = resolveByText(examPaperMapper, "试卷", "title", title,
                ExamPaper::getId, ExamPaper::getTitle,
                query -> {
                    if (courseId != null) {
                        query.eq("course_id", courseId);
                    }
                });
        parameters.put("paperId", id);
    }

    private void resolvePaperQuestion(Map<String, Object> parameters) {
        if (hasPositiveInteger(parameters, "paperQuestionId")) {
            return;
        }
        Integer paperId = positiveInteger(parameters, "paperId");
        Integer questionId = positiveInteger(parameters, "questionId");
        if (paperId == null || questionId == null) {
            return;
        }
        QueryWrapper<ExamPaperQuestion> query = new QueryWrapper<>();
        query.eq("paper_id", paperId).eq("question_id", questionId);
        List<ExamPaperQuestion> rows = examPaperQuestionMapper.selectList(query);
        if (rows == null || rows.isEmpty()) {
            throw new BusinessException("未找到试卷题目关系：paperId=" + paperId + "，questionId=" + questionId);
        }
        if (rows.size() > 1) {
            throw new BusinessException("试卷题目关系匹配到多条记录，请补充 paperQuestionId");
        }
        parameters.put("paperQuestionId", rows.get(0).getId());
    }

    private void resolveExamSession(Map<String, Object> parameters, String... titleKeys) {
        if (hasPositiveInteger(parameters, "examSessionId")) {
            return;
        }
        String title = text(parameters, titleKeys);
        if (isBlank(title)) {
            return;
        }
        Integer classId = positiveInteger(parameters, "classId");
        Integer id = resolveByText(examSessionMapper, "考试安排", "title", title,
                ExamSession::getId, ExamSession::getTitle,
                query -> {
                    if (classId != null) {
                        query.eq("class_id", classId);
                    }
                });
        parameters.put("examSessionId", id);
    }

    private void resolveQuestion(Map<String, Object> parameters, String... titleKeys) {
        if (hasPositiveInteger(parameters, "questionId")) {
            return;
        }
        String title = text(parameters, titleKeys);
        if (isBlank(title)) {
            return;
        }
        Integer courseId = positiveInteger(parameters, "courseId");
        Integer id = resolveByText(questionMapper, "试题", "title", title,
                Question::getId, Question::getTitle,
                query -> {
                    if (courseId != null) {
                        query.eq("course_id", courseId);
                    }
                });
        parameters.put("questionId", id);
    }

    private void resolveUsers(Map<String, Object> parameters) {
        resolveUser(parameters, "teacherId", "teacherName");
        resolveUser(parameters, "studentId", "studentName");
        resolveUser(parameters, "publisherId", "publisherName");
    }

    private void resolveUser(Map<String, Object> parameters, String idKey, String nameKey) {
        if (hasPositiveInteger(parameters, idKey)) {
            return;
        }
        String name = text(parameters, nameKey);
        if (isBlank(name)) {
            return;
        }
        QueryWrapper<User> exact = new QueryWrapper<>();
        exact.and(wrapper -> wrapper.eq("username", name).or().eq("nickname", name));
        List<User> rows = userMapper.selectList(exact);
        if (rows == null || rows.isEmpty()) {
            QueryWrapper<User> fuzzy = new QueryWrapper<>();
            fuzzy.and(wrapper -> wrapper.like("username", name).or().like("nickname", name));
            rows = userMapper.selectList(fuzzy);
        }
        parameters.put(idKey, uniqueId("用户", name, rows, User::getId, this::userLabel));
    }

    private <T> Integer resolveByText(BaseMapper<T> mapper,
                                      String entityLabel,
                                      String column,
                                      String value,
                                      Function<T, Integer> idExtractor,
                                      Function<T, String> labelExtractor,
                                      Consumer<QueryWrapper<T>> filters) {
        QueryWrapper<T> exact = new QueryWrapper<>();
        applyFilters(exact, filters);
        exact.eq(column, value).last("limit 5");
        List<T> rows = mapper.selectList(exact);
        if (rows == null || rows.isEmpty()) {
            QueryWrapper<T> fuzzy = new QueryWrapper<>();
            applyFilters(fuzzy, filters);
            fuzzy.like(column, value).last("limit 5");
            rows = mapper.selectList(fuzzy);
        }
        return uniqueId(entityLabel, value, rows, idExtractor, labelExtractor);
    }

    private <T> Integer uniqueId(String entityLabel,
                                 String locator,
                                 List<T> rows,
                                 Function<T, Integer> idExtractor,
                                 Function<T, String> labelExtractor) {
        if (rows == null || rows.isEmpty()) {
            throw new BusinessException("未找到" + entityLabel + "：" + locator);
        }
        if (rows.size() > 1) {
            String candidates = rows.stream()
                    .limit(5)
                    .map(row -> idExtractor.apply(row) + "(" + labelExtractor.apply(row) + ")")
                    .toList()
                    .toString();
            throw new BusinessException(entityLabel + "匹配到多条记录：" + candidates + "，请补充ID");
        }
        return idExtractor.apply(rows.get(0));
    }

    private <T> void applyFilters(QueryWrapper<T> query, Consumer<QueryWrapper<T>> filters) {
        if (filters != null) {
            filters.accept(query);
        }
    }

    private String text(Map<String, Object> parameters, String... keys) {
        if (parameters == null || keys == null) {
            return "";
        }
        for (String key : keys) {
            Object value = parameters.get(key);
            if (value != null && !String.valueOf(value).trim().isEmpty()) {
                return String.valueOf(value).trim();
            }
        }
        return "";
    }

    private boolean hasPositiveInteger(Map<String, Object> parameters, String key) {
        return positiveInteger(parameters, key) != null;
    }

    private Integer positiveInteger(Map<String, Object> parameters, String key) {
        if (parameters == null || key == null) {
            return null;
        }
        Object value = parameters.get(key);
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return null;
        }
        try {
            int number = value instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(value).trim());
            return number > 0 ? number : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String userLabel(User user) {
        if (user == null) {
            return "";
        }
        if (!isBlank(user.getNickname())) {
            return user.getNickname();
        }
        return user.getUsername() == null ? "" : user.getUsername();
    }

    private String safeTeachingClassName(TeachingClass teachingClass) {
        if (teachingClass == null || isBlank(teachingClass.getClassName())) {
            return "";
        }
        return teachingClass.getClassName();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalizeActionCode(String actionCode) {
        if (actionCode == null || actionCode.trim().isEmpty()) {
            return "";
        }
        return actionCode.trim().toLowerCase(Locale.ROOT);
    }
}
