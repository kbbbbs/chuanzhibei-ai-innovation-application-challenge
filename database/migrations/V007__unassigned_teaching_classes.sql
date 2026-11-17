-- Existing MySQL installation. Preserve classes and memberships while allowing pre-enrolment.
ALTER TABLE t_teaching_class MODIFY COLUMN course_id INT NULL COMMENT '课程编号';

-- Existing local H2 installation uses this equivalent statement, applied by the operator.
-- ALTER TABLE t_teaching_class ALTER COLUMN course_id DROP NOT NULL;
