package com.checkmarx.intellij.devassist.test.telemetry;

import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.utils.Constants;
import com.checkmarx.intellij.devassist.aiagents.AiAgent;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.telemetry.TelemetryService;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
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

    private static ScanIssue issue(ScanEngine engine, String severity) {
        ScanIssue scanIssue = new ScanIssue();
        scanIssue.setScanEngine(engine);
        scanIssue.setSeverity(severity);
        scanIssue.setFilePath("/project/file");
        return scanIssue;
    }

    // ===== getAIAgentProvider tests =====

    @Test
    @DisplayName("getAIAgentProvider_CopilotConfigured_ReturnsCopilot")
    void getAIAgentProvider_CopilotConfigured_ReturnsCopilot() throws Exception {
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.getAiAgent()).thenReturn("GITHUB_COPILOT");

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);

            String result = invokeGetAIAgentProvider();

            assertEquals("Copilot", result);
        }
    }

    @Test
    @DisplayName("getAIAgentProvider_UnknownValue_ReturnsDefaultCopilot")
    void getAIAgentProvider_UnknownValue_ReturnsDefaultCopilot() throws Exception {
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.getAiAgent()).thenReturn("COPILOT");

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);

            assertEquals("Copilot", invokeGetAIAgentProvider());
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

            // Same name as the Fix with AI event, not the enum name stored in the settings
            assertEquals("JetBrains AI Assistant", result);
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

    // ===== logUserAction integration tests =====

    @Test
    @DisplayName("logUserAction_WithCopilotAgent_IncludesAgentInTelemetry")
    void logUserAction_WithCopilotAgent_IncludesAgentInTelemetry() {
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.getAiAgent()).thenReturn("COPILOT");

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);

            assertDoesNotThrow(() ->
                    TelemetryService.logViewDetailsAction(issue(ScanEngine.OSS, "High")));
        }
    }

    @Test
    @DisplayName("logUserAction_WithJetBrainsAgent_IncludesAgentInTelemetry")
    void logUserAction_WithJetBrainsAgent_IncludesAgentInTelemetry() {
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.getAiAgent()).thenReturn("JETBRAINS_AI_ASSISTANT");

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);

            assertDoesNotThrow(() ->
                    TelemetryService.logViewDetailsAction(issue(ScanEngine.SECRETS, "Critical")));
        }
    }

    @Test
    @DisplayName("logUserAction_WithNoAgent_UsesDefaultCopilot")
    void logUserAction_WithNoAgent_UsesDefaultCopilot() {
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.getAiAgent()).thenReturn(null);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);

            assertDoesNotThrow(() ->
                    TelemetryService.logIgnorePackageAction(issue(ScanEngine.IAC, "Medium")));
        }
    }

    // ===== Edge case tests =====

    @Test
    @DisplayName("getAIAgentProvider_WhitespaceOnlyAiAgent_ReturnsDefaultCopilot")
    void getAIAgentProvider_WhitespaceOnlyAiAgent_ReturnsDefaultCopilot() throws Exception {
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.getAiAgent()).thenReturn("   ");

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);

            String result = invokeGetAIAgentProvider();

            assertEquals("Copilot", result);
        }
    }

    @Test
    @DisplayName("logScanFailure_WithCopilotAgent_DoesNotThrow")
    void logScanFailure_WithCopilotAgent_DoesNotThrow() {
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.getAiAgent()).thenReturn("COPILOT");

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);

            assertDoesNotThrow(() ->
                    TelemetryService.logScanFailure(ScanEngine.OSS, "/project/pom.xml", new RuntimeException("boom")));
        }
    }
}
