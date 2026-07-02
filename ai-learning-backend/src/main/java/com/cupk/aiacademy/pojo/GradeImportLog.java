package com.cupk.aiacademy.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_grade_import_log")
public class GradeImportLog {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("teacher_id")
    private Integer teacherId;

    @TableField("class_id")
    private Integer classId;

    @TableField("file_name")
    private String fileName;

    @TableField("file_url")
    private String fileUrl;

    @TableField("total_count")
    private Integer totalCount;

    @TableField("success_count")
    private Integer successCount;

    @TableField("fail_count")
    private Integer failCount;

    @TableField("error_message")
    private String errorMessage;

    @TableField("create_time")
    private LocalDateTime createTime;
}
