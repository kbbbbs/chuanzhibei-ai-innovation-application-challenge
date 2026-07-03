package com.cupk.aiacademy.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_exam_record")
public class ExamRecord {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("exam_session_id")
    private Integer examSessionId;

    @TableField("student_id")
    private Integer studentId;

    @TableField("candidate_id")
    private String candidateId;

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("submit_time")
    private LocalDateTime submitTime;

    @TableField("total_score")
    private BigDecimal totalScore;

    @TableField("objective_score")
    private BigDecimal objectiveScore;

    @TableField("subjective_score")
    private BigDecimal subjectiveScore;

    private Integer status;
    private String comment;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
