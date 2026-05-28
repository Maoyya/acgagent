package com.darkness.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色校验注解，标注在 Controller 类或方法上。
 * AOP 切面会校验当前用户的角色是否包含注解声明的任一角色。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    /** 允许访问的角色编码，满足任一即可 */
    String[] value();
}
