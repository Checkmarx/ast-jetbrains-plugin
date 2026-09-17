package com.checkmarx.intellij.devassist.test.remediation;

import com.checkmarx.intellij.devassist.configuration.mcp.McpAgentTarget;
import com.checkmarx.intellij.devassist.aiagents.AiAgent;
import com.checkmarx.intellij.devassist.aiagents.ChatIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Unit tests for {@link AiAgent}'s resolution logic. This is the sole point deciding which
 * {@link ChatIntegration}/{@link McpAgentTarget} runs for every settings load/apply/install/
 * fix-with-AI call, so its default/fallback behavior must be pinned down explicitly.
 */
@DisplayName("AiAgent unit tests")
class AiAgentTest {

    // ===== fromSettingsValue =====

    @Test
    @DisplayName("fromSettingsValue_Null_DefaultsToCopilot")
    void fromSettingsValue_Null_DefaultsToCopilot() {
        assertEquals(AiAgent.COPILOT, AiAgent.fromSettingsValue(null));
    }

    @Test
    @DisplayName("fromSettingsValue_Blank_DefaultsToCopilot")
    void fromSettingsValue_Blank_DefaultsToCopilot() {
        assertEquals(AiAgent.COPILOT, AiAgent.fromSettingsValue(""));
    }

    @Test
    @DisplayName("fromSettingsValue_UnknownValue_DefaultsToCopilot")
    void fromSettingsValue_UnknownValue_DefaultsToCopilot() {
        assertEquals(AiAgent.COPILOT, AiAgent.fromSettingsValue("SOME_FUTURE_AGENT"));
    }

    @Test
    @DisplayName("fromSettingsValue_LowercaseEnumName_DefaultsToCopilot")
    void fromSettingsValue_LowercaseEnumName_DefaultsToCopilot() {
        // valueOf is case-sensitive; a lowercase persisted value must not silently match
        // AI_ASSISTANT and must fall back to the documented default instead of throwing.
        assertEquals(AiAgent.COPILOT, AiAgent.fromSettingsValue("ai_assistant"));
    }

    @Test
    @DisplayName("fromSettingsValue_ExactCopilotName_ResolvesToCopilot")
    void fromSettingsValue_ExactCopilotName_ResolvesToCopilot() {
        assertEquals(AiAgent.COPILOT, AiAgent.fromSettingsValue("COPILOT"));
    }

    @Test
    @DisplayName("fromSettingsValue_ExactAiAssistantName_ResolvesToAiAssistant")
    void fromSettingsValue_ExactAiAssistantName_ResolvesToAiAssistant() {
        assertEquals(AiAgent.AI_ASSISTANT, AiAgent.fromSettingsValue("AI_ASSISTANT"));
    }

    // ===== fromAgentName =====

    @Test
    @DisplayName("fromAgentName_Null_DefaultsToCopilot")
    void fromAgentName_Null_DefaultsToCopilot() {
        assertEquals(AiAgent.COPILOT, AiAgent.fromAgentName(null));
    }

    @Test
    @DisplayName("fromAgentName_UnknownLabel_DefaultsToCopilot")
    void fromAgentName_UnknownLabel_DefaultsToCopilot() {
        assertEquals(AiAgent.COPILOT, AiAgent.fromAgentName("Some Future Agent"));
    }

    @Test
    @DisplayName("fromAgentName_ExactCopilotLabel_ResolvesToCopilot")
    void fromAgentName_ExactCopilotLabel_ResolvesToCopilot() {
        assertEquals(AiAgent.COPILOT, AiAgent.fromAgentName(AiAgent.COPILOT.getAgentName()));
    }

    @Test
    @DisplayName("fromAgentName_ExactAiAssistantLabel_ResolvesToAiAssistant")
    void fromAgentName_ExactAiAssistantLabel_ResolvesToAiAssistant() {
        assertEquals(AiAgent.AI_ASSISTANT, AiAgent.fromAgentName(AiAgent.AI_ASSISTANT.getAgentName()));
    }

    @Test
    @DisplayName("fromAgentName_MixedCaseLabel_ResolvesCaseInsensitively")
    void fromAgentName_MixedCaseLabel_ResolvesCaseInsensitively() {
        assertEquals(AiAgent.AI_ASSISTANT, AiAgent.fromAgentName("ai assistant"));
        assertEquals(AiAgent.AI_ASSISTANT, AiAgent.fromAgentName("AI ASSISTANT"));
    }

    // ===== round-trip =====

    @Test
    @DisplayName("roundTrip_SettingsValueToLabelAndBack_PreservesAgent")
    void roundTrip_SettingsValueToLabelAndBack_PreservesAgent() {
        for (AiAgent agent : AiAgent.values()) {
            AiAgent viaSettingsValue = AiAgent.fromSettingsValue(agent.name());
            AiAgent viaLabel = AiAgent.fromAgentName(agent.getAgentName());
            assertEquals(agent, viaSettingsValue, "fromSettingsValue(name()) must round-trip for " + agent);
            assertEquals(agent, viaLabel, "fromAgentName(getAgentName()) must round-trip for " + agent);
        }
    }

    // ===== chatIntegration() / mcpTarget() wiring =====

    @Test
    @DisplayName("chatIntegration_ReturnsNonNullDistinctImplementationsPerAgent")
    void chatIntegration_ReturnsNonNullDistinctImplementationsPerAgent() {
        ChatIntegration copilot = AiAgent.COPILOT.chatIntegration();
        ChatIntegration aiAssistant = AiAgent.AI_ASSISTANT.chatIntegration();

        assertNotNull(copilot);
        assertNotNull(aiAssistant);
        assertNotSame(copilot.getClass(), aiAssistant.getClass());
    }

    @Test
    @DisplayName("mcpTarget_ReturnsNonNullDistinctImplementationsPerAgent")
    void mcpTarget_ReturnsNonNullDistinctImplementationsPerAgent() {
        McpAgentTarget copilot = AiAgent.COPILOT.mcpTarget();
        McpAgentTarget aiAssistant = AiAgent.AI_ASSISTANT.mcpTarget();

        assertNotNull(copilot);
        assertNotNull(aiAssistant);
        assertNotSame(copilot.getClass(), aiAssistant.getClass());
    }

    @Test
    @DisplayName("mcpTarget_AiAssistantExposesSettingsPage_CopilotDoesNot")
    void mcpTarget_AiAssistantExposesSettingsPage_CopilotDoesNot() {
        assertEquals(true, AiAgent.AI_ASSISTANT.mcpTarget().getSettingsConfigurableId().isPresent());
        assertEquals(true, AiAgent.COPILOT.mcpTarget().getSettingsConfigurableId().isEmpty());
    }

    @Test
    @DisplayName("values_ContainsExactlyCopilotAndAiAssistant")
    void values_ContainsExactlyCopilotAndAiAssistant() {
        assertEquals(2, AiAgent.values().length);
        assertSame(AiAgent.COPILOT, AiAgent.values()[0]);
        assertSame(AiAgent.AI_ASSISTANT, AiAgent.values()[1]);
    }
}
