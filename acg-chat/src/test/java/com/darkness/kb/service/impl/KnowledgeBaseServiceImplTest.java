package com.darkness.kb.service.impl;

import com.darkness.agent.client.PythonAiClient;
import com.darkness.common.exception.BizException;
import com.darkness.common.model.KnowledgeBaseRequest;
import com.darkness.common.model.KnowledgeBaseVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 知识库代理 Service 测试：验证正确转发到 PythonAiClient，并透传 Python 错误。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceImplTest {

    @Mock
    private PythonAiClient pythonAiClient;

    @InjectMocks
    private KnowledgeBaseServiceImpl knowledgeBaseService;

    @Test
    void list_delegatesToPython() {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        vo.setId("kb1");
        when(pythonAiClient.listKnowledgeBases()).thenReturn(List.of(vo));

        List<KnowledgeBaseVO> result = knowledgeBaseService.list();

        assertThat(result).hasSize(1);
        verify(pythonAiClient).listKnowledgeBases();
    }

    @Test
    void get_delegatesToPython() {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        vo.setId("kb1");
        when(pythonAiClient.getKnowledgeBase("kb1")).thenReturn(vo);

        KnowledgeBaseVO result = knowledgeBaseService.get("kb1");

        assertThat(result.getId()).isEqualTo("kb1");
    }

    @Test
    void create_delegatesToPython() {
        KnowledgeBaseRequest req = new KnowledgeBaseRequest();
        req.setName("KB");
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        vo.setId("kb1");
        when(pythonAiClient.createKnowledgeBase(req)).thenReturn(vo);

        KnowledgeBaseVO result = knowledgeBaseService.create(req);

        assertThat(result.getId()).isEqualTo("kb1");
    }

    @Test
    void update_delegatesToPython() {
        KnowledgeBaseRequest req = new KnowledgeBaseRequest();
        req.setName("KB2");
        when(pythonAiClient.updateKnowledgeBase(eq("kb1"), any(KnowledgeBaseRequest.class)))
                .thenReturn(new KnowledgeBaseVO());

        knowledgeBaseService.update("kb1", req);

        verify(pythonAiClient).updateKnowledgeBase(eq("kb1"), eq(req));
    }

    @Test
    void delete_delegatesToPython() {
        knowledgeBaseService.delete("kb1");
        verify(pythonAiClient).deleteKnowledgeBase("kb1");
    }

    @Test
    void pythonError_propagatesAsBizException() {
        when(pythonAiClient.getKnowledgeBase("missing"))
                .thenThrow(new BizException(404, "Knowledge base not found"));

        assertThatThrownBy(() -> knowledgeBaseService.get("missing"))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(404);
    }
}
