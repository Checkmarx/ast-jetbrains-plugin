package com.checkmarx.intellij.devassist.listeners;

import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.devassist.registry.ScannerRegistry;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import org.jetbrains.annotations.NotNull;

/**
 * ProjectListener is responsible for listening for project open events and registering all scanners for the project.
 */
public class DevAssistProjectListener implements ProjectManagerListener {

    @Override
    public void projectOpened(@NotNull Project project) {
        ProjectManagerListener.super.projectOpened(project);
        if (GlobalSettingsState.getInstance().isAuthenticated()) {
            ProgressManager.getInstance().runProcess(() -> {
                ScannerRegistry scannerRegistry = project.getService(ScannerRegistry.class);
                scannerRegistry.registerAllScanners(project);
            }, new EmptyProgressIndicator());

        }
    }
}
