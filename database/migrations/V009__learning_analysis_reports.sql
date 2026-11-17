CREATE TABLE IF NOT EXISTS learning_analysis_template (
 id VARCHAR(36) PRIMARY KEY,
 owner_id INT NOT NULL,
 name VARCHAR(200) NOT NULL,
 template_bytes LONGBLOB NOT NULL,
 fields_json LONGTEXT NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS learning_analysis_report (
 id VARCHAR(36) PRIMARY KEY,
 owner_id INT NOT NULL,
 course_id INT NOT NULL,
 agent_id INT NOT NULL,
 request_id VARCHAR(100) NOT NULL,
 template_id VARCHAR(36) NOT NULL,
 target_words INT NOT NULL,
 status VARCHAR(20) NOT NULL,
 snapshot_json LONGTEXT NOT NULL,
 template_bytes LONGBLOB NOT NULL,
 fields_json LONGTEXT NOT NULL,
 sections_json LONGTEXT,
 content LONGTEXT,
 word_count INT NOT NULL DEFAULT 0,
 model VARCHAR(200),
 error_message VARCHAR(500),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(owner_id,request_id)
);
