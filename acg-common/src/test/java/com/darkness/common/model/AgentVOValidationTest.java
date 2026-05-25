package com.darkness.common.model;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** AgentVO 参数校验测试，覆盖 name、apiUrl、apiKey 必填与长度、description/model/avatar 长度。 */
class AgentVOValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private AgentVO buildValid() {
        AgentVO vo = new AgentVO();
        vo.setName("GPT-4 Agent");
        vo.setApiUrl("https://api.openai.com/v1/chat/completions");
        vo.setApiKey("sk-xxxxxxxxxxxxxxxx");
        return vo;
    }

    @Test
    void valid_vo_passes() {
        assertThat(validator.validate(buildValid())).isEmpty();
    }

    @Nested
    @DisplayName("name 校验")
    class Name {

        @Test
        void blank_violation() {
            AgentVO vo = buildValid();
            vo.setName("");
            var violations = validator.validate(vo);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
        }

        @Test
        void tooLong_violation() {
            AgentVO vo = buildValid();
            vo.setName("a".repeat(129));
            var violations = validator.validate(vo);
            assertThat(violations).anyMatch(v ->
                    v.getPropertyPath().toString().equals("name") && v.getMessage().contains("128"));
        }
    }

    @Nested
    @DisplayName("apiUrl 校验")
    class ApiUrl {

        @Test
        void blank_violation() {
            AgentVO vo = buildValid();
            vo.setApiUrl("");
            var violations = validator.validate(vo);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("apiUrl"));
        }

        @Test
        void tooLong_violation() {
            AgentVO vo = buildValid();
            vo.setApiUrl("https://" + "a".repeat(510) + ".com");
            var violations = validator.validate(vo);
            assertThat(violations).anyMatch(v ->
                    v.getPropertyPath().toString().equals("apiUrl") && v.getMessage().contains("512"));
        }
    }

    @Nested
    @DisplayName("apiKey 校验")
    class ApiKey {

        @Test
        void blank_violation() {
            AgentVO vo = buildValid();
            vo.setApiKey("");
            var violations = validator.validate(vo);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("apiKey"));
        }

        @Test
        void tooLong_violation() {
            AgentVO vo = buildValid();
            vo.setApiKey("sk-" + "x".repeat(510));
            var violations = validator.validate(vo);
            assertThat(violations).anyMatch(v ->
                    v.getPropertyPath().toString().equals("apiKey") && v.getMessage().contains("512"));
        }
    }

    @Nested
    @DisplayName("description 校验")
    class Description {

        @Test
        void tooLong_violation() {
            AgentVO vo = buildValid();
            vo.setDescription("d".repeat(513));
            var violations = validator.validate(vo);
            assertThat(violations).anyMatch(v ->
                    v.getPropertyPath().toString().equals("description") && v.getMessage().contains("512"));
        }

        @Test
        void null_isAllowed() {
            AgentVO vo = buildValid();
            vo.setDescription(null);
            assertThat(validator.validate(vo)).isEmpty();
        }
    }

    @Nested
    @DisplayName("model 校验")
    class Model {

        @Test
        void tooLong_violation() {
            AgentVO vo = buildValid();
            vo.setModel("gpt-" + "x".repeat(125));
            var violations = validator.validate(vo);
            assertThat(violations).anyMatch(v ->
                    v.getPropertyPath().toString().equals("model") && v.getMessage().contains("128"));
        }
    }

    @Nested
    @DisplayName("avatar 校验")
    class Avatar {

        @Test
        void tooLong_violation() {
            AgentVO vo = buildValid();
            vo.setAvatar("https://" + "x".repeat(510) + ".com");
            var violations = validator.validate(vo);
            assertThat(violations).anyMatch(v ->
                    v.getPropertyPath().toString().equals("avatar") && v.getMessage().contains("512"));
        }
    }
}
