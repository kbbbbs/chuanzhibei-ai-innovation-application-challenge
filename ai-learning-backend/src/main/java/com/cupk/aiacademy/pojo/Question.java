package com.cupk.aiacademy.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_question")
public class Question {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("course_id")
    private Integer courseId;

    @TableField("teacher_id")
    private Integer teacherId;

    @TableField("question_type")
    private String questionType;

    private String title;
    private String options;
    private String answer;
    private String analysis;
    private Integer difficulty;
    private BigDecimal score;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
