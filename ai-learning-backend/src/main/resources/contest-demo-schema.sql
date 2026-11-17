-- Isolated H2 demonstration schema derived from the current MySQL schema.
-- Additive only; no DROP/DELETE. MySQL remains the production schema with full indexes and foreign keys.

CREATE TABLE IF NOT EXISTS `lesson_comment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `lesson_id` int NOT NULL,
  `user_id` int NOT NULL,
  `parent_id` bigint DEFAULT NULL,
  `content` varchar(1000) NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `paper_user_mark` (
  `id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `paper_id` varchar(100) NOT NULL,
  `title` varchar(500) DEFAULT NULL,
  `source` varchar(50) DEFAULT NULL,
  `is_favorite` tinyint(1) DEFAULT '0',
  `read_status` varchar(20) DEFAULT 'UNREAD',
  `note_md` text,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE (`user_id`,`paper_id`)
);

CREATE TABLE IF NOT EXISTS `study_note` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `course_id` int NOT NULL,
  `lesson_id` int NOT NULL,
  `title` varchar(255) DEFAULT NULL,
  `content_md` longtext NOT NULL,
  `last_video_time` int DEFAULT '0',
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE (`user_id`,`lesson_id`)
);

CREATE TABLE IF NOT EXISTS `t_administrative_class` (
  `id` int NOT NULL AUTO_INCREMENT,
  `course_id` int NOT NULL,
  `class_code` varchar(50) NOT NULL,
  `class_name` varchar(100) NOT NULL,
  `grade` varchar(50) DEFAULT NULL,
  `major` varchar(100) DEFAULT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `status` tinyint DEFAULT '1',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE (`class_code`)
);

CREATE TABLE IF NOT EXISTS `t_administrative_class_student` (
  `id` int NOT NULL AUTO_INCREMENT,
  `administrative_class_id` int NOT NULL,
  `student_id` int NOT NULL,
  `join_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `status` tinyint DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE (`administrative_class_id`,`student_id`)
);

CREATE TABLE IF NOT EXISTS `t_agent` (
  `id` int NOT NULL AUTO_INCREMENT,
  `code` varchar(64) NOT NULL,
  `name` varchar(100) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `system_prompt` text,
  `model` varchar(100) DEFAULT NULL,
  `temperature` decimal(3,2) DEFAULT '0.70',
  `knowledge_base_ids` text,
  `enabled_tools` text,
  `status` tinyint DEFAULT '1',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE (`code`)
);

CREATE TABLE IF NOT EXISTS `t_agent_message` (
  `id` int NOT NULL AUTO_INCREMENT,
  `session_id` int NOT NULL,
  `role` varchar(20) NOT NULL,
  `content` longtext,
  `tool_name` varchar(100) DEFAULT NULL,
  `tool_args` text,
  `tool_result` longtext,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `t_agent_pending_action` (
  `action_id` varchar(64) NOT NULL,
  `action_code` varchar(100) NOT NULL,
  `created_by` int DEFAULT NULL,
  `parameters_json` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `expires_at` datetime NOT NULL,
  PRIMARY KEY (`action_id`)
);

CREATE TABLE IF NOT EXISTS `t_agent_session` (
  `id` int NOT NULL AUTO_INCREMENT,
  `agent_id` int NOT NULL,
  `user_id` int DEFAULT NULL,
  `title` varchar(100) NOT NULL,
  `status` tinyint DEFAULT '1',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `t_chapter` (
  `id` int NOT NULL AUTO_INCREMENT,
  `course_id` int NOT NULL,
  `title` varchar(200) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `sort_order` int DEFAULT NULL,
  `status` tinyint DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `t_class_student` (
  `id` int NOT NULL AUTO_INCREMENT,
  `class_id` int NOT NULL,
  `student_id` int NOT NULL,
  `join_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `status` tinyint DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE (`class_id`,`student_id`)
);

