package com.checkmarx.intellij.devassist.telemetry;

import com.checkmarx.ast.telemetry.TelemetryEvent;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Failed logins cannot be sent without a token, so they are kept and sent after the next successful login.
 * The stored list is always replaced, never changed in place, because the IDE can save the state at any time.
 */
public final class LoginFailureTracker {

    static final int MAX_PENDING = 10;
    static final Duration MAX_AGE = Duration.ofDays(30);

    private static final Object LOCK = new Object();

    private LoginFailureTracker() {
    }

    public static void record(String authMethod, String reason, Instant time) {
        TelemetryInstallationState state = TelemetryInstallationState.getInstance();
        if (Objects.isNull(state)) {
            return;
        }
        PendingLoginFailure failure = new PendingLoginFailure(Objects.toString(authMethod, ""), time.toString(),
                TelemetryEventFactory.sanitizeText(Objects.isNull(reason) || reason.isBlank() ? "unknown" : reason));
        synchronized (LOCK) {
            List<PendingLoginFailure> updated = new ArrayList<>(pending(state));
            updated.add(failure);
            while (updated.size() > MAX_PENDING) {
                updated.remove(0);
            }
            state.setPendingLoginFailures(updated);
        }
    }

    /**
     * Sends the kept failures oldest first. Stops at the first one the CLI does not accept; the rest are sent next time.
     */
    public static void flushPending(Predicate<TelemetryEvent.TelemetryEventBuilder> sender, Instant now) {
        TelemetryInstallationState state = TelemetryInstallationState.getInstance();
        if (Objects.isNull(state)) {
            return;
        }
        List<PendingLoginFailure> snapshot;
        synchronized (LOCK) {
            snapshot = new ArrayList<>(pending(state));
        }
        List<PendingLoginFailure> done = new ArrayList<>();
        try {
            for (PendingLoginFailure failure : snapshot) {
                if (isExpired(failure, now)) {
                    done.add(failure);
                    continue;
                }
                if (!sender.test(TelemetryEventFactory.loginEvent(failure.getAuthMethod(), false, failure.getReason(),
                        failure.getTimestamp()))) {
                    break;
                }
                done.add(failure);
            }
        } finally {
            remove(state, done);
        }
    }

    static boolean isExpired(PendingLoginFailure failure, Instant now) {
        try {
            return Instant.parse(failure.getTimestamp()).isBefore(now.minus(MAX_AGE));
        } catch (DateTimeParseException | NullPointerException e) {
            return true;
        }
    }

    private static void remove(TelemetryInstallationState state, List<PendingLoginFailure> done) {
        if (done.isEmpty()) {
            return;
        }
        synchronized (LOCK) {
            List<PendingLoginFailure> updated = new ArrayList<>(pending(state));
            updated.removeIf(failure -> done.stream().anyMatch(sent -> sent == failure));
            state.setPendingLoginFailures(updated);
        }
    }

    private static List<PendingLoginFailure> pending(TelemetryInstallationState state) {
        List<PendingLoginFailure> list = state.getPendingLoginFailures();
        return Objects.isNull(list) ? List.of() : list;
    }
}
