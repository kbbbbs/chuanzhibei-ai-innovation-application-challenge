package com.cupk.aiacademy.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_learning_progress")
public class LearningProgress {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("class_id")
    private Integer classId;

    @TableField("student_id")
    private Integer studentId;

    @TableField("lesson_id")
    private Integer lessonId;

    private Integer progress;

    @TableField("learned_seconds")
    private Integer learnedSeconds;

    @TableField("last_position")
    private Integer lastPosition;

    @TableField("last_learn_time")
    private LocalDateTime lastLearnTime;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
