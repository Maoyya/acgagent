package com.darkness.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 跨服务用户传输对象，用于 Dubbo RPC 调用时传递用户基本信息。
 */
@Data
public class UserDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long id;

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 状态：1-启用，0-禁用 */
    private Integer status;
}
