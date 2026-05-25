package com.darkness.common.model;

import com.darkness.common.enums.CommonStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** UserVO 参数校验测试，覆盖 username、nickname、email、phone、avatar 的边界约束。 */
class UserVOValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private UserVO buildValid() {
        UserVO vo = new UserVO();
        vo.setUsername("admin");
        vo.setNickname("管理员");
        vo.setEmail("admin@test.com");
        vo.setPhone("13800138000");
        vo.setAvatar("https://example.com/avatar.png");
        vo.setStatus(CommonStatus.ENABLED);
        return vo;
    }

    @Test
    void valid_vo_passes() {
        assertThat(validator.validate(buildValid())).isEmpty();
    }

    @Nested
    @DisplayName("username 校验")
    class Username {

        @Test
        void tooShort_violation() {
            UserVO vo = buildValid();
            vo.setUsername("ab");
            var violations = validator.validate(vo);
            assertThat(violations).anyMatch(v ->
                    v.getPropertyPath().toString().equals("username") && v.getMessage().contains("5"));
        }

        @Test
        void tooLong_violation() {
            UserVO vo = buildValid();
            vo.setUsername("a".repeat(51));
            var violations = validator.validate(vo);
            assertThat(violations).anyMatch(v ->
                    v.getPropertyPath().toString().equals("username") && v.getMessage().contains("50"));
        }
    }

    @Nested
    @DisplayName("nickname 校验")
    class Nickname {

        @Test
        void tooLong_violation() {
            UserVO vo = buildValid();
            vo.setNickname("x".repeat(65));
            var violations = validator.validate(vo);
            assertThat(violations).anyMatch(v ->
                    v.getPropertyPath().toString().equals("nickname") && v.getMessage().contains("64"));
        }
    }

    @Nested
    @DisplayName("email 校验")
    class Email {

        @Test
        void invalidFormat_violation() {
            UserVO vo = buildValid();
            vo.setEmail("not-an-email");
            var violations = validator.validate(vo);
            assertThat(violations).anyMatch(v ->
                    v.getPropertyPath().toString().equals("email") && v.getMessage().contains("邮箱"));
        }

        @Test
        void tooLong_violation() {
            UserVO vo = buildValid();
            // a@bb...bb.com = 2 + 122 + 4 = 128 正好边界，再加1个字符超限
            vo.setEmail("a@" + "b".repeat(123) + ".com");
            var violations = validator.validate(vo);
            assertThat(violations).anyMatch(v ->
                    v.getPropertyPath().toString().equals("email") && v.getMessage().contains("128"));
        }

        @Test
        void null_isAllowed() {
            UserVO vo = buildValid();
            vo.setEmail(null);
            assertThat(validator.validate(vo)).isEmpty();
        }
    }

    @Nested
    @DisplayName("phone 校验")
    class Phone {

        @Test
        void invalidFormat_violation() {
            UserVO vo = buildValid();
            vo.setPhone("12345");
            var violations = validator.validate(vo);
            assertThat(violations).anyMatch(v ->
                    v.getPropertyPath().toString().equals("phone") && v.getMessage().contains("手机号"));
        }

        @Test
        void emptyString_isAllowed() {
            // 空字符串匹配正则 ^$ 部分，允许不填手机号
            UserVO vo = buildValid();
            vo.setPhone("");
            assertThat(validator.validate(vo)).isEmpty();
        }

        @Test
        void null_isAllowed() {
            UserVO vo = buildValid();
            vo.setPhone(null);
            assertThat(validator.validate(vo)).isEmpty();
        }
    }

    @Nested
    @DisplayName("avatar 校验")
    class Avatar {

        @Test
        void tooLong_violation() {
            UserVO vo = buildValid();
            vo.setAvatar("https://" + "x".repeat(510) + ".com");
            var violations = validator.validate(vo);
            assertThat(violations).anyMatch(v ->
                    v.getPropertyPath().toString().equals("avatar") && v.getMessage().contains("512"));
        }
    }
}
