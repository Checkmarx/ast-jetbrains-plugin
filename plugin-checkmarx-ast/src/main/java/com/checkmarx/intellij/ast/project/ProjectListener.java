package com.checkmarx.intellij.ast.project;

import com.checkmarx.intellij.ast.results.Results;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.devassist.registry.ScannerRegistry;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import org.jetbrains.annotations.NotNull;

public class ProjectListener implements ProjectManagerListener {

    @Override
    public void projectOpened(@NotNull Project project) {
        ProjectManagerListener.super.projectOpened(project);
        project.getService(ProjectResultsService.class).indexResults(project, Results.emptyResults);
        if (GlobalSettingsState.getInstance().isAuthenticated()) {
            ProgressManager.getInstance().runProcess(() -> {
                ScannerRegistry scannerRegistry = project.getService(ScannerRegistry.class);
                scannerRegistry.registerAllScanners(project);
            }, new EmptyProgressIndicator());

        }
    }
}
