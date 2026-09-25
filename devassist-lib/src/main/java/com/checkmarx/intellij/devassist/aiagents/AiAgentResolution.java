package com.checkmarx.intellij.devassist.aiagents;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

/**
 * Result of {@link AiAgentLoginResolver#resolve}: the agent to use, and an optional user-facing
 * notice to surface in the welcome dialog.
 */
@Getter
@AllArgsConstructor
public class AiAgentResolution {

    private final AiAgent agent;

    @Nullable
    private final String noticeMessage;
}
