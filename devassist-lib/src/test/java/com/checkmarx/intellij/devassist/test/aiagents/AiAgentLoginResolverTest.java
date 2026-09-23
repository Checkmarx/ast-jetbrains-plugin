package com.checkmarx.intellij.devassist.test.aiagents;

import com.checkmarx.intellij.common.resources.Resource;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.utils.Utils;
import com.checkmarx.intellij.devassist.aiagents.AiAgent;
import com.checkmarx.intellij.devassist.aiagents.AiAgentLoginResolver;
import com.checkmarx.intellij.devassist.aiagents.AiAgentResolution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AiAgentLoginResolver}.
 * <p>
 * Tests the resolution logic that picks an AI agent on successful login, including:
 * - Handling when no agents are installed
 * - Handling when a configured agent is still installed
 * - Handling when the configured agent is no longer installed (fallback logic)
 */
@DisplayName("AiAgentLoginResolver unit tests")
class AiAgentLoginResolverTest {

    // ===== Scenario: No agents installed =====

    @Test
    @DisplayName("resolve_NoAgentsInstalled_UsesVersionBasedDefaultAndReturnNotice")
    void resolve_NoAgentsInstalled_UsesVersionBasedDefaultAndReturnNotice() {
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);

        try (MockedStatic<AiAgent> agentMock = mockStatic(AiAgent.class);
             MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {

            agentMock.when(() -> AiAgent.installedAgents(any())).thenReturn(List.of());
            AiAgent defaultAgent = AiAgent.COPILOT;
            agentMock.when(AiAgent::computeVersionBasedDefault).thenReturn(defaultAgent);
            utilsMock.when(Utils::getPluginDisplayName).thenReturn("Test Plugin");

            AiAgentResolution resolution = AiAgentLoginResolver.resolve(null, mockState);

            assertNotNull(resolution);
            assertEquals(defaultAgent, resolution.getAgent());
            assertNotNull(resolution.getNoticeMessage());
            assertTrue(resolution.getNoticeMessage().contains("No AI agents"));
        }
    }

    @Test
    @DisplayName("resolve_NoAgentsInstalled_PersistsDefaultAgentToState")
    void resolve_NoAgentsInstalled_PersistsDefaultAgentToState() {
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);

        try (MockedStatic<AiAgent> agentMock = mockStatic(AiAgent.class);
             MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {

            agentMock.when(() -> AiAgent.installedAgents(any())).thenReturn(List.of());
            AiAgent defaultAgent = AiAgent.JETBRAINS_AI_ASSISTANT;
            agentMock.when(AiAgent::computeVersionBasedDefault).thenReturn(defaultAgent);
            utilsMock.when(Utils::getPluginDisplayName).thenReturn("Test Plugin");

            AiAgentLoginResolver.resolve(null, mockState);

            // Verify the state was updated with the default agent
            org.mockito.Mockito.verify(mockState).setAiAgent(defaultAgent.name());
        }
    }

    // ===== Scenario: Configured agent is still installed =====

    @Test
    @DisplayName("resolve_ConfiguredAgentInstalled_UsesCopilot")
    void resolve_ConfiguredAgentInstalled_UsesCopilot() {
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.getAiAgent()).thenReturn("COPILOT");

