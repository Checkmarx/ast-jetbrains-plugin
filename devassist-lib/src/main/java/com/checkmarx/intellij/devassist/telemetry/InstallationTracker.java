package com.checkmarx.intellij.devassist.telemetry;

import com.checkmarx.ast.telemetry.TelemetryEvent;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * Install event of the Dev Assist analytics (AST-184118).
 * <p>
 * On the first start an installation id {@code <ide>-<uuid>} is created and an install event is marked as pending.
 * Sending needs an authenticated CLI, so the event is sent after the first successful login, or on a later start
 * with valid credentials, and it is cleared only after it was delivered.
 * <p>
 * Users who already used the plugin before this release get an id but no install event: they upgraded, they did not install.
 */
public final class InstallationTracker {

    private static final String DEFAULT_IDE_PREFIX = "jetbrains";
    private static final AtomicBoolean FLUSH_IN_PROGRESS = new AtomicBoolean(false);

    private InstallationTracker() {
    }

    /**
     * Creates the installation id on the first start. Does nothing afterwards.
     */
    public static void ensureInstallationId(String ideName) {
        TelemetryInstallationState state = TelemetryInstallationState.getInstance();
        if (state == null || StringUtils.isNotBlank(state.getInstallationId())) {
            return;
        }
        boolean upgraded = isExistingInstallation(GlobalSettingsState.getInstance());
        String timestamp = Instant.now().toString();
        state.setInstallationId(generateInstallationId(ideName));
        state.setInstallationTimestamp(timestamp);
        state.setInstallEventPending(!upgraded);
    }

    /**
     * Sends the pending install event, if any, and clears it once it was delivered.
     * Concurrent calls (start up and login at the same time) share one send.
     *
     * @param sender sends the event and returns true only when the CLI accepted it
     */
    public static void flushPendingInstallEvent(Predicate<TelemetryEvent.TelemetryEventBuilder> sender) {
        TelemetryInstallationState state = TelemetryInstallationState.getInstance();
        if (state == null || !state.isInstallEventPending() || StringUtils.isBlank(state.getInstallationId())) {
            return;
        }
        if (!FLUSH_IN_PROGRESS.compareAndSet(false, true)) {
            return;
        }
        try {
            boolean delivered = sender.test(TelemetryEventFactory.installEvent(state.getInstallationId(), state.getInstallationTimestamp()));
            if (delivered) {
                state.setInstallEventPending(false);
            }
        } finally {
            FLUSH_IN_PROGRESS.set(false);
        }
    }

    /**
     * True when the plugin was already used before installation ids existed.
     */
    static boolean isExistingInstallation(GlobalSettingsState settings) {
        return settings != null && (settings.isAuthenticated()
                || StringUtils.isNotBlank(settings.getBaseUrl())
                || StringUtils.isNotBlank(settings.getTenant())
                || settings.isMcpStatusChecked()
                || settings.isWelcomeShown()
                || StringUtils.isNotBlank(settings.getAiAgent()));
    }

    static String generateInstallationId(String ideName) {
        return ideSlug(ideName) + "-" + UUID.randomUUID();
    }

    /**
     * "IntelliJ IDEA" becomes "intellij-idea". Falls back to "jetbrains".
     */
    static String ideSlug(String ideName) {
        String slug = ideName == null ? "" : ideName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return slug.isEmpty() ? DEFAULT_IDE_PREFIX : slug;
    }
}
