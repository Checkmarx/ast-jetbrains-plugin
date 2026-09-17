package com.checkmarx.intellij.devassist.test.remediation;

import com.checkmarx.intellij.devassist.aiagents.ChatIntegrationResult;
import com.checkmarx.intellij.devassist.aiagents.aiassistant.AiAssistantChatIntegration;
import com.checkmarx.intellij.devassist.aiagents.aiassistant.AiAssistantIntegration;
import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AiAssistantChatIntegration}, the {@code ChatIntegration} adapter over the
 * static {@link AiAssistantIntegration} utility. Complements the higher-level, static-mocked
 * dispatch coverage in {@code RemediationManagerTest} by exercising this adapter's own
 * result-mapping and callback contract directly.
 */
@DisplayName("AiAssistantChatIntegration unit tests")
class AiAssistantChatIntegrationTest {

    private final AiAssistantChatIntegration integration = new AiAssistantChatIntegration();

    @Test
    @DisplayName("isAvailable_DelegatesToAiAssistantIntegration_True")
    void isAvailable_DelegatesToAiAssistantIntegration_True() {
        Project project = mock(Project.class);
        try (MockedStatic<AiAssistantIntegration> aiAssistantMock = mockStatic(AiAssistantIntegration.class)) {
            aiAssistantMock.when(() -> AiAssistantIntegration.isAiAssistantAvailable(project)).thenReturn(true);

            assertTrue(integration.isAvailable(project));
            aiAssistantMock.verify(() -> AiAssistantIntegration.isAiAssistantAvailable(project));
        }
    }

    @Test
    @DisplayName("isAvailable_DelegatesToAiAssistantIntegration_False")
    void isAvailable_DelegatesToAiAssistantIntegration_False() {
        try (MockedStatic<AiAssistantIntegration> aiAssistantMock = mockStatic(AiAssistantIntegration.class)) {
            aiAssistantMock.when(() -> AiAssistantIntegration.isAiAssistantAvailable(null)).thenReturn(false);

            assertFalse(integration.isAvailable(null));
        }
    }

    @Test
    @DisplayName("openWithPrompt_UnderlyingSucceeds_ReturnsSuccessAndInvokesCallbackOnceSynchronously")
    void openWithPrompt_UnderlyingSucceeds_ReturnsSuccessAndInvokesCallbackOnceSynchronously() {
        Project project = mock(Project.class);
        AiAssistantIntegration.IntegrationResult underlyingResult = mock(AiAssistantIntegration.IntegrationResult.class);
        when(underlyingResult.isSuccess()).thenReturn(true);
        when(underlyingResult.getMessage()).thenReturn("Prompt sent to AI Assistant");

        try (MockedStatic<AiAssistantIntegration> aiAssistantMock = mockStatic(AiAssistantIntegration.class)) {
            aiAssistantMock.when(() -> AiAssistantIntegration.openAiAssistantWithPromptDetailed("fix this", project))
                    .thenReturn(underlyingResult);

            List<ChatIntegrationResult> captured = new ArrayList<>();
            ChatIntegrationResult result = integration.openWithPrompt("fix this", project, captured::add);

            assertTrue(result.isSuccess());
            assertEquals("Prompt sent to AI Assistant", result.getMessage());
            // AiAssistantIntegration has no async completion signal of its own today, so the
            // adapter must treat its synchronous result as final and report it exactly once.
            assertEquals(1, captured.size());
            assertSame(result, captured.get(0));
        }
    }

    @Test
    @DisplayName("openWithPrompt_UnderlyingFails_ReturnsNotAvailableAndInvokesCallbackOnce")
    void openWithPrompt_UnderlyingFails_ReturnsNotAvailableAndInvokesCallbackOnce() {
        Project project = mock(Project.class);
        AiAssistantIntegration.IntegrationResult underlyingResult = mock(AiAssistantIntegration.IntegrationResult.class);
        when(underlyingResult.isSuccess()).thenReturn(false);
        when(underlyingResult.getMessage()).thenReturn("AI Assistant not installed");

        try (MockedStatic<AiAssistantIntegration> aiAssistantMock = mockStatic(AiAssistantIntegration.class)) {
            aiAssistantMock.when(() -> AiAssistantIntegration.openAiAssistantWithPromptDetailed("fix this", project))
                    .thenReturn(underlyingResult);

            List<ChatIntegrationResult> captured = new ArrayList<>();
            ChatIntegrationResult result = integration.openWithPrompt("fix this", project, captured::add);

            assertFalse(result.isSuccess());
            assertEquals("AI Assistant not installed", result.getMessage());
            assertEquals(1, captured.size());
            assertFalse(captured.get(0).isSuccess());
        }
    }

    @Test
    @DisplayName("openWithPrompt_NullCallback_DoesNotThrow")
    void openWithPrompt_NullCallback_DoesNotThrow() {
        Project project = mock(Project.class);
        AiAssistantIntegration.IntegrationResult underlyingResult = mock(AiAssistantIntegration.IntegrationResult.class);
        when(underlyingResult.isSuccess()).thenReturn(true);
        when(underlyingResult.getMessage()).thenReturn("ok");

        try (MockedStatic<AiAssistantIntegration> aiAssistantMock = mockStatic(AiAssistantIntegration.class)) {
            aiAssistantMock.when(() -> AiAssistantIntegration.openAiAssistantWithPromptDetailed("fix this", project))
                    .thenReturn(underlyingResult);

            ChatIntegrationResult result = assertDoesNotThrow(
                    () -> integration.openWithPrompt("fix this", project, null));

            assertTrue(result.isSuccess());
        }
    }
}
