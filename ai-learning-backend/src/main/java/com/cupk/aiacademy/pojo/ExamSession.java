package com.cupk.aiacademy.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_exam_session")
public class ExamSession {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("class_id")
    private Integer classId;

    @TableField("paper_id")
    private Integer paperId;

    @TableField("teacher_id")
    private Integer teacherId;

    private String title;

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("end_time")
    private LocalDateTime endTime;

    private Integer duration;
    private Integer status;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
