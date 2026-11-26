package com.checkmarx.intellij.devassist.telemetry;

import com.checkmarx.ast.telemetry.TelemetryEvent;
import com.checkmarx.intellij.common.wrapper.CxWrapperFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("LoginFailureTracker unit tests")
class LoginFailureTrackerTest {

    private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");

    @Test
    @DisplayName("record_keepsMethodTimeAndSanitizedReason")
    void record_keepsMethodTimeAndSanitizedReason() {
        TelemetryInstallationState state = new TelemetryInstallationState();

        try (MockedStatic<TelemetryInstallationState> stateMock = mockStatic(TelemetryInstallationState.class)) {
            stateMock.when(TelemetryInstallationState::getInstance).thenReturn(state);

            LoginFailureTracker.record("APIKey", "invalid apikey=abc123secret for tenant", NOW);

            assertEquals(1, state.getPendingLoginFailures().size());
            PendingLoginFailure failure = state.getPendingLoginFailures().get(0);
            assertEquals("APIKey", failure.getAuthMethod());
            assertEquals("2026-11-26T10:00:00Z", failure.getTimestamp());
            assertFalse(failure.getReason().contains("abc123secret"), failure.getReason());
        }
    }

    @Test
    @DisplayName("record_blankReason_storesUnknown")
    void record_blankReason_storesUnknown() {
        TelemetryInstallationState state = new TelemetryInstallationState();

        try (MockedStatic<TelemetryInstallationState> stateMock = mockStatic(TelemetryInstallationState.class)) {
            stateMock.when(TelemetryInstallationState::getInstance).thenReturn(state);

            LoginFailureTracker.record("OAuth", "  ", NOW);

            assertEquals("unknown", state.getPendingLoginFailures().get(0).getReason());
        }
    }

    @Test
    @DisplayName("record_keepsOnlyTheNewestFailures")
    void record_keepsOnlyTheNewestFailures() {
        TelemetryInstallationState state = new TelemetryInstallationState();

        try (MockedStatic<TelemetryInstallationState> stateMock = mockStatic(TelemetryInstallationState.class)) {
            stateMock.when(TelemetryInstallationState::getInstance).thenReturn(state);

            for (int i = 0; i < LoginFailureTracker.MAX_PENDING + 3; i++) {
                LoginFailureTracker.record("OAuth", "attempt " + i, NOW.plusSeconds(i));
            }

            List<PendingLoginFailure> kept = state.getPendingLoginFailures();
            assertEquals(LoginFailureTracker.MAX_PENDING, kept.size());
            assertEquals("attempt 3", kept.get(0).getReason());
            assertEquals("attempt " + (LoginFailureTracker.MAX_PENDING + 2), kept.get(kept.size() - 1).getReason());
        }
    }

    @Test
    @DisplayName("record_neverChangesThePublishedListInPlace")
    void record_neverChangesThePublishedListInPlace() {
        TelemetryInstallationState state = new TelemetryInstallationState();

        try (MockedStatic<TelemetryInstallationState> stateMock = mockStatic(TelemetryInstallationState.class)) {
            stateMock.when(TelemetryInstallationState::getInstance).thenReturn(state);

            LoginFailureTracker.record("OAuth", "first", NOW);
            List<PendingLoginFailure> before = state.getPendingLoginFailures();
            LoginFailureTracker.record("OAuth", "second", NOW);

            assertEquals(1, before.size());
            assertNotSame(before, state.getPendingLoginFailures());
        }
    }

