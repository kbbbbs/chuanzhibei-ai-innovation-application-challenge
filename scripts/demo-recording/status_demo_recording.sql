SET NAMES utf8mb4;
SET @demo_password = '$2a$10$MumA.oDHrQ1YbJGVWDXALeTyNcNhBjTlsHzVJBHFm9TY3fiTXYxb6';

SELECT
    CASE
        WHEN
            -- 账号存在、启用、密码统一，且每个账号恰好只有一个正确主角色。
            (SELECT COUNT(*) FROM t_user
             WHERE username IN ('demo_admin', 'demo_teacher', 'demo_student')
               AND status = 1
               AND password = @demo_password) = 3
            AND (SELECT COUNT(*)
                 FROM t_user_role ur
                 JOIN t_user u ON u.id = ur.user_id
                 WHERE u.username IN ('demo_admin', 'demo_teacher', 'demo_student')) = 3
            AND (SELECT COUNT(*)
                 FROM t_user_role ur
                 JOIN t_user u ON u.id = ur.user_id
                 JOIN t_role r ON r.id = ur.role_id
                 WHERE (u.username = 'demo_admin' AND r.role_code = 'admin')
                    OR (u.username = 'demo_teacher' AND r.role_code = 'teacher')
                    OR (u.username = 'demo_student' AND r.role_code = 'student')) = 3

            -- 课程、唯一课时、教学班、学生成员、学习进度形成同一条关系链。
            AND (SELECT COUNT(*)
                 FROM t_course c
                 JOIN t_course_category cc ON cc.id = c.category_id
                 JOIN t_user teacher ON teacher.id = c.teacher_id
                 WHERE c.title = 'DEMO_互动式机器学习实验'
                   AND cc.name = 'DEMO_录屏精品课'
                   AND teacher.username = 'demo_teacher'
                   AND c.status = 1) = 1
            AND (SELECT COUNT(*)
                 FROM t_chapter ch
                 JOIN t_course c ON c.id = ch.course_id
                 WHERE c.title = 'DEMO_互动式机器学习实验'
                   AND ch.title = 'DEMO_第一章：梯度下降'
                   AND ch.status = 1) = 1
            AND (SELECT COUNT(*)
                 FROM t_lesson l
                 JOIN t_chapter ch ON ch.id = l.chapter_id
                 JOIN t_course c ON c.id = ch.course_id
                 WHERE c.title = 'DEMO_互动式机器学习实验'
                   AND l.title = 'DEMO_互动实验：学习率调参'
                   AND l.resource_type = 'interactive_json_builtin'
                   AND l.status = 1) = 1
            AND (SELECT COUNT(*)
                 FROM t_teaching_class tc
                 JOIN t_course c ON c.id = tc.course_id
                 JOIN t_user teacher ON teacher.id = tc.teacher_id
                 WHERE tc.class_name = 'DEMO_2026春_人工智能实验班'
                   AND c.title = 'DEMO_互动式机器学习实验'
                   AND teacher.username = 'demo_teacher'
                   AND tc.status = 1) = 1
            AND (SELECT COUNT(*)
                 FROM t_class_student cs
                 JOIN t_teaching_class tc ON tc.id = cs.class_id
                 JOIN t_user student ON student.id = cs.student_id
                 WHERE tc.class_name = 'DEMO_2026春_人工智能实验班'
                   AND student.username = 'demo_student'
                   AND cs.status = 1) = 1
            AND (SELECT COUNT(*)
                 FROM t_class_student cs
                 JOIN t_teaching_class tc ON tc.id = cs.class_id
                 WHERE tc.class_name = 'DEMO_2026春_人工智能实验班') = 1
            AND (SELECT COUNT(*)
                 FROM t_learning_progress lp
                 JOIN t_teaching_class tc ON tc.id = lp.class_id
                 JOIN t_user student ON student.id = lp.student_id
                 JOIN t_lesson l ON l.id = lp.lesson_id
                 WHERE tc.class_name = 'DEMO_2026春_人工智能实验班'
                   AND student.username = 'demo_student'
                   AND l.title = 'DEMO_互动实验：学习率调参'
                   AND lp.progress = 72) = 1

            -- 行政班必须指向同一课程，并且恰好包含专用学生。
            AND (SELECT COUNT(*)
                 FROM t_administrative_class ac
                 JOIN t_course c ON c.id = ac.course_id
                 JOIN t_administrative_class_student acs ON acs.administrative_class_id = ac.id
                 JOIN t_user student ON student.id = acs.student_id
                 WHERE ac.class_code = 'DEMO_2026_AI_01'
                   AND ac.class_name = 'DEMO_人工智能实验行政班'
                   AND c.title = 'DEMO_互动式机器学习实验'
                   AND student.username = 'demo_student'
                   AND ac.status = 1
                   AND acs.status = 1) = 1

            -- 考试试卷、两题、四种录屏场景和三条答卷状态必须完整。
            AND (SELECT COUNT(*)
                 FROM t_exam_paper ep
                 JOIN t_course c ON c.id = ep.course_id
                 JOIN t_user teacher ON teacher.id = ep.teacher_id
                 WHERE ep.title = 'DEMO_梯度下降基础试卷'
                   AND c.title = 'DEMO_互动式机器学习实验'
                   AND teacher.username = 'demo_teacher'
                   AND ep.status = 1
                   AND ep.total_score = 100.00) = 1
            AND (SELECT COUNT(*)
                 FROM t_exam_paper_question epq
                 JOIN t_exam_paper ep ON ep.id = epq.paper_id
                 JOIN t_question q ON q.id = epq.question_id
                 WHERE ep.title = 'DEMO_梯度下降基础试卷'
                   AND q.status = 1
                   AND ((q.question_type = 'single' AND epq.score = 60.00)
                     OR (q.question_type = 'short_answer' AND epq.score = 40.00))) = 2
            AND (SELECT COALESCE(SUM(epq.score), 0)
                 FROM t_exam_paper_question epq
                 JOIN t_exam_paper ep ON ep.id = epq.paper_id
                 WHERE ep.title = 'DEMO_梯度下降基础试卷') = 100.00
            AND (SELECT COUNT(*) FROM t_exam_session WHERE LEFT(title, 5) = 'DEMO_') = 4
            AND (SELECT COUNT(*)
                 FROM t_exam_session es
                 JOIN t_teaching_class tc ON tc.id = es.class_id
                 JOIN t_exam_paper ep ON ep.id = es.paper_id
                 JOIN t_user teacher ON teacher.id = es.teacher_id
                 WHERE LEFT(es.title, 5) = 'DEMO_'
                   AND tc.class_name = 'DEMO_2026春_人工智能实验班'
                   AND ep.title = 'DEMO_梯度下降基础试卷'
                   AND teacher.username = 'demo_teacher') = 4
            AND (SELECT COUNT(*) FROM t_exam_session WHERE LEFT(title, 5) = 'DEMO_' AND status = 4) = 2
            AND (SELECT COUNT(*) FROM t_exam_session WHERE LEFT(title, 5) = 'DEMO_' AND status = 3) = 1
            AND (SELECT COUNT(*) FROM t_exam_session
                 WHERE title = 'DEMO_随堂测验（可立即参加）'
                   AND status = 1 AND start_time <= NOW() AND end_time > NOW()) = 1
            AND (SELECT COUNT(*)
                 FROM t_exam_record er
                 JOIN t_exam_session es ON es.id = er.exam_session_id
                 WHERE LEFT(es.title, 5) = 'DEMO_') = 3
            AND (SELECT COUNT(*)
                 FROM t_exam_record er
                 JOIN t_exam_session es ON es.id = er.exam_session_id
                 JOIN t_user student ON student.id = er.student_id
                 WHERE LEFT(es.title, 5) = 'DEMO_'
                   AND student.username = 'demo_student'
                   AND er.status = 2) = 2
            AND (SELECT COUNT(*)
                 FROM t_exam_record er
                 JOIN t_exam_session es ON es.id = er.exam_session_id
                 JOIN t_user student ON student.id = er.student_id
                 WHERE es.title = 'DEMO_综合训练（待教师批改）'
                   AND student.username = 'demo_student'
                   AND er.status = 3) = 1
            AND (SELECT COUNT(*)
                 FROM t_exam_record er
                 JOIN t_exam_session es ON es.id = er.exam_session_id
                 WHERE es.title = 'DEMO_随堂测验（可立即参加）') = 0
            AND (SELECT COUNT(*)
                 FROM t_exam_answer ea
                 JOIN t_exam_record er ON er.id = ea.exam_record_id
                 JOIN t_exam_session es ON es.id = er.exam_session_id
                 WHERE LEFT(es.title, 5) = 'DEMO_') = 6
            AND (SELECT COUNT(*)
                 FROM t_exam_answer ea
                 JOIN t_exam_record er ON er.id = ea.exam_record_id
                 JOIN t_exam_session es ON es.id = er.exam_session_id
                 WHERE es.title = 'DEMO_综合训练（待教师批改）'
                   AND ea.grade_status = 3
                   AND ea.student_answer IS NOT NULL) = 1

            -- 新版作业必须发布、两题合计 100 分，且学生尚未产生答卷。
            AND (SELECT COUNT(*)
                 FROM t_homework_paper hp
                 JOIN t_course c ON c.id = hp.course_id
                 JOIN t_user teacher ON teacher.id = hp.teacher_id
                 WHERE hp.title = 'DEMO_梯度下降课后作业试卷'
                   AND c.title = 'DEMO_互动式机器学习实验'
                   AND teacher.username = 'demo_teacher'
                   AND hp.status = 1
                   AND hp.total_score = 100.00) = 1
            AND (SELECT COUNT(*)
                 FROM t_homework_paper_question hpq
                 JOIN t_homework_paper hp ON hp.id = hpq.paper_id
                 JOIN t_question q ON q.id = hpq.question_id
                 WHERE hp.title = 'DEMO_梯度下降课后作业试卷'
                   AND q.status = 1
                   AND ((q.question_type = 'single' AND hpq.score = 60.00)
                     OR (q.question_type = 'short_answer' AND hpq.score = 40.00))) = 2
            AND (SELECT COALESCE(SUM(hpq.score), 0)
                 FROM t_homework_paper_question hpq
                 JOIN t_homework_paper hp ON hp.id = hpq.paper_id
                 WHERE hp.title = 'DEMO_梯度下降课后作业试卷') = 100.00
            AND (SELECT COUNT(*)
                 FROM t_homework_session hs
                 JOIN t_homework_paper hp ON hp.id = hs.paper_id
                 JOIN t_teaching_class tc ON tc.id = hs.class_id
                 JOIN t_user teacher ON teacher.id = hs.teacher_id
                 WHERE hs.title = 'DEMO_学习率调参课后作业（待完成）'
                   AND hp.title = 'DEMO_梯度下降课后作业试卷'
                   AND tc.class_name = 'DEMO_2026春_人工智能实验班'
                   AND teacher.username = 'demo_teacher'
                   AND hs.status = 1
                   AND hs.start_time <= NOW()
                   AND hs.end_time > NOW()) = 1
            AND (SELECT COUNT(*)
                 FROM t_homework_record hr
                 JOIN t_homework_session hs ON hs.id = hr.homework_session_id
                 WHERE hs.title = 'DEMO_学习率调参课后作业（待完成）') = 0

            -- 推荐和岗位必须与专用课程/学生关联。
            AND (SELECT COUNT(*)
                 FROM t_home_course_recommendation hcr
                 JOIN t_course c ON c.id = hcr.course_id
                 WHERE c.title = 'DEMO_互动式机器学习实验'
                   AND hcr.status = 1
                   AND hcr.sort_order = 1) = 1
            AND (SELECT COUNT(*) FROM t_work_study WHERE LEFT(job_name, 5) = 'DEMO_' AND status = 1) = 2
            AND (SELECT COUNT(*)
                 FROM t_work_study_student wss
                 JOIN t_work_study ws ON ws.id = wss.work_study_id
                 JOIN t_user student ON student.id = wss.student_id
                 WHERE ws.job_name = 'DEMO_互动课程内容助理'
                   AND student.username = 'demo_student'
                   AND wss.audit_status = 0
                   AND wss.status = 1) = 1
            AND (SELECT COUNT(*) FROM t_teacher_application
                 WHERE username = 'DEMO_teacher_candidate' AND status = 0) = 1
        THEN 'READY'
        ELSE 'CHECK_REQUIRED'
    END AS demo_recording_status,
    NOW() AS checked_at;

