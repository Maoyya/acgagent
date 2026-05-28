package com.darkness.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 微信绑定状态视图对象，用于返回当前用户的微信账号绑定情况。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WxBindStatusVO {

    /** 是否已绑定微信账号 */
    private boolean bound;

    /** 绑定的微信 openid，未绑定时为 null */
    private String openid;
}
