-- 仅清理 demo_ 专用账号产生的数据，以及 DEMO_ 前缀/专用教师拥有的数据。
-- 不 DROP 数据库，不删除普通账号，不修改 course=109 / lesson=900。

SET NAMES utf8mb4;
START TRANSACTION;

SET @demo_admin_id = (SELECT id FROM t_user WHERE username = 'demo_admin' LIMIT 1);
SET @demo_teacher_id = (SELECT id FROM t_user WHERE username = 'demo_teacher' LIMIT 1);
SET @demo_student_id = (SELECT id FROM t_user WHERE username = 'demo_student' LIMIT 1);

-- 专用账号在论文、Agent、学习笔记和评论中产生的可变活动。
DELETE FROM t_agent_pending_action
WHERE created_by IN (@demo_admin_id, @demo_teacher_id, @demo_student_id);

DELETE FROM t_agent_session
WHERE user_id IN (@demo_admin_id, @demo_teacher_id, @demo_student_id);

DELETE FROM paper_user_mark
WHERE user_id IN (@demo_admin_id, @demo_teacher_id, @demo_student_id);

DELETE lc
FROM lesson_comment lc
LEFT JOIN t_lesson l ON l.id = lc.lesson_id
LEFT JOIN t_chapter ch ON ch.id = l.chapter_id
LEFT JOIN t_course c ON c.id = ch.course_id
WHERE lc.user_id IN (@demo_admin_id, @demo_teacher_id, @demo_student_id)
   OR LEFT(c.title, 5) = 'DEMO_'
   OR c.teacher_id = @demo_teacher_id;

DELETE sn
FROM study_note sn
LEFT JOIN t_course c ON c.id = sn.course_id
WHERE sn.user_id IN (@demo_admin_id, @demo_teacher_id, @demo_student_id)
   OR LEFT(c.title, 5) = 'DEMO_'
   OR c.teacher_id = @demo_teacher_id;

-- 岗位：删除 DEMO_ 岗位及专用学生的申请，普通学生/普通岗位不受影响。
DELETE wss
FROM t_work_study_student wss
LEFT JOIN t_work_study ws ON ws.id = wss.work_study_id
WHERE wss.student_id = @demo_student_id
   OR LEFT(ws.job_name, 5) = 'DEMO_';

DELETE FROM t_work_study
WHERE LEFT(job_name, 5) = 'DEMO_';

DELETE FROM t_teacher_application
WHERE LEFT(username, 5) = 'DEMO_';

DELETE FROM t_operation_log
WHERE user_id IN (@demo_admin_id, @demo_teacher_id, @demo_student_id)
   OR LEFT(operation, 5) = 'DEMO_';

-- 先删考试答题，再删安排/试卷/题目，严格遵循外键方向。
DELETE ea
FROM t_exam_answer ea
JOIN t_exam_record er ON er.id = ea.exam_record_id
LEFT JOIN t_exam_session es ON es.id = er.exam_session_id
LEFT JOIN t_teaching_class tc ON tc.id = es.class_id
LEFT JOIN t_course c ON c.id = tc.course_id
WHERE er.student_id = @demo_student_id
   OR es.teacher_id = @demo_teacher_id
   OR LEFT(es.title, 5) = 'DEMO_'
   OR LEFT(c.title, 5) = 'DEMO_'
   OR c.teacher_id = @demo_teacher_id;

DELETE er
FROM t_exam_record er
LEFT JOIN t_exam_session es ON es.id = er.exam_session_id
LEFT JOIN t_teaching_class tc ON tc.id = es.class_id
LEFT JOIN t_course c ON c.id = tc.course_id
WHERE er.student_id = @demo_student_id
   OR es.teacher_id = @demo_teacher_id
   OR LEFT(es.title, 5) = 'DEMO_'
   OR LEFT(c.title, 5) = 'DEMO_'
   OR c.teacher_id = @demo_teacher_id;

DELETE es
FROM t_exam_session es
LEFT JOIN t_teaching_class tc ON tc.id = es.class_id
LEFT JOIN t_exam_paper ep ON ep.id = es.paper_id
LEFT JOIN t_course c ON c.id = tc.course_id
WHERE es.teacher_id = @demo_teacher_id
   OR LEFT(es.title, 5) = 'DEMO_'
   OR LEFT(ep.title, 5) = 'DEMO_'
   OR LEFT(c.title, 5) = 'DEMO_'
   OR c.teacher_id = @demo_teacher_id;

DELETE epq
FROM t_exam_paper_question epq
JOIN t_exam_paper ep ON ep.id = epq.paper_id
LEFT JOIN t_course c ON c.id = ep.course_id
WHERE ep.teacher_id = @demo_teacher_id
   OR LEFT(ep.title, 5) = 'DEMO_'
   OR LEFT(c.title, 5) = 'DEMO_'
   OR c.teacher_id = @demo_teacher_id;

