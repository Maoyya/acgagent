package com.darkness.common.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SSE 流式发送消息请求。
 */
@Data
public class SendMessageRequest {
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 32000, message = "消息内容不能超过32000个字符")
    private String content;
}
