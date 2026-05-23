package com.darkness.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * MyBatis-Plus 实体基类，提供自增主键、自动填充时间戳和逻辑删除支持。
 */
@Data
public class BaseEntity implements Serializable {

    /** 主键 ID，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 创建时间，插入时由 MyBatis-Plus MetaObjectHandler 自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间，插入和更新时由 MyBatis-Plus MetaObjectHandler 自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标志：0-未删除，1-已删除。查询时 MyBatis-Plus 自动过滤已删除记录 */
    @TableLogic
    private Integer deleted;
}
