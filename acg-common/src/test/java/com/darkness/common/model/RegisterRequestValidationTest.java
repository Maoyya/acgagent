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

/** RegisterRequest 参数校验测试，校验规则与 LoginRequest 一致。 */
class RegisterRequestValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private Set<ConstraintViolation<RegisterRequest>> validate(String username, String password) {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(username);
        req.setPassword(password);
        return validator.validate(req);
    }

    @Test
    void valid_request_passes() {
        assertThat(validate("newuser", "password123")).isEmpty();
    }

    @Test
    void username_tooShort_violation() {
        var violations = validate("ab", "password123");
        assertThat(violations.stream()
                .filter(v -> v.getPropertyPath().toString().equals("username"))
                .map(v -> v.getMessage()))
                .anyMatch(msg -> msg.contains("5"));
    }

    @Test
    void username_tooLong_violation() {
        var violations = validate("a".repeat(51), "password123");
        assertThat(violations.stream()
                .filter(v -> v.getPropertyPath().toString().equals("username"))
                .map(v -> v.getMessage()))
                .anyMatch(msg -> msg.contains("50"));
    }

    @Test
    void password_tooShort_violation() {
        var violations = validate("newuser", "abc");
        assertThat(violations.stream()
                .filter(v -> v.getPropertyPath().toString().equals("password"))
                .map(v -> v.getMessage()))
                .anyMatch(msg -> msg.contains("6"));
    }

    @Test
    void password_blank_violation() {
        var violations = validate("newuser", "");
        assertThat(violations.stream().map(v -> v.getPropertyPath().toString()))
                .contains("password");
    }
}