CREATE TABLE IF NOT EXISTS `t_course` (
  `id` int NOT NULL AUTO_INCREMENT,
  `category_id` int NOT NULL,
  `teacher_id` int DEFAULT NULL,
  `title` varchar(200) NOT NULL,
  `description` text,
  `cover` varchar(255) DEFAULT NULL,
  `cover_url` varchar(255) DEFAULT NULL,
  `teacher` varchar(100) DEFAULT NULL,
  `difficulty` varchar(20) DEFAULT 'beginner',
  `student_count` int DEFAULT '0',
  `view_count` int DEFAULT '0',
  `rating` decimal(2,1) DEFAULT '0.0',
  `status` tinyint DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `t_home_course_recommendation` (
  `id` int NOT NULL AUTO_INCREMENT,
  `course_id` int NOT NULL,
  `reason` varchar(255) DEFAULT NULL,
  `sort_order` int NOT NULL DEFAULT '1',
  `status` tinyint NOT NULL DEFAULT '1',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE (`course_id`)
);

CREATE TABLE IF NOT EXISTS `t_course_category` (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `sort_order` int DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `t_exam_answer` (
  `id` int NOT NULL AUTO_INCREMENT,
  `exam_record_id` int NOT NULL,
  `question_id` int NOT NULL,
  `paper_question_id` int DEFAULT NULL,
  `student_answer` text,
  `correct_answer` text,
  `question_snapshot` text,
  `score` decimal(6,2) DEFAULT NULL,
  `is_correct` tinyint DEFAULT NULL,
  `grade_status` tinyint DEFAULT '0',
  `grader_id` int DEFAULT NULL,
  `grade_time` datetime DEFAULT NULL,
  `comment` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `t_exam_paper` (
  `id` int NOT NULL AUTO_INCREMENT,
  `course_id` int DEFAULT NULL,
  `teacher_id` int DEFAULT NULL,
  `title` varchar(100) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `total_score` decimal(6,2) DEFAULT '100.00',
  `duration_minutes` int DEFAULT '0',
  `status` tinyint DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `t_exam_paper_question` (
  `id` int NOT NULL AUTO_INCREMENT,
  `paper_id` int NOT NULL,
  `question_id` int NOT NULL,
  `score` decimal(6,2) DEFAULT '0.00',
  `sort_order` int DEFAULT NULL,
  `required_answer` tinyint DEFAULT '1',
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `t_exam_record` (
  `id` int NOT NULL AUTO_INCREMENT,
  `exam_session_id` int NOT NULL,
  `student_id` int DEFAULT NULL,
  `candidate_id` varchar(64) DEFAULT NULL,
  `start_time` datetime DEFAULT NULL,
  `submit_time` datetime DEFAULT NULL,
  `total_score` decimal(6,2) DEFAULT NULL,
  `objective_score` decimal(6,2) DEFAULT NULL,
  `subjective_score` decimal(6,2) DEFAULT NULL,
  `status` tinyint DEFAULT '0',
  `comment` varchar(255) DEFAULT NULL,
  `screen_switch_count` int NOT NULL DEFAULT '0',
  `cheated` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE (`exam_session_id`,`student_id`)
);

CREATE TABLE IF NOT EXISTS `t_exam_session` (
  `id` int NOT NULL AUTO_INCREMENT,
  `class_id` int DEFAULT NULL,
  `paper_id` int NOT NULL,
  `teacher_id` int DEFAULT NULL,
  `title` varchar(100) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `start_time` datetime DEFAULT NULL,
  `end_time` datetime DEFAULT NULL,
  `duration` int DEFAULT NULL,
  `status` tinyint DEFAULT '0',
  `max_screen_switches` int NOT NULL DEFAULT '3',
  `disable_clipboard` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `t_grade_import_log` (
  `id` int NOT NULL AUTO_INCREMENT,
  `teacher_id` int NOT NULL,
  `class_id` int NOT NULL,
  `file_name` varchar(255) DEFAULT NULL,
  `file_url` varchar(255) DEFAULT NULL,
  `total_count` int DEFAULT '0',
  `success_count` int DEFAULT '0',
  `fail_count` int DEFAULT '0',
  `error_message` text,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `t_grade_record` (
  `id` int NOT NULL AUTO_INCREMENT,
  `student_id` int NOT NULL,
  `class_id` int NOT NULL,
  `course_id` int NOT NULL,
  `teacher_id` int NOT NULL,
  `grade_name` varchar(100) NOT NULL,
  `source_type` varchar(30) NOT NULL,
  `source_id` int DEFAULT NULL,
  `score` decimal(6,2) DEFAULT NULL,
  `full_score` decimal(6,2) DEFAULT '100.00',
  `grade_comment` varchar(255) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `t_homework` (
  `id` int NOT NULL AUTO_INCREMENT,
  `class_id` int NOT NULL,
  `teacher_id` int NOT NULL,
  `title` varchar(100) NOT NULL,
  `content` text,
  `attachment_url` varchar(255) DEFAULT NULL,
  `full_score` decimal(6,2) DEFAULT '100.00',
  `start_time` datetime DEFAULT NULL,
  `deadline` datetime DEFAULT NULL,
  `status` tinyint DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `t_homework_submit` (
  `id` int NOT NULL AUTO_INCREMENT,
  `homework_id` int NOT NULL,
  `student_id` int NOT NULL,
  `submit_content` text,
  `attachment_url` varchar(255) DEFAULT NULL,
  `submit_time` datetime DEFAULT NULL,
  `score` decimal(6,2) DEFAULT NULL,
  `feedback` text,
  `grade_status` tinyint DEFAULT '0',
  `grader_id` int DEFAULT NULL,
  `grade_time` datetime DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `t_learning_progress` (
  `id` int NOT NULL AUTO_INCREMENT,
  `class_id` int NOT NULL,
  `student_id` int NOT NULL,
  `lesson_id` int NOT NULL,
  `progress` int DEFAULT '0',
  `learned_seconds` int DEFAULT '0',
  `last_position` int DEFAULT '0',
  `last_learn_time` datetime DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `t_learning_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `class_id` int NOT NULL,
  `student_id` int NOT NULL,
  `lesson_id` int NOT NULL,
  `start_position` int DEFAULT '0',
  `end_position` int DEFAULT '0',
  `learned_seconds` int DEFAULT '0',
  `record_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `t_lesson` (
  `id` int NOT NULL AUTO_INCREMENT,
  `chapter_id` int NOT NULL,
  `title` varchar(200) NOT NULL,
  `resource_type` varchar(30) DEFAULT NULL,
  `resource_url` varchar(500) DEFAULT NULL,
  `video_url` varchar(255) DEFAULT NULL,
  `video_resource_id` bigint DEFAULT NULL,
  `content` mediumtext,
  `duration` int DEFAULT '0',
  `sort_order` int DEFAULT NULL,
  `view_count` int DEFAULT '0',
  `status` tinyint DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `t_notice` (
  `id` int NOT NULL AUTO_INCREMENT,
  `publisher_id` int NOT NULL,
  `class_id` int DEFAULT NULL,
  `title` varchar(100) NOT NULL,
  `content` text,
  `notice_type` varchar(30) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `status` tinyint DEFAULT '1',
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `t_operation_log` (
  `id` int NOT NULL AUTO_INCREMENT,
  `user_id` int DEFAULT NULL,
  `operation` varchar(100) DEFAULT NULL,
  `method` varchar(20) DEFAULT NULL,
  `url` varchar(255) DEFAULT NULL,
  `params` text,
  `ip` varchar(50) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `t_paper` (
  `id` int NOT NULL AUTO_INCREMENT,
  `arxiv_id` varchar(50) NOT NULL,
  `title` varchar(500) NOT NULL,
  `abstract_text` text,
  `year` int DEFAULT NULL,
  `url` varchar(500) DEFAULT NULL,
  `pdf_url` varchar(500) DEFAULT NULL,
  `venue` varchar(500) DEFAULT NULL,
  `citation_count` int DEFAULT '0',
  `source` varchar(50) DEFAULT 'arxiv',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE (`arxiv_id`)
);

CREATE TABLE IF NOT EXISTS `t_paper_author` (
  `id` int NOT NULL AUTO_INCREMENT,
  `paper_id` int NOT NULL,
  `name` varchar(200) NOT NULL,
  `sort_order` int DEFAULT '0',
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `t_question` (
  `id` int NOT NULL AUTO_INCREMENT,
  `course_id` int DEFAULT NULL,
  `teacher_id` int DEFAULT NULL,
  `question_type` varchar(30) NOT NULL,
  `title` text NOT NULL,
  `options` text,
  `answer` text,
  `analysis` text,
  `knowledge_point` varchar(200) DEFAULT NULL,
  `difficulty` tinyint DEFAULT '1',
  `score` decimal(6,2) DEFAULT '5.00',
  `status` tinyint DEFAULT '1',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `t_ai_preparation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `teacher_id` int NOT NULL,
  `title` varchar(200) NOT NULL,
  `config_json` longtext NOT NULL,
  `knowledge_base_ids_json` text,
  `owned_knowledge_base_id` varchar(100) DEFAULT NULL,
  `source_files_json` longtext,
  `outline_json` longtext,
  `lessons_json` longtext,
  `questions_json` longtext,
  `project_status` varchar(20) NOT NULL DEFAULT 'DRAFT',
  `current_stage` varchar(30) NOT NULL DEFAULT 'CONFIG',
  `generation_status` varchar(20) NOT NULL DEFAULT 'IDLE',
  `progress` int NOT NULL DEFAULT '0',
  `error_message` text,
  `published_course_id` int DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `t_role` (
  `id` int NOT NULL AUTO_INCREMENT,
  `role_name` varchar(50) NOT NULL,
  `role_code` varchar(50) NOT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE (`role_code`)
);

CREATE TABLE IF NOT EXISTS `t_teacher_application` (
  `id` int NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL,
  `password` varchar(100) NOT NULL,
  `nickname` varchar(50) DEFAULT NULL,
  `email` varchar(100) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `reason` varchar(500) NOT NULL,
  `status` tinyint DEFAULT '0',
  `reviewer_id` int DEFAULT NULL,
  `review_comment` varchar(255) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `review_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `t_teaching_class` (
  `id` int NOT NULL AUTO_INCREMENT,
  `course_id` int DEFAULT NULL,
  `teacher_id` int NOT NULL,
  `class_name` varchar(100) NOT NULL,
  `semester` varchar(50) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `status` tinyint DEFAULT '1',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `t_user` (
  `id` int NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL,
  `password` varchar(100) NOT NULL,
  `nickname` varchar(50) DEFAULT NULL,
  `email` varchar(100) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `avatar` varchar(255) DEFAULT NULL,
  `status` tinyint DEFAULT '1',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE (`username`)
);

CREATE TABLE IF NOT EXISTS `t_user_role` (
  `id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `role_id` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE (`user_id`,`role_id`)
);

CREATE TABLE IF NOT EXISTS `t_work_study` (
  `id` int NOT NULL AUTO_INCREMENT,
  `job_name` varchar(100) NOT NULL,
  `job_desc` varchar(500) DEFAULT NULL,
  `work_location` varchar(100) DEFAULT NULL,
  `work_hours` varchar(50) DEFAULT NULL,
  `hourly_rate` decimal(8,2) DEFAULT '0.00',
  `max_recruit` int DEFAULT '1',
  `status` tinyint DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `t_work_study_student` (
  `id` int NOT NULL AUTO_INCREMENT,
  `work_study_id` int NOT NULL,
  `student_id` int NOT NULL,
  `work_month` varchar(20) DEFAULT NULL,
  `work_hours` decimal(6,2) DEFAULT '0.00',
  `hourly_rate` decimal(8,2) DEFAULT '0.00',
  `total_amount` decimal(10,2) DEFAULT '0.00',
  `apply_time` datetime DEFAULT NULL,
  `audit_status` tinyint DEFAULT '0',
  `auditor_id` int DEFAULT NULL,
  `audit_time` datetime DEFAULT NULL,
  `reject_reason` varchar(500) DEFAULT NULL,
  `status` tinyint DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `video_resource` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `source_url` text NOT NULL,
  `source_url_hash` char(64) NOT NULL,
  `title` varchar(255) DEFAULT NULL,
  `provider` varchar(50) DEFAULT NULL,
  `status` varchar(30) NOT NULL DEFAULT 'PENDING',
  `storage_path` varchar(500) DEFAULT NULL,
  `play_url` varchar(500) DEFAULT NULL,
  `cover_url` varchar(500) DEFAULT NULL,
  `duration` int DEFAULT '0',
  `file_size` bigint DEFAULT '0',
  `summary_md` longtext,
  `error_message` text,
  `created_by` int NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE (`source_url_hash`)
);

CREATE TABLE IF NOT EXISTS `t_homework_paper` (
  `id` int NOT NULL AUTO_INCREMENT,
  `course_id` int DEFAULT NULL,
  `teacher_id` int DEFAULT NULL,
  `title` varchar(100) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `total_score` decimal(6,2) DEFAULT '100.00',
  `duration_minutes` int DEFAULT '0',
  `status` tinyint DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `t_homework_paper_question` (
  `id` int NOT NULL AUTO_INCREMENT,
  `paper_id` int NOT NULL,
  `question_id` int NOT NULL,
  `score` decimal(6,2) DEFAULT '0.00',
  `sort_order` int DEFAULT NULL,
  `required_answer` tinyint DEFAULT '1',
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `t_homework_session` (
  `id` int NOT NULL AUTO_INCREMENT,
  `class_id` int DEFAULT NULL,
  `paper_id` int NOT NULL,
  `teacher_id` int DEFAULT NULL,
  `title` varchar(100) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `start_time` datetime DEFAULT NULL,
  `end_time` datetime DEFAULT NULL,
  `duration` int DEFAULT NULL,
  `status` tinyint DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `t_homework_record` (
  `id` int NOT NULL AUTO_INCREMENT,
  `homework_session_id` int NOT NULL,
  `student_id` int DEFAULT NULL,
  `candidate_id` varchar(64) DEFAULT NULL,
  `start_time` datetime DEFAULT NULL,
  `submit_time` datetime DEFAULT NULL,
  `total_score` decimal(6,2) DEFAULT NULL,
  `objective_score` decimal(6,2) DEFAULT NULL,
  `subjective_score` decimal(6,2) DEFAULT NULL,
  `status` tinyint DEFAULT '0',
  `comment` varchar(255) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE (`homework_session_id`,`student_id`)
);

CREATE TABLE IF NOT EXISTS `t_homework_answer` (
  `id` int NOT NULL AUTO_INCREMENT,
  `homework_record_id` int NOT NULL,
  `question_id` int NOT NULL,
  `paper_question_id` int DEFAULT NULL,
  `student_answer` text,
  `correct_answer` text,
  `question_snapshot` text,
  `score` decimal(6,2) DEFAULT NULL,
  `is_correct` tinyint DEFAULT NULL,
  `grade_status` tinyint DEFAULT '0',
  `grader_id` int DEFAULT NULL,
  `grade_time` datetime DEFAULT NULL,
  `comment` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `t_homework_assessment_migration` (
  `legacy_homework_id` int NOT NULL,
  `homework_paper_id` int DEFAULT NULL,
  `homework_session_id` int DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `error_message` varchar(1000) DEFAULT NULL,
  `migrated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`legacy_homework_id`)
);
