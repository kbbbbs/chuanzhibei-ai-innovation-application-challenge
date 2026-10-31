ALTER TABLE t_exam_session
    ADD COLUMN max_screen_switches INT NOT NULL DEFAULT 3 COMMENT '允许切屏次数上限' AFTER status,
    ADD COLUMN disable_clipboard TINYINT NOT NULL DEFAULT 0 COMMENT '考试时是否禁用剪贴板：1是，0否' AFTER max_screen_switches;

ALTER TABLE t_exam_record
    ADD COLUMN screen_switch_count INT NOT NULL DEFAULT 0 COMMENT '切屏次数' AFTER comment,
    ADD COLUMN cheated TINYINT NOT NULL DEFAULT 0 COMMENT '是否作弊：1是，0否' AFTER screen_switch_count;
