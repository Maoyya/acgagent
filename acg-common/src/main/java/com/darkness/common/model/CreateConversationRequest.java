package com.darkness.common.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建会话请求。
 */
@Data
public class CreateConversationRequest {
    @NotNull(message = "agentId不能为空")
    private Long agentId;

    /** 会话标题，可选 */
    @Size(max = 256, message = "标题长度不能超过256个字符")
    private String title;
}
