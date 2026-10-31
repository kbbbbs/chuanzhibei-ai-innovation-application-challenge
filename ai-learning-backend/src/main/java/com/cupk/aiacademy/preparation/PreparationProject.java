package com.cupk.aiacademy.preparation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_ai_preparation")
public class PreparationProject {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("teacher_id")
    private Integer teacherId;
    private String title;
    @TableField("config_json")
    private String configJson;
    @TableField("knowledge_base_ids_json")
    private String knowledgeBaseIdsJson;
    @TableField("owned_knowledge_base_id")
    private String ownedKnowledgeBaseId;
    @TableField("source_files_json")
    private String sourceFilesJson;
    @TableField("outline_json")
    private String outlineJson;
    @TableField("lessons_json")
    private String lessonsJson;
    @TableField("questions_json")
    private String questionsJson;
    @TableField("project_status")
    private String projectStatus;
    @TableField("current_stage")
    private String currentStage;
    @TableField("generation_status")
    private String generationStatus;
    private Integer progress;
    @TableField("error_message")
    private String errorMessage;
    @TableField("published_course_id")
    private Integer publishedCourseId;
    @Version
    private Integer version;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
