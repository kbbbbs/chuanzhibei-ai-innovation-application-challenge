CREATE TABLE IF NOT EXISTS study_note (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    course_id INT NOT NULL,
    lesson_id INT NOT NULL,
    title VARCHAR(255),
    content_md LONGTEXT NOT NULL,
    last_video_time INT DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_study_note_user_lesson (user_id, lesson_id),
    INDEX idx_study_note_course_id (course_id),
    INDEX idx_study_note_lesson_id (lesson_id)
);
