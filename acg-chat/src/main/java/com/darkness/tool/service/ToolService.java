package com.darkness.tool.service;

import com.darkness.common.model.ToolCreateRequest;
import com.darkness.common.model.ToolVO;

import java.util.List;

/**
 * 工具代理服务：转发到 Python AI 引擎。
 */
public interface ToolService {

    /** 列出所有工具（内置 + 自定义）。 */
    List<ToolVO> list();

    /** 获取工具详情。 */
    ToolVO get(String toolId);

    /** 创建自定义 API 工具。 */
    ToolVO create(ToolCreateRequest request);

    /** 删除工具（内置工具不可删，错误透传）。 */
    void delete(String toolId);
}
