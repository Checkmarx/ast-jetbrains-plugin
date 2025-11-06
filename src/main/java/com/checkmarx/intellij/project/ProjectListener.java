package com.checkmarx.intellij.project;


import com.checkmarx.intellij.commands.results.Results;

import com.checkmarx.intellij.realtimeScanners.configuration.RealtimeScannerManager;
import com.checkmarx.intellij.realtimeScanners.registry.ScannerRegistry;
import com.checkmarx.intellij.settings.global.GlobalSettingsComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

public class ProjectListener implements ProjectManagerListener {

    private  RealtimeScannerManager scannerManager;

    @Override
    public void projectOpened(@NotNull Project project) {
        ProjectManagerListener.super.projectOpened(project);
        project.getService(ProjectResultsService.class).indexResults(project, Results.emptyResults);
        if (new GlobalSettingsComponent().isValid()){
            ScannerRegistry scannerRegistry= project.getService(ScannerRegistry.class);
            System.out.println("From projectOpened");
            scannerRegistry.registerAllScanners(project);
        }
    }
}
