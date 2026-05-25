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

/** SmsSendRequest 和 SmsLoginRequest 参数校验测试，覆盖手机号格式和验证码规则。 */
class SmsRequestValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Nested
    @DisplayName("SmsSendRequest 校验")
    class SmsSend {

        private Set<ConstraintViolation<SmsSendRequest>> validatePhone(String phone) {
            SmsSendRequest req = new SmsSendRequest();
            req.setPhone(phone);
            return validator.validate(req);
        }

        @Test
        void valid_phone_passes() {
            assertThat(validatePhone("13800138000")).isEmpty();
        }

        @Test
        void phone_blank_violation() {
            var violations = validatePhone("");
            assertThat(violations.stream().map(v -> v.getPropertyPath().toString()))
                    .contains("phone");
        }

        @Test
        void phone_invalidFormat_violation() {
            var violations = validatePhone("12345678901");
            assertThat(violations.stream()
                    .filter(v -> v.getPropertyPath().toString().equals("phone"))
                    .map(v -> v.getMessage()))
                    .anyMatch(msg -> msg.contains("手机号"));
        }

        @Test
        void phone_tooLong_violation() {
            var violations = validatePhone("1".repeat(21));
            assertThat(violations.stream()
                    .filter(v -> v.getPropertyPath().toString().equals("phone"))
                    .map(v -> v.getMessage()))
                    .anyMatch(msg -> msg.contains("20"));
        }
    }

    @Nested
    @DisplayName("SmsLoginRequest 校验")
    class SmsLogin {

        private Set<ConstraintViolation<SmsLoginRequest>> validate(String phone, String code) {
            SmsLoginRequest req = new SmsLoginRequest();
            req.setPhone(phone);
            req.setCode(code);
            return validator.validate(req);
        }

        @Test
        void valid_request_passes() {
            assertThat(validate("13912345678", "123456")).isEmpty();
        }

        @Test
        void code_not6digits_violation() {
            var violations = validate("13912345678", "12345");
            assertThat(violations.stream()
                    .filter(v -> v.getPropertyPath().toString().equals("code"))
                    .map(v -> v.getMessage()))
                    .anyMatch(msg -> msg.contains("6"));
        }

        @Test
        void code_containsLetter_violation() {
            var violations = validate("13912345678", "12a456");
            assertThat(violations.stream()
                    .filter(v -> v.getPropertyPath().toString().equals("code"))
                    .map(v -> v.getMessage()))
                    .anyMatch(msg -> msg.contains("数字"));
        }

        @Test
        void code_blank_violation() {
            var violations = validate("13912345678", "");
            assertThat(violations.stream().map(v -> v.getPropertyPath().toString()))
                    .contains("code");
        }
    }
}
