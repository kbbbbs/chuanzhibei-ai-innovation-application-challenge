CREATE DATABASE IF NOT EXISTS final_exam_question_bank
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE final_exam_question_bank;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS submission_answer;
DROP TABLE IF EXISTS exam_submission;
DROP TABLE IF EXISTS exam;
DROP TABLE IF EXISTS paper_question;
DROP TABLE IF EXISTS paper;
DROP TABLE IF EXISTS question_answer;
DROP TABLE IF EXISTS question_option;
DROP TABLE IF EXISTS question;

CREATE TABLE question (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '题目ID',
    content VARCHAR(500) NOT NULL COMMENT '题干',
    type VARCHAR(32) NOT NULL COMMENT '题型',
    difficulty VARCHAR(16) NOT NULL COMMENT '难度',
    score DECIMAL(10,2) NOT NULL COMMENT '默认分值',
    knowledge_point VARCHAR(200) DEFAULT NULL COMMENT '知识点',
    analysis VARCHAR(1000) DEFAULT NULL COMMENT '题目解析',
    status VARCHAR(16) NOT NULL COMMENT '状态',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目表';

CREATE TABLE question_option (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '选项ID',
    question_id BIGINT NOT NULL COMMENT '题目ID',
    option_key VARCHAR(8) NOT NULL COMMENT '选项标识',
    option_content VARCHAR(300) NOT NULL COMMENT '选项内容',
    sort_order INT NOT NULL COMMENT '排序',
    CONSTRAINT fk_question_option_question FOREIGN KEY (question_id) REFERENCES question(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目选项表';

CREATE TABLE question_answer (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '答案ID',
    question_id BIGINT NOT NULL COMMENT '题目ID',
    answer_content VARCHAR(500) NOT NULL COMMENT '标准答案',
    sort_order INT NOT NULL COMMENT '排序',
    CONSTRAINT fk_question_answer_question FOREIGN KEY (question_id) REFERENCES question(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目标答表';

CREATE TABLE paper (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '试卷ID',
    name VARCHAR(200) NOT NULL COMMENT '试卷名称',
    description VARCHAR(1000) DEFAULT NULL COMMENT '试卷描述',
    total_score DECIMAL(10,2) NOT NULL COMMENT '总分',
    duration_minutes INT NOT NULL COMMENT '时长(分钟)',
    status VARCHAR(16) NOT NULL COMMENT '试卷状态',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='试卷表';

CREATE TABLE paper_question (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '试卷题目ID',
    paper_id BIGINT NOT NULL COMMENT '试卷ID',
    question_id BIGINT NOT NULL COMMENT '题目ID',
    sort_order INT NOT NULL COMMENT '题目顺序',
    score DECIMAL(10,2) NOT NULL COMMENT '该题在试卷中的分值',
    required_answer BIT NOT NULL COMMENT '是否必答',
    CONSTRAINT fk_paper_question_paper FOREIGN KEY (paper_id) REFERENCES paper(id),
    CONSTRAINT fk_paper_question_question FOREIGN KEY (question_id) REFERENCES question(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='试卷题目关联表';

CREATE TABLE exam (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '考试ID',
    name VARCHAR(200) NOT NULL COMMENT '考试名称',
    paper_id BIGINT NOT NULL COMMENT '关联试卷ID',
    description VARCHAR(1000) DEFAULT NULL COMMENT '考试描述',
    start_time DATETIME NOT NULL COMMENT '开始时间',
    end_time DATETIME NOT NULL COMMENT '结束时间',
    duration_minutes INT NOT NULL COMMENT '考试时长(分钟)',
    status VARCHAR(16) NOT NULL COMMENT '考试状态',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    CONSTRAINT fk_exam_paper FOREIGN KEY (paper_id) REFERENCES paper(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考试表';

CREATE TABLE exam_submission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '答卷ID',
    exam_id BIGINT NOT NULL COMMENT '考试ID',
    candidate_id VARCHAR(64) NOT NULL COMMENT '考生标识',
    started_at DATETIME NOT NULL COMMENT '开始答题时间',
    submitted_at DATETIME DEFAULT NULL COMMENT '交卷时间',
    objective_score DECIMAL(10,2) NOT NULL COMMENT '客观题得分',
    subjective_score DECIMAL(10,2) NOT NULL COMMENT '主观题得分',
    total_score DECIMAL(10,2) NOT NULL COMMENT '总分',
    status VARCHAR(32) NOT NULL COMMENT '答卷状态',
    CONSTRAINT fk_submission_exam FOREIGN KEY (exam_id) REFERENCES exam(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='答卷主表';

CREATE TABLE submission_answer (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '答题记录ID',
    submission_id BIGINT NOT NULL COMMENT '答卷ID',
    paper_question_id BIGINT NOT NULL COMMENT '试卷题目ID',
    answer_content VARCHAR(2000) DEFAULT NULL COMMENT '作答内容',
    correct BIT DEFAULT NULL COMMENT '是否正确',
    score DECIMAL(10,2) NOT NULL COMMENT '该题得分',
    judge_status VARCHAR(32) NOT NULL COMMENT '判题状态',
    comment VARCHAR(1000) DEFAULT NULL COMMENT '评语',
    CONSTRAINT fk_submission_answer_submission FOREIGN KEY (submission_id) REFERENCES exam_submission(id),
    CONSTRAINT fk_submission_answer_paper_question FOREIGN KEY (paper_question_id) REFERENCES paper_question(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='答题记录表';

SET FOREIGN_KEY_CHECKS = 1;
