package com.cupk.aiacademy.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_agent_message")
public class AgentMessage {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("session_id")
    private Integer sessionId;

    private String role;
    private String content;

    @TableField("tool_name")
    private String toolName;

    @TableField("tool_args")
    private String toolArgs;

    @TableField("tool_result")
    private String toolResult;

    @TableField("create_time")
    private LocalDateTime createTime;
}
