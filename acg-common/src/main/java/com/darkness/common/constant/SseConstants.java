package com.darkness.common.constant;

/**
 * SSE 流式响应相关常量，用于解析 LLM API / Python AI 引擎的 SSE 协议。
 */
public final class SseConstants {
    private SseConstants() {}

    /** SSE data 行前缀。 */
    public static final String DATA_PREFIX = "data:";

    /** SSE 流结束标记。 */
    public static final String DONE_MARKER = "[DONE]";
}
