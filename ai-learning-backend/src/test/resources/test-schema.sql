DROP TABLE IF EXISTS t_homework_answer;
DROP TABLE IF EXISTS t_homework_record;
DROP TABLE IF EXISTS t_homework_session;
DROP TABLE IF EXISTS t_homework_paper_question;
DROP TABLE IF EXISTS t_homework_paper;
DROP TABLE IF EXISTS t_exam_answer;
DROP TABLE IF EXISTS t_exam_record;
DROP TABLE IF EXISTS t_exam_session;
DROP TABLE IF EXISTS t_exam_paper_question;
DROP TABLE IF EXISTS t_exam_paper;
DROP TABLE IF EXISTS t_agent_pending_action;
DROP TABLE IF EXISTS t_operation_log;
DROP TABLE IF EXISTS t_teacher_application;
DROP TABLE IF EXISTS t_question;
DROP TABLE IF EXISTS t_homework_submit;
DROP TABLE IF EXISTS t_homework;
DROP TABLE IF EXISTS study_note;
DROP TABLE IF EXISTS t_learning_progress;
DROP TABLE IF EXISTS t_class_student;
DROP TABLE IF EXISTS t_teaching_class;
DROP TABLE IF EXISTS t_lesson;
DROP TABLE IF EXISTS t_chapter;
DROP TABLE IF EXISTS t_home_course_recommendation;
DROP TABLE IF EXISTS t_course;
DROP TABLE IF EXISTS t_course_category;
DROP TABLE IF EXISTS t_user_role;
DROP TABLE IF EXISTS t_user;
DROP TABLE IF EXISTS t_role;

CREATE TABLE t_role (
    id INT AUTO_INCREMENT PRIMARY KEY,
    role_name VARCHAR(50),
    role_code VARCHAR(50),
    create_time DATETIME
);

CREATE TABLE t_user (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50),
    password VARCHAR(100),
    nickname VARCHAR(50),
    email VARCHAR(100),
    phone VARCHAR(20),
    avatar VARCHAR(255),
    status TINYINT,
    create_time DATETIME,
    update_time DATETIME
);

CREATE TABLE t_user_role (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT,
    role_id INT
);

CREATE TABLE t_course_category (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50),
    description VARCHAR(255),
    sort_order INT,
    create_time DATETIME,
    update_time DATETIME
);

CREATE TABLE t_course (
    id INT AUTO_INCREMENT PRIMARY KEY,
    category_id INT,
    teacher_id INT,
    title VARCHAR(200),
    description TEXT,
    cover VARCHAR(255),
    cover_url VARCHAR(255),
    teacher VARCHAR(100),
    difficulty VARCHAR(20),
    student_count INT,
    view_count INT,
    rating DECIMAL(2,1),
    status TINYINT,
    created_at DATETIME,
    updated_at DATETIME
);

CREATE TABLE t_home_course_recommendation (
    id INT AUTO_INCREMENT PRIMARY KEY,
    course_id INT NOT NULL UNIQUE,
    reason VARCHAR(255),
    sort_order INT NOT NULL DEFAULT 1,
    status TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME,
    update_time DATETIME
);

CREATE TABLE t_chapter (
    id INT AUTO_INCREMENT PRIMARY KEY,
    course_id INT,
    title VARCHAR(200),
    description VARCHAR(500),
    sort_order INT,
    status TINYINT,
    created_at DATETIME,
    updated_at DATETIME
);

CREATE TABLE t_lesson (
    id INT AUTO_INCREMENT PRIMARY KEY,
    chapter_id INT,
    title VARCHAR(200),
    resource_type VARCHAR(30),
    resource_url VARCHAR(500),
    video_url VARCHAR(255),
    video_resource_id BIGINT,
    content TEXT,
    duration INT,
    sort_order INT,
    view_count INT,
    status TINYINT,
    created_at DATETIME,
    updated_at DATETIME
);

CREATE TABLE t_teaching_class (
    id INT AUTO_INCREMENT PRIMARY KEY,
    course_id INT,
    teacher_id INT,
    class_name VARCHAR(100),
    semester VARCHAR(50),
    description VARCHAR(255),
    status TINYINT,
    create_time DATETIME,
    update_time DATETIME
);

CREATE TABLE t_class_student (
    id INT AUTO_INCREMENT PRIMARY KEY,
    class_id INT,
    student_id INT,
    join_time DATETIME,
    status TINYINT
);

CREATE TABLE t_learning_progress (
    id INT AUTO_INCREMENT PRIMARY KEY,
    class_id INT,
    student_id INT,
    lesson_id INT,
    progress INT,
    learned_seconds INT,
    last_position INT,
    last_learn_time DATETIME,
    create_time DATETIME,
    update_time DATETIME
);

