package com.darkness.common.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase B VO 反序列化测试：验证 camelCase VO 能从 Python 的 snake_case JSON 解析（@JsonAlias）。
 */
class PhaseBVoParsingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void documentVO_fromPythonSnakeCase() throws Exception {
        String json = "{\"id\":\"d1\",\"knowledge_base_id\":\"kb1\",\"file_name\":\"a.pdf\","
                + "\"file_size\":1024,\"chunk_count\":8,\"status\":\"completed\","
                + "\"error_message\":null,\"created_at\":\"2026-06-15T10:00:00\"}";
        DocumentVO vo = mapper.readValue(json, DocumentVO.class);
        assertThat(vo.getId()).isEqualTo("d1");
        assertThat(vo.getKnowledgeBaseId()).isEqualTo("kb1");
        assertThat(vo.getFileName()).isEqualTo("a.pdf");
        assertThat(vo.getFileSize()).isEqualTo(1024L);
        assertThat(vo.getChunkCount()).isEqualTo(8);
        assertThat(vo.getStatus()).isEqualTo("completed");
    }

    @Test
    void knowledgeBaseVO_fromPythonSnakeCase() throws Exception {
        String json = "{\"id\":\"kb1\",\"name\":\"KB\",\"description\":\"d\","
                + "\"document_count\":3,\"chunk_count\":50}";
        KnowledgeBaseVO vo = mapper.readValue(json, KnowledgeBaseVO.class);
        assertThat(vo.getId()).isEqualTo("kb1");
        assertThat(vo.getName()).isEqualTo("KB");
        assertThat(vo.getDocumentCount()).isEqualTo(3);
        assertThat(vo.getChunkCount()).isEqualTo(50);
    }

    @Test
    void toolVO_fromPythonSnakeCase() throws Exception {
        String json = "{\"id\":\"t1\",\"name\":\"T\",\"description\":\"desc\",\"type\":\"api\",\"created_at\":\"2026-06-15T10:00:00\"}";
        ToolVO vo = mapper.readValue(json, ToolVO.class);
        assertThat(vo.getId()).isEqualTo("t1");
        assertThat(vo.getType()).isEqualTo("api");
    }
}
