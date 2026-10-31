-- AI Learning 三角色录屏演示数据
--
-- 约定：
--   1. 所有本脚本拥有的数据使用 demo_ 账号或 DEMO_ 文本前缀标记。
--   2. 账号明文密码仅用于本地演示：123456；数据库保存固定 BCrypt 哈希，避免登录后升级导致状态误报。
--   3. 不固定任何现网自增 ID；每次执行均通过自然标记重新解析关联 ID。
--   4. 优先复制现有 course=109 / lesson=900 的互动课内容，但绝不修改它们。
--   5. 脚本可重复执行；重复执行会把录屏基线数据校准回相同的数量和状态。

SET NAMES utf8mb4;
START TRANSACTION;

SET @demo_password = '$2a$10$MumA.oDHrQ1YbJGVWDXALeTyNcNhBjTlsHzVJBHFm9TY3fiTXYxb6';

-- ---------------------------------------------------------------------------
-- 1. 三角色演示账号
-- ---------------------------------------------------------------------------
INSERT INTO t_user
    (username, password, nickname, email, phone, avatar, status, create_time, update_time)
VALUES
    ('demo_admin', @demo_password, '演示管理员', 'demo.admin@ai-learning.local', '13990000001', NULL, 1, NOW(), NOW()),
    ('demo_teacher', @demo_password, '陈老师（演示）', 'demo.teacher@ai-learning.local', '13990000002', NULL, 1, NOW(), NOW()),
    ('demo_student', @demo_password, '林同学（演示）', 'demo.student@ai-learning.local', '13990000003', NULL, 1, NOW(), NOW())
AS new
ON DUPLICATE KEY UPDATE
    password = new.password,
    nickname = new.nickname,
    email = new.email,
    phone = new.phone,
    status = 1,
    update_time = NOW();

SET @demo_admin_id = (SELECT id FROM t_user WHERE username = 'demo_admin' LIMIT 1);
SET @demo_teacher_id = (SELECT id FROM t_user WHERE username = 'demo_teacher' LIMIT 1);
SET @demo_student_id = (SELECT id FROM t_user WHERE username = 'demo_student' LIMIT 1);
SET @admin_role_id = (SELECT id FROM t_role WHERE role_code = 'admin' ORDER BY id LIMIT 1);
SET @teacher_role_id = (SELECT id FROM t_role WHERE role_code = 'teacher' ORDER BY id LIMIT 1);
SET @student_role_id = (SELECT id FROM t_role WHERE role_code = 'student' ORDER BY id LIMIT 1);

-- 专用账号只保留一个主角色，避免登录时角色解析受历史脏数据影响。
DELETE FROM t_user_role
WHERE user_id IN (@demo_admin_id, @demo_teacher_id, @demo_student_id);

INSERT INTO t_user_role (user_id, role_id)
VALUES
    (@demo_admin_id, @admin_role_id),
    (@demo_teacher_id, @teacher_role_id),
    (@demo_student_id, @student_role_id);

-- ---------------------------------------------------------------------------
-- 2. 独立演示课程（安全复制 109/900，不改动普通业务课程）
-- ---------------------------------------------------------------------------
SET @demo_category_id = (
    SELECT id FROM t_course_category WHERE name = 'DEMO_录屏精品课' ORDER BY id LIMIT 1
);

INSERT INTO t_course_category (name, description, sort_order, create_time, update_time)
SELECT 'DEMO_录屏精品课', '仅用于三角色录屏，可由 reset 脚本安全重建', 0, NOW(), NOW()
WHERE @demo_category_id IS NULL;

SET @demo_category_id = (
    SELECT id FROM t_course_category WHERE name = 'DEMO_录屏精品课' ORDER BY id LIMIT 1
);

UPDATE t_course_category
SET description = '仅用于三角色录屏，可由 reset 脚本安全重建',
    sort_order = 0,
    update_time = NOW()
WHERE id = @demo_category_id;

-- 只有 ID 与语义同时匹配才复制，避免在另一份数据库里误把 109/900 当成互动课程。
SET @source_course_id = (
    SELECT id FROM t_course
    WHERE id = 109 AND title = '互动' AND status = 1
    LIMIT 1
);
SET @source_lesson_id = (
    SELECT l.id
    FROM t_lesson l
    JOIN t_chapter ch ON ch.id = l.chapter_id
    WHERE l.id = 900
      AND ch.course_id = @source_course_id
      AND l.resource_type = 'interactive_json_builtin'
      AND l.status = 1
    LIMIT 1
);
SET @source_cover = (SELECT cover FROM t_course WHERE id = @source_course_id LIMIT 1);
SET @source_cover_url = (SELECT cover_url FROM t_course WHERE id = @source_course_id LIMIT 1);
SET @source_lesson_content = (SELECT content FROM t_lesson WHERE id = @source_lesson_id LIMIT 1);
SET @source_resource_type = (SELECT resource_type FROM t_lesson WHERE id = @source_lesson_id LIMIT 1);
SET @source_resource_url = (SELECT resource_url FROM t_lesson WHERE id = @source_lesson_id LIMIT 1);