CREATE TABLE study_note (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    course_id INT NOT NULL,
    lesson_id INT NOT NULL,
    title VARCHAR(255),
    content_md CLOB NOT NULL,
    last_video_time INT DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uk_study_note_user_lesson UNIQUE (user_id, lesson_id)
);

CREATE TABLE t_homework (
    id INT AUTO_INCREMENT PRIMARY KEY,
    class_id INT,
    teacher_id INT,
    title VARCHAR(100) NOT NULL,
    content TEXT,
    attachment_url VARCHAR(255),
    full_score DECIMAL(10,2),
    start_time DATETIME,
    deadline DATETIME,
    status TINYINT,
    create_time DATETIME,
    update_time DATETIME
);

CREATE TABLE t_homework_submit (
    id INT AUTO_INCREMENT PRIMARY KEY,
    homework_id INT,
    student_id INT,
    submit_content TEXT,
    attachment_url VARCHAR(255),
    submit_time DATETIME,
    score DECIMAL(10,2),
    feedback TEXT,
    grade_status TINYINT,
    grader_id INT,
    grade_time DATETIME,
    create_time DATETIME,
    update_time DATETIME
);

CREATE TABLE t_question (
    id INT AUTO_INCREMENT PRIMARY KEY,
    course_id INT,
    teacher_id INT,
    question_type VARCHAR(30) NOT NULL,
    title TEXT NOT NULL,
    options TEXT,
    answer TEXT,
    analysis TEXT,
    knowledge_point VARCHAR(200),
    difficulty TINYINT,
    score DECIMAL(10,2),
    status TINYINT,
    create_time DATETIME,
    update_time DATETIME
);

CREATE TABLE t_exam_paper (
    id INT AUTO_INCREMENT PRIMARY KEY,
    course_id INT,
    teacher_id INT,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    total_score DECIMAL(10,2),
    duration_minutes INT,
    status TINYINT,
    create_time DATETIME,
    update_time DATETIME
);

CREATE TABLE t_exam_paper_question (
    id INT AUTO_INCREMENT PRIMARY KEY,
    paper_id INT NOT NULL,
    question_id INT NOT NULL,
    score DECIMAL(10,2),
    sort_order INT,
    required_answer TINYINT
);

CREATE TABLE t_exam_session (
    id INT AUTO_INCREMENT PRIMARY KEY,
    class_id INT,
    paper_id INT NOT NULL,
    teacher_id INT,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    start_time DATETIME,
    end_time DATETIME,
    duration INT,
    status TINYINT,
    max_screen_switches INT DEFAULT 3,
    disable_clipboard TINYINT DEFAULT 0,
    create_time DATETIME,
    update_time DATETIME
);

CREATE TABLE t_exam_record (
    id INT AUTO_INCREMENT PRIMARY KEY,
    exam_session_id INT NOT NULL,
    student_id INT,
    candidate_id VARCHAR(64),
    start_time DATETIME,
    submit_time DATETIME,
    total_score DECIMAL(10,2),
    objective_score DECIMAL(10,2),
    subjective_score DECIMAL(10,2),
    status TINYINT,
    comment VARCHAR(255),
    screen_switch_count INT DEFAULT 0,
    cheated TINYINT DEFAULT 0,
    create_time DATETIME,
    update_time DATETIME,
    UNIQUE (exam_session_id, student_id)
);

CREATE TABLE t_exam_answer (
    id INT AUTO_INCREMENT PRIMARY KEY,
    exam_record_id INT NOT NULL,
    question_id INT NOT NULL,
    paper_question_id INT,
    student_answer TEXT,
    correct_answer TEXT,
    question_snapshot TEXT,
    score DECIMAL(10,2),
    is_correct TINYINT,
    grade_status TINYINT,
    grader_id INT,
    grade_time DATETIME,
    comment VARCHAR(255)
);

CREATE TABLE t_homework_paper (
    id INT AUTO_INCREMENT PRIMARY KEY,
    course_id INT,
    teacher_id INT,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    total_score DECIMAL(10,2),
    duration_minutes INT,
    status TINYINT,
    create_time DATETIME,
    update_time DATETIME
);

CREATE TABLE t_homework_paper_question (
    id INT AUTO_INCREMENT PRIMARY KEY,
    paper_id INT NOT NULL,
    question_id INT NOT NULL,
    score DECIMAL(10,2),
    sort_order INT,
    required_answer TINYINT
);

CREATE TABLE t_homework_session (
    id INT AUTO_INCREMENT PRIMARY KEY,
    class_id INT,
    paper_id INT NOT NULL,
    teacher_id INT,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    start_time DATETIME,
    end_time DATETIME,
    duration INT,
    status TINYINT,
    create_time DATETIME,
    update_time DATETIME
);

