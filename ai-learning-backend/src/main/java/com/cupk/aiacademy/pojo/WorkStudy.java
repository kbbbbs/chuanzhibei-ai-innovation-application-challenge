package com.cupk.aiacademy.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_work_study")
public class WorkStudy {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private String jobName;
    private String jobDesc;
    private String workLocation;
    private String workHours;
    private BigDecimal hourlyRate;
    private Integer maxRecruit;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
