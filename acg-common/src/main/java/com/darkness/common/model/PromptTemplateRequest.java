package com.darkness.common.model;

import com.darkness.common.enums.PromptMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 模板手建/更新请求。isPublic 仅管理员可置 true（→user_id=NULL）；普通用户置 true 由 Service 拒绝(403)。 */
@Data
public class PromptTemplateRequest {

    /** 模板名称 */
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 128, message = "模板名称长度不能超过128")
    private String name;

    /** 系统提示词正文 */
    @NotBlank(message = "系统提示词不能为空")
    private String systemPrompt;

    /** 生成模式，默认 ACG */
    private PromptMode mode = PromptMode.ACG;

    /** 能力标签数组 */
    private List<String> targetCapabilities;

    /** 是否公共模板；仅管理员可置 true。true→落库 user_id=NULL */
    private Boolean isPublic;
}
