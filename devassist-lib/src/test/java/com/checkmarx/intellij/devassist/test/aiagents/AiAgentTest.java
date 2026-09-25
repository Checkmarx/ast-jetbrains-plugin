package com.checkmarx.intellij.devassist.test.aiagents;

import com.checkmarx.intellij.devassist.configuration.mcp.McpAgentTarget;
import com.checkmarx.intellij.devassist.aiagents.AiAgent;
import com.checkmarx.intellij.devassist.aiagents.ChatIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
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
        assertEquals(AiAgent.GITHUB_COPILOT, AiAgent.fromSettingsValue(null));
    }

    @Test
    @DisplayName("fromSettingsValue_Blank_DefaultsToCopilot")
    void fromSettingsValue_Blank_DefaultsToCopilot() {
        assertEquals(AiAgent.GITHUB_COPILOT, AiAgent.fromSettingsValue(""));
    }

    @Test
    @DisplayName("fromSettingsValue_UnknownValue_DefaultsToCopilot")
    void fromSettingsValue_UnknownValue_DefaultsToCopilot() {
        assertEquals(AiAgent.GITHUB_COPILOT, AiAgent.fromSettingsValue("SOME_FUTURE_AGENT"));
    }

    @Test
    @DisplayName("fromSettingsValue_LowercaseEnumName_DefaultsToCopilot")
    void fromSettingsValue_LowercaseEnumName_DefaultsToCopilot() {
        // valueOf is case-sensitive; a lowercase persisted value must not silently match
        // AI_ASSISTANT and must fall back to the documented default instead of throwing.
        assertEquals(AiAgent.GITHUB_COPILOT, AiAgent.fromSettingsValue("ai_assistant"));
    }

    @Test
    @DisplayName("fromSettingsValue_ExactCopilotName_ResolvesToCopilot")
    void fromSettingsValue_ExactCopilotName_ResolvesToCopilot() {
        assertEquals(AiAgent.GITHUB_COPILOT, AiAgent.fromSettingsValue("COPILOT"));
    }

    @Test
    @DisplayName("fromSettingsValue_ExactAiAssistantName_ResolvesToAiAssistant")
    void fromSettingsValue_ExactAiAssistantName_ResolvesToAiAssistant() {
        assertEquals(AiAgent.JETBRAINS_AI_ASSISTANT, AiAgent.fromSettingsValue("JETBRAINS_AI_ASSISTANT"));
    }

    // ===== fromAgentName =====

    @Test
    @DisplayName("fromAgentName_Null_DefaultsToCopilot")
    void fromAgentName_Null_DefaultsToCopilot() {
        assertEquals(AiAgent.GITHUB_COPILOT, AiAgent.fromAgentName(null));
    }

    @Test
    @DisplayName("fromAgentName_UnknownLabel_DefaultsToCopilot")
    void fromAgentName_UnknownLabel_DefaultsToCopilot() {
        assertEquals(AiAgent.GITHUB_COPILOT, AiAgent.fromAgentName("Some Future Agent"));
    }

    @Test
    @DisplayName("fromAgentName_ExactCopilotLabel_ResolvesToCopilot")
    void fromAgentName_ExactCopilotLabel_ResolvesToCopilot() {
        assertEquals(AiAgent.GITHUB_COPILOT, AiAgent.fromAgentName(AiAgent.GITHUB_COPILOT.getAgentName()));
    }

    @Test
    @DisplayName("fromAgentName_ExactAiAssistantLabel_ResolvesToAiAssistant")
    void fromAgentName_ExactAiAssistantLabel_ResolvesToAiAssistant() {
        assertEquals(AiAgent.JETBRAINS_AI_ASSISTANT, AiAgent.fromAgentName(AiAgent.JETBRAINS_AI_ASSISTANT.getAgentName()));
    }

    @Test
    @DisplayName("fromAgentName_MixedCaseLabel_ResolvesCaseInsensitively")
    void fromAgentName_MixedCaseLabel_ResolvesCaseInsensitively() {
        assertEquals(AiAgent.JETBRAINS_AI_ASSISTANT, AiAgent.fromAgentName("jetbrains ai assistant"));
        assertEquals(AiAgent.JETBRAINS_AI_ASSISTANT, AiAgent.fromAgentName("JETBRAINS AI ASSISTANT"));
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
        ChatIntegration copilot = AiAgent.GITHUB_COPILOT.chatIntegration();
        ChatIntegration aiAssistant = AiAgent.JETBRAINS_AI_ASSISTANT.chatIntegration();

        assertNotNull(copilot);
        assertNotNull(aiAssistant);
        assertNotSame(copilot.getClass(), aiAssistant.getClass());
    }

    @Test
    @DisplayName("mcpTarget_ReturnsNonNullDistinctImplementationsPerAgent")
    void mcpTarget_ReturnsNonNullDistinctImplementationsPerAgent() {
        McpAgentTarget copilot = AiAgent.GITHUB_COPILOT.mcpTarget();
        McpAgentTarget aiAssistant = AiAgent.JETBRAINS_AI_ASSISTANT.mcpTarget();

        assertNotNull(copilot);
        assertNotNull(aiAssistant);
        assertNotSame(copilot.getClass(), aiAssistant.getClass());
    }

    @Test
    @DisplayName("mcpTarget_AiAssistantExposesSettingsPage_CopilotDoesNot")
    void mcpTarget_AiAssistantExposesSettingsPage_CopilotDoesNot() {
        assertEquals(true, AiAgent.JETBRAINS_AI_ASSISTANT.mcpTarget().getSettingsConfigurableId().isPresent());
        assertEquals(true, AiAgent.GITHUB_COPILOT.mcpTarget().getSettingsConfigurableId().isEmpty());
    }

    @Test
    @DisplayName("values_ContainsExactlyCopilotAndAiAssistant")
    void values_ContainsExactlyCopilotAndAiAssistant() {
        assertEquals(2, AiAgent.values().length);
        assertSame(AiAgent.GITHUB_COPILOT, AiAgent.values()[0]);
        assertSame(AiAgent.JETBRAINS_AI_ASSISTANT, AiAgent.values()[1]);
    }

    // ===== preferenceOrder tests =====

    @Test
    @DisplayName("preferenceOrder_DoesNotThrow")
    void preferenceOrder_DoesNotThrow() {
        assertDoesNotThrow(() -> AiAgent.preferenceOrder());
    }

    @Test
    @DisplayName("preferenceOrder_IsConsistent")
    void preferenceOrder_IsConsistent() {
        List<AiAgent> order1 = AiAgent.preferenceOrder();
        List<AiAgent> order2 = AiAgent.preferenceOrder();
        assertEquals(order1, order2, "Preference order should be consistent across calls");
    }

    // ===== computeVersionBasedDefault tests =====

    @Test
    @DisplayName("computeVersionBasedDefault_DoesNotThrow")
    void computeVersionBasedDefault_DoesNotThrow() {
        assertDoesNotThrow(() -> AiAgent.computeVersionBasedDefault());
    }

    @Test
    @DisplayName("computeVersionBasedDefault_ReturnsNonNull")
    void computeVersionBasedDefault_ReturnsNonNull() {
        AiAgent defaultAgent = AiAgent.computeVersionBasedDefault();
        assertNotNull(defaultAgent, "Default agent should never be null");
    }

    @Test
    @DisplayName("computeVersionBasedDefault_IsValidEnumValue")
    void computeVersionBasedDefault_IsValidEnumValue() {
        AiAgent defaultAgent = AiAgent.computeVersionBasedDefault();
        assertTrue(defaultAgent == AiAgent.GITHUB_COPILOT || defaultAgent == AiAgent.JETBRAINS_AI_ASSISTANT);
    }

    // ===== resolveBestInstalledAgent tests =====

    @Test
    @DisplayName("resolveBestInstalledAgent_EmptyList_ReturnsEmpty")
    void resolveBestInstalledAgent_EmptyList_ReturnsEmpty() {
        Optional<AiAgent> result = AiAgent.resolveBestInstalledAgent(List.of());
        assertTrue(result.isEmpty(), "Should return empty when no agents are installed");
    }

    @Test
    @DisplayName("resolveBestInstalledAgent_SingleAgentCopilot_ReturnsCopilot")
    void resolveBestInstalledAgent_SingleAgentCopilot_ReturnsCopilot() {
        Optional<AiAgent> result = AiAgent.resolveBestInstalledAgent(List.of(AiAgent.GITHUB_COPILOT));
        assertTrue(result.isPresent(), "Should return present when Copilot is installed");
        assertEquals(AiAgent.GITHUB_COPILOT, result.get());
    }

    @Test
    @DisplayName("resolveBestInstalledAgent_SingleAgentJetBrains_ReturnsJetBrains")
    void resolveBestInstalledAgent_SingleAgentJetBrains_ReturnsJetBrains() {
        Optional<AiAgent> result = AiAgent.resolveBestInstalledAgent(List.of(AiAgent.JETBRAINS_AI_ASSISTANT));
        assertTrue(result.isPresent(), "Should return present when JetBrains AI Assistant is installed");
        assertEquals(AiAgent.JETBRAINS_AI_ASSISTANT, result.get());
    }

    @Test
    @DisplayName("resolveBestInstalledAgent_BothAgents_ReturnsOneOfThem")
    void resolveBestInstalledAgent_BothAgents_ReturnsOneOfThem() {
        List<AiAgent> installed = List.of(AiAgent.GITHUB_COPILOT, AiAgent.JETBRAINS_AI_ASSISTANT);
        Optional<AiAgent> result = AiAgent.resolveBestInstalledAgent(installed);
        assertTrue(result.isPresent(), "Should return an agent when both are available");
        assertTrue(result.get() == AiAgent.GITHUB_COPILOT || result.get() == AiAgent.JETBRAINS_AI_ASSISTANT);
    }

    // ===== tryResolveConfigured tests =====

    @Test
    @DisplayName("tryResolveConfigured_Null_ReturnsEmpty")
    void tryResolveConfigured_Null_ReturnsEmpty() {
        Optional<AiAgent> result = AiAgent.tryResolveConfigured(null);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("tryResolveConfigured_Blank_ReturnsEmpty")
    void tryResolveConfigured_Blank_ReturnsEmpty() {
        Optional<AiAgent> result = AiAgent.tryResolveConfigured("");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("tryResolveConfigured_UnknownValue_ReturnsEmpty")
    void tryResolveConfigured_UnknownValue_ReturnsEmpty() {
        Optional<AiAgent> result = AiAgent.tryResolveConfigured("FUTURE_AGENT");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("tryResolveConfigured_LowercaseEnumName_ReturnsEmpty")
    void tryResolveConfigured_LowercaseEnumName_ReturnsEmpty() {
        Optional<AiAgent> result = AiAgent.tryResolveConfigured("copilot");
        assertTrue(result.isEmpty(), "valueOf is case-sensitive");
    }

    @Test
    @DisplayName("tryResolveConfigured_ExactCopilot_ReturnsCopilot")
    void tryResolveConfigured_ExactCopilot_ReturnsCopilot() {
        Optional<AiAgent> result = AiAgent.tryResolveConfigured("COPILOT");
        assertTrue(result.isPresent());
        assertEquals(AiAgent.GITHUB_COPILOT, result.get());
    }

    @Test
    @DisplayName("tryResolveConfigured_ExactJetBrains_ReturnsJetBrains")
    void tryResolveConfigured_ExactJetBrains_ReturnsJetBrains() {
        Optional<AiAgent> result = AiAgent.tryResolveConfigured("JETBRAINS_AI_ASSISTANT");
        assertTrue(result.isPresent());
        assertEquals(AiAgent.JETBRAINS_AI_ASSISTANT, result.get());
    }

    // ===== installedAgents tests =====

    @Test
    @DisplayName("installedAgents_ReturnsListNeverNull")
    void installedAgents_ReturnsListNeverNull() {
        List<AiAgent> installed = AiAgent.installedAgents(null);
        assertNotNull(installed, "installedAgents should return a non-null list");
    }

    @Test
    @DisplayName("installedAgents_ReturnsOnlyValidAgents")
    void installedAgents_ReturnsOnlyValidAgents() {
        List<AiAgent> installed = AiAgent.installedAgents(null);
        for (AiAgent agent : installed) {
            assertTrue(agent == AiAgent.GITHUB_COPILOT || agent == AiAgent.JETBRAINS_AI_ASSISTANT,
                    "Result should only contain known agents");
        }
    }

    @Test
    @DisplayName("installedAgents_ResultSizeIsNonNegative")
    void installedAgents_ResultSizeIsNonNegative() {
        List<AiAgent> installed = AiAgent.installedAgents(null);
        assertTrue(installed.size() >= 0);
        assertTrue(installed.size() <= AiAgent.values().length,
                "Result size should not exceed enum size");
    }

    // ===== Additional fromAgentName case-sensitivity tests =====

    @Test
    @DisplayName("fromAgentName_CopilotLabel_ReturnsCopilot")
    void fromAgentName_CopilotLabel_ReturnsCopilot() {
        String label = AiAgent.GITHUB_COPILOT.getAgentName();
        assertEquals(AiAgent.GITHUB_COPILOT, AiAgent.fromAgentName(label));
    }

    @Test
    @DisplayName("fromAgentName_JetBrainsLabel_ReturnsJetBrains")
    void fromAgentName_JetBrainsLabel_ReturnsJetBrains() {
        String label = AiAgent.JETBRAINS_AI_ASSISTANT.getAgentName();
        assertEquals(AiAgent.JETBRAINS_AI_ASSISTANT, AiAgent.fromAgentName(label));
    }

    @Test
    @DisplayName("fromAgentName_LowercaseLabel_ResolvesCorrectly")
    void fromAgentName_LowercaseLabel_ResolvesCorrectly() {
        String label = AiAgent.JETBRAINS_AI_ASSISTANT.getAgentName().toLowerCase();
        assertEquals(AiAgent.JETBRAINS_AI_ASSISTANT, AiAgent.fromAgentName(label));
    }
}
