package com.cupk.aiacademy.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_homework_submit")
public class HomeworkSubmit {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("homework_id")
    private Integer homeworkId;

    @TableField("student_id")
    private Integer studentId;

    @TableField("submit_content")
    private String submitContent;

    @TableField("attachment_url")
    private String attachmentUrl;

    @TableField("submit_time")
    private LocalDateTime submitTime;

    private BigDecimal score;
    private String feedback;

    @TableField("grade_status")
    private Integer gradeStatus;

    @TableField("grader_id")
    private Integer graderId;

    @TableField("grade_time")
    private LocalDateTime gradeTime;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
