package com.checkmarx.intellij.devassist.telemetry;

import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("InstallationTracker unit tests")
class InstallationTrackerTest {

    // ===== id generation =====

    @Test
    @DisplayName("ideSlug_turnsIdeNameIntoLowercaseWords")
    void ideSlug_turnsIdeNameIntoLowercaseWords() {
        assertEquals("intellij-idea", InstallationTracker.ideSlug("IntelliJ IDEA"));
        assertEquals("pycharm", InstallationTracker.ideSlug("PyCharm"));
        assertEquals("webstorm", InstallationTracker.ideSlug("  WebStorm  "));
    }

    @Test
    @DisplayName("ideSlug_blankName_fallsBackToJetbrains")
    void ideSlug_blankName_fallsBackToJetbrains() {
        assertEquals("jetbrains", InstallationTracker.ideSlug(null));
        assertEquals("jetbrains", InstallationTracker.ideSlug(""));
        assertEquals("jetbrains", InstallationTracker.ideSlug("***"));
    }

    @Test
    @DisplayName("generateInstallationId_hasIdePrefixAndUuid")
    void generateInstallationId_hasIdePrefixAndUuid() {
        String id = InstallationTracker.generateInstallationId("IntelliJ IDEA");

        assertTrue(id.matches("intellij-idea-[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"), id);
        assertNotEquals(id, InstallationTracker.generateInstallationId("IntelliJ IDEA"));
    }

    // ===== existing users =====

    @Test
    @DisplayName("isExistingInstallation_freshSettings_isFalse")
    void isExistingInstallation_freshSettings_isFalse() {
        GlobalSettingsState settings = mock(GlobalSettingsState.class);
        when(settings.getBaseUrl()).thenReturn("");
        when(settings.getTenant()).thenReturn("");
        when(settings.getAiAgent()).thenReturn("");

        assertFalse(InstallationTracker.isExistingInstallation(settings));
        assertFalse(InstallationTracker.isExistingInstallation(null));
    }

    @Test
    @DisplayName("isExistingInstallation_anyPreviousUse_isTrue")
    void isExistingInstallation_anyPreviousUse_isTrue() {
        GlobalSettingsState authenticated = mock(GlobalSettingsState.class);
        when(authenticated.isAuthenticated()).thenReturn(true);
        assertTrue(InstallationTracker.isExistingInstallation(authenticated));

        GlobalSettingsState withUrl = mock(GlobalSettingsState.class);
        when(withUrl.getBaseUrl()).thenReturn("https://ast.checkmarx.net");
        assertTrue(InstallationTracker.isExistingInstallation(withUrl));

        GlobalSettingsState mcpChecked = mock(GlobalSettingsState.class);
        when(mcpChecked.isMcpStatusChecked()).thenReturn(true);
        assertTrue(InstallationTracker.isExistingInstallation(mcpChecked));

        GlobalSettingsState welcomed = mock(GlobalSettingsState.class);
        when(welcomed.isWelcomeShown()).thenReturn(true);
        assertTrue(InstallationTracker.isExistingInstallation(welcomed));
    }

    // ===== ensureInstallationId =====

    @Test
    @DisplayName("ensureInstallationId_newUser_createsIdAndPendingEvent")
    void ensureInstallationId_newUser_createsIdAndPendingEvent() {
        TelemetryInstallationState state = new TelemetryInstallationState();
        GlobalSettingsState settings = mock(GlobalSettingsState.class);
        when(settings.getBaseUrl()).thenReturn("");
        when(settings.getTenant()).thenReturn("");
        when(settings.getAiAgent()).thenReturn("");

        try (MockedStatic<TelemetryInstallationState> stateMock = mockStatic(TelemetryInstallationState.class);
             MockedStatic<GlobalSettingsState> settingsMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(TelemetryInstallationState::getInstance).thenReturn(state);
            settingsMock.when(GlobalSettingsState::getInstance).thenReturn(settings);

            InstallationTracker.ensureInstallationId("PyCharm");

            assertTrue(state.getInstallationId().startsWith("pycharm-"));
            assertFalse(state.getInstallationTimestamp().isEmpty());
            assertTrue(state.isInstallEventPending());
        }
    }

