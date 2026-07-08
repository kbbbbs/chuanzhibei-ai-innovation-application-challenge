CREATE TABLE IF NOT EXISTS video_resource (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '视频资源编号',
    source_url TEXT NOT NULL COMMENT '教师提交的原始视频地址',
    source_url_hash CHAR(64) NOT NULL COMMENT '原始地址 SHA-256',
    title VARCHAR(255) COMMENT '视频标题',
    provider VARCHAR(50) COMMENT '来源平台',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT '下载状态',
    storage_path VARCHAR(500) COMMENT '服务器本地存储相对路径',
    play_url VARCHAR(500) COMMENT '前端播放地址',
    cover_url VARCHAR(500) COMMENT '封面地址',
    duration INT DEFAULT 0 COMMENT '视频时长，单位秒',
    file_size BIGINT DEFAULT 0 COMMENT '文件大小，单位字节',
    summary_md LONGTEXT COMMENT '知识点总结',
    error_message TEXT COMMENT '失败原因',
    created_by INT NOT NULL COMMENT '提交人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_video_resource_url_hash (source_url_hash),
    INDEX idx_video_resource_status (status),
    INDEX idx_video_resource_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频资源表';

SET @lesson_video_resource_column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_lesson'
      AND COLUMN_NAME = 'video_resource_id'
);

SET @add_lesson_video_resource_column := IF(
    @lesson_video_resource_column_exists = 0,
    'ALTER TABLE t_lesson ADD COLUMN video_resource_id BIGINT NULL COMMENT ''关联视频资源编号'' AFTER video_url',
    'SELECT ''t_lesson.video_resource_id already exists'''
);
PREPARE add_lesson_video_resource_column_stmt FROM @add_lesson_video_resource_column;
EXECUTE add_lesson_video_resource_column_stmt;
DEALLOCATE PREPARE add_lesson_video_resource_column_stmt;

SET @lesson_video_resource_index_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_lesson'
      AND INDEX_NAME = 'idx_lesson_video_resource_id'
);

SET @add_lesson_video_resource_index := IF(
    @lesson_video_resource_index_exists = 0,
    'ALTER TABLE t_lesson ADD INDEX idx_lesson_video_resource_id (video_resource_id)',
    'SELECT ''idx_lesson_video_resource_id already exists'''
);
PREPARE add_lesson_video_resource_index_stmt FROM @add_lesson_video_resource_index;
EXECUTE add_lesson_video_resource_index_stmt;
DEALLOCATE PREPARE add_lesson_video_resource_index_stmt;
