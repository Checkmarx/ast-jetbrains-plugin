package com.checkmarx.intellij.project;


import com.checkmarx.intellij.commands.results.Results;

import com.checkmarx.intellij.realtimeScanners.configuration.RealtimeScannerManager;
import com.checkmarx.intellij.realtimeScanners.registry.ScannerRegistry;
import com.checkmarx.intellij.settings.global.GlobalSettingsComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import org.jetbrains.annotations.NotNull;

public class ProjectListener implements ProjectManagerListener {

    private  RealtimeScannerManager scannerManager;

    @Override
    public void projectOpened(@NotNull Project project) {
        ProjectManagerListener.super.projectOpened(project);
        scannerManager= project.getService(RealtimeScannerManager.class);
        project.getService(ProjectResultsService.class).indexResults(project, Results.emptyResults);
        if (new GlobalSettingsComponent().isValid()){
            ScannerRegistry scannerRegistry= new ScannerRegistry(project,project,scannerManager);
            scannerRegistry.registerAllScanners(project);
        }
    }
}
