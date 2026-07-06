package com.cupk.aiacademy.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lesson_comment")
public class LessonComment {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("lesson_id")
    private Integer lessonId;

    @TableField("user_id")
    private Integer userId;

    @TableField("parent_id")
    private Long parentId;

    private String content;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    private Integer deleted;
}