CREATE TABLE t_homework_record (
    id INT AUTO_INCREMENT PRIMARY KEY,
    homework_session_id INT NOT NULL,
    student_id INT,
    candidate_id VARCHAR(64),
    start_time DATETIME,
    submit_time DATETIME,
    total_score DECIMAL(10,2),
    objective_score DECIMAL(10,2),
    subjective_score DECIMAL(10,2),
    status TINYINT,
    comment VARCHAR(255),
    create_time DATETIME,
    update_time DATETIME,
    UNIQUE (homework_session_id, student_id)
);

CREATE TABLE t_homework_answer (
    id INT AUTO_INCREMENT PRIMARY KEY,
    homework_record_id INT NOT NULL,
    question_id INT NOT NULL,
    paper_question_id INT,
    student_answer TEXT,
    correct_answer TEXT,
    question_snapshot TEXT,
    score DECIMAL(10,2),
    is_correct TINYINT,
    grade_status TINYINT,
    grader_id INT,
    grade_time DATETIME,
    comment VARCHAR(255)
);

CREATE TABLE t_operation_log (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT,
    operation VARCHAR(100),
    method VARCHAR(20),
    url VARCHAR(255),
    params TEXT,
    ip VARCHAR(64),
    create_time DATETIME
);

CREATE TABLE t_agent_pending_action (
    action_id VARCHAR(64) PRIMARY KEY,
    action_code VARCHAR(100) NOT NULL,
    created_by INT,
    parameters_json TEXT,
    created_at DATETIME NOT NULL,
    expires_at DATETIME NOT NULL
);

CREATE TABLE t_teacher_application (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50),
    password VARCHAR(100),
    nickname VARCHAR(50),
    email VARCHAR(100),
    phone VARCHAR(20),
    reason VARCHAR(500),
    status TINYINT,
    reviewer_id INT,
    review_comment VARCHAR(255),
    create_time DATETIME,
    review_time DATETIME
);

CREATE TABLE t_ai_preparation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    teacher_id INT NOT NULL,
    title VARCHAR(200) NOT NULL,
    config_json CLOB NOT NULL,
    knowledge_base_ids_json CLOB,
    owned_knowledge_base_id VARCHAR(100),
    source_files_json CLOB,
    outline_json CLOB,
    lessons_json CLOB,
    questions_json CLOB,
    project_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    current_stage VARCHAR(30) NOT NULL DEFAULT 'CONFIG',
    generation_status VARCHAR(20) NOT NULL DEFAULT 'IDLE',
    progress INT NOT NULL DEFAULT 0,
    error_message CLOB,
    published_course_id INT,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

INSERT INTO t_role (id, role_name, role_code, create_time) VALUES
(1, '学生', 'student', CURRENT_TIMESTAMP),
(2, '教师', 'teacher', CURRENT_TIMESTAMP),
(3, '管理员', 'admin', CURRENT_TIMESTAMP);

INSERT INTO t_user (id, username, password, nickname, email, phone, status, create_time, update_time) VALUES
(1, 'admin', 'e10adc3949ba59abbe56e057f20f883e', '系统管理员', 'admin@example.com', '13800000001', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 'teacher', 'e10adc3949ba59abbe56e057f20f883e', '王老师', 'teacher@example.com', '13800000002', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 'student', 'e10adc3949ba59abbe56e057f20f883e', '张同学', 'student@example.com', '13800000003', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO t_user_role (id, user_id, role_id) VALUES
(1, 1, 3),
(2, 2, 2),
(3, 3, 1);

INSERT INTO t_course_category (id, name, description, sort_order, create_time, update_time) VALUES
(1, '默认分类', 'E2E 测试基础分类', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO t_course (id, category_id, teacher_id, title, description, teacher, difficulty, student_count, view_count, rating, status, created_at, updated_at) VALUES
(1, 1, 2, 'Java 基础课程', 'E2E 测试基础课程', '王老师', 'beginner', 1, 10, 4.8, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO t_teaching_class (id, course_id, teacher_id, class_name, semester, description, status, create_time, update_time) VALUES
(1, 1, 2, 'Java 基础班', '2026春季', 'E2E 测试基础班级', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO t_class_student (id, class_id, student_id, join_time, status) VALUES
(1, 1, 3, CURRENT_TIMESTAMP, 1);

INSERT INTO t_homework (id, class_id, teacher_id, title, content, full_score, start_time, deadline, status, create_time, update_time) VALUES
(1, 1, 2, '基础作业', 'E2E 测试基础作业', 100, CURRENT_TIMESTAMP, DATEADD('DAY', 7, CURRENT_TIMESTAMP), 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
