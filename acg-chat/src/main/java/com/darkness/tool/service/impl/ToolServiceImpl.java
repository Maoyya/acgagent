package com.darkness.tool.service.impl;

import com.darkness.agent.client.PythonAiClient;
import com.darkness.common.model.ToolCreateRequest;
import com.darkness.common.model.ToolVO;
import com.darkness.tool.service.ToolService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 工具代理服务实现，转发到 PythonAiClient。
 */
@Service
@RequiredArgsConstructor
public class ToolServiceImpl implements ToolService {

    private final PythonAiClient pythonAiClient;

    @Override
    public List<ToolVO> list() {
        return pythonAiClient.listTools();
    }

    @Override
    public ToolVO get(String toolId) {
        return pythonAiClient.getTool(toolId);
    }

    @Override
    public ToolVO create(ToolCreateRequest request) {
        return pythonAiClient.createTool(request);
    }

    @Override
    public void delete(String toolId) {
        pythonAiClient.deleteTool(toolId);
    }
}
