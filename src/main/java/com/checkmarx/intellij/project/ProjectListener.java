package com.checkmarx.intellij.project;

import com.checkmarx.intellij.commands.results.Results;
import com.checkmarx.intellij.realtimeScanners.registry.ScannerRegistry;
import com.checkmarx.intellij.settings.global.GlobalSettingsComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import org.jetbrains.annotations.NotNull;

public class ProjectListener implements ProjectManagerListener {

    @Override
    public void projectOpened(@NotNull Project project) {
        ProjectManagerListener.super.projectOpened(project);
        project.getService(ProjectResultsService.class).indexResults(project, Results.emptyResults);
        if (new GlobalSettingsComponent().isValid()){
            ScannerRegistry scannerRegistry= new ScannerRegistry(project,project);
            scannerRegistry.registerAllScanners();
        }
    }
}