    @Test
    @DisplayName("flushPending_sendsOldestFirstAsFailureWithAttemptTime_andClearsThem")
    void flushPending_sendsOldestFirstAsFailureWithAttemptTime_andClearsThem() {
        TelemetryInstallationState state = stateWith(
                failure("OAuth", "2026-11-26T09:00:00Z", "timeout"),
                failure("APIKey", "2026-11-26T09:05:00Z", "invalid key"));
        List<TelemetryEvent> sent = new ArrayList<>();

        try (MockedStatic<TelemetryInstallationState> stateMock = mockStatic(TelemetryInstallationState.class)) {
            stateMock.when(TelemetryInstallationState::getInstance).thenReturn(state);

            LoginFailureTracker.flushPending(builder -> sent.add(builder.build()), NOW);

            assertEquals(2, sent.size());
            TelemetryEvent first = sent.get(0);
            assertEquals("authentication", first.getEventType());
            assertEquals("authentication", first.getSubType());
            assertEquals("failure", first.getStatus());
            assertEquals("Authentication failed. Reason: timeout, Error Code: N/A", first.getStatusResult());
            assertEquals("OAuth", first.getEventAttributes().get("authMethod"));
            assertEquals("2026-11-26T09:00:00Z", first.getEventAttributes().get("loginTimestamp"));
            assertEquals("APIKey", sent.get(1).getEventAttributes().get("authMethod"));
            assertTrue(state.getPendingLoginFailures().isEmpty());
        }
    }

    @Test
    @DisplayName("flushPending_notDelivered_keepsItAndTheRestForNextTime")
    void flushPending_notDelivered_keepsItAndTheRestForNextTime() {
        TelemetryInstallationState state = stateWith(
                failure("OAuth", "2026-11-26T09:00:00Z", "first"),
                failure("OAuth", "2026-11-26T09:01:00Z", "second"),
                failure("OAuth", "2026-11-26T09:02:00Z", "third"));
        List<String> attempted = new ArrayList<>();

        try (MockedStatic<TelemetryInstallationState> stateMock = mockStatic(TelemetryInstallationState.class)) {
            stateMock.when(TelemetryInstallationState::getInstance).thenReturn(state);

            LoginFailureTracker.flushPending(builder -> {
                attempted.add(builder.build().getStatusResult());
                return attempted.size() == 1;
            }, NOW);

            assertEquals(2, attempted.size(), "stops after the first failure that is not delivered");
            assertEquals(List.of("second", "third"),
                    state.getPendingLoginFailures().stream().map(PendingLoginFailure::getReason).collect(java.util.stream.Collectors.toList()));
        }
    }

    @Test
    @DisplayName("flushPending_dropsExpiredAndInvalidEntriesWithoutSending")
    void flushPending_dropsExpiredAndInvalidEntriesWithoutSending() {
        String old = NOW.minus(LoginFailureTracker.MAX_AGE).minus(Duration.ofMinutes(1)).toString();
        TelemetryInstallationState state = stateWith(
                failure("OAuth", old, "too old"),
                failure("OAuth", "not-a-time", "broken"),
                failure("OAuth", "2026-11-26T09:00:00Z", "recent"));
        List<String> sent = new ArrayList<>();

        try (MockedStatic<TelemetryInstallationState> stateMock = mockStatic(TelemetryInstallationState.class)) {
            stateMock.when(TelemetryInstallationState::getInstance).thenReturn(state);

            LoginFailureTracker.flushPending(builder -> sent.add(builder.build().getStatusResult()), NOW);

            assertEquals(List.of("Authentication failed. Reason: recent, Error Code: N/A"), sent);
            assertTrue(state.getPendingLoginFailures().isEmpty());
        }
    }

    @Test
    @DisplayName("flushPending_nothingPending_doesNotSend")
    void flushPending_nothingPending_doesNotSend() {
        TelemetryInstallationState state = new TelemetryInstallationState();

        try (MockedStatic<TelemetryInstallationState> stateMock = mockStatic(TelemetryInstallationState.class)) {
            stateMock.when(TelemetryInstallationState::getInstance).thenReturn(state);

            LoginFailureTracker.flushPending(builder -> fail("nothing should be sent"), NOW);
        }
    }

