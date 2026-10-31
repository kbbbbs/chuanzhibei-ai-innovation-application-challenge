package com.cupk.aiacademy.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_administrative_class_student")
public class AdministrativeClassStudent {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("administrative_class_id")
    private Integer administrativeClassId;

    @TableField("student_id")
    private Integer studentId;

    @TableField("join_time")
    private LocalDateTime joinTime;

    private Integer status;
}
