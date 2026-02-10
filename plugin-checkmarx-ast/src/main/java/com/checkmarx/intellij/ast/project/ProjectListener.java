package com.checkmarx.intellij.ast.project;

import com.checkmarx.intellij.ast.commands.Results;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import org.jetbrains.annotations.NotNull;

public class ProjectListener implements ProjectManagerListener {

    @Override
    public void projectOpened(@NotNull Project project) {
        ProjectManagerListener.super.projectOpened(project);
        project.getService(ProjectResultsService.class).indexResults(project, Results.emptyResults);
    }
}
