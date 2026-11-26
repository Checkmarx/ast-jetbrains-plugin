package com.checkmarx.intellij.devassist.telemetry;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Kept apart from GlobalSettingsState, which the settings pages replace as a whole when they apply it.
 */
@Getter
@Setter
@State(name = "CheckmarxTelemetryInstallation", storages = @Storage("cx_telemetry_installation.xml"))
public class TelemetryInstallationState implements PersistentStateComponent<TelemetryInstallationState> {

    private String installationId = "";
    private String installationTimestamp = "";
    private boolean installEventPending = false;
    private List<PendingLoginFailure> pendingLoginFailures = new ArrayList<>();

    public static TelemetryInstallationState getInstance() {
        return ApplicationManager.getApplication().getService(TelemetryInstallationState.class);
    }

    @Override
    public TelemetryInstallationState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull TelemetryInstallationState state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
