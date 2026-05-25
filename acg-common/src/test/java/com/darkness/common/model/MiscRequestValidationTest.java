package com.darkness.common.model;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RefreshTokenRequest、WxLoginRequest、CreateConversationRequest、SendMessageRequest 参数校验测试。
 */
class MiscRequestValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Nested
    @DisplayName("RefreshTokenRequest 校验")
    class RefreshToken {

        private Set<ConstraintViolation<RefreshTokenRequest>> validate(String token) {
            RefreshTokenRequest req = new RefreshTokenRequest();
            req.setRefreshToken(token);
            return validator.validate(req);
        }

        @Test
        void valid_token_passes() {
            assertThat(validate("some.jwt.token")).isEmpty();
        }

        @Test
        void token_blank_violation() {
            var violations = validate("");
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("refreshToken"));
        }

        @Test
        void token_tooLong_violation() {
            var violations = validate("x".repeat(2049));
            assertThat(violations).anyMatch(v ->
                    v.getPropertyPath().toString().equals("refreshToken") && v.getMessage().contains("2048"));
        }
    }

    @Nested
    @DisplayName("WxLoginRequest 校验")
    class WxLogin {

        private Set<ConstraintViolation<WxLoginRequest>> validate(String code) {
            WxLoginRequest req = new WxLoginRequest();
            req.setCode(code);
            return validator.validate(req);
        }

        @Test
        void valid_code_passes() {
            assertThat(validate("wx_auth_code_123")).isEmpty();
        }

        @Test
        void code_blank_violation() {
            assertThat(validate("")).anyMatch(v -> v.getPropertyPath().toString().equals("code"));
        }

        @Test
        void code_tooLong_violation() {
            assertThat(validate("x".repeat(129))).anyMatch(v ->
                    v.getPropertyPath().toString().equals("code") && v.getMessage().contains("128"));
        }
    }

    @Nested
    @DisplayName("CreateConversationRequest 校验")
    class CreateConversation {

        private Set<ConstraintViolation<CreateConversationRequest>> validate(Long agentId, String title) {
            CreateConversationRequest req = new CreateConversationRequest();
            req.setAgentId(agentId);
            req.setTitle(title);
            return validator.validate(req);
        }

        @Test
        void valid_request_passes() {
            assertThat(validate(1L, "测试对话")).isEmpty();
        }

        @Test
        void agentId_null_violation() {
            assertThat(validate(null, "标题"))
                    .anyMatch(v -> v.getPropertyPath().toString().equals("agentId"));
        }

        @Test
        void title_optional_noViolation() {
            assertThat(validate(1L, null)).isEmpty();
        }

        @Test
        void title_tooLong_violation() {
            assertThat(validate(1L, "x".repeat(257)))
                    .anyMatch(v -> v.getPropertyPath().toString().equals("title") && v.getMessage().contains("256"));
        }
    }

    @Nested
    @DisplayName("SendMessageRequest 校验")
    class SendMessage {

        private Set<ConstraintViolation<SendMessageRequest>> validate(String content) {
            SendMessageRequest req = new SendMessageRequest();
            req.setContent(content);
            return validator.validate(req);
        }

        @Test
        void valid_content_passes() {
            assertThat(validate("你好")).isEmpty();
        }

        @Test
        void content_blank_violation() {
            assertThat(validate("")).anyMatch(v -> v.getPropertyPath().toString().equals("content"));
        }

        @Test
        void content_tooLong_violation() {
            assertThat(validate("x".repeat(32001)))
                    .anyMatch(v -> v.getPropertyPath().toString().equals("content") && v.getMessage().contains("32000"));
        }
    }
}