SET @fallback_interactive_content = CAST(JSON_OBJECT(
    'schemaVersion', 1,
    'key', 'demo.gradient-descent',
    'title', '梯度下降：学习率与收敛',
    'subject', '机器学习',
    'summary', '拖动学习率并运行 Python，直观看到收敛、震荡和发散。',
    'estimatedMinutes', 20,
    'blocks', JSON_ARRAY(
        JSON_OBJECT(
            'type', 'markdown',
            'title', '学习目标',
            'content', '理解学习率对梯度下降速度和稳定性的影响。'
        ),
        JSON_OBJECT(
            'type', 'experiment',
            'title', '拖动学习率，观察下降轨迹',
            'description', '依次尝试 0.05、0.12 和 1.1，比较轨迹。',
            'kind', 'gradient',
            'defaults', JSON_OBJECT('learningRate', 0.12, 'startX', -4)
        ),
        JSON_OBJECT(
            'type', 'ide',
            'title', 'Python：手写梯度下降',
            'description', '修改 lr 后重新运行并观察 loss。',
            'language', 'python',
            'starterCode', 'x = -4.0\nlr = 0.12\nfor step in range(10):\n    grad = 2 * x\n    x = x - lr * grad\n    print(step, round(x, 4), round(x ** 2, 4))',
            'expectedOutputHint', '合适的学习率会让 x 逐步接近 0。'
        )
    ),
    'chapterTitle', '机器学习'
) AS CHAR CHARACTER SET utf8mb4);

SET @demo_course_id = (
    SELECT id FROM t_course WHERE title = 'DEMO_互动式机器学习实验' ORDER BY id LIMIT 1
);

INSERT INTO t_course
    (category_id, teacher_id, title, description, cover, cover_url, teacher, difficulty,
     student_count, view_count, rating, status, created_at, updated_at)
SELECT
    @demo_category_id,
    @demo_teacher_id,
    'DEMO_互动式机器学习实验',
    '从学习率调参到 Python 运行，串联学生学习、教师教学和管理员治理的录屏主线。',
    @source_cover,
    @source_cover_url,
    '陈老师（演示）',
    'beginner',
    1,
    128,
    4.9,
    1,
    NOW(),
    NOW()
WHERE @demo_course_id IS NULL;

SET @demo_course_id = (
    SELECT id FROM t_course WHERE title = 'DEMO_互动式机器学习实验' ORDER BY id LIMIT 1
);

UPDATE t_course
SET category_id = @demo_category_id,
    teacher_id = @demo_teacher_id,
    description = '从学习率调参到 Python 运行，串联学生学习、教师教学和管理员治理的录屏主线。',
    cover = @source_cover,
    cover_url = @source_cover_url,
    teacher = '陈老师（演示）',
    difficulty = 'beginner',
    student_count = 1,
    view_count = 128,
    rating = 4.9,
    status = 1,
    updated_at = NOW()
WHERE id = @demo_course_id;

SET @demo_chapter_id = (
    SELECT id FROM t_chapter
    WHERE course_id = @demo_course_id AND title = 'DEMO_第一章：梯度下降'
    ORDER BY id LIMIT 1
);

INSERT INTO t_chapter
    (course_id, title, description, sort_order, status, created_at, updated_at)
SELECT
    @demo_course_id,
    'DEMO_第一章：梯度下降',
    '通过可视化实验理解学习率与收敛。',
    1,
    1,
    NOW(),
    NOW()
WHERE @demo_chapter_id IS NULL;

SET @demo_chapter_id = (
    SELECT id FROM t_chapter
    WHERE course_id = @demo_course_id AND title = 'DEMO_第一章：梯度下降'
    ORDER BY id LIMIT 1
);

UPDATE t_chapter
SET description = '通过可视化实验理解学习率与收敛。',
    sort_order = 1,
    status = 1,
    updated_at = NOW()
WHERE id = @demo_chapter_id;

SET @demo_lesson_id = (
    SELECT id FROM t_lesson
    WHERE chapter_id = @demo_chapter_id AND title = 'DEMO_互动实验：学习率调参'
    ORDER BY id LIMIT 1
);

INSERT INTO t_lesson
    (chapter_id, title, resource_type, resource_url, video_url, video_resource_id, content,
     duration, sort_order, view_count, status, created_at, updated_at)
SELECT
    @demo_chapter_id,
    'DEMO_互动实验：学习率调参',
    COALESCE(NULLIF(@source_resource_type, ''), 'interactive_json_builtin'),
    @source_resource_url,
    NULL,
    NULL,
    COALESCE(NULLIF(@source_lesson_content, ''), @fallback_interactive_content),
    1680,
    1,
    86,
    1,
    NOW(),
    NOW()
WHERE @demo_lesson_id IS NULL;

SET @demo_lesson_id = (
    SELECT id FROM t_lesson
    WHERE chapter_id = @demo_chapter_id AND title = 'DEMO_互动实验：学习率调参'
    ORDER BY id LIMIT 1
);

UPDATE t_lesson
SET resource_type = COALESCE(NULLIF(@source_resource_type, ''), 'interactive_json_builtin'),
    resource_url = @source_resource_url,
    content = COALESCE(NULLIF(@source_lesson_content, ''), @fallback_interactive_content),
    duration = 1680,
    sort_order = 1,
    view_count = 86,
    status = 1,
    updated_at = NOW()
WHERE id = @demo_lesson_id;

-- ---------------------------------------------------------------------------
-- 3. 教学班、行政班、成员和学习进度
-- ---------------------------------------------------------------------------
SET @demo_class_id = (
    SELECT id FROM t_teaching_class
    WHERE class_name = 'DEMO_2026春_人工智能实验班'
    ORDER BY id LIMIT 1
);

INSERT INTO t_teaching_class
    (course_id, teacher_id, class_name, semester, description, status, create_time, update_time)
SELECT
    @demo_course_id,
    @demo_teacher_id,
    'DEMO_2026春_人工智能实验班',
    '2026春',
    '录屏专用教学班：课程、学生、考试和成绩均形成闭环。',
    1,
    NOW(),
    NOW()
WHERE @demo_class_id IS NULL;

