package com.cupk.aiacademy.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_agent")
public class AgentDefinition {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private String code;
    private String name;
    private String description;

    @TableField("system_prompt")
    private String systemPrompt;

    private String model;
    private BigDecimal temperature;

    @TableField("knowledge_base_ids")
    private String knowledgeBaseIds;

    @TableField("enabled_tools")
    private String enabledTools;

    private Integer status;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
