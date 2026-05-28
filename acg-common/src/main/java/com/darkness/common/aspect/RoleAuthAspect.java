package com.darkness.common.aspect;

import com.darkness.common.annotation.RequireRole;
import com.darkness.common.exception.BizException;
import com.darkness.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色校验切面，读取 X-User-Roles 请求头与 @RequireRole 注解声明的角色取交集，
 * 交集为空时抛出 BizException(FORBIDDEN)。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class RoleAuthAspect {

    @Before("@within(com.darkness.common.annotation.RequireRole) || " +
            "@annotation(com.darkness.common.annotation.RequireRole)")
    public void checkRole(JoinPoint joinPoint) {
        HttpServletRequest request = getRequest();
        String rolesHeader = request.getHeader("X-User-Roles");

        Set<String> userRoles = rolesHeader != null && !rolesHeader.isBlank()
                ? Arrays.stream(rolesHeader.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet())
                : Set.of();

        RequireRole methodAnnotation = getMethodAnnotation(joinPoint);
        RequireRole classAnnotation = getClassAnnotation(joinPoint);
        RequireRole annotation = methodAnnotation != null ? methodAnnotation : classAnnotation;

        if (annotation == null) {
            return;
        }

        boolean hasRole = Arrays.stream(annotation.value())
                .anyMatch(userRoles::contains);

        if (!hasRole) {
            throw new BizException(ResultCode.FORBIDDEN, "权限不足");
        }
    }

    private HttpServletRequest getRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new BizException(ResultCode.FORBIDDEN, "无法获取请求上下文");
        }
        return attrs.getRequest();
    }

    private RequireRole getMethodAnnotation(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        return method.getAnnotation(RequireRole.class);
    }

    private RequireRole getClassAnnotation(JoinPoint joinPoint) {
        return joinPoint.getTarget().getClass().getAnnotation(RequireRole.class);
    }
}
