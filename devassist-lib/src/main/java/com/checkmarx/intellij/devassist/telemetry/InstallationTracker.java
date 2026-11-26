package com.checkmarx.intellij.devassist.telemetry;

import com.checkmarx.ast.telemetry.TelemetryEvent;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Installation id and install event. Users who used the plugin before this version get an id but no install event.
 */
public final class InstallationTracker {

    private static final String DEFAULT_IDE_PREFIX = "jetbrains";

    private InstallationTracker() {
    }

    public static synchronized void ensureInstallationId(String ideName) {
        TelemetryInstallationState state = TelemetryInstallationState.getInstance();
        if (state == null || StringUtils.isNotBlank(state.getInstallationId())) {
            return;
        }
        boolean upgraded = isExistingInstallation(GlobalSettingsState.getInstance());
        state.setInstallationId(generateInstallationId(ideName));
        state.setInstallationTimestamp(Instant.now().toString());
        state.setInstallEventPending(!upgraded);
    }

    /**
     * @param sender returns true only when the CLI accepted the event; the event stays pending otherwise
     */
    public static void flushPendingInstallEvent(Predicate<TelemetryEvent.TelemetryEventBuilder> sender) {
        TelemetryInstallationState state = TelemetryInstallationState.getInstance();
        if (state == null || !state.isInstallEventPending() || StringUtils.isBlank(state.getInstallationId())) {
            return;
        }
        if (sender.test(TelemetryEventFactory.installEvent(state.getInstallationId(), state.getInstallationTimestamp()))) {
            state.setInstallEventPending(false);
        }
    }

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

    static String ideSlug(String ideName) {
        String slug = ideName == null ? "" : ideName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return slug.isEmpty() ? DEFAULT_IDE_PREFIX : slug;
    }
}
