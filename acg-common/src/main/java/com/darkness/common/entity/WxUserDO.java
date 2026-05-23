package com.darkness.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 微信用户绑定关系实体，映射 wx_user 表。记录 openid 与系统 userId 的对应关系。
 */
@Data
@TableName("wx_user")
public class WxUserDO {

    /** 主键 ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 微信用户唯一标识（openid），由微信 OAuth2 授权后返回 */
    private String openid;

    /** 微信开放平台 unionid，用于跨应用识别同一用户，可为空 */
    private String unionId;

    /** 关联的系统用户 ID，对应 user 表的主键 */
    private Long userId;

    /** 微信昵称，首次绑定时从微信用户信息中获取 */
    private String nickname;

    /** 微信头像 URL，首次绑定时从微信用户信息中获取 */
    private String avatarUrl;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
