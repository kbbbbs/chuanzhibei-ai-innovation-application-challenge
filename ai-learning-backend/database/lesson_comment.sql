CREATE TABLE IF NOT EXISTS lesson_comment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  lesson_id INT NOT NULL,
  user_id INT NOT NULL,
  parent_id BIGINT NULL,
  content VARCHAR(1000) NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  INDEX idx_lesson_comment_lesson (lesson_id, deleted, created_at),
  INDEX idx_lesson_comment_user (user_id)
);
