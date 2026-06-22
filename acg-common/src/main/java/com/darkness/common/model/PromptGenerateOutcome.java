package com.darkness.common.model;

import lombok.Getter;

/**
 * generate 结果：要么成功带响应(success)，要么被 moderation 拦截带裁决(verdict)。
 * blocked=true 时 success=null。用于让 Service 把 403 当业务结果而非异常处理。
 */
@Getter
public class PromptGenerateOutcome {
    private final boolean blocked;
    private final PromptGenerateResponseVO success;
    private final ModerationVerdictVO verdict;

    private PromptGenerateOutcome(boolean blocked, PromptGenerateResponseVO success, ModerationVerdictVO verdict) {
        this.blocked = blocked;
        this.success = success;
        this.verdict = verdict;
    }

    public static PromptGenerateOutcome success(PromptGenerateResponseVO s) {
        return new PromptGenerateOutcome(false, s, null);
    }

    public static PromptGenerateOutcome blocked(ModerationVerdictVO v) {
        return new PromptGenerateOutcome(true, null, v);
    }
}
