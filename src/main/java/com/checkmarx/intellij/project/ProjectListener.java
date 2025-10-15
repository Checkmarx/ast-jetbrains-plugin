package com.checkmarx.intellij.project;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.commands.results.Results;
import com.checkmarx.intellij.realtimeScanners.configuration.ConfigurationManager;
import com.checkmarx.intellij.realtimeScanners.registry.ScannerRegistry;
import com.checkmarx.intellij.settings.global.GlobalSettingsComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

public class ProjectListener implements ProjectManagerListener {

    @Override
    public void projectOpened(@NotNull Project project) {
        ProjectManagerListener.super.projectOpened(project);
        project.getService(ProjectResultsService.class).indexResults(project, Results.emptyResults);
        if (new GlobalSettingsComponent().isValid()){
            ScannerRegistry scannerRegistry= new ScannerRegistry(project,project);
            scannerRegistry.registerAllScanners(project);
        }
    }
}
