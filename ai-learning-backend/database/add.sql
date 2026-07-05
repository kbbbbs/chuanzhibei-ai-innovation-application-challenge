USE ai_academy;

CREATE TABLE IF NOT EXISTS t_agent (
                                       id INT AUTO_INCREMENT PRIMARY KEY COMMENT 'Agent编号',
                                       code VARCHAR(64) NOT NULL COMMENT 'Agent编码',
    name VARCHAR(100) NOT NULL COMMENT 'Agent名称',
    description VARCHAR(255) COMMENT 'Agent描述',
    system_prompt TEXT COMMENT '系统提示词',
    model VARCHAR(100) COMMENT '模型名称',
    temperature DECIMAL(3,2) DEFAULT 0.70 COMMENT '采样温度',
    knowledge_base_ids TEXT COMMENT '关联知识库ID，JSON数组字符串',
    enabled_tools TEXT COMMENT '启用工具，JSON数组字符串',
    status TINYINT DEFAULT 1 COMMENT '状态：1启用，0停用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_agent_code (code)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent定义表';

CREATE TABLE IF NOT EXISTS t_agent_session (
                                               id INT AUTO_INCREMENT PRIMARY KEY COMMENT 'Agent会话编号',
                                               agent_id INT NOT NULL COMMENT 'Agent编号',
                                               user_id INT COMMENT '用户编号',
                                               title VARCHAR(100) NOT NULL COMMENT '会话标题',
    status TINYINT DEFAULT 1 COMMENT '状态：1正常，0删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_agent_session_agent (agent_id),
    INDEX idx_agent_session_user (user_id),
    CONSTRAINT fk_agent_session_agent FOREIGN KEY (agent_id) REFERENCES t_agent(id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_session_user FOREIGN KEY (user_id) REFERENCES t_user(id) ON DELETE SET NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent会话表';

CREATE TABLE IF NOT EXISTS t_agent_message (
                                               id INT AUTO_INCREMENT PRIMARY KEY COMMENT 'Agent消息编号',
                                               session_id INT NOT NULL COMMENT '会话编号',
                                               role VARCHAR(20) NOT NULL COMMENT '消息角色：system/user/assistant/tool',
    content LONGTEXT COMMENT '消息内容',
    tool_name VARCHAR(100) COMMENT '工具名称',
    tool_args TEXT COMMENT '工具参数',
    tool_result LONGTEXT COMMENT '工具结果',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_agent_message_session (session_id),
    CONSTRAINT fk_agent_message_session FOREIGN KEY (session_id) REFERENCES t_agent_session(id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent消息表';