DELETE ep
FROM t_exam_paper ep
LEFT JOIN t_course c ON c.id = ep.course_id
WHERE ep.teacher_id = @demo_teacher_id
   OR LEFT(ep.title, 5) = 'DEMO_'
   OR LEFT(c.title, 5) = 'DEMO_'
   OR c.teacher_id = @demo_teacher_id;

DELETE q
FROM t_question q
LEFT JOIN t_course c ON c.id = q.course_id
WHERE q.teacher_id = @demo_teacher_id
   OR LEFT(q.title, 5) = 'DEMO_'
   OR LEFT(c.title, 5) = 'DEMO_'
   OR c.teacher_id = @demo_teacher_id;

-- 清理两代作业模型中由专用账号/课程/教学班产生的数据。
DELETE ha
FROM t_homework_answer ha
JOIN t_homework_record hr ON hr.id = ha.homework_record_id
LEFT JOIN t_homework_session hs ON hs.id = hr.homework_session_id
LEFT JOIN t_teaching_class tc ON tc.id = hs.class_id
LEFT JOIN t_course c ON c.id = tc.course_id
WHERE hr.student_id = @demo_student_id
   OR hs.teacher_id = @demo_teacher_id
   OR LEFT(hs.title, 5) = 'DEMO_'
   OR LEFT(c.title, 5) = 'DEMO_'
   OR c.teacher_id = @demo_teacher_id;

DELETE hr
FROM t_homework_record hr
LEFT JOIN t_homework_session hs ON hs.id = hr.homework_session_id
LEFT JOIN t_teaching_class tc ON tc.id = hs.class_id
LEFT JOIN t_course c ON c.id = tc.course_id
WHERE hr.student_id = @demo_student_id
   OR hs.teacher_id = @demo_teacher_id
   OR LEFT(hs.title, 5) = 'DEMO_'
   OR LEFT(c.title, 5) = 'DEMO_'
   OR c.teacher_id = @demo_teacher_id;

DELETE hs
FROM t_homework_session hs
LEFT JOIN t_teaching_class tc ON tc.id = hs.class_id
LEFT JOIN t_homework_paper hp ON hp.id = hs.paper_id
LEFT JOIN t_course c ON c.id = tc.course_id
WHERE hs.teacher_id = @demo_teacher_id
   OR LEFT(hs.title, 5) = 'DEMO_'
   OR LEFT(hp.title, 5) = 'DEMO_'
   OR LEFT(c.title, 5) = 'DEMO_'
   OR c.teacher_id = @demo_teacher_id;

DELETE hpq
FROM t_homework_paper_question hpq
JOIN t_homework_paper hp ON hp.id = hpq.paper_id
LEFT JOIN t_course c ON c.id = hp.course_id
WHERE hp.teacher_id = @demo_teacher_id
   OR LEFT(hp.title, 5) = 'DEMO_'
   OR LEFT(c.title, 5) = 'DEMO_'
   OR c.teacher_id = @demo_teacher_id;

DELETE hp
FROM t_homework_paper hp
LEFT JOIN t_course c ON c.id = hp.course_id
WHERE hp.teacher_id = @demo_teacher_id
   OR LEFT(hp.title, 5) = 'DEMO_'
   OR LEFT(c.title, 5) = 'DEMO_'
   OR c.teacher_id = @demo_teacher_id;

DELETE hws
FROM t_homework_submit hws
LEFT JOIN t_homework hw ON hw.id = hws.homework_id
LEFT JOIN t_teaching_class tc ON tc.id = hw.class_id
LEFT JOIN t_course c ON c.id = tc.course_id
WHERE hws.student_id = @demo_student_id
   OR hw.teacher_id = @demo_teacher_id
   OR LEFT(hw.title, 5) = 'DEMO_'
   OR LEFT(c.title, 5) = 'DEMO_'
   OR c.teacher_id = @demo_teacher_id;

DELETE hw
FROM t_homework hw
LEFT JOIN t_teaching_class tc ON tc.id = hw.class_id
LEFT JOIN t_course c ON c.id = tc.course_id
WHERE hw.teacher_id = @demo_teacher_id
   OR LEFT(hw.title, 5) = 'DEMO_'
   OR LEFT(c.title, 5) = 'DEMO_'
   OR c.teacher_id = @demo_teacher_id;

-- 成绩导入/成绩表没有统一的级联删除策略，先显式清理专用关系。
DELETE gr
FROM t_grade_record gr
LEFT JOIN t_teaching_class tc ON tc.id = gr.class_id
LEFT JOIN t_course c ON c.id = gr.course_id
WHERE gr.student_id = @demo_student_id
   OR gr.teacher_id = @demo_teacher_id
   OR LEFT(tc.class_name, 5) = 'DEMO_'
   OR LEFT(c.title, 5) = 'DEMO_'
   OR c.teacher_id = @demo_teacher_id;

