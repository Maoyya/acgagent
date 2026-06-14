package com.darkness.common.model;

import com.darkness.common.entity.AgentDO;
import com.darkness.common.enums.AgentCategory;
import com.darkness.common.enums.CommonStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgentVO from/to 转换测试：验证新增丰富配置字段在 VO↔DO 间正确传递，
 * 且 apiKey 在 from() 时被脱敏、toEntity() 时不做逆脱敏。
 */
class AgentVOTest {

    @Test
    void from_carriesRichFields_andMasksApiKey() {
        AgentDO entity = new AgentDO();
        entity.setId(7L);
        entity.setApiKey("real-secret");
        entity.setSystemPrompt("你是一个助手");
        entity.setProvider("doubao");
        entity.setTemperature(new BigDecimal("0.5"));
        entity.setMaxTokens(2048);
        entity.setTopP(new BigDecimal("0.8"));
        entity.setMemoryType("conversation_window");
        entity.setMemoryMaxTokens(8000);
        entity.setCapabilities(List.of("chat", "rag"));
        entity.setKnowledgeBaseIds(List.of("kb1"));
        entity.setToolIds(List.of("calculator"));
        entity.setPythonAgentId("py-abc");
        entity.setCategory(AgentCategory.CHAT);
        entity.setStatus(CommonStatus.ENABLED);

        AgentVO vo = AgentVO.from(entity);

        assertThat(vo.getSystemPrompt()).isEqualTo("你是一个助手");
        assertThat(vo.getProvider()).isEqualTo("doubao");
        assertThat(vo.getTemperature()).isEqualByComparingTo("0.5");
        assertThat(vo.getCapabilities()).containsExactly("chat", "rag");
        assertThat(vo.getKnowledgeBaseIds()).containsExactly("kb1");
        assertThat(vo.getToolIds()).containsExactly("calculator");
        assertThat(vo.getPythonAgentId()).isEqualTo("py-abc");
        // apiKey 必须脱敏
        assertThat(vo.getApiKey()).isEqualTo("******");
    }

    @Test
    void toEntity_carriesRichFields_withoutUnmasking() {
        AgentVO vo = new AgentVO();
        vo.setName("A");
        vo.setApiUrl("http://x");
        vo.setApiKey("******");
        vo.setSystemPrompt("prompt");
        vo.setCapabilities(List.of("chat"));
        vo.setPythonAgentId("py-1");

        AgentDO entity = vo.toEntity();

        assertThat(entity.getSystemPrompt()).isEqualTo("prompt");
        assertThat(entity.getCapabilities()).containsExactly("chat");
        assertThat(entity.getPythonAgentId()).isEqualTo("py-1");
        // toEntity 不逆脱敏，apiKey 原样传入（由 Service 层判断掩码）
        assertThat(entity.getApiKey()).isEqualTo("******");
    }
}
