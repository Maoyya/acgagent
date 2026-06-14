package com.darkness.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.Map;

/**
 * 对话 SSE 事件 DTO，镜像 Python acgagent-ai 的 ChatEvent schema。
 * <p>
 * 线上报文为 snake_case（Python pydantic 默认输出），故使用 SnakeCaseStrategy 命名。
 * type 取值：content / tool_call / tool_result / thinking / error / done。
 * 该 schema 同时是前端 SSE 解析契约，变更需走版本化。
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatEvent {

    /** 事件类型：content | tool_call | tool_result | thinking | error | done */
    private String type;

    /** 文本片段（content/thinking 事件使用） */
    private String content;

    /** 工具名（tool_call/tool_result 事件使用） */
    private String toolName;

    /** 工具输入参数（tool_call 事件使用） */
    private Map<String, Object> toolInput;

    /** 工具输出（tool_result 事件使用） */
    private String toolOutput;

    /** 错误码（error 事件使用） */
    private Integer code;

    /** 错误/状态信息 */
    private String message;

    /** token 用量（done 事件使用） */
    private Map<String, Object> usage;
}
