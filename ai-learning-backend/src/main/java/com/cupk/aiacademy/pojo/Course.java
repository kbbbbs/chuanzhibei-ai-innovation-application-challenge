package com.cupk.aiacademy.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_course")
public class Course {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private String title;
    private String description;
    private String cover;
    private String category;
    private String teacher;
    private String difficulty;

    @TableField("student_count")
    private Integer studentCount;

    private BigDecimal rating;
    private Integer status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