SET @demo_class_id = (
    SELECT id FROM t_teaching_class
    WHERE class_name = 'DEMO_2026春_人工智能实验班'
    ORDER BY id LIMIT 1
);

UPDATE t_teaching_class
SET course_id = @demo_course_id,
    teacher_id = @demo_teacher_id,
    semester = '2026春',
    description = '录屏专用教学班：课程、学生、考试和成绩均形成闭环。',
    status = 1,
    update_time = NOW()
WHERE id = @demo_class_id;

INSERT INTO t_class_student (class_id, student_id, join_time, status)
SELECT @demo_class_id, @demo_student_id, NOW() - INTERVAL 30 DAY, 1
WHERE NOT EXISTS (
    SELECT 1 FROM t_class_student
    WHERE class_id = @demo_class_id AND student_id = @demo_student_id
);

UPDATE t_class_student
SET status = 1,
    join_time = NOW() - INTERVAL 30 DAY
WHERE class_id = @demo_class_id AND student_id = @demo_student_id;

SET @demo_admin_class_id = (
    SELECT id FROM t_administrative_class
    WHERE class_code = 'DEMO_2026_AI_01'
    ORDER BY id LIMIT 1
);

INSERT INTO t_administrative_class
    (course_id, class_code, class_name, grade, major, description, status, create_time, update_time)
SELECT
    @demo_course_id,
    'DEMO_2026_AI_01',
    'DEMO_人工智能实验行政班',
    '2026级',
    '人工智能',
    '管理员录屏专用行政班。',
    1,
    NOW(),
    NOW()
WHERE @demo_admin_class_id IS NULL;

SET @demo_admin_class_id = (
    SELECT id FROM t_administrative_class
    WHERE class_code = 'DEMO_2026_AI_01'
    ORDER BY id LIMIT 1
);

UPDATE t_administrative_class
SET course_id = @demo_course_id,
    class_name = 'DEMO_人工智能实验行政班',
    grade = '2026级',
    major = '人工智能',
    description = '管理员录屏专用行政班。',
    status = 1,
    update_time = NOW()
WHERE id = @demo_admin_class_id;

INSERT INTO t_administrative_class_student
    (administrative_class_id, student_id, join_time, status)
SELECT @demo_admin_class_id, @demo_student_id, NOW() - INTERVAL 30 DAY, 1
WHERE NOT EXISTS (
    SELECT 1 FROM t_administrative_class_student
    WHERE administrative_class_id = @demo_admin_class_id AND student_id = @demo_student_id
);

UPDATE t_administrative_class_student
SET status = 1,
    join_time = NOW() - INTERVAL 30 DAY
WHERE administrative_class_id = @demo_admin_class_id AND student_id = @demo_student_id;

DELETE FROM t_learning_progress
WHERE class_id = @demo_class_id
  AND student_id = @demo_student_id
  AND lesson_id = @demo_lesson_id;

INSERT INTO t_learning_progress
    (class_id, student_id, lesson_id, progress, learned_seconds, last_position,
     last_learn_time, create_time, update_time)
VALUES
    (@demo_class_id, @demo_student_id, @demo_lesson_id, 72, 1210, 640,
     NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 14 DAY, NOW());

DELETE FROM t_learning_record
WHERE class_id = @demo_class_id
  AND student_id = @demo_student_id
  AND lesson_id = @demo_lesson_id;

INSERT INTO t_learning_record
    (class_id, student_id, lesson_id, start_position, end_position, learned_seconds,
     record_time, create_time)
VALUES
    (@demo_class_id, @demo_student_id, @demo_lesson_id, 0, 420, 420,
     NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY),
    (@demo_class_id, @demo_student_id, @demo_lesson_id, 420, 640, 220,
     NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY);

-- 班级公告让教师端与学生端都有可见的近期内容。
DELETE FROM t_notice
WHERE title = 'DEMO_本周学习任务'
   OR (class_id = @demo_class_id AND LEFT(title, 5) = 'DEMO_');

INSERT INTO t_notice
    (publisher_id, class_id, title, content, notice_type, create_time, status)
VALUES
    (@demo_teacher_id, @demo_class_id, 'DEMO_本周学习任务',
     '请完成学习率互动实验，并在随堂测验中验证你对收敛与震荡的理解。',
     'course', NOW() - INTERVAL 1 DAY, 1);

-- ---------------------------------------------------------------------------
-- 4. 同一门课的考试闭环：可参加、两次已出成绩、一次待人工批改
-- ---------------------------------------------------------------------------
SET @demo_question_single_id = (
    SELECT id FROM t_question
    WHERE title = 'DEMO_学习率过大时最可能出现什么现象？'
    ORDER BY id LIMIT 1
);

INSERT INTO t_question
    (course_id, teacher_id, question_type, title, options, answer, analysis,
     knowledge_point, difficulty, score, status, create_time, update_time)
SELECT
    @demo_course_id,
    @demo_teacher_id,
    'single',
    'DEMO_学习率过大时最可能出现什么现象？',
    JSON_ARRAY(
        JSON_OBJECT('optionKey', 'A', 'optionContent', '在最低点两侧震荡甚至发散', 'sortOrder', 1),
        JSON_OBJECT('optionKey', 'B', 'optionContent', '一定一次到达最低点', 'sortOrder', 2),
        JSON_OBJECT('optionKey', 'C', 'optionContent', '梯度永远变成零', 'sortOrder', 3),
        JSON_OBJECT('optionKey', 'D', 'optionContent', '与训练过程无关', 'sortOrder', 4)
    ),
    JSON_ARRAY(JSON_OBJECT('answerContent', 'A', 'sortOrder', 1)),
    '步长过大容易跨过最低点，在两侧来回震荡。',
    '梯度下降与学习率',
    1,
    60.00,
    1,
    NOW(),
    NOW()
