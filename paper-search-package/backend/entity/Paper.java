package com.cupk.aiacademy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_paper")
public class Paper implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("arxiv_id")
    private String arxivId;

    @TableField("title")
    private String title;

    @TableField("abstract_text")
    private String abstractText;

    @TableField("year")
    private Integer year;

    @TableField("url")
    private String url;

    @TableField("pdf_url")
    private String pdfUrl;

    @TableField("venue")
    private String venue;

    @TableField("citation_count")
    private Integer citationCount;

    @TableField("source")
    private String source;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
