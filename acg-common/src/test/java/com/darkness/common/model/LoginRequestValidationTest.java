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

/** LoginRequest 参数校验测试，覆盖 username/password 的空值和长度边界。 */
class LoginRequestValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private Set<ConstraintViolation<LoginRequest>> validate(String username, String password) {
        LoginRequest req = new LoginRequest();
        req.setUsername(username);
        req.setPassword(password);
        return validator.validate(req);
    }

    @Nested
    @DisplayName("username 校验")
    class Username {

        @Test
        void valid_username_passes() {
            assertThat(validate("admin", "password123")).isEmpty();
        }

        @Test
        void username_at_min_boundary_passes() {
            assertThat(validate("abcde", "password123")).isEmpty();
        }

        @Test
        void username_at_max_boundary_passes() {
            String name50 = "a".repeat(50);
            assertThat(validate(name50, "password123")).isEmpty();
        }

        @Test
        void username_blank_violation() {
            var violations = validate("", "password123");
            assertThat(violations.stream().map(v -> v.getPropertyPath().toString()))
                    .contains("username");
        }

        @Test
        void username_null_violation() {
            var violations = validate(null, "password123");
            assertThat(violations.stream().map(v -> v.getPropertyPath().toString()))
                    .contains("username");
        }

        @Test
        void username_tooShort_violation() {
            var violations = validate("abcd", "password123");
            assertThat(violations.stream()
                    .filter(v -> v.getPropertyPath().toString().equals("username"))
                    .map(v -> v.getMessage()))
                    .anyMatch(msg -> msg.contains("5"));
        }

        @Test
        void username_tooLong_violation() {
            String name51 = "a".repeat(51);
            var violations = validate(name51, "password123");
            assertThat(violations.stream()
                    .filter(v -> v.getPropertyPath().toString().equals("username"))
                    .map(v -> v.getMessage()))
                    .anyMatch(msg -> msg.contains("50"));
        }
    }

    @Nested
    @DisplayName("password 校验")
    class Password {

        @Test
        void password_at_min_boundary_passes() {
            assertThat(validate("admin", "abcdef")).isEmpty();
        }

        @Test
        void password_at_max_boundary_passes() {
            String pwd256 = "x".repeat(256);
            assertThat(validate("admin", pwd256)).isEmpty();
        }

        @Test
        void password_blank_violation() {
            var violations = validate("admin", "");
            assertThat(violations.stream().map(v -> v.getPropertyPath().toString()))
                    .contains("password");
        }

        @Test
        void password_tooShort_violation() {
            var violations = validate("admin", "abcde");
            assertThat(violations.stream()
                    .filter(v -> v.getPropertyPath().toString().equals("password"))
                    .map(v -> v.getMessage()))
                    .anyMatch(msg -> msg.contains("6"));
        }

        @Test
        void password_tooLong_violation() {
            String pwd257 = "x".repeat(257);
            var violations = validate("admin", pwd257);
            assertThat(violations.stream()
                    .filter(v -> v.getPropertyPath().toString().equals("password"))
                    .map(v -> v.getMessage()))
                    .anyMatch(msg -> msg.contains("256"));
        }
    }
}
