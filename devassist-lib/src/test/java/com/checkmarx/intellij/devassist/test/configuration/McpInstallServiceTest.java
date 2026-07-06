package com.checkmarx.intellij.devassist.test.configuration;

import com.checkmarx.intellij.common.commands.TenantSetting;
import com.checkmarx.intellij.common.settings.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.devassist.configuration.mcp.McpInstallService;
import com.checkmarx.intellij.devassist.configuration.mcp.McpSettingsInjector;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Constructor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class McpInstallServiceTest {

    @Mock
    private Project mockProject;

    private McpInstallService createInstance() throws Exception {
        Constructor<McpInstallService> c = McpInstallService.class.getDeclaredConstructor();
        c.setAccessible(true);
        return c.newInstance();
    }

    /** Creates a synchronous executor; must be called OUTSIDE MockedStatic try blocks. */
    private ExecutorService directExecutor() {
        ExecutorService exec = mock(ExecutorService.class);
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(exec).execute(any(Runnable.class));
        return exec;
    }

    // ---- installSilentlyAsync ----

    @Test
    void installSilentlyAsync_nullCredential_returnsFalse() throws Exception {
        CompletableFuture<Boolean> future = McpInstallService.installSilentlyAsync(null);
        assertEquals(Boolean.FALSE, future.get(5, TimeUnit.SECONDS));
    }

    @Test
    void installSilentlyAsync_blankCredential_returnsFalse() throws Exception {
        CompletableFuture<Boolean> future = McpInstallService.installSilentlyAsync("   ");
        assertEquals(Boolean.FALSE, future.get(5, TimeUnit.SECONDS));
    }

    @Test
    void installSilentlyAsync_validCredential_callsInstallForCopilot() throws Exception {
        ExecutorService exec = directExecutor();
        try (MockedStatic<McpSettingsInjector> injStatic = mockStatic(McpSettingsInjector.class);
             MockedStatic<AppExecutorUtil> execStatic = mockStatic(AppExecutorUtil.class)) {

            injStatic.when(() -> McpSettingsInjector.installForCopilot("my-token")).thenReturn(true);
            execStatic.when(AppExecutorUtil::getAppExecutorService).thenReturn(exec);

            CompletableFuture<Boolean> future = McpInstallService.installSilentlyAsync("my-token");
            Boolean result = future.get(5, TimeUnit.SECONDS);

            assertEquals(Boolean.TRUE, result);
            injStatic.verify(() -> McpSettingsInjector.installForCopilot("my-token"));
        }
    }

    @Test
    void installSilentlyAsync_injectorThrows_returnsNull() throws Exception {
        ExecutorService exec = directExecutor();
        try (MockedStatic<McpSettingsInjector> injStatic = mockStatic(McpSettingsInjector.class);
             MockedStatic<AppExecutorUtil> execStatic = mockStatic(AppExecutorUtil.class)) {

            injStatic.when(() -> McpSettingsInjector.installForCopilot(anyString()))
                    .thenThrow(new RuntimeException("install failed"));
            execStatic.when(AppExecutorUtil::getAppExecutorService).thenReturn(exec);

            CompletableFuture<Boolean> future = McpInstallService.installSilentlyAsync("my-token");
            Boolean result = future.get(5, TimeUnit.SECONDS);

            assertNull(result);
        }
    }

    // ---- runActivity ----

    @Test
    void runActivity_notAuthenticated_skips() throws Exception {
        McpInstallService service = createInstance();
        try (MockedStatic<GlobalSettingsState> gsStatic = mockStatic(GlobalSettingsState.class);
             MockedStatic<GlobalSettingsSensitiveState> gssStatic = mockStatic(GlobalSettingsSensitiveState.class);
             MockedStatic<McpSettingsInjector> injStatic = mockStatic(McpSettingsInjector.class)) {

            GlobalSettingsState mockState = mock(GlobalSettingsState.class);
            GlobalSettingsSensitiveState mockSensitive = mock(GlobalSettingsSensitiveState.class);
            gsStatic.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            gssStatic.when(GlobalSettingsSensitiveState::getInstance).thenReturn(mockSensitive);
            when(mockState.isAuthenticated()).thenReturn(false);

            service.runActivity(mockProject);

            injStatic.verify(() -> McpSettingsInjector.installForCopilot(anyString()), never());
        }
    }

    @Test
    void runActivity_aiMcpDisabled_skips() throws Exception {
        McpInstallService service = createInstance();
        try (MockedStatic<GlobalSettingsState> gsStatic = mockStatic(GlobalSettingsState.class);
             MockedStatic<GlobalSettingsSensitiveState> gssStatic = mockStatic(GlobalSettingsSensitiveState.class);
             MockedStatic<TenantSetting> tenantStatic = mockStatic(TenantSetting.class);
             MockedStatic<McpSettingsInjector> injStatic = mockStatic(McpSettingsInjector.class)) {

            GlobalSettingsState mockState = mock(GlobalSettingsState.class);
            GlobalSettingsSensitiveState mockSensitive = mock(GlobalSettingsSensitiveState.class);
            gsStatic.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            gssStatic.when(GlobalSettingsSensitiveState::getInstance).thenReturn(mockSensitive);
            when(mockState.isAuthenticated()).thenReturn(true);
            tenantStatic.when(() -> TenantSetting.isAiMcpServerEnabled(mockState, mockSensitive)).thenReturn(false);

            service.runActivity(mockProject);

            injStatic.verify(() -> McpSettingsInjector.installForCopilot(anyString()), never());
        }
    }

    @Test
    void runActivity_tenantSettingThrows_skips() throws Exception {
        McpInstallService service = createInstance();
        try (MockedStatic<GlobalSettingsState> gsStatic = mockStatic(GlobalSettingsState.class);
             MockedStatic<GlobalSettingsSensitiveState> gssStatic = mockStatic(GlobalSettingsSensitiveState.class);
             MockedStatic<TenantSetting> tenantStatic = mockStatic(TenantSetting.class);
             MockedStatic<McpSettingsInjector> injStatic = mockStatic(McpSettingsInjector.class)) {

            GlobalSettingsState mockState = mock(GlobalSettingsState.class);
            GlobalSettingsSensitiveState mockSensitive = mock(GlobalSettingsSensitiveState.class);
            gsStatic.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            gssStatic.when(GlobalSettingsSensitiveState::getInstance).thenReturn(mockSensitive);
            when(mockState.isAuthenticated()).thenReturn(true);
            tenantStatic.when(() -> TenantSetting.isAiMcpServerEnabled(mockState, mockSensitive))
                    .thenThrow(new RuntimeException("network error"));

            assertDoesNotThrow(() -> service.runActivity(mockProject));
            injStatic.verify(() -> McpSettingsInjector.installForCopilot(anyString()), never());
        }
    }

    @Test
    void runActivity_noCredentialToken_skips() throws Exception {
        McpInstallService service = createInstance();
        try (MockedStatic<GlobalSettingsState> gsStatic = mockStatic(GlobalSettingsState.class);
             MockedStatic<GlobalSettingsSensitiveState> gssStatic = mockStatic(GlobalSettingsSensitiveState.class);
             MockedStatic<TenantSetting> tenantStatic = mockStatic(TenantSetting.class);
             MockedStatic<McpSettingsInjector> injStatic = mockStatic(McpSettingsInjector.class)) {

            GlobalSettingsState mockState = mock(GlobalSettingsState.class);
            GlobalSettingsSensitiveState mockSensitive = mock(GlobalSettingsSensitiveState.class);
            gsStatic.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            gssStatic.when(GlobalSettingsSensitiveState::getInstance).thenReturn(mockSensitive);
            when(mockState.isAuthenticated()).thenReturn(true);
            tenantStatic.when(() -> TenantSetting.isAiMcpServerEnabled(mockState, mockSensitive)).thenReturn(true);
            when(mockState.isApiKeyEnabled()).thenReturn(false);
            when(mockSensitive.getRefreshToken()).thenReturn(null);

            service.runActivity(mockProject);

            injStatic.verify(() -> McpSettingsInjector.installForCopilot(anyString()), never());
        }
    }

    @Test
    void runActivity_apiKeyEnabled_usesApiKey() throws Exception {
        McpInstallService service = createInstance();
        ExecutorService exec = directExecutor();
        try (MockedStatic<GlobalSettingsState> gsStatic = mockStatic(GlobalSettingsState.class);
             MockedStatic<GlobalSettingsSensitiveState> gssStatic = mockStatic(GlobalSettingsSensitiveState.class);
             MockedStatic<TenantSetting> tenantStatic = mockStatic(TenantSetting.class);
             MockedStatic<AppExecutorUtil> execStatic = mockStatic(AppExecutorUtil.class);
             MockedStatic<McpSettingsInjector> injStatic = mockStatic(McpSettingsInjector.class)) {

            GlobalSettingsState mockState = mock(GlobalSettingsState.class);
            GlobalSettingsSensitiveState mockSensitive = mock(GlobalSettingsSensitiveState.class);
            gsStatic.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            gssStatic.when(GlobalSettingsSensitiveState::getInstance).thenReturn(mockSensitive);
            when(mockState.isAuthenticated()).thenReturn(true);
            tenantStatic.when(() -> TenantSetting.isAiMcpServerEnabled(mockState, mockSensitive)).thenReturn(true);
            when(mockState.isApiKeyEnabled()).thenReturn(true);
            when(mockSensitive.getApiKey()).thenReturn("api-key-123");
            execStatic.when(AppExecutorUtil::getAppExecutorService).thenReturn(exec);
            injStatic.when(() -> McpSettingsInjector.installForCopilot("api-key-123")).thenReturn(true);

            service.runActivity(mockProject);

            injStatic.verify(() -> McpSettingsInjector.installForCopilot("api-key-123"));
        }
    }
}
