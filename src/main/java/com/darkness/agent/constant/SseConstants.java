package com.darkness.agent.constant;

/**
 * SSE 流式响应相关常量，用于 AgentClient 解析 LLM API 的 SSE 协议。
 */
public final class SseConstants {
    private SseConstants() {}

    /** SSE data 行前缀。 */
    public static final String DATA_PREFIX = "data:";

    /** SSE 流结束标记。 */
    public static final String DONE_MARKER = "[DONE]";
}
