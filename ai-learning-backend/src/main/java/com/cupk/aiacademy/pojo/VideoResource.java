package com.cupk.aiacademy.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("video_resource")
public class VideoResource {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("source_url")
    private String sourceUrl;

    @TableField("source_url_hash")
    private String sourceUrlHash;

    private String title;
    private String provider;
    private String status;

    @TableField("storage_path")
    private String storagePath;

    @TableField("play_url")
    private String playUrl;

    @TableField("cover_url")
    private String coverUrl;

    private Integer duration;

    @TableField("file_size")
    private Long fileSize;

    @TableField("summary_md")
    private String summaryMd;

    @TableField("error_message")
    private String errorMessage;

    @TableField("created_by")
    private Integer createdBy;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
