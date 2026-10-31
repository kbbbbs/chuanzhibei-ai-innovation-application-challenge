CREATE TABLE IF NOT EXISTS work_study (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT '岗位编号',
    job_name VARCHAR(100) NOT NULL COMMENT '岗位名称',
    job_description VARCHAR(500) COMMENT '岗位描述',
    location VARCHAR(200) COMMENT '工作地点',
    work_time VARCHAR(200) COMMENT '工作时间',
    hourly_rate DECIMAL(10,2) DEFAULT 0.00 COMMENT '时薪标准',
    max_recruit INT DEFAULT 0 COMMENT '招聘人数',
    status TINYINT DEFAULT 1 COMMENT '状态：1招聘中，0已停止',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='勤工俭学岗位表';

CREATE TABLE IF NOT EXISTS work_study_student (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT '记录编号',
    work_study_id INT NOT NULL COMMENT '岗位编号',
    student_id INT NOT NULL COMMENT '学生编号',
    student_name VARCHAR(50) COMMENT '学生姓名',
    work_month VARCHAR(20) COMMENT '工作月份',
    work_hours DECIMAL(10,2) DEFAULT 0.00 COMMENT '工作时长（小时）',
    hourly_rate DECIMAL(10,2) DEFAULT 0.00 COMMENT '适用时薪',
    total_amount DECIMAL(10,2) DEFAULT 0.00 COMMENT '劳务费金额',
    status TINYINT DEFAULT 1 COMMENT '状态：1正常，0停用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_wss_work_study FOREIGN KEY (work_study_id) REFERENCES work_study(id) ON DELETE CASCADE,
    CONSTRAINT fk_wss_student FOREIGN KEY (student_id) REFERENCES t_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='勤工俭学学生劳务费表';

INSERT INTO work_study (job_name, job_description, location, work_time, hourly_rate, max_recruit, status, create_time) 
VALUES 
('图书馆助理', '负责图书整理和借阅服务', '图书馆', '周一至周五 14:00-17:00', 15.00, 5, 1, NOW()),
('实验室助手', '协助实验准备和器材整理', '实验楼A301', '周二、周四 9:00-12:00', 20.00, 3, 1, NOW()),
('行政助理', '协助办公室日常事务处理', '行政楼201', '周一至周五 8:30-12:00', 18.00, 2, 1, NOW());
