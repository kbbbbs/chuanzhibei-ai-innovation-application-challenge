package com.cupk.aiacademy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Agent 会话更新请求。
 * 当前只允许用户修改会话标题，归属校验由 AgentChatService 统一处理。
 */
@Data
public class AgentSessionUpdateRequest {
    @NotBlank(message = "会话标题不能为空")
    @Size(max = 100, message = "会话标题不能超过100字")
    private String title;
}
