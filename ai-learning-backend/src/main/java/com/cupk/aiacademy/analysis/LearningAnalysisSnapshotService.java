package com.cupk.aiacademy.analysis;

import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.service.PermissionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static com.cupk.aiacademy.analysis.LearningAnalysisSnapshot.*;

@Service
public class LearningAnalysisSnapshotService {
    private final JdbcTemplate db;
    private final PermissionService permissions;

    public LearningAnalysisSnapshotService(JdbcTemplate db, PermissionService permissions) {
        this.db = db;
        this.permissions = permissions;
    }

    public int resolveCourse(Integer courseId, String title) {
        permissions.requireTeacherOrAdmin();
        if (courseId != null && courseId > 0) return courseId;
        if (title == null || title.isBlank()) throw new BusinessException("请选择课程或提供准确的课程名称");
        String sql = "SELECT id FROM t_course WHERE title=?";
        List<Integer> ids = permissions.isAdmin()
                ? db.queryForList(sql, Integer.class, title.trim())
                : db.queryForList(sql + " AND teacher_id=?", Integer.class, title.trim(), permissions.currentUser().getUserId());
        if (ids.isEmpty()) throw new BusinessException("未找到有权限分析的课程，请提供准确的课程名称或课程ID");
        if (ids.size() > 1) throw new BusinessException("存在同名课程，请补充课程ID");
        return ids.getFirst();
    }

