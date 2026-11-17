-- MySQL migration for teaching classes prepared before course creation.
-- Existing class/course assignments and student memberships are preserved.
ALTER TABLE t_teaching_class MODIFY COLUMN course_id INT NULL COMMENT '课程编号，待关联时为空';
