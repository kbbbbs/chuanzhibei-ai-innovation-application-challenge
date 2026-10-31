package com.cupk.aiacademy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;

@Mapper
public interface DashboardMapper {

    @Select("""
            SELECT h.id, h.class_id AS classId, h.title, h.deadline, h.full_score AS fullScore
            FROM t_homework h
            JOIN t_class_student cs ON h.class_id = cs.class_id
            WHERE cs.student_id = #{studentId}
              AND cs.status = 1
              AND h.status = 1
              AND (h.start_time IS NULL OR h.start_time <= NOW())
              AND h.deadline > NOW()
              AND NOT EXISTS (
                  SELECT 1 FROM t_homework_submit s
                  WHERE s.homework_id = h.id AND s.student_id = #{studentId}
              )
            ORDER BY h.deadline ASC, h.id ASC
            LIMIT 8
            """)
    List<Map<String, Object>> selectPendingHomework(Integer studentId);

    @Select("""
            SELECT e.id, e.class_id AS classId, e.title, e.start_time AS startTime,
                   e.end_time AS endTime, e.duration, e.status
            FROM t_exam_session e
            JOIN t_class_student cs ON e.class_id = cs.class_id
            WHERE cs.student_id = #{studentId}
              AND cs.status = 1
              AND e.status IN (1, 2)
              AND e.end_time > NOW()
              AND NOT EXISTS (
                  SELECT 1 FROM t_exam_record r
                  WHERE r.exam_session_id = e.id
                    AND r.student_id = #{studentId}
                    AND r.status <> 0
              )
            ORDER BY e.start_time ASC, e.id ASC
            LIMIT 8
            """)
    List<Map<String, Object>> selectUpcomingExams(Integer studentId);

    @Select("SELECT COUNT(*) FROM t_teaching_class WHERE teacher_id = #{teacherId}")
    long countTeacherClasses(Integer teacherId);

    @Select("SELECT COUNT(*) FROM t_teaching_class WHERE teacher_id = #{teacherId} AND status = 1")
    long countTeacherActiveClasses(Integer teacherId);

    @Select("SELECT COUNT(*) FROM t_homework_submit s JOIN t_homework h ON s.homework_id = h.id WHERE h.teacher_id = #{teacherId} AND s.grade_status = 0")
    long countPendingGrading(Integer teacherId);

    @Select("SELECT COUNT(*) FROM t_homework_submit s JOIN t_homework h ON s.homework_id = h.id WHERE h.teacher_id = #{teacherId} AND s.grade_status = 1 AND s.score < 60")
    long countLowScoreSubmissions(Integer teacherId);

    @Select("""
            SELECT s.id, s.submit_time AS submitTime, s.grade_status AS gradeStatus, s.score,
                   h.title AS homeworkTitle, COALESCE(u.nickname, u.username) AS studentName
            FROM t_homework_submit s
            JOIN t_homework h ON s.homework_id = h.id
            JOIN t_user u ON s.student_id = u.id
            WHERE h.teacher_id = #{teacherId}
            ORDER BY s.submit_time DESC, s.id DESC
            LIMIT 8
            """)
    List<Map<String, Object>> selectRecentSubmissions(Integer teacherId);

    @Select("SELECT COUNT(*) FROM t_teaching_class")
    long countAllClasses();

    @Select("SELECT COUNT(*) FROM t_teaching_class WHERE status = 1")
    long countAllActiveClasses();

    @Select("SELECT COUNT(*) FROM t_homework_submit WHERE grade_status = 0")
    long countAllPendingGrading();

    @Select("SELECT COUNT(*) FROM t_homework_submit WHERE grade_status = 1 AND score < 60")
    long countAllLowScoreSubmissions();

    @Select("""
            SELECT s.id, s.submit_time AS submitTime, s.grade_status AS gradeStatus, s.score,
                   h.title AS homeworkTitle, COALESCE(u.nickname, u.username) AS studentName
            FROM t_homework_submit s
            JOIN t_homework h ON s.homework_id = h.id
            JOIN t_user u ON s.student_id = u.id
            ORDER BY s.submit_time DESC, s.id DESC
            LIMIT 8
            """)
    List<Map<String, Object>> selectAllRecentSubmissions();

    @Select("""
            SELECT
              CASE
                WHEN r.total_score < 60 THEN '0-59'
                WHEN r.total_score < 70 THEN '60-69'
                WHEN r.total_score < 80 THEN '70-79'
                WHEN r.total_score < 90 THEN '80-89'
                ELSE '90-100'
              END AS scoreRange,
              COUNT(*) AS count,
              ROUND(AVG(r.total_score), 1) AS avgScore
            FROM t_exam_record r
            JOIN t_exam_session s ON r.exam_session_id = s.id
            WHERE s.teacher_id = #{teacherId}
              AND r.status = 2
              AND r.total_score IS NOT NULL
            GROUP BY scoreRange
            ORDER BY MIN(r.total_score)
            """)
    List<Map<String, Object>> selectTeacherScoreDistribution(Integer teacherId);

    @Select("""
            SELECT
              CASE
                WHEN r.total_score < 60 THEN '0-59'
                WHEN r.total_score < 70 THEN '60-69'
                WHEN r.total_score < 80 THEN '70-79'
                WHEN r.total_score < 90 THEN '80-89'
                ELSE '90-100'
              END AS scoreRange,
              COUNT(*) AS count,
              ROUND(AVG(r.total_score), 1) AS avgScore
            FROM t_exam_record r
            WHERE r.status = 2
              AND r.total_score IS NOT NULL
            GROUP BY scoreRange
            ORDER BY MIN(r.total_score)
            """)
    List<Map<String, Object>> selectAllScoreDistribution();

    @Select("""
            SELECT r.submit_time AS submitTime, r.total_score AS score,
                   s.title AS examTitle, COALESCE(u.nickname, u.username) AS studentName
            FROM t_exam_record r
            JOIN t_exam_session s ON r.exam_session_id = s.id
            JOIN t_user u ON r.student_id = u.id
            WHERE s.teacher_id = #{teacherId}
              AND r.status = 2
              AND r.total_score IS NOT NULL
            ORDER BY r.submit_time DESC
            LIMIT 20
            """)
    List<Map<String, Object>> selectTeacherRecentExams(Integer teacherId);

    @Select("""
            SELECT r.submit_time AS submitTime, r.total_score AS score,
                   s.title AS examTitle, COALESCE(u.nickname, u.username) AS studentName
            FROM t_exam_record r
            JOIN t_exam_session s ON r.exam_session_id = s.id
            JOIN t_user u ON r.student_id = u.id
            WHERE r.status = 2
              AND r.total_score IS NOT NULL
            ORDER BY r.submit_time DESC
            LIMIT 20
            """)
    List<Map<String, Object>> selectAllRecentExams();
}