WHERE @demo_question_single_id IS NULL;

SET @demo_question_single_id = (
    SELECT id FROM t_question
    WHERE title = 'DEMO_学习率过大时最可能出现什么现象？'
    ORDER BY id LIMIT 1
);

UPDATE t_question
SET course_id = @demo_course_id,
    teacher_id = @demo_teacher_id,
    question_type = 'single',
    options = JSON_ARRAY(
        JSON_OBJECT('optionKey', 'A', 'optionContent', '在最低点两侧震荡甚至发散', 'sortOrder', 1),
        JSON_OBJECT('optionKey', 'B', 'optionContent', '一定一次到达最低点', 'sortOrder', 2),
        JSON_OBJECT('optionKey', 'C', 'optionContent', '梯度永远变成零', 'sortOrder', 3),
        JSON_OBJECT('optionKey', 'D', 'optionContent', '与训练过程无关', 'sortOrder', 4)
    ),
    answer = JSON_ARRAY(JSON_OBJECT('answerContent', 'A', 'sortOrder', 1)),
    analysis = '步长过大容易跨过最低点，在两侧来回震荡。',
    knowledge_point = '梯度下降与学习率',
    difficulty = 1,
    score = 60.00,
    status = 1,
    update_time = NOW()
WHERE id = @demo_question_single_id;

SET @demo_question_short_id = (
    SELECT id FROM t_question
    WHERE title = 'DEMO_请解释如何判断一次梯度下降是否正在收敛。'
    ORDER BY id LIMIT 1
);

INSERT INTO t_question
    (course_id, teacher_id, question_type, title, options, answer, analysis,
     knowledge_point, difficulty, score, status, create_time, update_time)
SELECT
    @demo_course_id,
    @demo_teacher_id,
    'short_answer',
    'DEMO_请解释如何判断一次梯度下降是否正在收敛。',
    NULL,
    JSON_ARRAY(JSON_OBJECT(
        'answerContent', '观察损失是否整体下降、参数是否逐渐靠近稳定值，并排除持续震荡或发散。',
        'sortOrder', 1
    )),
    '回答应同时提到损失趋势与参数变化。',
    '梯度下降与学习率',
    2,
    40.00,
    1,
    NOW(),
    NOW()
WHERE @demo_question_short_id IS NULL;

SET @demo_question_short_id = (
    SELECT id FROM t_question
    WHERE title = 'DEMO_请解释如何判断一次梯度下降是否正在收敛。'
    ORDER BY id LIMIT 1
);

UPDATE t_question
SET course_id = @demo_course_id,
    teacher_id = @demo_teacher_id,
    question_type = 'short_answer',
    options = NULL,
    answer = JSON_ARRAY(JSON_OBJECT(
        'answerContent', '观察损失是否整体下降、参数是否逐渐靠近稳定值，并排除持续震荡或发散。',
        'sortOrder', 1
    )),
    analysis = '回答应同时提到损失趋势与参数变化。',
    knowledge_point = '梯度下降与学习率',
    difficulty = 2,
    score = 40.00,
    status = 1,
    update_time = NOW()
WHERE id = @demo_question_short_id;

SET @demo_paper_id = (
    SELECT id FROM t_exam_paper
    WHERE title = 'DEMO_梯度下降基础试卷'
    ORDER BY id LIMIT 1
);

INSERT INTO t_exam_paper
    (course_id, teacher_id, title, description, total_score, duration_minutes,
     status, create_time, update_time)
SELECT
    @demo_course_id,
    @demo_teacher_id,
    'DEMO_梯度下降基础试卷',
    '一题自动批改、一题教师批改，完整展示考试闭环。',
    100.00,
    45,
    1,
    NOW(),
    NOW()
WHERE @demo_paper_id IS NULL;

SET @demo_paper_id = (
    SELECT id FROM t_exam_paper
    WHERE title = 'DEMO_梯度下降基础试卷'
    ORDER BY id LIMIT 1
);

UPDATE t_exam_paper
SET course_id = @demo_course_id,
    teacher_id = @demo_teacher_id,
    description = '一题自动批改、一题教师批改，完整展示考试闭环。',
    total_score = 100.00,
    duration_minutes = 45,
    status = 1,
    update_time = NOW()
WHERE id = @demo_paper_id;

-- 先清理现有演示答题明细，避免重建试卷题目关联时触发外键冲突。
DELETE ea
FROM t_exam_answer ea
JOIN t_exam_record er ON er.id = ea.exam_record_id
JOIN t_exam_session es ON es.id = er.exam_session_id
WHERE LEFT(es.title, 5) = 'DEMO_'
   OR es.teacher_id = @demo_teacher_id
   OR es.class_id = @demo_class_id;

SET @demo_pq_single_id = (
    SELECT id FROM t_exam_paper_question
    WHERE paper_id = @demo_paper_id AND sort_order = 1
    ORDER BY id LIMIT 1
);

INSERT INTO t_exam_paper_question
    (paper_id, question_id, score, sort_order, required_answer)
SELECT @demo_paper_id, @demo_question_single_id, 60.00, 1, 1
WHERE @demo_pq_single_id IS NULL;

SET @demo_pq_single_id = (
    SELECT id FROM t_exam_paper_question
    WHERE paper_id = @demo_paper_id AND sort_order = 1
    ORDER BY id LIMIT 1
);

UPDATE t_exam_paper_question
SET question_id = @demo_question_single_id,
    score = 60.00,
    required_answer = 1
WHERE id = @demo_pq_single_id;

