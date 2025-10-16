package com.checkmarx.intellij.realtimeScanners.configuration;

import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.settings.SettingsListener;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Manager: starts/stops realtime scanners based on settings toggles.
 * Uses ConfigurationManager to listen to specific realtime checkbox changes (mirrors VS Code affectsConfiguration).
 */
public class RealtimeScannerManager implements Disposable, SettingsListener {

    public enum ScannerKind { OSS, SECRETS, CONTAINERS, IAC }

    private final Project project;
    private final Map<ScannerKind, Boolean> active = new EnumMap<>(ScannerKind.class);
    private final MessageBusConnection settingsConnection;


    public RealtimeScannerManager(@NotNull Project project) {
        this.project = project;
        this.settingsConnection = ApplicationManager.getApplication().getMessageBus().connect();
        this.settingsConnection.subscribe(SettingsListener.SETTINGS_APPLIED, this);
        syncAll();
    }

    /** Called when any settings are applied (may include auth changes); resync global state */
    @Override
    public void settingsApplied() {
        syncAll();
    }

    private synchronized void syncAll() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        if (!state.isAuthenticated()) {
            // Stop all if not authenticated
            for (ScannerKind kind : ScannerKind.values()) {
                stop(kind);
            }
            return;
        }
        update(ScannerKind.OSS, state.isOssRealtime());
        update(ScannerKind.SECRETS, state.isSecretDetectionRealtime());
        update(ScannerKind.CONTAINERS, state.isContainersRealtime());
        update(ScannerKind.IAC, state.isIacRealtime());
    }

    private void update(ScannerKind kind, boolean shouldRun) {
        boolean running = active.getOrDefault(kind, false);
        if (shouldRun && !running) {
            start(kind);
        } else if (!shouldRun && running) {
            stop(kind);
        }
    }

    private void start(ScannerKind kind) {
        active.put(kind, true);
        // TODO: Instantiate real scanner service & listeners (future scope)
    }

    private void stop(ScannerKind kind) {
        if (active.remove(kind) != null) {
            // TODO: Dispose real scanner service & listeners (future scope)
        }
    }

    /** Public API: query whether a scanner is currently active (running) */
    public boolean isScannerActive(String engineName) {
        if (engineName == null) return false;
        try {
            ScannerKind kind = ScannerKind.valueOf(engineName.toUpperCase());
            return active.getOrDefault(kind, false);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    @Override
    public void dispose() {
        for (ScannerKind kind : ScannerKind.values()) {
            stop(kind);
        }
        settingsConnection.dispose();

    }
}
