package com.checkmarx.intellij.devassist.telemetry;

import com.checkmarx.ast.telemetry.TelemetryEvent;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * Failed logins of the Dev Assist analytics (AST-184118).
 * <p>
 * A failed login cannot be sent when it happens: the user is not logged in, so the CLI has no token and the analytics
 * service rejects the request. Sending it with a credential that is still stored would also report it for the wrong
 * user. The failure is therefore kept and sent after the next successful login, with the time of the failed attempt
 * as loginTimestamp. At most {@value #MAX_PENDING} failures are kept (the newest ones), and failures older than
 * {@link #MAX_AGE} are dropped without being sent.
 * <p>
 * The list is never changed in place: every change stores a new list, so the IDE can save the state at any time.
 */
public final class LoginFailureTracker {

    static final int MAX_PENDING = 10;
    static final Duration MAX_AGE = Duration.ofDays(30);

    private static final Object LOCK = new Object();
    private static final AtomicBoolean FLUSH_IN_PROGRESS = new AtomicBoolean(false);

    private LoginFailureTracker() {
    }

    /**
     * Keeps a failed login until it can be sent.
     *
     * @param authMethod OAuth or APIKey
     * @param reason     failure reason, sanitized before it is stored
     * @param time       time of the failed attempt
     */
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
     * Sends the kept failures, oldest first, and removes each one once it was delivered.
     * Stops at the first failure that is not delivered, so the remaining ones are sent next time.
     * Concurrent calls share one send.
     *
     * @param sender sends the event and returns true only when the CLI accepted it
     * @param now    current time, used to drop failures older than {@link #MAX_AGE}
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
        if (snapshot.isEmpty() || !FLUSH_IN_PROGRESS.compareAndSet(false, true)) {
            return;
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
            FLUSH_IN_PROGRESS.set(false);
        }
    }

    static boolean isExpired(PendingLoginFailure failure, Instant now) {
        try {
            return Instant.parse(failure.getTimestamp()).isBefore(now.minus(MAX_AGE));
        } catch (DateTimeParseException | NullPointerException e) {
            // A failure without a valid time cannot be reported correctly
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
