package com.cupk.aiacademy.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_work_study_student")
public class WorkStudyStudent {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private Integer workStudyId;
    private Integer studentId;
    private String workMonth;
    private BigDecimal workHours;
    private BigDecimal hourlyRate;
    private BigDecimal totalAmount;
    private Integer status;
    private LocalDateTime applyTime;
    private Integer auditStatus;
    private Integer auditorId;
    private LocalDateTime auditTime;
    private String rejectReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private String studentName;

    @TableField(exist = false)
    private String jobName;
}
