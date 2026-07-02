package com.cupk.aiacademy.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_lesson")
public class Lesson {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("chapter_id")
    private Integer chapterId;

    private String title;
    private String content;

    @TableField("video_url")
    private String videoUrl;

    private Integer duration;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("view_count")
    private Integer viewCount;

    private Integer status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
