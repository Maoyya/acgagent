package com.darkness.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 跨服务 Agent 传输对象，用于 Dubbo RPC 调用时传递 Agent 基本信息。
 */
@Data
public class AgentDTO implements Serializable {

    private static final long serialVersionUID = 7382194683720194857L;

    /** Agent ID */
    private Long id;

    /** Agent 名称 */
    private String name;

    /** 状态：1-启用，0-禁用 */
    private Integer status;
}
