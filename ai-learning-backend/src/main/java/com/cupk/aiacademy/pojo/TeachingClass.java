package com.cupk.aiacademy.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_teaching_class")
public class TeachingClass {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField(value = "course_id", updateStrategy = FieldStrategy.ALWAYS)
    private Integer courseId;

    @TableField("teacher_id")
    private Integer teacherId;

    @TableField("class_name")
    private String className;

    private String semester;
    private String description;
    private Integer status;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
