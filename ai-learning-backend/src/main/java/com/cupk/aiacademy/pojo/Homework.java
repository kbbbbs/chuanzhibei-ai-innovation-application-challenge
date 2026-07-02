package com.cupk.aiacademy.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_homework")
public class Homework {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("class_id")
    private Integer classId;

    @TableField("teacher_id")
    private Integer teacherId;

    private String title;
    private String content;

    @TableField("attachment_url")
    private String attachmentUrl;

    @TableField("full_score")
    private BigDecimal fullScore;

    @TableField("start_time")
    private LocalDateTime startTime;

    private LocalDateTime deadline;
    private Integer status;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
