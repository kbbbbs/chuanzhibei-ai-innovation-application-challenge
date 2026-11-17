package com.cupk.aiacademy.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("t_course")
public class Course {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private String title;
    private String description;
    private String cover;

    @TableField("cover_url")
    private String coverUrl;

    private String teacher;
    private String difficulty;

    @TableField("category_id")
    private Integer categoryId;

    @TableField("teacher_id")
    private Integer teacherId;

    @TableField("student_count")
    private Integer studentCount;

    @TableField("view_count")
    private Integer viewCount;

    private BigDecimal rating;
    private Integer status;

    private String setupStage;
    private String contentMode;
    private Long preparationId;
    private Long workflowVersion;
    private LocalDateTime setupCompletedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private List<Chapter> chapters;
}
