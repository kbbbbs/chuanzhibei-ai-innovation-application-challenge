package com.cupk.aiacademy.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("t_chapter")
public class Chapter {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("course_id")
    private Integer courseId;

    private String title;
    private String description;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private List<Lesson> lessons;
}
