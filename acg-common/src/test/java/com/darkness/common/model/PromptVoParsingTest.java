package com.darkness.common.model;

import com.darkness.common.enums.PromptMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Prompt VO 解析测试。业务意义：camelCase VO 必须能从 Python snake_case JSON 解析（@JsonAlias），保证契约对齐。 */
class PromptVoParsingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void moderationVerdictVO_fromPythonSnakeCase() throws Exception {
        String json = "{\"passed\":false,\"violated_rules\":[\"禁止二次元风格\"],\"reasons\":[\"动漫夸张人设\"],\"confidence\":0.9,\"mode\":\"compliant\"}";
        ModerationVerdictVO v = mapper.readValue(json, ModerationVerdictVO.class);
        assertThat(v.getPassed()).isFalse();
        assertThat(v.getViolatedRules()).containsExactly("禁止二次元风格");
        assertThat(v.getConfidence()).isEqualTo(0.9);
        assertThat(v.getMode()).isEqualTo(PromptMode.COMPLIANT);
    }

    @Test
    void costEstimateVO_fromPythonSnakeCase() throws Exception {
        String json = "{\"prompt_tokens\":120,\"est_completion_tokens\":0,\"model\":\"deepseek-chat\"}";
        CostEstimateVO e = mapper.readValue(json, CostEstimateVO.class);
        assertThat(e.getPromptTokens()).isEqualTo(120);
        assertThat(e.getEstCompletionTokens()).isEqualTo(0);
        assertThat(e.getModel()).isEqualTo("deepseek-chat");
    }

    @Test
    void promptGenerateResponseVO_fromPythonSnakeCase() throws Exception {
        // v1.1：generate 响应只含草稿（systemPrompt/mode/moderation/estimate），不带 templateId
        String json = "{\"system_prompt\":\"你是...\",\"mode\":\"acg\","
                + "\"moderation\":{\"passed\":true,\"violated_rules\":[],\"reasons\":[],\"confidence\":0.95,\"mode\":\"acg\"},"
                + "\"estimate\":{\"prompt_tokens\":120,\"est_completion_tokens\":0,\"model\":\"deepseek-chat\"}}";
        PromptGenerateResponseVO r = mapper.readValue(json, PromptGenerateResponseVO.class);
        assertThat(r.getSystemPrompt()).isEqualTo("你是...");
        assertThat(r.getMode()).isEqualTo(PromptMode.ACG);
        assertThat(r.getModeration().getPassed()).isTrue();
        assertThat(r.getEstimate().getPromptTokens()).isEqualTo(120);
    }

    @Test
    void outcome_factories() {
        PromptGenerateResponseVO s = new PromptGenerateResponseVO();
        PromptGenerateOutcome ok = PromptGenerateOutcome.success(s);
        assertThat(ok.isBlocked()).isFalse();
        assertThat(ok.getSuccess()).isSameAs(s);
        assertThat(ok.getVerdict()).isNull();

        ModerationVerdictVO v = new ModerationVerdictVO();
        PromptGenerateOutcome blocked = PromptGenerateOutcome.blocked(v);
        assertThat(blocked.isBlocked()).isTrue();
        assertThat(blocked.getVerdict()).isSameAs(v);
        assertThat(blocked.getSuccess()).isNull();
    }
}
