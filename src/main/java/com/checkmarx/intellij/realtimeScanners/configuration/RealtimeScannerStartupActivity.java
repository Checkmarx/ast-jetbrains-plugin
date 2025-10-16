package com.checkmarx.intellij.realtimeScanners.configuration;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

/**
 * Creates and wires the RealtimeScannerManager after project startup.
 * Evaluates current flags and starts/stops placeholder scanners.
 */
public class RealtimeScannerStartupActivity implements StartupActivity.DumbAware {
    @Override
    public void runActivity(@NotNull Project project) {
        RealtimeScannerManager manager = new RealtimeScannerManager(project);
        // Ensure disposal with project lifecycle
        Disposer.register(project, manager);
    }
}

