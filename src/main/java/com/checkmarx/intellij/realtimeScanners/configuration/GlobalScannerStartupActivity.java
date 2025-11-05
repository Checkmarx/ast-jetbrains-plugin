package com.checkmarx.intellij.realtimeScanners.configuration;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

/**
 * Creates and wires the GlobalScannerController after project startup.
 */
public class GlobalScannerStartupActivity implements StartupActivity.DumbAware {
    @Override
    public void runActivity(@NotNull Project project) {
        ApplicationManager.getApplication().getService(GlobalScannerController.class);
    }
}

