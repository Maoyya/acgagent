package com.darkness.common.util;

import com.darkness.common.exception.BizException;
import com.darkness.common.result.ResultCode;

/**
 * Service 层公共工具方法。
 * 提供实体查询不存在时统一抛异常的模式。
 */
public class ServiceHelper {

    private ServiceHelper() {}

    /**
     * 校验实体是否存在，不存在则抛 NOT_FOUND 异常。
     *
     * @param entity 查询结果，可为 null
     * @param name   实体名称，用于错误消息（如 "User"、"Agent"）
     * @param id     查询使用的 ID，用于错误消息
     * @return 非 null 的实体
     * @throws BizException entity 为 null 时抛出 NOT_FOUND
     */
    public static <T> T findOrThrow(T entity, String name, Object id) {
        if (entity == null) {
            throw new BizException(ResultCode.NOT_FOUND, name + " not found: " + id);
        }
        return entity;
    }
}
