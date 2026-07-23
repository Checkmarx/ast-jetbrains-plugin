package com.checkmarx.intellij.common.startup;

import com.checkmarx.intellij.common.commands.TenantSetting;
import com.checkmarx.intellij.common.settings.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.settings.SettingsListener;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LicenseFlagSyncStartupActivityTest {

    @Mock
    private Project mockProject;

    // ---- helpers ----

    private GlobalSettingsState setupMockState(MockedStatic<GlobalSettingsState> gsStatic) {
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        gsStatic.when(GlobalSettingsState::getInstance).thenReturn(mockState);
        return mockState;
    }

    private GlobalSettingsSensitiveState setupMockSensitive(MockedStatic<GlobalSettingsSensitiveState> gssStatic) {
        GlobalSettingsSensitiveState mockSensitive = mock(GlobalSettingsSensitiveState.class);
        gssStatic.when(GlobalSettingsSensitiveState::getInstance).thenReturn(mockSensitive);
        return mockSensitive;
    }

    /** Wires ApplicationManager to return mockApp and runs executeOnPooledThread synchronously. */
    private Application setupSyncExecutor(MockedStatic<ApplicationManager> appStatic) {
        Application mockApp = mock(Application.class);
        appStatic.when(ApplicationManager::getApplication).thenReturn(mockApp);
        doAnswer(inv -> { inv.getArgument(0, Runnable.class).run(); return null; })
                .when(mockApp).executeOnPooledThread(any(Runnable.class));
        return mockApp;
    }

    // ---- tests ----

    @Test
    void runActivity_notAuthenticated_skips() {
        LicenseFlagSyncStartupActivity activity = new LicenseFlagSyncStartupActivity();
        try (MockedStatic<GlobalSettingsState> gsStatic = mockStatic(GlobalSettingsState.class);
             MockedStatic<GlobalSettingsSensitiveState> gssStatic = mockStatic(GlobalSettingsSensitiveState.class);
             MockedStatic<ApplicationManager> appStatic = mockStatic(ApplicationManager.class)) {

            GlobalSettingsState mockState = setupMockState(gsStatic);
            setupMockSensitive(gssStatic);
            Application mockApp = mock(Application.class);
            appStatic.when(ApplicationManager::getApplication).thenReturn(mockApp);
            when(mockState.isAuthenticated()).thenReturn(false);

            activity.runActivity(mockProject);

            verify(mockApp, never()).executeOnPooledThread(any(Runnable.class));
        }
    }

    @Test
    void runActivity_tenantSettingThrows_doesNotPropagate() {
        LicenseFlagSyncStartupActivity activity = new LicenseFlagSyncStartupActivity();
        try (MockedStatic<GlobalSettingsState> gsStatic = mockStatic(GlobalSettingsState.class);
             MockedStatic<GlobalSettingsSensitiveState> gssStatic = mockStatic(GlobalSettingsSensitiveState.class);
             MockedStatic<TenantSetting> tenantStatic = mockStatic(TenantSetting.class);
             MockedStatic<ApplicationManager> appStatic = mockStatic(ApplicationManager.class)) {

            GlobalSettingsState mockState = setupMockState(gsStatic);
            GlobalSettingsSensitiveState mockSensitive = setupMockSensitive(gssStatic);
            when(mockState.isAuthenticated()).thenReturn(true);
            tenantStatic.when(() -> TenantSetting.getTenantSettingsMap(mockState, mockSensitive))
                    .thenThrow(new RuntimeException("network error"));
            setupSyncExecutor(appStatic);

            assertDoesNotThrow(() -> activity.runActivity(mockProject));
        }
    }

    @Test
    void runActivity_flagsUnchanged_noEventPublished() {
        LicenseFlagSyncStartupActivity activity = new LicenseFlagSyncStartupActivity();
        try (MockedStatic<GlobalSettingsState> gsStatic = mockStatic(GlobalSettingsState.class);
             MockedStatic<GlobalSettingsSensitiveState> gssStatic = mockStatic(GlobalSettingsSensitiveState.class);
             MockedStatic<TenantSetting> tenantStatic = mockStatic(TenantSetting.class);
             MockedStatic<ApplicationManager> appStatic = mockStatic(ApplicationManager.class)) {

            GlobalSettingsState mockState = setupMockState(gsStatic);
            GlobalSettingsSensitiveState mockSensitive = setupMockSensitive(gssStatic);
            when(mockState.isAuthenticated()).thenReturn(true);
            when(mockState.isDevAssistLicenseEnabled()).thenReturn(true);
            when(mockState.isOneAssistLicenseEnabled()).thenReturn(false);
            tenantStatic.when(() -> TenantSetting.getTenantSettingsMap(mockState, mockSensitive))
                    .thenReturn(Map.of(TenantSetting.KEY_DEV_ASSIST, "true", TenantSetting.KEY_ONE_ASSIST, "false"));
            Application mockApp = setupSyncExecutor(appStatic);

            activity.runActivity(mockProject);

            verify(mockApp, never()).invokeLater(any(Runnable.class));
        }
    }

    @Test
    void runActivity_devAssistFlagChanged_updatesStateAndPublishesEvent() {
        LicenseFlagSyncStartupActivity activity = new LicenseFlagSyncStartupActivity();
        try (MockedStatic<GlobalSettingsState> gsStatic = mockStatic(GlobalSettingsState.class);
             MockedStatic<GlobalSettingsSensitiveState> gssStatic = mockStatic(GlobalSettingsSensitiveState.class);
             MockedStatic<TenantSetting> tenantStatic = mockStatic(TenantSetting.class);
             MockedStatic<ApplicationManager> appStatic = mockStatic(ApplicationManager.class)) {

            GlobalSettingsState mockState = setupMockState(gsStatic);
            GlobalSettingsSensitiveState mockSensitive = setupMockSensitive(gssStatic);
            Application mockApp = setupSyncExecutor(appStatic);
            MessageBus mockBus = mock(MessageBus.class);
            SettingsListener mockListener = mock(SettingsListener.class);

            when(mockState.isAuthenticated()).thenReturn(true);
            when(mockState.isDevAssistLicenseEnabled()).thenReturn(false);
            when(mockState.isOneAssistLicenseEnabled()).thenReturn(false);
            tenantStatic.when(() -> TenantSetting.getTenantSettingsMap(mockState, mockSensitive))
                    .thenReturn(Map.of(TenantSetting.KEY_DEV_ASSIST, "true"));
            doAnswer(inv -> { inv.getArgument(0, Runnable.class).run(); return null; })
                    .when(mockApp).invokeLater(any(Runnable.class));
            when(mockApp.getMessageBus()).thenReturn(mockBus);
            when(mockBus.syncPublisher(SettingsListener.SETTINGS_APPLIED)).thenReturn(mockListener);

            activity.runActivity(mockProject);

            verify(mockState).setDevAssistLicenseEnabled(true);
            verify(mockListener).settingsApplied();
        }
    }

    @Test
    void runActivity_oneAssistFlagChanged_updatesStateAndPublishesEvent() {
        LicenseFlagSyncStartupActivity activity = new LicenseFlagSyncStartupActivity();
        try (MockedStatic<GlobalSettingsState> gsStatic = mockStatic(GlobalSettingsState.class);
             MockedStatic<GlobalSettingsSensitiveState> gssStatic = mockStatic(GlobalSettingsSensitiveState.class);
             MockedStatic<TenantSetting> tenantStatic = mockStatic(TenantSetting.class);
             MockedStatic<ApplicationManager> appStatic = mockStatic(ApplicationManager.class)) {

            GlobalSettingsState mockState = setupMockState(gsStatic);
            GlobalSettingsSensitiveState mockSensitive = setupMockSensitive(gssStatic);
            Application mockApp = setupSyncExecutor(appStatic);
            MessageBus mockBus = mock(MessageBus.class);
            SettingsListener mockListener = mock(SettingsListener.class);

            when(mockState.isAuthenticated()).thenReturn(true);
            when(mockState.isDevAssistLicenseEnabled()).thenReturn(false);
            when(mockState.isOneAssistLicenseEnabled()).thenReturn(false);
            tenantStatic.when(() -> TenantSetting.getTenantSettingsMap(mockState, mockSensitive))
                    .thenReturn(Map.of(TenantSetting.KEY_ONE_ASSIST, "true"));
            doAnswer(inv -> { inv.getArgument(0, Runnable.class).run(); return null; })
                    .when(mockApp).invokeLater(any(Runnable.class));
            when(mockApp.getMessageBus()).thenReturn(mockBus);
            when(mockBus.syncPublisher(SettingsListener.SETTINGS_APPLIED)).thenReturn(mockListener);

            activity.runActivity(mockProject);

            verify(mockState).setOneAssistLicenseEnabled(true);
            verify(mockListener).settingsApplied();
        }
    }

    @Test
    void runActivity_emptyTenantSettings_devAssistDisabled_publishesEvent() {
        LicenseFlagSyncStartupActivity activity = new LicenseFlagSyncStartupActivity();
        try (MockedStatic<GlobalSettingsState> gsStatic = mockStatic(GlobalSettingsState.class);
             MockedStatic<GlobalSettingsSensitiveState> gssStatic = mockStatic(GlobalSettingsSensitiveState.class);
             MockedStatic<TenantSetting> tenantStatic = mockStatic(TenantSetting.class);
             MockedStatic<ApplicationManager> appStatic = mockStatic(ApplicationManager.class)) {

            GlobalSettingsState mockState = setupMockState(gsStatic);
            GlobalSettingsSensitiveState mockSensitive = setupMockSensitive(gssStatic);
            Application mockApp = setupSyncExecutor(appStatic);
            MessageBus mockBus = mock(MessageBus.class);
            SettingsListener mockListener = mock(SettingsListener.class);

            when(mockState.isAuthenticated()).thenReturn(true);
            when(mockState.isDevAssistLicenseEnabled()).thenReturn(true);
            when(mockState.isOneAssistLicenseEnabled()).thenReturn(false);
            tenantStatic.when(() -> TenantSetting.getTenantSettingsMap(mockState, mockSensitive))
                    .thenReturn(Collections.emptyMap());
            doAnswer(inv -> { inv.getArgument(0, Runnable.class).run(); return null; })
                    .when(mockApp).invokeLater(any(Runnable.class));
            when(mockApp.getMessageBus()).thenReturn(mockBus);
            when(mockBus.syncPublisher(SettingsListener.SETTINGS_APPLIED)).thenReturn(mockListener);

            activity.runActivity(mockProject);

            verify(mockState).setDevAssistLicenseEnabled(false);
            verify(mockListener).settingsApplied();
        }
    }

    @Test
    void runActivity_bothFlagsChanged_publishesSingleEvent() {
        LicenseFlagSyncStartupActivity activity = new LicenseFlagSyncStartupActivity();
        try (MockedStatic<GlobalSettingsState> gsStatic = mockStatic(GlobalSettingsState.class);
             MockedStatic<GlobalSettingsSensitiveState> gssStatic = mockStatic(GlobalSettingsSensitiveState.class);
             MockedStatic<TenantSetting> tenantStatic = mockStatic(TenantSetting.class);
             MockedStatic<ApplicationManager> appStatic = mockStatic(ApplicationManager.class)) {

            GlobalSettingsState mockState = setupMockState(gsStatic);
            GlobalSettingsSensitiveState mockSensitive = setupMockSensitive(gssStatic);
            Application mockApp = setupSyncExecutor(appStatic);
            MessageBus mockBus = mock(MessageBus.class);
            SettingsListener mockListener = mock(SettingsListener.class);

            when(mockState.isAuthenticated()).thenReturn(true);
            when(mockState.isDevAssistLicenseEnabled()).thenReturn(false);
            when(mockState.isOneAssistLicenseEnabled()).thenReturn(false);
            tenantStatic.when(() -> TenantSetting.getTenantSettingsMap(mockState, mockSensitive))
                    .thenReturn(Map.of(TenantSetting.KEY_DEV_ASSIST, "true", TenantSetting.KEY_ONE_ASSIST, "true"));
            doAnswer(inv -> { inv.getArgument(0, Runnable.class).run(); return null; })
                    .when(mockApp).invokeLater(any(Runnable.class));
            when(mockApp.getMessageBus()).thenReturn(mockBus);
            when(mockBus.syncPublisher(SettingsListener.SETTINGS_APPLIED)).thenReturn(mockListener);

            activity.runActivity(mockProject);

            verify(mockState).setDevAssistLicenseEnabled(true);
            verify(mockState).setOneAssistLicenseEnabled(true);
            verify(mockApp, times(1)).invokeLater(any(Runnable.class));
            verify(mockListener).settingsApplied();
        }
    }
}
