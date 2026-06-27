package com.darkness.common.model;

import com.darkness.common.entity.PromptTemplateDO;
import com.darkness.common.enums.CommonStatus;
import com.darkness.common.enums.PromptMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** PromptTemplateVO 转换测试。业务意义：isPublic 必须由 user_id 派生（公共库 vs 用户私有的展示区分）。 */
class PromptTemplateVOTest {

    @Test
    void from_publicTemplate_isPublicTrue() {
        PromptTemplateDO pub = new PromptTemplateDO();
        pub.setId(1L);
        pub.setUserId(null); // 公共
        pub.setName("P");
        pub.setSystemPrompt("s");
        pub.setMode(PromptMode.ACG);
        pub.setStatus(CommonStatus.ENABLED);
        PromptTemplateVO vo = PromptTemplateVO.from(pub);
        assertThat(vo.getIsPublic()).isTrue();
        assertThat(vo.getName()).isEqualTo("P");
    }

    @Test
    void from_privateTemplate_isPublicFalse() {
        PromptTemplateDO priv = new PromptTemplateDO();
        priv.setId(2L);
        priv.setUserId(5L); // 私有
        priv.setName("Q");
        priv.setSystemPrompt("s2");
        assertThat(PromptTemplateVO.from(priv).getIsPublic()).isFalse();
    }

    @Test
    void from_null_returnsNull() {
        assertThat(PromptTemplateVO.from(null)).isNull();
    }
}
