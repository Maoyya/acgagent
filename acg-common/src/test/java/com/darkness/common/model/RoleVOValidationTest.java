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

/** RoleVO 参数校验测试，覆盖 name、code 必填与长度约束、remark 长度约束。 */
class RoleVOValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private RoleVO buildValid() {
        RoleVO vo = new RoleVO();
        vo.setName("管理员");
        vo.setCode("admin");
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
            RoleVO vo = buildValid();
            vo.setName("");
            var violations = validator.validate(vo);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
        }

        @Test
        void tooLong_violation() {
            RoleVO vo = buildValid();
            vo.setName("角".repeat(65));
            var violations = validator.validate(vo);
            assertThat(violations).anyMatch(v ->
                    v.getPropertyPath().toString().equals("name") && v.getMessage().contains("64"));
        }
    }

    @Nested
    @DisplayName("code 校验")
    class Code {

        @Test
        void blank_violation() {
            RoleVO vo = buildValid();
            vo.setCode("");
            var violations = validator.validate(vo);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("code"));
        }

        @Test
        void tooLong_violation() {
            RoleVO vo = buildValid();
            vo.setCode("a".repeat(65));
            var violations = validator.validate(vo);
            assertThat(violations).anyMatch(v ->
                    v.getPropertyPath().toString().equals("code") && v.getMessage().contains("64"));
        }
    }

    @Nested
    @DisplayName("remark 校验")
    class Remark {

        @Test
        void tooLong_violation() {
            RoleVO vo = buildValid();
            vo.setRemark("x".repeat(257));
            var violations = validator.validate(vo);
            assertThat(violations).anyMatch(v ->
                    v.getPropertyPath().toString().equals("remark") && v.getMessage().contains("256"));
        }

        @Test
        void null_isAllowed() {
            RoleVO vo = buildValid();
            vo.setRemark(null);
            assertThat(validator.validate(vo)).isEmpty();
        }
    }
}