    public void requireScope(int courseId, List<ClassSummary> originalClasses) {
        permissions.requireTeacherOrAdmin();
        permissions.requireCourseTeacher(courseId);
        for (ClassSummary summary : originalClasses) {
            permissions.requireClassTeacher(summary.id());
            if (!Objects.equals(permissions.getTeachingClass(summary.id()).getCourseId(), courseId))
                throw new BusinessException("报告对应教学班的课程关系已变更");
        }
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public LearningAnalysisSnapshot capture(int courseId) {
        permissions.requireTeacherOrAdmin();
        var course = permissions.getCourse(courseId);
        permissions.requireCourseTeacher(course);
        var classes = db.queryForList("SELECT id,class_name,semester,status FROM t_teaching_class WHERE course_id=? ORDER BY id", courseId);
        List<ClassSummary> summaries = new ArrayList<>();
        List<Assessment> assessments = new ArrayList<>();
        Set<Integer> uniqueStudents = new HashSet<>();
        Set<String> semesters = new LinkedHashSet<>();
        for (var teachingClass : classes) {
            int classId = number(teachingClass.get("id"));
            permissions.requireClassTeacher(classId);
            String name = text(teachingClass.get("class_name"));
            String semester = text(teachingClass.get("semester"));
            if (!semester.isBlank()) semesters.add(semester);
            var students = db.queryForList("SELECT DISTINCT cs.student_id,COALESCE(u.nickname,u.username) AS student_name "
                    + "FROM t_class_student cs JOIN t_user u ON u.id=cs.student_id WHERE cs.class_id=? AND cs.status=1 ORDER BY cs.student_id", classId);
            students.forEach(s -> uniqueStudents.add(number(s.get("student_id"))));
            List<Assessment> classAssessments = new ArrayList<>();
            classAssessments.addAll(assessments(classId, name, students, "EXAM"));
            classAssessments.addAll(assessments(classId, name, students, "HOMEWORK"));
            classAssessments.addAll(assessments(classId, name, students, "LEGACY_HOMEWORK"));
            assessments.addAll(classAssessments);
            summaries.add(new ClassSummary(classId, name, semester, number(teachingClass.get("status")), students.size(),
                    stats(classAssessments, "HOMEWORK"), stats(classAssessments, "EXAM")));
        }
        assessments.sort(Comparator.comparing(Assessment::startTime).thenComparingInt(Assessment::id));
        String category = db.queryForList("SELECT name FROM t_course_category WHERE id=?", String.class, course.getCategoryId())
                .stream().findFirst().orElse("");
        int chapters = db.queryForObject("SELECT COUNT(*) FROM t_chapter WHERE course_id=?", Integer.class, courseId);
        int lessons = db.queryForObject("SELECT COUNT(*) FROM t_lesson l JOIN t_chapter c ON c.id=l.chapter_id WHERE c.course_id=?", Integer.class, courseId);
        return new LearningAnalysisSnapshot(courseId, course.getTitle(), course.getDescription(), category,
                course.getDifficulty(), course.getTeacher(), String.join("、", semesters), chapters, lessons,
                uniqueStudents.size(), LocalDateTime.now(), summaries, assessments,
                stats(assessments, "HOMEWORK"), stats(assessments, "EXAM"));
    }

    private List<Assessment> assessments(int classId, String className, List<Map<String,Object>> students, String type) {
        String prefix = type.equals("EXAM") ? "exam" : "homework";
        boolean legacy = type.equals("LEGACY_HOMEWORK");
        String sessionSql = legacy
                ? "SELECT h.id,h.title,h.start_time,h.deadline AS end_time,h.full_score AS total_score FROM t_homework h WHERE h.class_id=? AND h.status<>0 "
                    + "AND NOT EXISTS(SELECT 1 FROM t_homework_assessment_migration m JOIN t_homework_session s ON s.id=m.homework_session_id WHERE m.legacy_homework_id=h.id) ORDER BY h.start_time,h.id"
                : "SELECT s.id,s.title,s.start_time,s.end_time,p.total_score FROM t_" + prefix
                    + "_session s LEFT JOIN t_" + prefix + "_paper p ON p.id=s.paper_id WHERE s.class_id=? AND s.status<>0 ORDER BY s.start_time,s.id";
        var sessions = db.queryForList(sessionSql, classId);
        List<Assessment> result = new ArrayList<>();
        for (var session : sessions) {
            int id = number(session.get("id"));
            Double fullScore = decimal(session.get("total_score"));
            String recordSql = legacy
                    ? "SELECT student_id,score AS total_score,CASE WHEN grade_status=1 THEN 2 ELSE 1 END AS status,submit_time FROM t_homework_submit WHERE homework_id=? ORDER BY id"
                    : "SELECT student_id,total_score,status,submit_time FROM t_" + prefix + "_record WHERE " + prefix + "_session_id=?";
            var records = db.queryForList(recordSql, id);
            Map<Integer,Map<String,Object>> recordsByStudent = new HashMap<>();
            for (var row : records) if (row.get("student_id") != null) recordsByStudent.put(number(row.get("student_id")), row);
            List<StudentResult> rows = new ArrayList<>();
            for (var student : students) {
                int studentId = number(student.get("student_id"));
                var record = recordsByStudent.get(studentId);
                String status = record == null ? "NOT_STARTED" : switch (number(record.get("status"))) {
                    case 0 -> "IN_PROGRESS";
                    case 1 -> "SUBMITTED";
                    case 2 -> "GRADED";
                    case 3 -> "PENDING_MANUAL_REVIEW";
                    default -> "UNKNOWN";
                };
                Double score = record == null || !status.equals("GRADED") ? null : decimal(record.get("total_score"));
                Double percentage = null;
                if (score != null && fullScore != null && fullScore > 0 && score >= 0 && score <= fullScore)
                    percentage = Math.round(score * 10000.0 / fullScore) / 100.0;
                rows.add(new StudentResult(studentId, text(student.get("student_name")), status, score, percentage,
                        record == null ? "" : text(record.get("submit_time"))));
            }
            var assessment = new Assessment(id, classId, className, type, text(session.get("title")), fullScore,
                    text(session.get("start_time")), text(session.get("end_time")), rows, null);
            result.add(new Assessment(id, classId, className, type, assessment.title(), fullScore,
                    assessment.startTime(), assessment.endTime(), rows, Stats.of(List.of(assessment))));
        }
        return result;
    }

    private Stats stats(List<Assessment> assessments, String type) {
        return Stats.of(assessments.stream().filter(a -> a.type().endsWith(type)).toList());
    }
    private static int number(Object value) { return value == null ? 0 : ((Number) value).intValue(); }
    private static Double decimal(Object value) { return value == null ? null : ((Number) value).doubleValue(); }
    private static String text(Object value) { return value == null ? "" : value.toString(); }
}