SELECT
    u.id,
    u.username,
    u.nickname,
    r.role_code,
    u.status,
    CASE WHEN u.password = @demo_password THEN '123456 (BCrypt)' ELSE 'custom' END AS password_profile
FROM t_user u
LEFT JOIN t_user_role ur ON ur.user_id = u.id
LEFT JOIN t_role r ON r.id = ur.role_id
WHERE u.username IN ('demo_admin', 'demo_teacher', 'demo_student')
ORDER BY FIELD(u.username, 'demo_admin', 'demo_teacher', 'demo_student'), r.id;

SELECT
    c.id AS course_id,
    c.title AS course_title,
    tc.id AS class_id,
    tc.class_name,
    l.id AS lesson_id,
    l.title AS lesson_title,
    l.resource_type,
    lp.progress,
    CONCAT('/course-learn/', c.id, '?classId=', tc.id, '&lessonId=', l.id) AS student_learning_route,
    CASE
        WHEN source_lesson.id IS NULL THEN 'fallback_json'
        WHEN SHA2(COALESCE(l.content, ''), 256) = SHA2(COALESCE(source_lesson.content, ''), 256)
            THEN 'copied_from_lesson_900'
        ELSE 'customized'
    END AS interactive_content_source
FROM t_course c
JOIN t_chapter ch ON ch.course_id = c.id
JOIN t_lesson l ON l.chapter_id = ch.id
JOIN t_teaching_class tc ON tc.course_id = c.id
JOIN t_user teacher ON teacher.id = tc.teacher_id AND teacher.username = 'demo_teacher'
JOIN t_class_student cs ON cs.class_id = tc.id
JOIN t_user student ON student.id = cs.student_id AND student.username = 'demo_student'
LEFT JOIN t_learning_progress lp
       ON lp.class_id = tc.id AND lp.student_id = student.id AND lp.lesson_id = l.id