SET @demo_pq_short_id = (
    SELECT id FROM t_exam_paper_question
    WHERE paper_id = @demo_paper_id AND sort_order = 2
    ORDER BY id LIMIT 1
);

INSERT INTO t_exam_paper_question
    (paper_id, question_id, score, sort_order, required_answer)
SELECT @demo_paper_id, @demo_question_short_id, 40.00, 2, 1
WHERE @demo_pq_short_id IS NULL;

SET @demo_pq_short_id = (
    SELECT id FROM t_exam_paper_question
    WHERE paper_id = @demo_paper_id AND sort_order = 2
    ORDER BY id LIMIT 1
);

UPDATE t_exam_paper_question
SET question_id = @demo_question_short_id,
    score = 40.00,
    required_answer = 1
WHERE id = @demo_pq_short_id;

DELETE FROM t_exam_paper_question
WHERE paper_id = @demo_paper_id
  AND id NOT IN (@demo_pq_single_id, @demo_pq_short_id);

-- 创建或校准四场考试。数据库状态码严格遵循 Java 枚举：1发布、3关闭、4成绩已提交。
SET @demo_exam_graded_1_id = (
    SELECT id FROM t_exam_session WHERE title = 'DEMO_单元测验一（已发布成绩）' ORDER BY id LIMIT 1
);
INSERT INTO t_exam_session
    (class_id, paper_id, teacher_id, title, description, start_time, end_time, duration,
     status, max_screen_switches, disable_clipboard, create_time, update_time)
SELECT @demo_class_id, @demo_paper_id, @demo_teacher_id,
       'DEMO_单元测验一（已发布成绩）', '用于学生成绩趋势和教师成绩分析。',
       NOW() - INTERVAL 40 DAY, NOW() - INTERVAL 39 DAY, 45, 4, 3, 1, NOW(), NOW()
WHERE @demo_exam_graded_1_id IS NULL;
SET @demo_exam_graded_1_id = (
    SELECT id FROM t_exam_session WHERE title = 'DEMO_单元测验一（已发布成绩）' ORDER BY id LIMIT 1
);
UPDATE t_exam_session
SET class_id = @demo_class_id, paper_id = @demo_paper_id, teacher_id = @demo_teacher_id,
    description = '用于学生成绩趋势和教师成绩分析。',
    start_time = NOW() - INTERVAL 40 DAY, end_time = NOW() - INTERVAL 39 DAY,
    duration = 45, status = 4, max_screen_switches = 3, disable_clipboard = 1,
    update_time = NOW()
WHERE id = @demo_exam_graded_1_id;

SET @demo_exam_graded_2_id = (
    SELECT id FROM t_exam_session WHERE title = 'DEMO_单元测验二（已发布成绩）' ORDER BY id LIMIT 1
);
INSERT INTO t_exam_session
    (class_id, paper_id, teacher_id, title, description, start_time, end_time, duration,
     status, max_screen_switches, disable_clipboard, create_time, update_time)
SELECT @demo_class_id, @demo_paper_id, @demo_teacher_id,
       'DEMO_单元测验二（已发布成绩）', '用于展示成绩提升趋势。',
       NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 9 DAY, 45, 4, 3, 1, NOW(), NOW()
WHERE @demo_exam_graded_2_id IS NULL;
SET @demo_exam_graded_2_id = (
    SELECT id FROM t_exam_session WHERE title = 'DEMO_单元测验二（已发布成绩）' ORDER BY id LIMIT 1
);
UPDATE t_exam_session
SET class_id = @demo_class_id, paper_id = @demo_paper_id, teacher_id = @demo_teacher_id,
    description = '用于展示成绩提升趋势。',
    start_time = NOW() - INTERVAL 10 DAY, end_time = NOW() - INTERVAL 9 DAY,
    duration = 45, status = 4, max_screen_switches = 3, disable_clipboard = 1,
    update_time = NOW()
WHERE id = @demo_exam_graded_2_id;

SET @demo_exam_pending_id = (
    SELECT id FROM t_exam_session WHERE title = 'DEMO_综合训练（待教师批改）' ORDER BY id LIMIT 1
);
INSERT INTO t_exam_session
    (class_id, paper_id, teacher_id, title, description, start_time, end_time, duration,
     status, max_screen_switches, disable_clipboard, create_time, update_time)
SELECT @demo_class_id, @demo_paper_id, @demo_teacher_id,
       'DEMO_综合训练（待教师批改）', '主观题已作答，教师可直接进入批改。',
       NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 1 DAY, 45, 3, 3, 1, NOW(), NOW()
WHERE @demo_exam_pending_id IS NULL;
SET @demo_exam_pending_id = (
    SELECT id FROM t_exam_session WHERE title = 'DEMO_综合训练（待教师批改）' ORDER BY id LIMIT 1
);
UPDATE t_exam_session
SET class_id = @demo_class_id, paper_id = @demo_paper_id, teacher_id = @demo_teacher_id,
    description = '主观题已作答，教师可直接进入批改。',
    start_time = NOW() - INTERVAL 2 DAY, end_time = NOW() - INTERVAL 1 DAY,
    duration = 45, status = 3, max_screen_switches = 3, disable_clipboard = 1,
    update_time = NOW()
WHERE id = @demo_exam_pending_id;

SET @demo_exam_available_id = (
    SELECT id FROM t_exam_session WHERE title = 'DEMO_随堂测验（可立即参加）' ORDER BY id LIMIT 1
);
INSERT INTO t_exam_session
    (class_id, paper_id, teacher_id, title, description, start_time, end_time, duration,
     status, max_screen_switches, disable_clipboard, create_time, update_time)
