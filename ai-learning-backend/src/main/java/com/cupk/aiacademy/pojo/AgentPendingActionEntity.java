package com.cupk.aiacademy.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_agent_pending_action")
public class AgentPendingActionEntity {
    @TableId(value = "action_id", type = IdType.INPUT)
    private String actionId;

    @TableField("action_code")
    private String actionCode;

    @TableField("created_by")
    private Integer createdBy;

    @TableField("parameters_json")
    private String parametersJson;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("expires_at")
    private LocalDateTime expiresAt;
}