LEFT JOIN t_lesson source_lesson ON source_lesson.id = 900
WHERE c.title = 'DEMO_互动式机器学习实验'
ORDER BY c.id, ch.sort_order, l.sort_order;

SELECT
    es.id AS exam_id,
    es.title AS exam_title,
    es.status AS db_exam_status,
    CASE es.status
        WHEN 0 THEN 'DRAFT'
        WHEN 1 THEN 'PUBLISHED'
        WHEN 2 THEN 'PUBLISHED_COMPAT'
        WHEN 3 THEN 'CLOSED'
        WHEN 4 THEN 'RESULTS_SUBMITTED'
        ELSE 'UNKNOWN'
    END AS exam_status,
    er.id AS record_id,
    er.status AS db_submission_status,
    CASE er.status
        WHEN 0 THEN 'IN_PROGRESS'
        WHEN 1 THEN 'SUBMITTED'
        WHEN 2 THEN 'GRADED'
        WHEN 3 THEN 'PENDING_MANUAL_REVIEW'
        ELSE 'NO_RECORD'
    END AS submission_status,
    er.objective_score,
    er.subjective_score,
    er.total_score,
    (SELECT COUNT(*) FROM t_exam_answer ea WHERE ea.exam_record_id = er.id) AS answer_count
FROM t_exam_session es
LEFT JOIN t_exam_record er ON er.exam_session_id = es.id
WHERE LEFT(es.title, 5) = 'DEMO_'
ORDER BY es.start_time, es.id;

