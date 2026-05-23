package com.darkness.common.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * SSE 流式发送消息请求。
 */
@Data
public class SendMessageRequest {
    @NotBlank(message = "content is required")
    private String content;
}
