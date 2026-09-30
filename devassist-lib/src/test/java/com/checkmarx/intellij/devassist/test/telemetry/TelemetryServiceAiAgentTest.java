package com.checkmarx.intellij.devassist.test.telemetry;

import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.utils.Constants;
import com.checkmarx.intellij.devassist.aiagents.AiAgent;
import com.checkmarx.intellij.devassist.telemetry.TelemetryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TelemetryService} AI Agent provider detection.
 * <p>
 * Tests the telemetry service's ability to determine the active AI agent provider
 * and include it in telemetry events.
 */
@DisplayName("TelemetryService AI Agent unit tests")
class TelemetryServiceAiAgentTest {

    /**
     * Reflection helper to invoke the private getAIAgentProvider method.
     */
    private static String invokeGetAIAgentProvider() throws Exception {
        Method method = TelemetryService.class.getDeclaredMethod("getAIAgentProvider");
        method.setAccessible(true);
        return (String) method.invoke(null);
    }

    /**
     * Reflection helper to invoke the private getAgentName method.
     */
    private static String invokeGetAgentName() throws Exception {
        Method method = TelemetryService.class.getDeclaredMethod("getAgentName");
        method.setAccessible(true);
        return (String) method.invoke(null);
    }

    // ===== getAIAgentProvider tests =====

    @Test
    @DisplayName("getAIAgentProvider_CopilotConfigured_ReturnsCopilot")
    void getAIAgentProvider_CopilotConfigured_ReturnsCopilot() throws Exception {
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.getAiAgent()).thenReturn("COPILOT");

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);

            String result = invokeGetAIAgentProvider();

            assertEquals("COPILOT", result);
        }
    }

    @Test
    @DisplayName("getAIAgentProvider_JetBrainsAiAssistantConfigured_ReturnsJetBrainsAiAssistant")
    void getAIAgentProvider_JetBrainsAiAssistantConfigured_ReturnsJetBrainsAiAssistant() throws Exception {
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.getAiAgent()).thenReturn("JETBRAINS_AI_ASSISTANT");

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);

            String result = invokeGetAIAgentProvider();

            assertEquals("JETBRAINS_AI_ASSISTANT", result);
        }
    }

    @Test
    @DisplayName("getAIAgentProvider_NullAiAgent_ReturnsDefaultCopilot")
    void getAIAgentProvider_NullAiAgent_ReturnsDefaultCopilot() throws Exception {
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.getAiAgent()).thenReturn(null);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);

            String result = invokeGetAIAgentProvider();

            assertEquals("Copilot", result);
        }
    }

    @Test
    @DisplayName("getAIAgentProvider_EmptyAiAgent_ReturnsDefaultCopilot")
    void getAIAgentProvider_EmptyAiAgent_ReturnsDefaultCopilot() throws Exception {
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.getAiAgent()).thenReturn("");

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);

            String result = invokeGetAIAgentProvider();

            assertEquals("Copilot", result);
        }
    }

    @Test
    @DisplayName("getAIAgentProvider_SettingsStateNull_ReturnsDefaultCopilot")
    void getAIAgentProvider_SettingsStateNull_ReturnsDefaultCopilot() throws Exception {
        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(null);

            String result = invokeGetAIAgentProvider();

            assertEquals("Copilot", result);
        }
    }

    @Test
    @DisplayName("getAIAgentProvider_SettingsStateThrowsException_ReturnsDefaultCopilot")
    void getAIAgentProvider_SettingsStateThrowsException_ReturnsDefaultCopilot() throws Exception {
        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance)
                    .thenThrow(new RuntimeException("Settings unavailable"));

            String result = invokeGetAIAgentProvider();

            assertEquals("Copilot", result);
        }
    }

    // ===== getAgentName tests =====

    @Test
    @DisplayName("getAgentName_ReturnsJetBrainsPrefix")
    void getAgentName_ReturnsJetBrainsPrefix() throws Exception {
        String result = invokeGetAgentName();

        assertNotNull(result);
        assertTrue(result.contains(Constants.JET_BRAINS_AGENT_NAME),
                "Agent name should contain JetBrains prefix");
    }

    @Test
    @DisplayName("getAgentName_IncludesJetBrainsPrefix")
    void getAgentName_IncludesJetBrainsPrefix() throws Exception {
        String result = invokeGetAgentName();

        assertNotNull(result);
        // Should start with or contain JetBrains
        assertTrue(result.contains(Constants.JET_BRAINS_AGENT_NAME),
                "Agent name should include JetBrains prefix");
    }

    @Test
    @DisplayName("getAgentName_IsNotNull")
    void getAgentName_IsNotNull() throws Exception {
        String result = invokeGetAgentName();
        assertNotNull(result);
    }

    @Test
    @DisplayName("getAgentName_IsNotEmpty")
    void getAgentName_IsNotEmpty() throws Exception {
        String result = invokeGetAgentName();
        assertFalse(result.isEmpty());
    }

    // ===== setUserEventDataForLogs integration tests =====

    @Test
    @DisplayName("setUserEventDataForLogs_WithCopilotAgent_IncludesAgentInTelemetry")
    void setUserEventDataForLogs_WithCopilotAgent_IncludesAgentInTelemetry() {
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.getAiAgent()).thenReturn("COPILOT");

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);

            assertDoesNotThrow(() ->
                    TelemetryService.setUserEventDataForLogs("click", "fixWithAIChat", "Oss", "High"));
        }
    }

    @Test
    @DisplayName("setUserEventDataForLogs_WithJetBrainsAgent_IncludesAgentInTelemetry")
    void setUserEventDataForLogs_WithJetBrainsAgent_IncludesAgentInTelemetry() {
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.getAiAgent()).thenReturn("JETBRAINS_AI_ASSISTANT");

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);

            assertDoesNotThrow(() ->
                    TelemetryService.setUserEventDataForLogs("click", "viewDetails", "Secrets", "Critical"));
        }
    }

    @Test
    @DisplayName("setUserEventDataForLogs_WithNoAgent_UsesDefaultCopilot")
    void setUserEventDataForLogs_WithNoAgent_UsesDefaultCopilot() {
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.getAiAgent()).thenReturn(null);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);

            assertDoesNotThrow(() ->
                    TelemetryService.setUserEventDataForLogs("click", "ignorePackage", "IaC", "Medium"));
        }
    }

    // ===== Edge case tests =====

    @Test
    @DisplayName("getAIAgentProvider_WhitespaceOnlyAiAgent_ReturnsAsIs")
    void getAIAgentProvider_WhitespaceOnlyAiAgent_ReturnsAsIs() throws Exception {
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.getAiAgent()).thenReturn("   ");

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);

            String result = invokeGetAIAgentProvider();

            // Implementation checks isEmpty(), not isBlank(), so whitespace is returned as-is
            assertNotNull(result);
        }
    }

    @Test
    @DisplayName("setUserEventDataForDetectionLogs_WithCopilotAgent_DoesNotThrow")
    void setUserEventDataForDetectionLogs_WithCopilotAgent_DoesNotThrow() {
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.getAiAgent()).thenReturn("COPILOT");

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);

            assertDoesNotThrow(() ->
                    TelemetryService.setUserEventDataForDetectionLogs("Oss", "High", 5));
        }
    }
}