SELECT
    hs.id AS homework_id,
    hs.title AS homework_title,
    hs.status AS db_homework_status,
    CASE hs.status WHEN 1 THEN 'PUBLISHED' WHEN 3 THEN 'CLOSED' ELSE 'DRAFT' END AS homework_status,
    hp.id AS paper_id,
    hp.title AS paper_title,
    hp.status AS db_paper_status,
    hp.total_score,
    hs.start_time,
    hs.end_time,
    (SELECT COUNT(*) FROM t_homework_paper_question hpq WHERE hpq.paper_id = hp.id) AS question_count,
    (SELECT COUNT(*) FROM t_homework_record hr WHERE hr.homework_session_id = hs.id) AS submission_count
FROM t_homework_session hs
JOIN t_homework_paper hp ON hp.id = hs.paper_id
WHERE hs.title = 'DEMO_学习率调参课后作业（待完成）';

SELECT
    ws.id AS job_id,
    ws.job_name,
    ws.status AS job_status,
    wss.id AS application_id,
    wss.audit_status,
    CASE wss.audit_status WHEN 0 THEN 'PENDING' WHEN 1 THEN 'APPROVED' WHEN 2 THEN 'REJECTED' ELSE 'NOT_APPLIED' END AS application_status
FROM t_work_study ws
LEFT JOIN t_work_study_student wss
       ON wss.work_study_id = ws.id
      AND wss.student_id = (SELECT id FROM t_user WHERE username = 'demo_student' LIMIT 1)
