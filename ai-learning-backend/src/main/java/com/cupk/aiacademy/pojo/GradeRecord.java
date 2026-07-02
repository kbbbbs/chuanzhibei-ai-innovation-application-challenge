package com.cupk.aiacademy.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_grade_record")
public class GradeRecord {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("student_id")
    private Integer studentId;

    @TableField("class_id")
    private Integer classId;

    @TableField("course_id")
    private Integer courseId;

    @TableField("teacher_id")
    private Integer teacherId;

    @TableField("grade_name")
    private String gradeName;

    @TableField("source_type")
    private String sourceType;

    @TableField("source_id")
    private Integer sourceId;

    private BigDecimal score;

    @TableField("full_score")
    private BigDecimal fullScore;

    @TableField("grade_comment")
    private String comment;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
