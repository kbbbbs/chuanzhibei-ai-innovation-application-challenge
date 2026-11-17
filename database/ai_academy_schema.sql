-- MySQL dump 10.13  Distrib 8.0.42, for Win64 (x86_64)
--
-- Host: localhost    Database: ai_academy
-- ------------------------------------------------------
-- Server version	8.0.42

-- AI 智学平台数据库结构
-- 导出日期：2026-07-10
-- 内容：46 张表、主键、外键、唯一约束及索引；仅包含最小登录账号、角色、学科分类与默认 Agent 初始化数据
-- 注意：本脚本会删除并重建 ai_academy 数据库，请勿直接用于需要保留数据的环境
-- 初始账号：admin、teacher、student；密码均为 123456，仅限本地开发和测试
-- 导入示例：mysql -uroot -p --default-character-set=utf8mb4 < ai_academy_schema.sql

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `ai_academy`
--

/*!40000 DROP DATABASE IF EXISTS `ai_academy`*/;

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `ai_academy` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `ai_academy`;

--
-- Table structure for table `lesson_comment`
--

DROP TABLE IF EXISTS `lesson_comment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lesson_comment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `lesson_id` int NOT NULL,
  `user_id` int NOT NULL,
  `parent_id` bigint DEFAULT NULL,
  `content` varchar(1000) NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_lesson_comment_lesson` (`lesson_id`,`deleted`,`created_at`),
  KEY `idx_lesson_comment_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课时评论表';
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `paper_user_mark`
--

DROP TABLE IF EXISTS `paper_user_mark`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `paper_user_mark` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'User paper mark primary key',
  `user_id` int NOT NULL COMMENT 'Current user id',
  `paper_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'External or local paper identifier',
  `title` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Paper title snapshot',
  `source` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Paper source snapshot',
  `is_favorite` tinyint(1) DEFAULT '0' COMMENT 'Favorite flag',
  `read_status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'UNREAD' COMMENT 'Read status',
  `note_md` text COLLATE utf8mb4_unicode_ci COMMENT 'Personal markdown note',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_paper` (`user_id`,`paper_id`),
  KEY `idx_user_updated` (`user_id`,`updated_at`),
  CONSTRAINT `fk_paper_user_mark_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User paper favorites and notes';
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `study_note`
--

DROP TABLE IF EXISTS `study_note`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `study_note` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `course_id` int NOT NULL,
  `lesson_id` int NOT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `content_md` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `last_video_time` int DEFAULT '0',
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_study_note_user_lesson` (`user_id`,`lesson_id`),
  KEY `idx_study_note_course_id` (`course_id`),
  KEY `idx_study_note_lesson_id` (`lesson_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `t_administrative_class`
--

DROP TABLE IF EXISTS `t_administrative_class`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_administrative_class` (
  `id` int NOT NULL AUTO_INCREMENT,
  `course_id` int NOT NULL COMMENT '关联课程ID',
  `class_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '行政班编号',
  `class_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '行政班名称',
  `grade` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '年级',
  `major` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '专业',
  `description` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明',
  `status` tinyint DEFAULT '1' COMMENT '状态：1正常 0停用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_administrative_class_code` (`class_code`),
  KEY `idx_administrative_class_course` (`course_id`),
  KEY `idx_administrative_class_name` (`class_name`),
  CONSTRAINT `fk_administrative_class_course` FOREIGN KEY (`course_id`) REFERENCES `t_course` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `t_administrative_class_student`
--

DROP TABLE IF EXISTS `t_administrative_class_student`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_administrative_class_student` (
  `id` int NOT NULL AUTO_INCREMENT,
  `administrative_class_id` int NOT NULL COMMENT '行政班ID',
  `student_id` int NOT NULL COMMENT '学生ID',
  `join_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `status` tinyint DEFAULT '1' COMMENT '状态：1正常 0停用',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_administrative_class_student` (`administrative_class_id`,`student_id`),
  KEY `idx_administrative_class_student_id` (`student_id`),
  CONSTRAINT `fk_administrative_class_student_class` FOREIGN KEY (`administrative_class_id`) REFERENCES `t_administrative_class` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_administrative_class_student_user` FOREIGN KEY (`student_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `t_agent`
--

DROP TABLE IF EXISTS `t_agent`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_agent` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'Agent编号',
  `code` varchar(64) NOT NULL COMMENT 'Agent编码',
  `name` varchar(100) NOT NULL COMMENT 'Agent名称',
  `description` varchar(255) DEFAULT NULL COMMENT 'Agent描述',
  `system_prompt` text COMMENT '系统提示词',
  `model` varchar(100) DEFAULT NULL COMMENT '模型名称',
  `temperature` decimal(3,2) DEFAULT '0.70' COMMENT '采样温度',
  `knowledge_base_ids` text COMMENT '关联知识库ID，JSON数组字符串',
  `enabled_tools` text COMMENT '启用工具，JSON数组字符串',
  `status` tinyint DEFAULT '1' COMMENT '状态：1启用，0停用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=102 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent定义表';
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `t_agent_message`
--

DROP TABLE IF EXISTS `t_agent_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_agent_message` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'Agent消息编号',
  `session_id` int NOT NULL COMMENT '会话编号',
  `role` varchar(20) NOT NULL COMMENT '消息角色：system/user/assistant/tool',
  `content` longtext COMMENT '消息内容',
  `tool_name` varchar(100) DEFAULT NULL COMMENT '工具名称',
  `tool_args` text COMMENT '工具参数',
  `tool_result` longtext COMMENT '工具结果',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_agent_message_session` (`session_id`),
  CONSTRAINT `fk_agent_message_session` FOREIGN KEY (`session_id`) REFERENCES `t_agent_session` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=101 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent消息表';
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `t_agent_pending_action`
--

DROP TABLE IF EXISTS `t_agent_pending_action`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_agent_pending_action` (
  `action_id` varchar(64) NOT NULL COMMENT '待确认操作ID',
  `action_code` varchar(100) NOT NULL COMMENT '操作编码',
  `created_by` int DEFAULT NULL COMMENT '创建用户编号',
  `parameters_json` text COMMENT '待确认参数JSON',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `expires_at` datetime NOT NULL COMMENT '过期时间',
  PRIMARY KEY (`action_id`),
  KEY `idx_agent_pending_created_by` (`created_by`),
  KEY `idx_agent_pending_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent待确认操作表';
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `t_agent_session`
--

DROP TABLE IF EXISTS `t_agent_session`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_agent_session` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'Agent会话编号',
  `agent_id` int NOT NULL COMMENT 'Agent编号',
  `user_id` int DEFAULT NULL COMMENT '用户编号',
  `title` varchar(100) NOT NULL COMMENT '会话标题',
  `status` tinyint DEFAULT '1' COMMENT '状态：1正常，0删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_agent_session_agent` (`agent_id`),
  KEY `idx_agent_session_user` (`user_id`),
  CONSTRAINT `fk_agent_session_agent` FOREIGN KEY (`agent_id`) REFERENCES `t_agent` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_agent_session_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=101 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent会话表';
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `t_chapter`
--

DROP TABLE IF EXISTS `t_chapter`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_chapter` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '章节编号',
  `course_id` int NOT NULL COMMENT '课程编号',
  `title` varchar(200) NOT NULL COMMENT '章节名称',
  `description` varchar(500) DEFAULT NULL COMMENT '章节说明',
  `sort_order` int DEFAULT NULL COMMENT '排序',
  `status` tinyint DEFAULT '1' COMMENT '状态：0已删除，1正常',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_chapter_course_id` (`course_id`),
  KEY `idx_chapter_status` (`status`),
  CONSTRAINT `fk_chapter_course` FOREIGN KEY (`course_id`) REFERENCES `t_course` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=879 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='章节表';
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `t_class_student`
--

DROP TABLE IF EXISTS `t_class_student`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_class_student` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '编号',
  `class_id` int NOT NULL COMMENT '教学班编号',
  `student_id` int NOT NULL COMMENT '学生编号',
  `join_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `status` tinyint DEFAULT '1' COMMENT '状态：1正常，2休学',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_class_student` (`class_id`,`student_id`),
  KEY `fk_class_student_user` (`student_id`),
  CONSTRAINT `fk_class_student_class` FOREIGN KEY (`class_id`) REFERENCES `t_teaching_class` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_class_student_user` FOREIGN KEY (`student_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1919 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='教学班学生表';
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `t_course`
--

DROP TABLE IF EXISTS `t_course`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_course` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '课程编号',
  `category_id` int NOT NULL COMMENT '分类编号',
  `teacher_id` int DEFAULT NULL COMMENT '创建教师编号',
  `title` varchar(200) NOT NULL COMMENT '课程名称',
  `description` text COMMENT '课程简介',
  `cover` varchar(255) DEFAULT NULL COMMENT '封面地址',
  `cover_url` varchar(255) DEFAULT NULL COMMENT '课程封面地址',
  `teacher` varchar(100) DEFAULT NULL COMMENT '授课教师',
  `difficulty` varchar(20) DEFAULT 'beginner' COMMENT '难度',
  `student_count` int DEFAULT '0' COMMENT '学习人数',
  `view_count` int DEFAULT '0' COMMENT '浏览次数',
  `rating` decimal(2,1) DEFAULT '0.0' COMMENT '评分',
  `status` tinyint DEFAULT '1' COMMENT '状态：0下架，1正常，2草稿',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_course_category_id` (`category_id`),
  KEY `idx_course_teacher_id` (`teacher_id`),
  KEY `idx_course_status` (`status`),
  CONSTRAINT `fk_course_category` FOREIGN KEY (`category_id`) REFERENCES `t_course_category` (`id`),
  CONSTRAINT `fk_course_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=109 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `t_home_course_recommendation`
--

DROP TABLE IF EXISTS `t_home_course_recommendation`;
CREATE TABLE `t_home_course_recommendation` (
  `id` int NOT NULL AUTO_INCREMENT,
  `course_id` int DEFAULT NULL COMMENT '推荐课程ID',
  `reason` varchar(255) DEFAULT NULL COMMENT '首页推荐理由',
  `sort_order` int NOT NULL DEFAULT '1' COMMENT '首页展示顺序，越小越靠前',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '0停用，1启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_home_course_recommendation_course` (`course_id`),
  KEY `idx_home_course_recommendation_sort` (`status`,`sort_order`),
  CONSTRAINT `fk_home_course_recommendation_course` FOREIGN KEY (`course_id`) REFERENCES `t_course` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='首页人工推荐课程';



--
-- Table structure for table `t_course_category`
--

DROP TABLE IF EXISTS `t_course_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_course_category` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '分类编号',
  `name` varchar(50) NOT NULL COMMENT '分类名称',
  `description` varchar(255) DEFAULT NULL COMMENT '分类说明',
  `sort_order` int DEFAULT NULL COMMENT '排序',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_category_sort` (`sort_order`)
) ENGINE=InnoDB AUTO_INCREMENT=108 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程分类表';
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `t_exam_answer`
--

DROP TABLE IF EXISTS `t_exam_answer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_exam_answer` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '答题编号',
  `exam_record_id` int NOT NULL COMMENT '考试记录编号',
  `question_id` int NOT NULL COMMENT '题目编号',
  `paper_question_id` int DEFAULT NULL COMMENT '试卷题目编号',
  `student_answer` text COMMENT '学生答案',
  `correct_answer` text COMMENT '正确答案',
  `question_snapshot` text COMMENT '开始答题时的题目快照',
  `score` decimal(6,2) DEFAULT NULL COMMENT '本题得分',
  `is_correct` tinyint DEFAULT NULL COMMENT '是否正确',
  `grade_status` tinyint DEFAULT '0' COMMENT '批改状态',
  `grader_id` int DEFAULT NULL COMMENT '批改教师',
  `grade_time` datetime DEFAULT NULL COMMENT '批改时间',
  `comment` varchar(255) DEFAULT NULL COMMENT '批改评语',
  PRIMARY KEY (`id`),
  KEY `fk_exam_answer_record` (`exam_record_id`),
  KEY `fk_exam_answer_question` (`question_id`),
  KEY `fk_exam_answer_paper_question` (`paper_question_id`),
  KEY `fk_exam_answer_grader` (`grader_id`),
  CONSTRAINT `fk_exam_answer_grader` FOREIGN KEY (`grader_id`) REFERENCES `t_user` (`id`),
  CONSTRAINT `fk_exam_answer_paper_question` FOREIGN KEY (`paper_question_id`) REFERENCES `t_exam_paper_question` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_exam_answer_question` FOREIGN KEY (`question_id`) REFERENCES `t_question` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_exam_answer_record` FOREIGN KEY (`exam_record_id`) REFERENCES `t_exam_record` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=4415 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='考试答题表';
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `t_exam_paper`
--

DROP TABLE IF EXISTS `t_exam_paper`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_exam_paper` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '试卷编号',
  `course_id` int DEFAULT NULL COMMENT '课程编号',
  `teacher_id` int DEFAULT NULL COMMENT '教师编号',
  `title` varchar(100) NOT NULL COMMENT '试卷名称',
  `description` varchar(255) DEFAULT NULL COMMENT '试卷说明',
  `total_score` decimal(6,2) DEFAULT '100.00' COMMENT '试卷满分',
  `duration_minutes` int DEFAULT '0' COMMENT '考试时长，分钟',
  `status` tinyint DEFAULT '0' COMMENT '状态：0草稿，1已发布，2归档',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_exam_paper_course` (`course_id`),
  KEY `fk_exam_paper_teacher` (`teacher_id`),
  CONSTRAINT `fk_exam_paper_course` FOREIGN KEY (`course_id`) REFERENCES `t_course` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_exam_paper_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=102 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='试卷表';
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `t_exam_paper_question`
--

DROP TABLE IF EXISTS `t_exam_paper_question`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_exam_paper_question` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '编号',
  `paper_id` int NOT NULL COMMENT '试卷编号',
  `question_id` int NOT NULL COMMENT '题目编号',
  `score` decimal(6,2) DEFAULT '0.00' COMMENT '本题分值',
  `sort_order` int DEFAULT NULL COMMENT '题目顺序',
  `required_answer` tinyint DEFAULT '1' COMMENT '是否必答：1必答，0选答',
  PRIMARY KEY (`id`),
  KEY `fk_paper_question_paper` (`paper_id`),
  KEY `fk_paper_question_question` (`question_id`),
  CONSTRAINT `fk_paper_question_paper` FOREIGN KEY (`paper_id`) REFERENCES `t_exam_paper` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_paper_question_question` FOREIGN KEY (`question_id`) REFERENCES `t_question` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=510 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='试卷题目表';
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `t_exam_record`
--

DROP TABLE IF EXISTS `t_exam_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_exam_record` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '考试记录编号',
  `exam_session_id` int NOT NULL COMMENT '考试安排编号',
  `student_id` int DEFAULT NULL COMMENT '学生编号',
  `candidate_id` varchar(64) DEFAULT NULL COMMENT '兼容考试分支的考生编号',
  `start_time` datetime DEFAULT NULL COMMENT '开始考试时间',
  `submit_time` datetime DEFAULT NULL COMMENT '提交时间',
  `total_score` decimal(6,2) DEFAULT NULL COMMENT '总得分',
  `objective_score` decimal(6,2) DEFAULT NULL COMMENT '客观题得分',
  `subjective_score` decimal(6,2) DEFAULT NULL COMMENT '主观题得分',
  `status` tinyint DEFAULT '0' COMMENT '状态：0进行中，1已提交，2已批改，3待人工批改',
  `comment` varchar(255) DEFAULT NULL COMMENT '答卷备注',
  `screen_switch_count` int NOT NULL DEFAULT '0' COMMENT '切屏次数',
  `cheated` tinyint NOT NULL DEFAULT '0' COMMENT '是否作弊：1是，0否',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exam_record_session_student` (`exam_session_id`,`student_id`),
  KEY `fk_exam_record_session` (`exam_session_id`),
  KEY `fk_exam_record_student` (`student_id`),
  CONSTRAINT `fk_exam_record_session` FOREIGN KEY (`exam_session_id`) REFERENCES `t_exam_session` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_exam_record_student` FOREIGN KEY (`student_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=806 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='考试记录表';
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `t_exam_session`
--

DROP TABLE IF EXISTS `t_exam_session`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_exam_session` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '考试安排编号',
  `class_id` int DEFAULT NULL COMMENT '教学班编号',
  `paper_id` int NOT NULL COMMENT '试卷编号',
  `teacher_id` int DEFAULT NULL COMMENT '教师编号',
  `title` varchar(100) NOT NULL COMMENT '考试名称',
  `description` varchar(255) DEFAULT NULL COMMENT '考试说明',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `duration` int DEFAULT NULL COMMENT '考试时长，分钟',
  `status` tinyint DEFAULT '0' COMMENT '状态：0草稿，1已发布，2进行中，3已结束',
  `max_screen_switches` int NOT NULL DEFAULT '3' COMMENT '允许切屏次数上限',
  `disable_clipboard` tinyint NOT NULL DEFAULT '0' COMMENT '考试时是否禁用剪贴板：1是，0否',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_exam_session_class` (`class_id`),
  KEY `fk_exam_session_paper` (`paper_id`),
  KEY `fk_exam_session_teacher` (`teacher_id`),
  CONSTRAINT `fk_exam_session_class` FOREIGN KEY (`class_id`) REFERENCES `t_teaching_class` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_exam_session_paper` FOREIGN KEY (`paper_id`) REFERENCES `t_exam_paper` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_exam_session_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=135 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='考试安排表';
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `t_grade_import_log`
--

DROP TABLE IF EXISTS `t_grade_import_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_grade_import_log` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '导入日志编号',
  `teacher_id` int NOT NULL COMMENT '上传教师',
  `class_id` int NOT NULL COMMENT '教学班编号',
  `file_name` varchar(255) DEFAULT NULL COMMENT '原始文件名',
  `file_url` varchar(255) DEFAULT NULL COMMENT '文件地址',
  `total_count` int DEFAULT '0' COMMENT '总记录数',
  `success_count` int DEFAULT '0' COMMENT '成功数量',
  `fail_count` int DEFAULT '0' COMMENT '失败数量',
  `error_message` text COMMENT '错误信息',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_grade_import_teacher` (`teacher_id`),
  KEY `fk_grade_import_class` (`class_id`),
  CONSTRAINT `fk_grade_import_class` FOREIGN KEY (`class_id`) REFERENCES `t_teaching_class` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_grade_import_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=106 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='成绩导入日志表';
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `t_grade_record`
--

DROP TABLE IF EXISTS `t_grade_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_grade_record` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '成绩编号',
  `student_id` int NOT NULL COMMENT '学生编号',
  `class_id` int NOT NULL COMMENT '教学班编号',
  `course_id` int NOT NULL COMMENT '课程编号',
  `teacher_id` int NOT NULL COMMENT '教师编号',
  `grade_name` varchar(100) NOT NULL COMMENT '成绩名称',
  `source_type` varchar(30) NOT NULL COMMENT '成绩来源',
  `source_id` int DEFAULT NULL COMMENT '来源记录',
  `score` decimal(6,2) DEFAULT NULL COMMENT '得分',
  `full_score` decimal(6,2) DEFAULT '100.00' COMMENT '满分',
  `grade_comment` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_grade_student` (`student_id`),
  KEY `fk_grade_class` (`class_id`),
  KEY `fk_grade_course` (`course_id`),
  KEY `fk_grade_teacher` (`teacher_id`),
  CONSTRAINT `fk_grade_class` FOREIGN KEY (`class_id`) REFERENCES `t_teaching_class` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_grade_course` FOREIGN KEY (`course_id`) REFERENCES `t_course` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_grade_student` FOREIGN KEY (`student_id`) REFERENCES `t_user` (`id`),
  CONSTRAINT `fk_grade_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=104 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='成绩记录表';
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `t_homework`
--

DROP TABLE IF EXISTS `t_homework`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_homework` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '作业编号',
  `class_id` int NOT NULL COMMENT '教学班编号',
  `teacher_id` int NOT NULL COMMENT '教师编号',
  `title` varchar(100) NOT NULL COMMENT '作业标题',
  `content` text COMMENT '作业内容',
  `attachment_url` varchar(255) DEFAULT NULL COMMENT '附件地址',
  `full_score` decimal(6,2) DEFAULT '100.00' COMMENT '满分',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `deadline` datetime DEFAULT NULL COMMENT '截止时间',
  `status` tinyint DEFAULT '0' COMMENT '状态：0草稿，1已发布，2已结束',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_homework_class` (`class_id`),
  KEY `fk_homework_teacher` (`teacher_id`),
  CONSTRAINT `fk_homework_class` FOREIGN KEY (`class_id`) REFERENCES `t_teaching_class` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_homework_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=493 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='作业表';
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `t_homework_submit`
--

DROP TABLE IF EXISTS `t_homework_submit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_homework_submit` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '提交编号',
  `homework_id` int NOT NULL COMMENT '作业编号',
  `student_id` int NOT NULL COMMENT '学生编号',
  `submit_content` text COMMENT '提交内容',
  `attachment_url` varchar(255) DEFAULT NULL COMMENT '提交附件',
  `submit_time` datetime DEFAULT NULL COMMENT '提交时间',
  `score` decimal(6,2) DEFAULT NULL COMMENT '得分',
  `feedback` text COMMENT '教师评语',
  `grade_status` tinyint DEFAULT '0' COMMENT '批改状态：0未批改，1已批改',
  `grader_id` int DEFAULT NULL COMMENT '批改教师',
  `grade_time` datetime DEFAULT NULL COMMENT '批改时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_homework_submit_homework` (`homework_id`),
  KEY `fk_homework_submit_student` (`student_id`),
  KEY `fk_homework_submit_grader` (`grader_id`),
  CONSTRAINT `fk_homework_submit_grader` FOREIGN KEY (`grader_id`) REFERENCES `t_user` (`id`),
  CONSTRAINT `fk_homework_submit_homework` FOREIGN KEY (`homework_id`) REFERENCES `t_homework` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_homework_submit_student` FOREIGN KEY (`student_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3091 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='作业提交表';
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `t_learning_progress`
--

DROP TABLE IF EXISTS `t_learning_progress`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_learning_progress` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '进度编号',
  `class_id` int NOT NULL COMMENT '教学班编号',
  `student_id` int NOT NULL COMMENT '学生编号',
  `lesson_id` int NOT NULL COMMENT '课时编号',
  `progress` int DEFAULT '0' COMMENT '学习进度',
  `learned_seconds` int DEFAULT '0' COMMENT '学习时长',
  `last_position` int DEFAULT '0' COMMENT '上次播放位置',
  `last_learn_time` datetime DEFAULT NULL COMMENT '最近学习时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_progress_class` (`class_id`),
  KEY `fk_progress_student` (`student_id`),
  KEY `fk_progress_lesson` (`lesson_id`),
  CONSTRAINT `fk_progress_class` FOREIGN KEY (`class_id`) REFERENCES `t_teaching_class` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_progress_lesson` FOREIGN KEY (`lesson_id`) REFERENCES `t_lesson` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_progress_student` FOREIGN KEY (`student_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=119 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学习进度表';
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `t_learning_record`
--

DROP TABLE IF EXISTS `t_learning_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_learning_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '学习记录编号',
  `class_id` int NOT NULL COMMENT '教学班编号',
  `student_id` int NOT NULL COMMENT '学生编号',
  `lesson_id` int NOT NULL COMMENT '课时编号',
  `start_position` int DEFAULT '0' COMMENT '开始播放位置，单位秒',
  `end_position` int DEFAULT '0' COMMENT '结束播放位置，单位秒',
  `learned_seconds` int DEFAULT '0' COMMENT '本次有效学习时长，单位秒',
  `record_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '学习发生时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_learning_record_class` (`class_id`),
  KEY `idx_learning_record_student` (`student_id`),
  KEY `idx_learning_record_lesson` (`lesson_id`),
  KEY `idx_learning_record_student_time` (`student_id`,`record_time`),
  CONSTRAINT `fk_learning_record_class` FOREIGN KEY (`class_id`) REFERENCES `t_teaching_class` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_learning_record_lesson` FOREIGN KEY (`lesson_id`) REFERENCES `t_lesson` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_learning_record_student` FOREIGN KEY (`student_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学习行为明细表';
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `t_lesson`
--

DROP TABLE IF EXISTS `t_lesson`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_lesson` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '课时编号',
  `chapter_id` int NOT NULL COMMENT '章节编号',
  `title` varchar(200) NOT NULL COMMENT '课时名称',
  `resource_type` varchar(30) DEFAULT NULL COMMENT '资源类型',
  `resource_url` varchar(500) DEFAULT NULL COMMENT '资源地址',
  `video_url` varchar(255) DEFAULT NULL COMMENT '视频地址',
  `video_resource_id` bigint DEFAULT NULL COMMENT '关联视频资源编号',
  `content` mediumtext COMMENT '课时内容',
  `duration` int DEFAULT '0' COMMENT '时长，单位秒',
  `sort_order` int DEFAULT NULL COMMENT '排序',
  `view_count` int DEFAULT '0' COMMENT '浏览次数',
  `status` tinyint DEFAULT '1' COMMENT '状态：1启用，0停用',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_lesson_chapter_id` (`chapter_id`),
  KEY `idx_lesson_video_resource_id` (`video_resource_id`),
  CONSTRAINT `fk_lesson_chapter` FOREIGN KEY (`chapter_id`) REFERENCES `t_chapter` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=900 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课时表';
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `t_notice`
--

DROP TABLE IF EXISTS `t_notice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_notice` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '通知编号',
  `publisher_id` int NOT NULL COMMENT '发布人',
  `class_id` int DEFAULT NULL COMMENT '教学班编号',
  `title` varchar(100) NOT NULL COMMENT '通知标题',
  `content` text COMMENT '通知内容',
  `notice_type` varchar(30) DEFAULT NULL COMMENT '通知类型',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `status` tinyint DEFAULT '1' COMMENT '状态：1正常，0删除',
  PRIMARY KEY (`id`),
  KEY `fk_notice_publisher` (`publisher_id`),
  KEY `fk_notice_class` (`class_id`),
  CONSTRAINT `fk_notice_class` FOREIGN KEY (`class_id`) REFERENCES `t_teaching_class` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_notice_publisher` FOREIGN KEY (`publisher_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=112 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通知公告表';
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `t_operation_log`
--

DROP TABLE IF EXISTS `t_operation_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_operation_log` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '日志编号',
  `user_id` int DEFAULT NULL COMMENT '操作用户',
  `operation` varchar(100) DEFAULT NULL COMMENT '操作名称',
  `method` varchar(20) DEFAULT NULL COMMENT '请求方式',
  `url` varchar(255) DEFAULT NULL COMMENT '请求地址',
  `params` text COMMENT '请求参数',
  `ip` varchar(50) DEFAULT NULL COMMENT 'IP地址',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_operation_log_user` (`user_id`),
  CONSTRAINT `fk_operation_log_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=378 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志表';
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `t_paper`
--

DROP TABLE IF EXISTS `t_paper`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_paper` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'Internal primary key',
  `arxiv_id` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'arXiv paper ID (dedup key)',
  `title` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Paper title',
  `abstract_text` text COLLATE utf8mb4_unicode_ci COMMENT 'Abstract',
  `year` int DEFAULT NULL COMMENT 'Publication year',
  `url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Paper page URL',
  `pdf_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Direct PDF URL',
  `venue` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Journal/conference venue',
  `citation_count` int DEFAULT '0' COMMENT 'Citation count',
  `source` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT 'arxiv' COMMENT 'Data source',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_arxiv_id` (`arxiv_id`),
  FULLTEXT KEY `ft_title_abstract` (`title`,`abstract_text`)
) ENGINE=InnoDB AUTO_INCREMENT=109 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Locally stored academic papers';
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `t_paper_author`
--

DROP TABLE IF EXISTS `t_paper_author`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_paper_author` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'Author primary key',
  `paper_id` int NOT NULL COMMENT 'FK to t_paper.id',
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Author display name',
  `sort_order` int DEFAULT '0' COMMENT 'Author order in paper',
  PRIMARY KEY (`id`),
  KEY `idx_paper_id` (`paper_id`),
  CONSTRAINT `fk_pa_paper` FOREIGN KEY (`paper_id`) REFERENCES `t_paper` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=422 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Paper authors';
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `t_question`
--

DROP TABLE IF EXISTS `t_question`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_question` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '题目编号',
  `course_id` int DEFAULT NULL COMMENT '课程编号',
  `teacher_id` int DEFAULT NULL COMMENT '教师编号',
  `question_type` varchar(30) NOT NULL COMMENT '题型',
  `title` text NOT NULL COMMENT '题干',
  `options` text COMMENT '选项',
  `answer` text COMMENT '答案',
  `analysis` text COMMENT '解析',
  `knowledge_point` varchar(200) DEFAULT NULL COMMENT '知识点',
  `difficulty` tinyint DEFAULT '1' COMMENT '难度',
  `score` decimal(6,2) DEFAULT '5.00' COMMENT '默认分值',
  `status` tinyint DEFAULT '1' COMMENT '状态：0草稿，1启用，2停用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_question_course` (`course_id`),
  KEY `fk_question_teacher` (`teacher_id`),
  CONSTRAINT `fk_question_course` FOREIGN KEY (`course_id`) REFERENCES `t_course` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_question_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=101 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题库表';
/*!40101 SET character_set_client = @saved_cs_client */;


--
-- Table structure for table `t_ai_preparation`
--

DROP TABLE IF EXISTS `t_ai_preparation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_ai_preparation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '智能备课项目编号',
  `teacher_id` int NOT NULL COMMENT '创建教师编号',
  `title` varchar(200) NOT NULL COMMENT '备课项目标题',
  `config_json` longtext NOT NULL COMMENT '课程与生成配置JSON',
  `knowledge_base_ids_json` text COMMENT '选择的已有知识库ID列表',
  `owned_knowledge_base_id` varchar(100) DEFAULT NULL COMMENT '上传资料自动创建的专属知识库ID',
  `source_files_json` longtext COMMENT '上传资料及解析信息JSON',
  `outline_json` longtext COMMENT '章节与课时骨架草稿JSON',
  `lessons_json` longtext COMMENT '课时Markdown草稿JSON',
  `questions_json` longtext COMMENT '按章节分组的题目草稿JSON',
  `project_status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/ABANDONED',
  `current_stage` varchar(30) NOT NULL DEFAULT 'CONFIG' COMMENT '当前审核阶段',
  `generation_status` varchar(20) NOT NULL DEFAULT 'IDLE' COMMENT 'IDLE/RUNNING/SUCCEEDED/FAILED',
  `progress` int NOT NULL DEFAULT '0' COMMENT '当前生成进度0-100',
  `error_message` text COMMENT '最近一次生成错误',
  `published_course_id` int DEFAULT NULL COMMENT '发布后的课程编号',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_preparation_teacher` (`teacher_id`,`updated_at`),
  KEY `idx_ai_preparation_status` (`project_status`,`generation_status`),
  KEY `idx_ai_preparation_course` (`published_course_id`),
  CONSTRAINT `fk_ai_preparation_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `t_user` (`id`),
  CONSTRAINT `fk_ai_preparation_course` FOREIGN KEY (`published_course_id`) REFERENCES `t_course` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='智能备课草稿表';
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `t_role`
--

DROP TABLE IF EXISTS `t_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_role` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '角色编号',
  `role_name` varchar(50) NOT NULL COMMENT '角色名称',
  `role_code` varchar(50) NOT NULL COMMENT '角色编码',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `role_code` (`role_code`)
) ENGINE=InnoDB AUTO_INCREMENT=104 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `t_teacher_application`
--

DROP TABLE IF EXISTS `t_teacher_application`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_teacher_application` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '教师入驻申请编号',
  `username` varchar(50) NOT NULL COMMENT '申请用户名',
  `password` varchar(100) NOT NULL COMMENT '加密密码',
  `nickname` varchar(50) DEFAULT NULL COMMENT '教师姓名/昵称',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `reason` varchar(500) NOT NULL COMMENT '申请理由',
  `status` tinyint DEFAULT '0' COMMENT '状态：0待审核，1通过，2拒绝',
  `reviewer_id` int DEFAULT NULL COMMENT '审核管理员',
  `review_comment` varchar(255) DEFAULT NULL COMMENT '审核意见',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `review_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_teacher_application_status` (`status`),
  KEY `idx_teacher_application_username` (`username`),
  KEY `fk_teacher_application_reviewer` (`reviewer_id`),
  CONSTRAINT `fk_teacher_application_reviewer` FOREIGN KEY (`reviewer_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='教师入驻申请表';
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `t_teaching_class`
--

DROP TABLE IF EXISTS `t_teaching_class`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_teaching_class` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '教学班编号',
  `course_id` int NOT NULL COMMENT '课程编号',
  `teacher_id` int NOT NULL COMMENT '教师编号',
  `class_name` varchar(100) NOT NULL COMMENT '教学班名称',
  `semester` varchar(50) DEFAULT NULL COMMENT '学期',
  `description` varchar(255) DEFAULT NULL COMMENT '教学班说明',
  `status` tinyint DEFAULT '1' COMMENT '状态：0结束，1进行中',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_teaching_class_course` (`course_id`),
  KEY `fk_teaching_class_teacher` (`teacher_id`),
  CONSTRAINT `fk_teaching_class_course` FOREIGN KEY (`course_id`) REFERENCES `t_course` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_teaching_class_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=197 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='教学班表';
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `t_user`
--

DROP TABLE IF EXISTS `t_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_user` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '用户编号',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(100) NOT NULL COMMENT '加密密码',
  `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像',
  `status` tinyint DEFAULT '1' COMMENT '状态：1启用，0停用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=121 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `t_user_role`
--

DROP TABLE IF EXISTS `t_user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_user_role` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '编号',
  `user_id` int NOT NULL COMMENT '用户编号',
  `role_id` int NOT NULL COMMENT '角色编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`,`role_id`),
  KEY `fk_user_role_role` (`role_id`),
  CONSTRAINT `fk_user_role_role` FOREIGN KEY (`role_id`) REFERENCES `t_role` (`id`),
  CONSTRAINT `fk_user_role_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=129 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色表';
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `t_work_study`
--

DROP TABLE IF EXISTS `t_work_study`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_work_study` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '岗位编号',
  `job_name` varchar(100) NOT NULL COMMENT '岗位名称',
  `job_desc` varchar(500) DEFAULT NULL COMMENT '岗位描述',
  `work_location` varchar(100) DEFAULT NULL COMMENT '工作地点',
  `work_hours` varchar(50) DEFAULT NULL COMMENT '工作时段',
  `hourly_rate` decimal(8,2) DEFAULT '0.00' COMMENT '时薪标准',
  `max_recruit` int DEFAULT '1' COMMENT '最大招聘人数',
  `status` tinyint DEFAULT '0' COMMENT '状态：0草稿，1发布，2已结束',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_work_study_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='勤工俭学岗位表';
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `t_work_study_student`
--

DROP TABLE IF EXISTS `t_work_study_student`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_work_study_student` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '编号',
  `work_study_id` int NOT NULL COMMENT '岗位编号',
  `student_id` int NOT NULL COMMENT '学生编号',
  `work_month` varchar(20) DEFAULT NULL COMMENT '工作月份，如 2026-06',
  `work_hours` decimal(6,2) DEFAULT '0.00' COMMENT '本月工作时长',
  `hourly_rate` decimal(8,2) DEFAULT '0.00' COMMENT '适用时薪',
  `total_amount` decimal(10,2) DEFAULT '0.00' COMMENT '劳务费金额',
  `apply_time` datetime DEFAULT NULL COMMENT '申请时间',
  `audit_status` tinyint DEFAULT '0' COMMENT '审核状态：0待审核，1已通过，2已拒绝',
  `auditor_id` int DEFAULT NULL COMMENT '审核人ID',
  `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
  `reject_reason` varchar(500) DEFAULT NULL COMMENT '拒绝原因',
  `status` tinyint DEFAULT '0' COMMENT '状态：0待发放，1已发放',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_wss_student` (`student_id`),
  KEY `idx_wss_month` (`work_month`),
  KEY `idx_wss_audit_status` (`audit_status`),
  KEY `fk_wss_work_study` (`work_study_id`),
  CONSTRAINT `fk_wss_student` FOREIGN KEY (`student_id`) REFERENCES `t_user` (`id`),
  CONSTRAINT `fk_wss_work_study` FOREIGN KEY (`work_study_id`) REFERENCES `t_work_study` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='勤工俭学学生申请/劳务费表';
/*!40101 SET character_set_client = @saved_cs_client */;



--
-- Table structure for table `video_resource`
--

DROP TABLE IF EXISTS `video_resource`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `video_resource` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '视频资源编号',
  `source_url` text NOT NULL COMMENT '教师提交的原始视频地址',
  `source_url_hash` char(64) NOT NULL COMMENT '原始地址 SHA-256',
  `title` varchar(255) DEFAULT NULL COMMENT '视频标题',
  `provider` varchar(50) DEFAULT NULL COMMENT '来源平台',
  `status` varchar(30) NOT NULL DEFAULT 'PENDING' COMMENT '下载状态',
  `storage_path` varchar(500) DEFAULT NULL COMMENT '服务器本地存储相对路径',
  `play_url` varchar(500) DEFAULT NULL COMMENT '前端播放地址',
  `cover_url` varchar(500) DEFAULT NULL COMMENT '封面地址',
  `duration` int DEFAULT '0' COMMENT '视频时长，单位秒',
  `file_size` bigint DEFAULT '0' COMMENT '文件大小，单位字节',
  `summary_md` longtext COMMENT '知识点总结',
  `error_message` text COMMENT '失败原因',
  `created_by` int NOT NULL COMMENT '提交人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_video_resource_url_hash` (`source_url_hash`),
  KEY `idx_video_resource_status` (`status`),
  KEY `idx_video_resource_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='视频资源表';
/*!40101 SET character_set_client = @saved_cs_client */;
--
-- Table structure for table `t_homework_paper`
--

DROP TABLE IF EXISTS `t_homework_paper`;
CREATE TABLE `t_homework_paper` (
  `id` int NOT NULL AUTO_INCREMENT,
  `course_id` int DEFAULT NULL,
  `teacher_id` int DEFAULT NULL,
  `title` varchar(100) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `total_score` decimal(6,2) DEFAULT '100.00',
  `duration_minutes` int DEFAULT '0',
  `status` tinyint DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_homework_paper_course` (`course_id`),
  KEY `fk_homework_paper_teacher` (`teacher_id`),
  CONSTRAINT `fk_homework_paper_course` FOREIGN KEY (`course_id`) REFERENCES `t_course` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_homework_paper_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='作业试卷表';

--
-- Table structure for table `t_homework_paper_question`
--

DROP TABLE IF EXISTS `t_homework_paper_question`;
CREATE TABLE `t_homework_paper_question` (
  `id` int NOT NULL AUTO_INCREMENT,
  `paper_id` int NOT NULL,
  `question_id` int NOT NULL,
  `score` decimal(6,2) DEFAULT '0.00',
  `sort_order` int DEFAULT NULL,
  `required_answer` tinyint DEFAULT '1',
  PRIMARY KEY (`id`),
  KEY `fk_homework_paper_question_paper` (`paper_id`),
  KEY `fk_homework_paper_question_question` (`question_id`),
  CONSTRAINT `fk_homework_paper_question_paper` FOREIGN KEY (`paper_id`) REFERENCES `t_homework_paper` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_homework_paper_question_question` FOREIGN KEY (`question_id`) REFERENCES `t_question` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='作业试卷题目表';

--
-- Table structure for table `t_homework_session`
--

DROP TABLE IF EXISTS `t_homework_session`;
CREATE TABLE `t_homework_session` (
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
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_homework_session_class` (`class_id`),
  KEY `fk_homework_session_paper` (`paper_id`),
  KEY `fk_homework_session_teacher` (`teacher_id`),
  CONSTRAINT `fk_homework_session_class` FOREIGN KEY (`class_id`) REFERENCES `t_teaching_class` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_homework_session_paper` FOREIGN KEY (`paper_id`) REFERENCES `t_homework_paper` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_homework_session_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='作业安排表';

--
-- Table structure for table `t_homework_record`
--

DROP TABLE IF EXISTS `t_homework_record`;
CREATE TABLE `t_homework_record` (
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
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_homework_record_session_student` (`homework_session_id`,`student_id`),
  KEY `fk_homework_record_student` (`student_id`),
  CONSTRAINT `fk_homework_record_session` FOREIGN KEY (`homework_session_id`) REFERENCES `t_homework_session` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_homework_record_student` FOREIGN KEY (`student_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='作业答卷表';

--
-- Table structure for table `t_homework_answer`
--

DROP TABLE IF EXISTS `t_homework_answer`;
CREATE TABLE `t_homework_answer` (
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
  PRIMARY KEY (`id`),
  KEY `fk_homework_answer_record` (`homework_record_id`),
  KEY `fk_homework_answer_question` (`question_id`),
  KEY `fk_homework_answer_paper_question` (`paper_question_id`),
  KEY `fk_homework_answer_grader` (`grader_id`),
  CONSTRAINT `fk_homework_answer_record` FOREIGN KEY (`homework_record_id`) REFERENCES `t_homework_record` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_homework_answer_question` FOREIGN KEY (`question_id`) REFERENCES `t_question` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_homework_answer_paper_question` FOREIGN KEY (`paper_question_id`) REFERENCES `t_homework_paper_question` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_homework_answer_grader` FOREIGN KEY (`grader_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='作业答题表';

--
-- Table structure for table `t_homework_assessment_migration`
--

DROP TABLE IF EXISTS `t_homework_assessment_migration`;
CREATE TABLE `t_homework_assessment_migration` (
  `legacy_homework_id` int NOT NULL,
  `homework_paper_id` int DEFAULT NULL,
  `homework_session_id` int DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `error_message` varchar(1000) DEFAULT NULL,
  `migrated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`legacy_homework_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='旧作业评测迁移账本';

--
-- Minimal bootstrap data required for a fresh local installation
--

INSERT INTO `t_role` (`id`, `role_name`, `role_code`, `create_time`) VALUES
(1, '学生', 'student', CURRENT_TIMESTAMP),
(2, '教师', 'teacher', CURRENT_TIMESTAMP),
(3, '管理员', 'admin', CURRENT_TIMESTAMP);

INSERT INTO `t_user` (`id`, `username`, `password`, `nickname`, `email`, `phone`, `status`, `create_time`, `update_time`) VALUES
(1, 'admin', 'e10adc3949ba59abbe56e057f20f883e', '系统管理员', 'admin@example.com', '13800000001', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 'teacher', 'e10adc3949ba59abbe56e057f20f883e', '王老师', 'teacher@example.com', '13800000002', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 'student', 'e10adc3949ba59abbe56e057f20f883e', '张同学', 'student@example.com', '13800000003', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO `t_user_role` (`id`, `user_id`, `role_id`) VALUES
(1, 1, 3),
(2, 2, 2),
(3, 3, 1);

INSERT INTO `t_course_category` (`id`, `name`, `description`, `sort_order`, `create_time`, `update_time`) VALUES
(1, '计算机科学与技术', '工学 / 计算机类 / 计算机科学与技术', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, '软件工程', '工学 / 计算机类 / 软件工程', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, '网络工程', '工学 / 计算机类 / 网络工程', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(4, '数据科学与大数据技术', '工学 / 计算机类 / 数据科学与大数据技术', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(5, '人工智能', '工学 / 计算机类 / 人工智能', 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(6, '网络空间安全', '工学 / 计算机类 / 网络空间安全', 6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Agent 会话表通过外键依赖真实 Agent；保留一个启用的默认助教供学生首次登录后直接对话。
INSERT INTO `t_agent` (`id`, `code`, `name`, `description`, `system_prompt`, `model`, `temperature`, `knowledge_base_ids`, `enabled_tools`, `status`, `create_time`, `update_time`) VALUES
(1, 'learning_assistant', '通用学习助教', '面向学生的通用学习答疑 Agent', '你是 AI 智学平台里的学习助教。请用清晰、准确、友好的中文回答，必要时分步骤说明。遇到不确定的信息，请明确说明不确定。', NULL, 0.70, NULL, NULL, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

--
-- Dumping events for database 'ai_academy'
--

--
-- Dumping routines for database 'ai_academy'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-07-10 19:20:19
