package com.checkmarx.intellij.devassist.telemetry;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

public class TelemetryStartupActivity implements StartupActivity.DumbAware {

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    @Override
    public void runActivity(@NotNull Project project) {
        if (STARTED.compareAndSet(false, true)) {
            TelemetryService.startInstallTracking();
        }
    }
}