        try (MockedStatic<AiAgent> agentMock = mockStatic(AiAgent.class)) {

            agentMock.when(() -> AiAgent.installedAgents(any())).thenReturn(
                    List.of(AiAgent.COPILOT, AiAgent.JETBRAINS_AI_ASSISTANT));
            agentMock.when(() -> AiAgent.tryResolveConfigured("COPILOT"))
                    .thenCallRealMethod();

            AiAgentResolution resolution = AiAgentLoginResolver.resolve(null, mockState);

            assertNotNull(resolution);
            assertEquals(AiAgent.COPILOT, resolution.getAgent());
            assertNull(resolution.getNoticeMessage(), "No notice should be shown when agent is still installed");
        }
    }

    @Test
    @DisplayName("resolve_ConfiguredAgentInstalled_UsesJetBrainsAiAssistant")
    void resolve_ConfiguredAgentInstalled_UsesJetBrainsAiAssistant() {
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.getAiAgent()).thenReturn("JETBRAINS_AI_ASSISTANT");

        try (MockedStatic<AiAgent> agentMock = mockStatic(AiAgent.class)) {

            agentMock.when(() -> AiAgent.installedAgents(any())).thenReturn(
                    List.of(AiAgent.COPILOT, AiAgent.JETBRAINS_AI_ASSISTANT));
            agentMock.when(() -> AiAgent.tryResolveConfigured("JETBRAINS_AI_ASSISTANT"))
                    .thenCallRealMethod();

            AiAgentResolution resolution = AiAgentLoginResolver.resolve(null, mockState);

            assertNotNull(resolution);
            assertEquals(AiAgent.JETBRAINS_AI_ASSISTANT, resolution.getAgent());
            assertNull(resolution.getNoticeMessage());
        }
    }

    // ===== Scenario: Configured agent is not installed, fallback required =====

    @Test
    @DisplayName("resolve_ConfiguredAgentNotInstalled_FallsBackToHighestPreference")
    void resolve_ConfiguredAgentNotInstalled_FallsBackToHighestPreference() {
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.getAiAgent()).thenReturn("JETBRAINS_AI_ASSISTANT");

        try (MockedStatic<AiAgent> agentMock = mockStatic(AiAgent.class);
             MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {

            // Only Copilot is installed
            agentMock.when(() -> AiAgent.installedAgents(any())).thenReturn(
                    List.of(AiAgent.COPILOT));
            agentMock.when(() -> AiAgent.tryResolveConfigured("JETBRAINS_AI_ASSISTANT"))
                    .thenCallRealMethod();
            agentMock.when(() -> AiAgent.resolveBestInstalledAgent(any()))
                    .thenCallRealMethod();
            utilsMock.when(Utils::getPluginDisplayName).thenReturn("Test Plugin");

            AiAgentResolution resolution = AiAgentLoginResolver.resolve(null, mockState);

            assertNotNull(resolution);
            assertEquals(AiAgent.COPILOT, resolution.getAgent());
            assertNotNull(resolution.getNoticeMessage());
            assertTrue(resolution.getNoticeMessage().contains("switched"));
            assertTrue(resolution.getNoticeMessage().contains("JetBrains"));
        }
    }

    @Test
    @DisplayName("resolve_ConfiguredAgentNotInstalled_PersistsNewAgentToState")
    void resolve_ConfiguredAgentNotInstalled_PersistsNewAgentToState() {
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.getAiAgent()).thenReturn("JETBRAINS_AI_ASSISTANT");

        try (MockedStatic<AiAgent> agentMock = mockStatic(AiAgent.class);
             MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {

            agentMock.when(() -> AiAgent.installedAgents(any())).thenReturn(
                    List.of(AiAgent.COPILOT));
            agentMock.when(() -> AiAgent.tryResolveConfigured("JETBRAINS_AI_ASSISTANT"))
                    .thenCallRealMethod();
            agentMock.when(() -> AiAgent.resolveBestInstalledAgent(any()))
                    .thenCallRealMethod();
            utilsMock.when(Utils::getPluginDisplayName).thenReturn("Test Plugin");

            AiAgentLoginResolver.resolve(null, mockState);

            // Verify the state was updated with the fallback agent
            org.mockito.Mockito.verify(mockState).setAiAgent(AiAgent.COPILOT.name());
        }
    }

    @Test
    @DisplayName("resolve_ConfiguredAgentUnrecognized_DefaultsToCopilot")
    void resolve_ConfiguredAgentUnrecognized_DefaultsToCopilot() {
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.getAiAgent()).thenReturn("FUTURE_AGENT");

        try (MockedStatic<AiAgent> agentMock = mockStatic(AiAgent.class);
             MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {

            agentMock.when(() -> AiAgent.installedAgents(any())).thenReturn(
                    List.of(AiAgent.COPILOT));
            agentMock.when(() -> AiAgent.tryResolveConfigured("FUTURE_AGENT"))
                    .thenCallRealMethod();
            agentMock.when(() -> AiAgent.resolveBestInstalledAgent(any()))
                    .thenCallRealMethod();
            utilsMock.when(Utils::getPluginDisplayName).thenReturn("Test Plugin");

            AiAgentResolution resolution = AiAgentLoginResolver.resolve(null, mockState);

            assertNotNull(resolution);
            assertEquals(AiAgent.COPILOT, resolution.getAgent());
        }
    }

    // ===== Edge cases =====

    @Test
    @DisplayName("resolve_NullAiAgentInState_UsesVersionBasedDefault")
    void resolve_NullAiAgentInState_UsesVersionBasedDefault() {
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.getAiAgent()).thenReturn(null);

        try (MockedStatic<AiAgent> agentMock = mockStatic(AiAgent.class);
             MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {

            agentMock.when(() -> AiAgent.installedAgents(any())).thenReturn(
                    List.of(AiAgent.COPILOT));
            agentMock.when(() -> AiAgent.tryResolveConfigured(null))
                    .thenCallRealMethod();
            agentMock.when(() -> AiAgent.resolveBestInstalledAgent(any()))
                    .thenCallRealMethod();
            utilsMock.when(Utils::getPluginDisplayName).thenReturn("Test Plugin");

            AiAgentResolution resolution = AiAgentLoginResolver.resolve(null, mockState);

            assertNotNull(resolution);
            assertEquals(AiAgent.COPILOT, resolution.getAgent());
        }
    }

    @Test
    @DisplayName("resolve_BlankAiAgentInState_UsesVersionBasedDefault")
    void resolve_BlankAiAgentInState_UsesVersionBasedDefault() {
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.getAiAgent()).thenReturn("");

        try (MockedStatic<AiAgent> agentMock = mockStatic(AiAgent.class);
             MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {

            agentMock.when(() -> AiAgent.installedAgents(any())).thenReturn(
                    List.of(AiAgent.COPILOT));
            agentMock.when(() -> AiAgent.tryResolveConfigured(""))
                    .thenCallRealMethod();
            agentMock.when(() -> AiAgent.resolveBestInstalledAgent(any()))
                    .thenCallRealMethod();
            utilsMock.when(Utils::getPluginDisplayName).thenReturn("Test Plugin");

            AiAgentResolution resolution = AiAgentLoginResolver.resolve(null, mockState);

            assertNotNull(resolution);
            assertEquals(AiAgent.COPILOT, resolution.getAgent());
        }
    }
}
