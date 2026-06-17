package com.darkness.tool.service.impl;

import com.darkness.agent.client.PythonAiClient;
import com.darkness.common.exception.BizException;
import com.darkness.common.model.ToolCreateRequest;
import com.darkness.common.model.ToolVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * 工具代理 Service 测试：验证转发 + 错误透传（含内置工具不可删的 404 错误透传）。
 */
@ExtendWith(MockitoExtension.class)
class ToolServiceImplTest {

    @Mock
    private PythonAiClient pythonAiClient;

    @InjectMocks
    private ToolServiceImpl toolService;

    @Test
    void list_delegatesToPython() {
        when(pythonAiClient.listTools()).thenReturn(List.of(new ToolVO()));
        assertThat(toolService.list()).hasSize(1);
    }

    @Test
    void get_delegatesToPython() {
        ToolVO vo = new ToolVO();
        vo.setId("t1");
        when(pythonAiClient.getTool("t1")).thenReturn(vo);
        assertThat(toolService.get("t1").getId()).isEqualTo("t1");
    }

    @Test
    void create_delegatesToPython() {
        ToolCreateRequest req = new ToolCreateRequest();
        req.setName("T");
        req.setDescription("d");
        when(pythonAiClient.createTool(req)).thenReturn(new ToolVO());
        toolService.create(req);
        verify(pythonAiClient).createTool(req);
    }

    @Test
    void delete_builtinTool_propagatesError() {
        // 内置工具不可删，Python 返回 code!=200，PythonAiClient 抛 BizException
        doThrow(new BizException(404, "builtin tool cannot be deleted"))
                .when(pythonAiClient).deleteTool("calculator");
        assertThatThrownBy(() -> toolService.delete("calculator"))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(404);
    }
}
