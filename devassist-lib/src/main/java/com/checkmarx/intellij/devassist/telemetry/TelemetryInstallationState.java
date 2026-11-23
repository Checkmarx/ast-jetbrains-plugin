package com.checkmarx.intellij.devassist.telemetry;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

/**
 * Installation id and the install event that still has to be delivered.
 * Kept apart from GlobalSettingsState, because the settings pages replace that state as a whole when they apply it.
 */
@Getter
@Setter
@State(name = "CheckmarxTelemetryInstallation", storages = @Storage("cx_telemetry_installation.xml"))
public class TelemetryInstallationState implements PersistentStateComponent<TelemetryInstallationState> {

    private String installationId = "";
    private String installationTimestamp = "";
    private boolean installEventPending = false;

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