    @Test
    @DisplayName("ensureInstallationId_upgradedUser_createsIdWithoutPendingEvent")
    void ensureInstallationId_upgradedUser_createsIdWithoutPendingEvent() {
        TelemetryInstallationState state = new TelemetryInstallationState();
        GlobalSettingsState settings = mock(GlobalSettingsState.class);
        when(settings.isAuthenticated()).thenReturn(true);

        try (MockedStatic<TelemetryInstallationState> stateMock = mockStatic(TelemetryInstallationState.class);
             MockedStatic<GlobalSettingsState> settingsMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(TelemetryInstallationState::getInstance).thenReturn(state);
            settingsMock.when(GlobalSettingsState::getInstance).thenReturn(settings);

            InstallationTracker.ensureInstallationId("PyCharm");

            assertFalse(state.getInstallationId().isEmpty());
            assertFalse(state.isInstallEventPending());
        }
    }

    @Test
    @DisplayName("ensureInstallationId_existingId_isNotReplaced")
    void ensureInstallationId_existingId_isNotReplaced() {
        TelemetryInstallationState state = new TelemetryInstallationState();
        state.setInstallationId("pycharm-existing");
        state.setInstallEventPending(true);

        try (MockedStatic<TelemetryInstallationState> stateMock = mockStatic(TelemetryInstallationState.class)) {
            stateMock.when(TelemetryInstallationState::getInstance).thenReturn(state);

            InstallationTracker.ensureInstallationId("WebStorm");

            assertEquals("pycharm-existing", state.getInstallationId());
            assertTrue(state.isInstallEventPending());
        }
    }

    // ===== flushPendingInstallEvent =====

    @Test
    @DisplayName("flushPendingInstallEvent_delivered_clearsPendingFlag")
    void flushPendingInstallEvent_delivered_clearsPendingFlag() {
        TelemetryInstallationState state = pendingState();
        AtomicReference<com.checkmarx.ast.telemetry.TelemetryEvent> sent = new AtomicReference<>();

        try (MockedStatic<TelemetryInstallationState> stateMock = mockStatic(TelemetryInstallationState.class)) {
            stateMock.when(TelemetryInstallationState::getInstance).thenReturn(state);

            InstallationTracker.flushPendingInstallEvent(builder -> {
                sent.set(builder.build());
                return true;
            });

            assertFalse(state.isInstallEventPending());
            assertEquals("install", sent.get().getEventType());
            assertEquals("pycharm-abc", sent.get().getEventAttributes().get("installationId"));
            assertEquals("2026-11-19T09:00:00Z", sent.get().getEventAttributes().get("installationTimestamp"));
        }
    }

    @Test
    @DisplayName("flushPendingInstallEvent_notDelivered_keepsPendingFlag")
    void flushPendingInstallEvent_notDelivered_keepsPendingFlag() {
        TelemetryInstallationState state = pendingState();

        try (MockedStatic<TelemetryInstallationState> stateMock = mockStatic(TelemetryInstallationState.class)) {
            stateMock.when(TelemetryInstallationState::getInstance).thenReturn(state);

            InstallationTracker.flushPendingInstallEvent(builder -> false);

            assertTrue(state.isInstallEventPending());
        }
    }

    @Test
    @DisplayName("flushPendingInstallEvent_nothingPending_doesNotSend")
    void flushPendingInstallEvent_nothingPending_doesNotSend() {
        TelemetryInstallationState state = pendingState();
        state.setInstallEventPending(false);

        try (MockedStatic<TelemetryInstallationState> stateMock = mockStatic(TelemetryInstallationState.class)) {
            stateMock.when(TelemetryInstallationState::getInstance).thenReturn(state);

            InstallationTracker.flushPendingInstallEvent(builder -> {
                fail("nothing should be sent");
                return true;
            });
        }
    }

    @Test
    @DisplayName("flushPendingInstallEvent_senderThrows_releasesTheLock")
    void flushPendingInstallEvent_senderThrows_releasesTheLock() {
        TelemetryInstallationState state = pendingState();

        try (MockedStatic<TelemetryInstallationState> stateMock = mockStatic(TelemetryInstallationState.class)) {
            stateMock.when(TelemetryInstallationState::getInstance).thenReturn(state);

            assertThrows(IllegalStateException.class, () -> InstallationTracker.flushPendingInstallEvent(builder -> {
                throw new IllegalStateException("boom");
            }));
            InstallationTracker.flushPendingInstallEvent(builder -> true);

            assertFalse(state.isInstallEventPending());
        }
    }

    private static TelemetryInstallationState pendingState() {
        TelemetryInstallationState state = new TelemetryInstallationState();
        state.setInstallationId("pycharm-abc");
        state.setInstallationTimestamp("2026-11-19T09:00:00Z");
        state.setInstallEventPending(true);
        return state;
    }
}
