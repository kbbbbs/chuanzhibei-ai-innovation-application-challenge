CREATE TABLE IF NOT EXISTS t_question (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    content VARCHAR(500) NOT NULL,
    type VARCHAR(32) NOT NULL,
    difficulty VARCHAR(16) NOT NULL,
    score DECIMAL(10,2) NOT NULL,
    knowledge_point VARCHAR(200),
    analysis VARCHAR(1000),
    status VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS t_question_option (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    option_key VARCHAR(8) NOT NULL,
    option_content VARCHAR(300) NOT NULL,
    sort_order INT NOT NULL,
    CONSTRAINT fk_question_option_question FOREIGN KEY (question_id) REFERENCES question(id)
);

CREATE TABLE IF NOT EXISTS t_question_answer (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    answer_content VARCHAR(500) NOT NULL,
    sort_order INT NOT NULL,
    CONSTRAINT fk_question_answer_question FOREIGN KEY (question_id) REFERENCES question(id)
);

CREATE TABLE IF NOT EXISTS t_paper (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    total_score DECIMAL(10,2) NOT NULL,
    duration_minutes INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS t_paper_question (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    paper_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    sort_order INT NOT NULL,
    score DECIMAL(10,2) NOT NULL,
    required_answer BIT NOT NULL,
    CONSTRAINT fk_paper_question_paper FOREIGN KEY (paper_id) REFERENCES paper(id),
    CONSTRAINT fk_paper_question_question FOREIGN KEY (question_id) REFERENCES question(id)
);

CREATE TABLE IF NOT EXISTS t_exam (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    paper_id BIGINT NOT NULL,
    description VARCHAR(1000),
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    duration_minutes INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_exam_paper FOREIGN KEY (paper_id) REFERENCES paper(id)
);

CREATE TABLE IF NOT EXISTS t_exam_submission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    exam_id BIGINT NOT NULL,
    candidate_id VARCHAR(64) NOT NULL,
    started_at DATETIME NOT NULL,
    submitted_at DATETIME,
    objective_score DECIMAL(10,2) NOT NULL,
    subjective_score DECIMAL(10,2) NOT NULL,
    total_score DECIMAL(10,2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    CONSTRAINT fk_submission_exam FOREIGN KEY (exam_id) REFERENCES exam(id)
);

CREATE TABLE IF NOT EXISTS t_submission_answer (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    submission_id BIGINT NOT NULL,
    paper_question_id BIGINT NOT NULL,
    answer_content VARCHAR(2000),
    correct BIT,
    score DECIMAL(10,2) NOT NULL,
    judge_status VARCHAR(32) NOT NULL,
    comment VARCHAR(1000),
    CONSTRAINT fk_submission_answer_submission FOREIGN KEY (submission_id) REFERENCES exam_submission(id),
    CONSTRAINT fk_submission_answer_paper_question FOREIGN KEY (paper_question_id) REFERENCES paper_question(id)
);
