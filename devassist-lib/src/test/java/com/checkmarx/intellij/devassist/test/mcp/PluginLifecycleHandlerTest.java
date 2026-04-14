package com.checkmarx.intellij.devassist.test.mcp;

import com.checkmarx.intellij.common.settings.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.devassist.configuration.mcp.McpSettingsInjector;
import com.checkmarx.intellij.devassist.configuration.mcp.PluginLifecycleHandler;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PluginLifecycleHandler}.
 * Covers auth session cleanup and MCP removal on plugin uninstall lifecycle events.
 */
class PluginLifecycleHandlerTest {

    private GlobalSettingsState mockGlobalState;
    private GlobalSettingsSensitiveState mockSensitiveState;
    private MockedStatic<GlobalSettingsState> mockedGlobalState;
    private MockedStatic<GlobalSettingsSensitiveState> mockedSensitiveState;

    @BeforeEach
    void setUp() {
        mockGlobalState = mock(GlobalSettingsState.class);
        mockSensitiveState = mock(GlobalSettingsSensitiveState.class);
        mockedGlobalState = mockStatic(GlobalSettingsState.class);
        mockedSensitiveState = mockStatic(GlobalSettingsSensitiveState.class);
        mockedGlobalState.when(GlobalSettingsState::getInstance).thenReturn(mockGlobalState);
        mockedSensitiveState.when(GlobalSettingsSensitiveState::getInstance).thenReturn(mockSensitiveState);
    }

    @AfterEach
    void tearDown() {
        mockedGlobalState.close();
        mockedSensitiveState.close();
    }

    @Test
    @DisplayName("beforePluginUnload_PluginUpdate_SkipsCleanup")
    void testBeforePluginUnload_PluginUpdate_SkipsCleanup() {
        // When isUpdate=true, neither auth nor MCP cleanup should run
        IdeaPluginDescriptor descriptor = mockDescriptor("com.checkmarx.checkmarx-ast-jetbrains-plugin");
        PluginLifecycleHandler handler = new PluginLifecycleHandler();

        assertDoesNotThrow(() -> handler.beforePluginUnload(descriptor, true));

        verify(mockGlobalState, never()).setAuthenticated(anyBoolean());
        verify(mockSensitiveState, never()).deleteRefreshToken();
    }

    @Test
    @DisplayName("beforePluginUnload_CheckmarxAstUninstall_ClearsAuthAndRemovesMcp")
    void testBeforePluginUnload_CheckmarxAstUninstall_ClearsAuthAndRemovesMcp() {
        IdeaPluginDescriptor descriptor = mockDescriptor("com.checkmarx.checkmarx-ast-jetbrains-plugin");
        PluginLifecycleHandler handler = new PluginLifecycleHandler();

        try (MockedStatic<McpSettingsInjector> mockedInjector = mockStatic(McpSettingsInjector.class)) {
            mockedInjector.when(McpSettingsInjector::uninstallFromCopilot).thenReturn(true);

            assertDoesNotThrow(() -> handler.beforePluginUnload(descriptor, false));

            verify(mockGlobalState).setAuthenticated(false);
            verify(mockSensitiveState).deleteRefreshToken();
            mockedInjector.verify(McpSettingsInjector::uninstallFromCopilot);
        }
    }

    @Test
    @DisplayName("beforePluginUnload_DevAssistUninstall_ClearsAuthAndRemovesMcp")
    void testBeforePluginUnload_DevAssistUninstall_ClearsAuthAndRemovesMcp() {
        IdeaPluginDescriptor descriptor = mockDescriptor("com.checkmarx.devassist-jetbrains-plugin");
        PluginLifecycleHandler handler = new PluginLifecycleHandler();

        try (MockedStatic<McpSettingsInjector> mockedInjector = mockStatic(McpSettingsInjector.class)) {
            mockedInjector.when(McpSettingsInjector::uninstallFromCopilot).thenReturn(true);

            assertDoesNotThrow(() -> handler.beforePluginUnload(descriptor, false));

            verify(mockGlobalState).setAuthenticated(false);
            verify(mockSensitiveState).deleteRefreshToken();
            mockedInjector.verify(McpSettingsInjector::uninstallFromCopilot);
        }
    }

    @Test
    @DisplayName("beforePluginUnload_UnknownPlugin_DoesNothing")
    void testBeforePluginUnload_UnknownPlugin_DoesNothing() {
        // Uninstalling a plugin that is not a Checkmarx plugin should not trigger any cleanup
        IdeaPluginDescriptor descriptor = mockDescriptor("com.some.other.plugin");
        PluginLifecycleHandler handler = new PluginLifecycleHandler();

        try (MockedStatic<McpSettingsInjector> mockedInjector = mockStatic(McpSettingsInjector.class)) {
            assertDoesNotThrow(() -> handler.beforePluginUnload(descriptor, false));

            verify(mockGlobalState, never()).setAuthenticated(anyBoolean());
            verify(mockSensitiveState, never()).deleteRefreshToken();
            mockedInjector.verify(McpSettingsInjector::uninstallFromCopilot, never());
        }
    }

    @Test
    @DisplayName("clearAuthSession_AuthClearedAndRefreshTokenDeleted")
    void testClearAuthSession_AuthClearedAndRefreshTokenDeleted() {
        // Regardless of current auth state, authenticated must be set to false
        // and the OAuth refresh token must be removed from PasswordSafe
        PluginLifecycleHandler handler = new PluginLifecycleHandler();

        handler.clearAuthSession();

        verify(mockGlobalState).setAuthenticated(false);
        verify(mockSensitiveState).deleteRefreshToken();
    }

    @Test
    @DisplayName("clearAuthSession_NullServices_HandlesGracefully")
    void testClearAuthSession_NullServices_HandlesGracefully() {
        // If services are unavailable (e.g. during early teardown), no exception should propagate
        mockedGlobalState.when(GlobalSettingsState::getInstance).thenReturn(null);
        mockedSensitiveState.when(GlobalSettingsSensitiveState::getInstance).thenReturn(null);

        PluginLifecycleHandler handler = new PluginLifecycleHandler();

        assertDoesNotThrow(handler::clearAuthSession);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private IdeaPluginDescriptor mockDescriptor(String pluginId) {
        IdeaPluginDescriptor descriptor = mock(IdeaPluginDescriptor.class);
        PluginId id = mock(PluginId.class);
        when(descriptor.getPluginId()).thenReturn(id);
        when(id.getIdString()).thenReturn(pluginId);
        return descriptor;
    }
}

