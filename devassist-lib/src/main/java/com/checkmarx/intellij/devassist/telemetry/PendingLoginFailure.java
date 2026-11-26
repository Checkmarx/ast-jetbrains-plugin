package com.checkmarx.intellij.devassist.telemetry;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A failed login that could not be sent yet (AST-184118).
 * <p>
 * Without a successful login the CLI has no token, so the analytics service cannot receive the event.
 * It is kept in {@link TelemetryInstallationState} and sent after the next successful login.
 * Only the sanitized reason is stored, never a key or a token.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PendingLoginFailure {

    /** {@link TelemetryConstants#AUTH_METHOD_OAUTH} or {@link TelemetryConstants#AUTH_METHOD_API_KEY}. */
    private String authMethod = "";

    /** ISO-8601 time of the failed attempt, sent as loginTimestamp. */
    private String timestamp = "";

    /** Failure reason, already sanitized. */
    private String reason = "";
}
