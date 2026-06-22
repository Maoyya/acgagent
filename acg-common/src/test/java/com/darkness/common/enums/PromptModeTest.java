package com.darkness.common.enums;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PromptMode 序列化/反序列化测试。业务意义：mode 必须严格匹配 Python 契约的小写 "acg"/"compliant"，非法值拒绝。 */
class PromptModeTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serialize_outputsLowercaseValue() throws Exception {
        assertThat(mapper.writeValueAsString(PromptMode.ACG)).isEqualTo("\"acg\"");
        assertThat(mapper.writeValueAsString(PromptMode.COMPLIANT)).isEqualTo("\"compliant\"");
    }

    @Test
    void deserialize_acceptsLowercase() throws Exception {
        assertThat(mapper.readValue("\"acg\"", PromptMode.class)).isEqualTo(PromptMode.ACG);
        assertThat(mapper.readValue("\"compliant\"", PromptMode.class)).isEqualTo(PromptMode.COMPLIANT);
    }

    @Test
    void deserialize_illegalValue_throws() {
        assertThatThrownBy(() -> mapper.readValue("\"sandbox\"", PromptMode.class))
                .isInstanceOf(Exception.class);
    }
}