SELECT @demo_class_id, @demo_paper_id, @demo_teacher_id,
       'DEMO_随堂测验（可立即参加）', '录屏时可从学生端直接进入答题。',
       NOW() - INTERVAL 5 MINUTE, NOW() + INTERVAL 7 DAY, 45, 1, 3, 1, NOW(), NOW()
WHERE @demo_exam_available_id IS NULL;
SET @demo_exam_available_id = (
    SELECT id FROM t_exam_session WHERE title = 'DEMO_随堂测验（可立即参加）' ORDER BY id LIMIT 1
);
UPDATE t_exam_session
SET class_id = @demo_class_id, paper_id = @demo_paper_id, teacher_id = @demo_teacher_id,
    description = '录屏时可从学生端直接进入答题。',
    start_time = NOW() - INTERVAL 5 MINUTE, end_time = NOW() + INTERVAL 7 DAY,
    duration = 45, status = 1, max_screen_switches = 3, disable_clipboard = 1,
    update_time = NOW()
WHERE id = @demo_exam_available_id;

-- 可参加的测验必须没有该学生的历史答卷。
DELETE FROM t_exam_record
WHERE exam_session_id = @demo_exam_available_id AND student_id = @demo_student_id;

-- 两条已评分记录和一条待人工批改记录；唯一键保证重复执行不增加记录数。
INSERT INTO t_exam_record
    (exam_session_id, student_id, candidate_id, start_time, submit_time, total_score,
     objective_score, subjective_score, status, comment, screen_switch_count, cheated,
     create_time, update_time)
VALUES
    (@demo_exam_graded_1_id, @demo_student_id, CAST(@demo_student_id AS CHAR),
     NOW() - INTERVAL 40 DAY, NOW() - INTERVAL 40 DAY + INTERVAL 32 MINUTE,
     78.00, 60.00, 18.00, 2, '第一次测验已完成，建议进一步说明参数稳定性。', 0, 0,
     NOW() - INTERVAL 40 DAY, NOW()),
    (@demo_exam_graded_2_id, @demo_student_id, CAST(@demo_student_id AS CHAR),
     NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 10 DAY + INTERVAL 27 MINUTE,
     88.00, 60.00, 28.00, 2, '第二次测验进步明显，能够结合损失曲线进行判断。', 0, 0,
     NOW() - INTERVAL 10 DAY, NOW()),
    (@demo_exam_pending_id, @demo_student_id, CAST(@demo_student_id AS CHAR),
     NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY + INTERVAL 24 MINUTE,
     60.00, 60.00, 0.00, 3, '客观题已自动批改，主观题等待教师评分。', 0, 0,
     NOW() - INTERVAL 2 DAY, NOW())
AS new
ON DUPLICATE KEY UPDATE
    candidate_id = new.candidate_id,
    start_time = new.start_time,
    submit_time = new.submit_time,
    total_score = new.total_score,
    objective_score = new.objective_score,
    subjective_score = new.subjective_score,
    status = new.status,
    comment = new.comment,
    screen_switch_count = 0,
    cheated = 0,
    update_time = NOW();

SET @demo_record_graded_1_id = (
    SELECT id FROM t_exam_record
    WHERE exam_session_id = @demo_exam_graded_1_id AND student_id = @demo_student_id LIMIT 1
);
SET @demo_record_graded_2_id = (
    SELECT id FROM t_exam_record
    WHERE exam_session_id = @demo_exam_graded_2_id AND student_id = @demo_student_id LIMIT 1
);
SET @demo_record_pending_id = (
    SELECT id FROM t_exam_record
    WHERE exam_session_id = @demo_exam_pending_id AND student_id = @demo_student_id LIMIT 1
);

-- 之前已删除演示考试的答题明细，现在按稳定基线重建。
INSERT INTO t_exam_answer
    (exam_record_id, question_id, paper_question_id, student_answer, correct_answer,
     question_snapshot, score, is_correct, grade_status, grader_id, grade_time, comment)
VALUES
    (@demo_record_graded_1_id, @demo_question_single_id, @demo_pq_single_id,
     'A', 'A', NULL, 60.00, 1, 2, NULL, NULL, '系统自动批改：回答正确'),
    (@demo_record_graded_1_id, @demo_question_short_id, @demo_pq_short_id,
     '我会观察 loss 是否持续下降，以及参数是否逐渐靠近 0。',
     '观察损失是否整体下降、参数是否逐渐靠近稳定值，并排除持续震荡或发散。',
     NULL, 18.00, NULL, 4, @demo_teacher_id, NOW() - INTERVAL 39 DAY,
     '方向正确，可补充震荡与发散的判断。'),
    (@demo_record_graded_2_id, @demo_question_single_id, @demo_pq_single_id,
     'A', 'A', NULL, 60.00, 1, 2, NULL, NULL, '系统自动批改：回答正确'),
    (@demo_record_graded_2_id, @demo_question_short_id, @demo_pq_short_id,
     '如果 loss 整体下降且步长逐渐变小，参数最终稳定在最低点附近，就说明正在收敛；持续跨越最低点则说明学习率偏大。',
     '观察损失是否整体下降、参数是否逐渐靠近稳定值，并排除持续震荡或发散。',
     NULL, 28.00, NULL, 4, @demo_teacher_id, NOW() - INTERVAL 9 DAY,
     '能够结合损失、参数和震荡现象进行判断。'),
    (@demo_record_pending_id, @demo_question_single_id, @demo_pq_single_id,
     'A', 'A', NULL, 60.00, 1, 2, NULL, NULL, '系统自动批改：回答正确'),
    (@demo_record_pending_id, @demo_question_short_id, @demo_pq_short_id,
     '我先看每一步的 loss 是否越来越小，再看 x 是否逐渐接近 0。如果来回跳动且幅度变大，就不是收敛。',
     '观察损失是否整体下降、参数是否逐渐靠近稳定值，并排除持续震荡或发散。',
     NULL, 0.00, NULL, 3, NULL, NULL, NULL);

