package com.darkness.common.model;

import com.darkness.common.enums.PermissionType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** PermissionVO 参数校验测试，覆盖 name、code 必填与长度、type 必填、path/icon 长度。 */
class PermissionVOValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private PermissionVO buildValid() {
        PermissionVO vo = new PermissionVO();
        vo.setName("用户管理");
        vo.setCode("user:list");
        vo.setType(PermissionType.MENU);
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
            PermissionVO vo = buildValid();
            vo.setName("");
            var violations = validator.validate(vo);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
        }

        @Test
        void tooLong_violation() {
            PermissionVO vo = buildValid();
            vo.setName("权".repeat(65));
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
            PermissionVO vo = buildValid();
            vo.setCode("");
            var violations = validator.validate(vo);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("code"));
        }

        @Test
        void tooLong_violation() {
            PermissionVO vo = buildValid();
            vo.setCode("a".repeat(129));
            var violations = validator.validate(vo);
            assertThat(violations).anyMatch(v ->
                    v.getPropertyPath().toString().equals("code") && v.getMessage().contains("128"));
        }
    }

    @Nested
    @DisplayName("type 校验")
    class Type {

        @Test
        void null_violation() {
            PermissionVO vo = buildValid();
            vo.setType(null);
            var violations = validator.validate(vo);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("type"));
        }
    }

    @Nested
    @DisplayName("path 校验")
    class Path {

        @Test
        void tooLong_violation() {
            PermissionVO vo = buildValid();
            vo.setPath("/" + "a".repeat(256));
            var violations = validator.validate(vo);
            assertThat(violations).anyMatch(v ->
                    v.getPropertyPath().toString().equals("path") && v.getMessage().contains("256"));
        }
    }

    @Nested
    @DisplayName("icon 校验")
    class Icon {

        @Test
        void tooLong_violation() {
            PermissionVO vo = buildValid();
            vo.setIcon("x".repeat(65));
            var violations = validator.validate(vo);
            assertThat(violations).anyMatch(v ->
                    v.getPropertyPath().toString().equals("icon") && v.getMessage().contains("64"));
        }
    }
}
