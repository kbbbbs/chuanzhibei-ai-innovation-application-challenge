package com.cupk.aiacademy.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_notice")
public class Notice {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("publisher_id")
    private Integer publisherId;

    @TableField("class_id")
    private Integer classId;

    private String title;
    private String content;

    @TableField("notice_type")
    private String noticeType;

    @TableField("create_time")
    private LocalDateTime createTime;

    private Integer status;
}