DELETE gil
FROM t_grade_import_log gil
LEFT JOIN t_teaching_class tc ON tc.id = gil.class_id
LEFT JOIN t_course c ON c.id = tc.course_id
WHERE gil.teacher_id = @demo_teacher_id
   OR LEFT(tc.class_name, 5) = 'DEMO_'
   OR LEFT(c.title, 5) = 'DEMO_'
   OR c.teacher_id = @demo_teacher_id;

DELETE lp
FROM t_learning_progress lp
LEFT JOIN t_teaching_class tc ON tc.id = lp.class_id
LEFT JOIN t_course c ON c.id = tc.course_id
WHERE lp.student_id = @demo_student_id
   OR LEFT(tc.class_name, 5) = 'DEMO_'
   OR LEFT(c.title, 5) = 'DEMO_'
   OR c.teacher_id = @demo_teacher_id;

DELETE lr
FROM t_learning_record lr
LEFT JOIN t_teaching_class tc ON tc.id = lr.class_id
LEFT JOIN t_course c ON c.id = tc.course_id
WHERE lr.student_id = @demo_student_id
   OR LEFT(tc.class_name, 5) = 'DEMO_'
   OR LEFT(c.title, 5) = 'DEMO_'
   OR c.teacher_id = @demo_teacher_id;

DELETE n
FROM t_notice n
LEFT JOIN t_teaching_class tc ON tc.id = n.class_id
LEFT JOIN t_course c ON c.id = tc.course_id
WHERE n.publisher_id IN (@demo_admin_id, @demo_teacher_id, @demo_student_id)
   OR LEFT(n.title, 5) = 'DEMO_'
   OR LEFT(tc.class_name, 5) = 'DEMO_'
   OR LEFT(c.title, 5) = 'DEMO_';

DELETE acs
FROM t_administrative_class_student acs
LEFT JOIN t_administrative_class ac ON ac.id = acs.administrative_class_id
LEFT JOIN t_course c ON c.id = ac.course_id
WHERE acs.student_id = @demo_student_id
   OR LEFT(ac.class_code, 5) = 'DEMO_'
   OR LEFT(ac.class_name, 5) = 'DEMO_'
   OR LEFT(c.title, 5) = 'DEMO_';

DELETE ac
FROM t_administrative_class ac
LEFT JOIN t_course c ON c.id = ac.course_id
WHERE LEFT(ac.class_code, 5) = 'DEMO_'
   OR LEFT(ac.class_name, 5) = 'DEMO_'
   OR LEFT(c.title, 5) = 'DEMO_'
   OR c.teacher_id = @demo_teacher_id;

DELETE cs
FROM t_class_student cs
LEFT JOIN t_teaching_class tc ON tc.id = cs.class_id
LEFT JOIN t_course c ON c.id = tc.course_id
WHERE cs.student_id = @demo_student_id
   OR LEFT(tc.class_name, 5) = 'DEMO_'
   OR LEFT(c.title, 5) = 'DEMO_'
   OR c.teacher_id = @demo_teacher_id;

DELETE hcr
FROM t_home_course_recommendation hcr
JOIN t_course c ON c.id = hcr.course_id
WHERE LEFT(c.title, 5) = 'DEMO_'
   OR c.teacher_id = @demo_teacher_id;

DELETE aip
FROM t_ai_preparation aip
LEFT JOIN t_course c ON c.id = aip.published_course_id
WHERE aip.teacher_id = @demo_teacher_id
   OR LEFT(c.title, 5) = 'DEMO_'
   OR c.teacher_id = @demo_teacher_id;

DELETE tc
FROM t_teaching_class tc
LEFT JOIN t_course c ON c.id = tc.course_id
WHERE tc.teacher_id = @demo_teacher_id
   OR LEFT(tc.class_name, 5) = 'DEMO_'
   OR LEFT(c.title, 5) = 'DEMO_'
   OR c.teacher_id = @demo_teacher_id;

DELETE FROM t_course
WHERE teacher_id = @demo_teacher_id
   OR LEFT(title, 5) = 'DEMO_';

DELETE FROM t_course_category
WHERE LEFT(name, 5) = 'DEMO_';

COMMIT;

SELECT
    'DEMO_RECORDING_CLEANED' AS result,
    (SELECT COUNT(*) FROM t_user
     WHERE username IN ('demo_admin', 'demo_teacher', 'demo_student')) AS preserved_demo_accounts,
    (SELECT COUNT(*) FROM t_course WHERE LEFT(title, 5) = 'DEMO_') AS remaining_demo_courses,
    (SELECT COUNT(*) FROM t_teaching_class WHERE LEFT(class_name, 5) = 'DEMO_') AS remaining_demo_classes,
    (SELECT COUNT(*) FROM t_teacher_application WHERE LEFT(username, 5) = 'DEMO_') AS remaining_demo_applications;