    @Test
    @DisplayName("flushPending_senderThrows_keepsDeliveredOnesRemovedAndReleasesTheLock")
    void flushPending_senderThrows_keepsDeliveredOnesRemovedAndReleasesTheLock() {
        TelemetryInstallationState state = stateWith(
                failure("OAuth", "2026-11-26T09:00:00Z", "first"),
                failure("OAuth", "2026-11-26T09:01:00Z", "second"));
        int[] calls = {0};

        try (MockedStatic<TelemetryInstallationState> stateMock = mockStatic(TelemetryInstallationState.class)) {
            stateMock.when(TelemetryInstallationState::getInstance).thenReturn(state);

            assertThrows(IllegalStateException.class, () -> LoginFailureTracker.flushPending(builder -> {
                if (++calls[0] == 2) {
                    throw new IllegalStateException("boom");
                }
                return true;
            }, NOW));
            assertEquals(1, state.getPendingLoginFailures().size(), "the delivered one is not sent twice");

            LoginFailureTracker.flushPending(builder -> true, NOW);
            assertTrue(state.getPendingLoginFailures().isEmpty());
        }
    }

    @Test
    @DisplayName("noState_doesNothing")
    void noState_doesNothing() {
        try (MockedStatic<TelemetryInstallationState> stateMock = mockStatic(TelemetryInstallationState.class)) {
            stateMock.when(TelemetryInstallationState::getInstance).thenReturn(null);

            assertDoesNotThrow(() -> LoginFailureTracker.record("OAuth", "x", NOW));
            assertDoesNotThrow(() -> LoginFailureTracker.flushPending(builder -> true, NOW));
        }
    }

    @Test
    @DisplayName("state_pendingFailuresSurviveSaveAndLoad")
    void state_pendingFailuresSurviveSaveAndLoad() {
        TelemetryInstallationState state = stateWith(failure("APIKey", "2026-11-26T09:00:00Z", "invalid key"));
        state.setInstallationId("pycharm-abc");

        org.jdom.Element saved = com.intellij.util.xmlb.XmlSerializer.serialize(state);
        TelemetryInstallationState loaded = new TelemetryInstallationState();
        loaded.loadState(com.intellij.util.xmlb.XmlSerializer.deserialize(saved, TelemetryInstallationState.class));

        assertEquals("pycharm-abc", loaded.getInstallationId());
        assertEquals(1, loaded.getPendingLoginFailures().size());
        PendingLoginFailure failure = loaded.getPendingLoginFailures().get(0);
        assertEquals("APIKey", failure.getAuthMethod());
        assertEquals("2026-11-26T09:00:00Z", failure.getTimestamp());
        assertEquals("invalid key", failure.getReason());
    }

    @Test
    @DisplayName("logLoginEvent_failure_isKeptAndNotSentNow")
    void logLoginEvent_failure_isKeptAndNotSentNow() {
        TelemetryInstallationState state = new TelemetryInstallationState();

        try (MockedStatic<TelemetryInstallationState> stateMock = mockStatic(TelemetryInstallationState.class);
             MockedStatic<CxWrapperFactory> factory = mockStatic(CxWrapperFactory.class)) {
            stateMock.when(TelemetryInstallationState::getInstance).thenReturn(state);

            TelemetryService.logLoginEvent(TelemetryConstants.AUTH_METHOD_OAUTH, false, "Connection timed out");

            assertEquals(1, state.getPendingLoginFailures().size());
            assertEquals("OAuth", state.getPendingLoginFailures().get(0).getAuthMethod());
            factory.verifyNoInteractions();
        }
    }

    private static TelemetryInstallationState stateWith(PendingLoginFailure... failures) {
        TelemetryInstallationState state = new TelemetryInstallationState();
        state.setPendingLoginFailures(new ArrayList<>(List.of(failures)));
        return state;
    }

    private static PendingLoginFailure failure(String method, String time, String reason) {
        return new PendingLoginFailure(method, time, reason);
    }
}
