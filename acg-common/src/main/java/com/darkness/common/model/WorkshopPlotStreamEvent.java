package com.darkness.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 创作工坊剧情生成 SSE 事件 DTO，镜像 Python acgagent-ai 的 workshop plot 流式事件 schema。
 * <p>
 * Python 剧情生成接口返回 {@code text/event-stream}，每个事件一行 {@code data: \{json\}}，
 * 反序列化为本类。type 取值：
 * <ul>
 *   <li>{@code content} — 流式 token，字段 {@link #content} 装载文本片段</li>
 *   <li>{@code done} — 流结束（无额外字段）</li>
 *   <li>{@code error} — 流内错误，字段 {@link #message} 装载错误信息</li>
 * </ul>
 * 与 {@link GenerateStreamEvent} 的差异：剧情流不返回消耗估算（无 estimate 字段）。
 * 该 schema 同时是前端 SSE 解析契约，变更需走版本化。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkshopPlotStreamEvent {

    /** 事件类型：content / done / error */
    private String type;

    /** content 事件：流式 token 文本片段 */
    private String content;

    /** error 事件：错误信息 */
    private String message;
}
