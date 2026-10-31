CREATE TABLE IF NOT EXISTS `t_home_course_recommendation` (
  `id` int NOT NULL AUTO_INCREMENT,
  `course_id` int NOT NULL COMMENT '推荐课程ID',
  `reason` varchar(255) DEFAULT NULL COMMENT '首页推荐理由',
  `sort_order` int NOT NULL DEFAULT 1 COMMENT '首页展示顺序，越小越靠前',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '0停用，1启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_home_course_recommendation_course` (`course_id`),
  KEY `idx_home_course_recommendation_sort` (`status`,`sort_order`),
  CONSTRAINT `fk_home_course_recommendation_course` FOREIGN KEY (`course_id`) REFERENCES `t_course` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='首页人工推荐课程';
