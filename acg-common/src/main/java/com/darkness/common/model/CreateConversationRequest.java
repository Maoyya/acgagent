package com.darkness.common.model;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建会话请求。
 */
@Data
public class CreateConversationRequest {
    @NotNull(message = "agentId is required")
    private Long agentId;

    /** 会话标题，可选 */
    private String title;
}