WHERE LEFT(ws.job_name, 5) = 'DEMO_'
ORDER BY ws.id;

SELECT
    'summary' AS section,
    (SELECT COUNT(*) FROM t_user WHERE username IN ('demo_admin', 'demo_teacher', 'demo_student')) AS accounts,
    (SELECT COUNT(*) FROM t_course WHERE LEFT(title, 5) = 'DEMO_') AS courses,
    (SELECT COUNT(*) FROM t_teaching_class WHERE LEFT(class_name, 5) = 'DEMO_') AS teaching_classes,
    (SELECT COUNT(*) FROM t_class_student cs
     JOIN t_teaching_class tc ON tc.id = cs.class_id
     WHERE LEFT(tc.class_name, 5) = 'DEMO_') AS class_memberships,
    (SELECT COUNT(*) FROM t_administrative_class WHERE LEFT(class_code, 5) = 'DEMO_') AS administrative_classes,
    (SELECT COUNT(*) FROM t_exam_session WHERE LEFT(title, 5) = 'DEMO_') AS exams,
    (SELECT COUNT(*) FROM t_exam_record er
     JOIN t_exam_session es ON es.id = er.exam_session_id
     WHERE LEFT(es.title, 5) = 'DEMO_') AS exam_records,
    (SELECT COUNT(*) FROM t_homework_session WHERE LEFT(title, 5) = 'DEMO_') AS homeworks,
    (SELECT COUNT(*) FROM t_homework_record hr
     JOIN t_homework_session hs ON hs.id = hr.homework_session_id
     WHERE LEFT(hs.title, 5) = 'DEMO_') AS homework_records,
    (SELECT COUNT(*) FROM t_work_study WHERE LEFT(job_name, 5) = 'DEMO_') AS jobs,
    (SELECT COUNT(*) FROM t_home_course_recommendation hcr
     JOIN t_course c ON c.id = hcr.course_id
     WHERE LEFT(c.title, 5) = 'DEMO_') AS recommendations,
    (SELECT COUNT(*) FROM t_teacher_application
     WHERE username = 'DEMO_teacher_candidate' AND status = 0) AS pending_teacher_applications;