-- ---------------------------------------------------------------------------
-- 5. 新版作业：已发布、学生尚未提交，可从两端直接进入
-- ---------------------------------------------------------------------------
SET @demo_homework_id = (
    SELECT id FROM t_homework_session
    WHERE title = 'DEMO_学习率调参课后作业（待完成）'
    ORDER BY id LIMIT 1
);

-- apply 也会恢复“待完成”基线：清理该专用作业在彩排中产生的答卷。
DELETE ha
FROM t_homework_answer ha
JOIN t_homework_record hr ON hr.id = ha.homework_record_id
WHERE hr.homework_session_id = @demo_homework_id;

DELETE FROM t_homework_record
WHERE homework_session_id = @demo_homework_id;

SET @demo_homework_paper_id = (
    SELECT id FROM t_homework_paper
    WHERE title = 'DEMO_梯度下降课后作业试卷'
    ORDER BY id LIMIT 1
);

INSERT INTO t_homework_paper
    (course_id, teacher_id, title, description, total_score, duration_minutes,
     status, create_time, update_time)
SELECT
    @demo_course_id,
    @demo_teacher_id,
    'DEMO_梯度下降课后作业试卷',
    '复用随堂测验的两道题，供学生在截止时间前完成。',
    100.00,
    120,
    1,
    NOW(),
    NOW()
WHERE @demo_homework_paper_id IS NULL;

SET @demo_homework_paper_id = (
    SELECT id FROM t_homework_paper
    WHERE title = 'DEMO_梯度下降课后作业试卷'
    ORDER BY id LIMIT 1
);

UPDATE t_homework_paper
SET course_id = @demo_course_id,
    teacher_id = @demo_teacher_id,
    description = '复用随堂测验的两道题，供学生在截止时间前完成。',
    total_score = 100.00,
    duration_minutes = 120,
    status = 1,
    update_time = NOW()
WHERE id = @demo_homework_paper_id;

SET @demo_homework_pq_single_id = (
    SELECT id FROM t_homework_paper_question
    WHERE paper_id = @demo_homework_paper_id AND sort_order = 1
    ORDER BY id LIMIT 1
);

INSERT INTO t_homework_paper_question
    (paper_id, question_id, score, sort_order, required_answer)
SELECT @demo_homework_paper_id, @demo_question_single_id, 60.00, 1, 1
WHERE @demo_homework_pq_single_id IS NULL;

SET @demo_homework_pq_single_id = (
    SELECT id FROM t_homework_paper_question
    WHERE paper_id = @demo_homework_paper_id AND sort_order = 1
    ORDER BY id LIMIT 1
);

UPDATE t_homework_paper_question
SET question_id = @demo_question_single_id,
    score = 60.00,
    required_answer = 1
WHERE id = @demo_homework_pq_single_id;

SET @demo_homework_pq_short_id = (
    SELECT id FROM t_homework_paper_question
    WHERE paper_id = @demo_homework_paper_id AND sort_order = 2
    ORDER BY id LIMIT 1
);

INSERT INTO t_homework_paper_question
    (paper_id, question_id, score, sort_order, required_answer)
SELECT @demo_homework_paper_id, @demo_question_short_id, 40.00, 2, 1
WHERE @demo_homework_pq_short_id IS NULL;

SET @demo_homework_pq_short_id = (
    SELECT id FROM t_homework_paper_question
    WHERE paper_id = @demo_homework_paper_id AND sort_order = 2
    ORDER BY id LIMIT 1
);

UPDATE t_homework_paper_question
SET question_id = @demo_question_short_id,
    score = 40.00,
    required_answer = 1
WHERE id = @demo_homework_pq_short_id;

DELETE FROM t_homework_paper_question
WHERE paper_id = @demo_homework_paper_id
  AND id NOT IN (@demo_homework_pq_single_id, @demo_homework_pq_short_id);

INSERT INTO t_homework_session
    (class_id, paper_id, teacher_id, title, description, start_time, end_time,
     duration, status, create_time, update_time)
SELECT
    @demo_class_id,
    @demo_homework_paper_id,
    @demo_teacher_id,
    'DEMO_学习率调参课后作业（待完成）',
    '结合互动实验回答两道题，录屏账号当前尚未提交。',
    NOW() - INTERVAL 1 DAY,
    NOW() + INTERVAL 5 DAY,
    120,
    1,
    NOW(),
    NOW()
WHERE @demo_homework_id IS NULL;

SET @demo_homework_id = (
    SELECT id FROM t_homework_session
    WHERE title = 'DEMO_学习率调参课后作业（待完成）'
    ORDER BY id LIMIT 1
);

UPDATE t_homework_session
SET class_id = @demo_class_id,
    paper_id = @demo_homework_paper_id,
    teacher_id = @demo_teacher_id,
    description = '结合互动实验回答两道题，录屏账号当前尚未提交。',
    start_time = NOW() - INTERVAL 1 DAY,
    end_time = NOW() + INTERVAL 5 DAY,
    duration = 120,
    status = 1,
    update_time = NOW()
WHERE id = @demo_homework_id;

-- 双保险：首次创建后同样保证专用学生无作业答卷。
DELETE FROM t_homework_record
WHERE homework_session_id = @demo_homework_id;

