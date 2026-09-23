package com.checkmarx.intellij.devassist.test.aiagents;

import com.checkmarx.intellij.devassist.aiagents.ChatIntegrationResult;
import com.checkmarx.intellij.devassist.aiagents.copilot.CopilotChatIntegration;
import com.checkmarx.intellij.devassist.aiagents.copilot.CopilotIntegration;
import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CopilotChatIntegration}, the {@code ChatIntegration} adapter over the
 * static {@link CopilotIntegration} utility. This adapter is the piece that forwards Copilot's
 * real, possibly-delayed automation outcome to the caller's callback rather than assuming success
 * just because the chat window opened - these tests exercise that contract directly, independent
 * of the higher-level dispatch coverage in {@code RemediationManagerTest}.
 */
@DisplayName("CopilotChatIntegration unit tests")
class CopilotChatIntegrationTest {

    private final CopilotChatIntegration integration = new CopilotChatIntegration();

    @Test
    @DisplayName("isAvailable_DelegatesToCopilotIntegration")
    void isAvailable_DelegatesToCopilotIntegration() {
        Project project = mock(Project.class);
        try (MockedStatic<CopilotIntegration> copilotMock = mockStatic(CopilotIntegration.class)) {
            copilotMock.when(() -> CopilotIntegration.isCopilotAvailable(project)).thenReturn(true);

            assertTrue(integration.isAvailable(project));
            copilotMock.verify(() -> CopilotIntegration.isCopilotAvailable(project));
        }
    }

    @Test
    @DisplayName("openWithPrompt_ImmediateResult_MapsSuccessAndForwardsSameOutcomeToCallback")
    void openWithPrompt_ImmediateResult_MapsSuccessAndForwardsSameOutcomeToCallback() {
        Project project = mock(Project.class);
        CopilotIntegration.IntegrationResult openedResult = mock(CopilotIntegration.IntegrationResult.class);
        when(openedResult.isSuccess()).thenReturn(true);
        when(openedResult.getMessage()).thenReturn("Copilot chat opened, automation in progress...");

        try (MockedStatic<CopilotIntegration> copilotMock = mockStatic(CopilotIntegration.class)) {
            // Mimics CopilotIntegration's own early-exit paths, which invoke the callback with
            // the same result they return when there is no further async work.
            copilotMock.when(() -> CopilotIntegration.openCopilotWithPromptDetailed(eq("fix this"), eq(project), any()))
                    .thenAnswer(invocation -> {
                        @SuppressWarnings("unchecked")
                        Consumer<CopilotIntegration.IntegrationResult> callback = invocation.getArgument(2, Consumer.class);
                        callback.accept(openedResult);
                        return openedResult;
                    });

            List<ChatIntegrationResult> captured = new ArrayList<>();
            ChatIntegrationResult result = integration.openWithPrompt("fix this", project, captured::add);

            assertTrue(result.isSuccess());
            assertEquals("Copilot chat opened, automation in progress...", result.getMessage());
            assertEquals(1, captured.size());
            assertTrue(captured.get(0).isSuccess());
        }
    }

    @Test
    @DisplayName("openWithPrompt_ChatOpensButAsyncAutomationLaterFails_CallbackReceivesTheRealFailure")
    void openWithPrompt_ChatOpensButAsyncAutomationLaterFails_CallbackReceivesTheRealFailure() {
        Project project = mock(Project.class);
        CopilotIntegration.IntegrationResult openedResult = mock(CopilotIntegration.IntegrationResult.class);
        when(openedResult.isSuccess()).thenReturn(true);
        when(openedResult.getMessage()).thenReturn("Copilot chat opened, automation in progress...");

        CopilotIntegration.IntegrationResult automationFailedResult = mock(CopilotIntegration.IntegrationResult.class);
        when(automationFailedResult.isSuccess()).thenReturn(false);
        when(automationFailedResult.getMessage()).thenReturn("Automation failed, prompt copied to clipboard");

        try (MockedStatic<CopilotIntegration> copilotMock = mockStatic(CopilotIntegration.class)) {
            copilotMock.when(() -> CopilotIntegration.openCopilotWithPromptDetailed(eq("fix this"), eq(project), any()))
                    .thenAnswer(invocation -> {
                        @SuppressWarnings("unchecked")
                        Consumer<CopilotIntegration.IntegrationResult> callback = invocation.getArgument(2, Consumer.class);
                        // Simulate the background automation completing (with failure) strictly
                        // after the synchronous "opened" result has already been returned below.
                        callback.accept(automationFailedResult);
                        return openedResult;
                    });

            List<ChatIntegrationResult> captured = new ArrayList<>();
            ChatIntegrationResult result = integration.openWithPrompt("fix this", project, captured::add);

            // The immediate return only reflects "the chat opened" - true here.
            assertTrue(result.isSuccess());
            // The true final outcome, delivered via the callback, must reflect the real
            // automation failure - this is the whole point of wiring the callback through.
            assertEquals(1, captured.size());
            assertFalse(captured.get(0).isSuccess());
            assertEquals("Automation failed, prompt copied to clipboard", captured.get(0).getMessage());
        }
    }

    @Test
    @DisplayName("openWithPrompt_NullCallback_DoesNotThrowEvenWhenUnderlyingInvokesCallback")
    void openWithPrompt_NullCallback_DoesNotThrowEvenWhenUnderlyingInvokesCallback() {
        Project project = mock(Project.class);
        CopilotIntegration.IntegrationResult openedResult = mock(CopilotIntegration.IntegrationResult.class);
        when(openedResult.isSuccess()).thenReturn(false);
        when(openedResult.getMessage()).thenReturn("Copilot not available");

        try (MockedStatic<CopilotIntegration> copilotMock = mockStatic(CopilotIntegration.class)) {
            copilotMock.when(() -> CopilotIntegration.openCopilotWithPromptDetailed(eq("fix this"), eq(project), any()))
                    .thenAnswer(invocation -> {
                        @SuppressWarnings("unchecked")
                        Consumer<CopilotIntegration.IntegrationResult> callback = invocation.getArgument(2, Consumer.class);
                        callback.accept(openedResult);
                        return openedResult;
                    });

            ChatIntegrationResult result = assertDoesNotThrow(
                    () -> integration.openWithPrompt("fix this", project, null));

            assertFalse(result.isSuccess());
        }
    }
}
