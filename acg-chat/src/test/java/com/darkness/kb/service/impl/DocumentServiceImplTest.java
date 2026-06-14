package com.darkness.kb.service.impl;

import com.darkness.agent.client.PythonAiClient;
import com.darkness.common.exception.BizException;
import com.darkness.common.model.DocumentVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * 文档代理 Service 测试。upload（multipart 转发）按惯例不单测，验证 list/get/delete/upload 委托 + 错误透传。
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {

    @Mock
    private PythonAiClient pythonAiClient;

    @InjectMocks
    private DocumentServiceImpl documentService;

    @Test
    void list_delegatesToPython() {
        DocumentVO vo = new DocumentVO();
        vo.setId("d1");
        when(pythonAiClient.listDocuments("kb1")).thenReturn(List.of(vo));

        List<DocumentVO> result = documentService.list("kb1");

        assertThat(result).hasSize(1);
    }

    @Test
    void get_delegatesToPython() {
        DocumentVO vo = new DocumentVO();
        vo.setId("d1");
        when(pythonAiClient.getDocument("kb1", "d1")).thenReturn(vo);

        assertThat(documentService.get("kb1", "d1").getId()).isEqualTo("d1");
    }

    @Test
    void delete_delegatesToPython() {
        documentService.delete("kb1", "d1");
        verify(pythonAiClient).deleteDocument("kb1", "d1");
    }

    @Test
    void upload_delegatesToPython() {
        MultipartFile file = mock(MultipartFile.class);
        DocumentVO vo = new DocumentVO();
        vo.setId("d1");
        vo.setStatus("processing");
        when(pythonAiClient.uploadDocument("kb1", file)).thenReturn(vo);

        DocumentVO result = documentService.upload("kb1", file);

        assertThat(result.getId()).isEqualTo("d1");
        assertThat(result.getStatus()).isEqualTo("processing");
    }

    @Test
    void pythonError_propagatesAsBizException() {
        when(pythonAiClient.listDocuments("kbMissing"))
                .thenThrow(new BizException(404, "Knowledge base not found"));

        assertThatThrownBy(() -> documentService.list("kbMissing"))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(404);
    }
}