-- ---------------------------------------------------------------------------
-- 6. 岗位、推荐、待审核教师申请和管理员日志
-- ---------------------------------------------------------------------------
SET @demo_job_applied_id = (
    SELECT id FROM t_work_study WHERE job_name = 'DEMO_互动课程内容助理' ORDER BY id LIMIT 1
);
INSERT INTO t_work_study
    (job_name, job_desc, work_location, work_hours, hourly_rate, max_recruit,
     status, create_time, update_time)
SELECT
    'DEMO_互动课程内容助理',
    '协助整理互动实验素材、核对题目与课程章节。',
    '人工智能学院 302',
    '每周 6 小时',
    25.00,
    3,
    1,
    NOW(),
    NOW()
WHERE @demo_job_applied_id IS NULL;
SET @demo_job_applied_id = (
    SELECT id FROM t_work_study WHERE job_name = 'DEMO_互动课程内容助理' ORDER BY id LIMIT 1
);
UPDATE t_work_study
SET job_desc = '协助整理互动实验素材、核对题目与课程章节。',
    work_location = '人工智能学院 302', work_hours = '每周 6 小时',
    hourly_rate = 25.00, max_recruit = 3, status = 1, update_time = NOW()
WHERE id = @demo_job_applied_id;

SET @demo_job_available_id = (
    SELECT id FROM t_work_study WHERE job_name = 'DEMO_智慧实验室值班助理' ORDER BY id LIMIT 1
);
INSERT INTO t_work_study
    (job_name, job_desc, work_location, work_hours, hourly_rate, max_recruit,
     status, create_time, update_time)
SELECT
    'DEMO_智慧实验室值班助理',
    '协助课前设备检查和实验环境准备。',
    '综合实验楼 205',
    '每周 4 小时',
    22.00,
    2,
    1,
    NOW(),
    NOW()
WHERE @demo_job_available_id IS NULL;
SET @demo_job_available_id = (
    SELECT id FROM t_work_study WHERE job_name = 'DEMO_智慧实验室值班助理' ORDER BY id LIMIT 1
);
UPDATE t_work_study
SET job_desc = '协助课前设备检查和实验环境准备。',
    work_location = '综合实验楼 205', work_hours = '每周 4 小时',
    hourly_rate = 22.00, max_recruit = 2, status = 1, update_time = NOW()
WHERE id = @demo_job_available_id;

DELETE FROM t_work_study_student
WHERE student_id = @demo_student_id
  AND work_study_id IN (@demo_job_applied_id, @demo_job_available_id);

INSERT INTO t_work_study_student
    (work_study_id, student_id, work_month, work_hours, hourly_rate, total_amount,
     apply_time, audit_status, auditor_id, audit_time, reject_reason, status,
     create_time, update_time)
VALUES
    (@demo_job_applied_id, @demo_student_id, NULL, 0.00, 25.00, 0.00,
     NOW() - INTERVAL 2 DAY, 0, NULL, NULL, NULL, 1,
     NOW() - INTERVAL 2 DAY, NOW());

INSERT INTO t_home_course_recommendation
    (course_id, reason, sort_order, status, create_time, update_time)
VALUES
    (@demo_course_id, '本周主线：拖动学习率、运行 Python，并完成随堂测验', 1, 1, NOW(), NOW())
AS new
ON DUPLICATE KEY UPDATE
    reason = new.reason,
    sort_order = 1,
    status = 1,
    update_time = NOW();

UPDATE t_teacher_application
SET password = @demo_password,
    nickname = '周老师（待审核演示）',
    email = 'demo.candidate@ai-learning.local',
    phone = '13990000004',
    reason = '希望建设面向新生的 Python 与人工智能实践课程。',
    status = 0,
    reviewer_id = NULL,
    review_comment = NULL,
    review_time = NULL
WHERE username = 'DEMO_teacher_candidate';

INSERT INTO t_teacher_application
    (username, password, nickname, email, phone, reason, status,
     reviewer_id, review_comment, create_time, review_time)
SELECT
    'DEMO_teacher_candidate',
    @demo_password,
    '周老师（待审核演示）',
    'demo.candidate@ai-learning.local',
    '13990000004',
    '希望建设面向新生的 Python 与人工智能实践课程。',
    0,
    NULL,
    NULL,
    NOW() - INTERVAL 1 DAY,
    NULL
WHERE NOT EXISTS (
    SELECT 1 FROM t_teacher_application WHERE username = 'DEMO_teacher_candidate'
);

DELETE FROM t_operation_log
WHERE LEFT(operation, 5) = 'DEMO_';

INSERT INTO t_operation_log
    (user_id, operation, method, url, params, ip, create_time)
VALUES
    (@demo_admin_id, 'DEMO_审核教师入驻材料', 'GET', '/teacher-applications',
     '{"status":0,"source":"recording-demo"}', '127.0.0.1', NOW() - INTERVAL 30 MINUTE),
    (@demo_teacher_id, 'DEMO_发布随堂测验', 'POST', '/api/exams',
     '{"title":"DEMO_随堂测验（可立即参加）"}', '127.0.0.1', NOW() - INTERVAL 2 HOUR),
    (@demo_student_id, 'DEMO_完成互动实验', 'PUT', '/learning-progress',
     '{"progress":72,"source":"recording-demo"}', '127.0.0.1', NOW() - INTERVAL 1 DAY);

COMMIT;

SELECT
    'DEMO_RECORDING_APPLIED' AS result,
    @demo_admin_id AS admin_id,
    @demo_teacher_id AS teacher_id,
    @demo_student_id AS student_id,
    @demo_course_id AS course_id,
    @demo_class_id AS class_id,
    @demo_lesson_id AS lesson_id,
    @demo_homework_id AS pending_homework_id,
    @demo_exam_available_id AS available_exam_id,
    @demo_exam_pending_id AS pending_grading_exam_id;
