package com.checkmarx.intellij.project;


import com.checkmarx.intellij.commands.results.Results;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import org.jetbrains.annotations.NotNull;

/**
 * Listener for project lifecycle events.
 * Initializes project-level services when a project is opened.
 */
public class ProjectListener implements ProjectManagerListener {

    @Override
    public void projectOpened(@NotNull Project project) {
        ProjectManagerListener.super.projectOpened(project);
        // Initialize project results service with empty results
        project.getService(ProjectResultsService.class).indexResults(project, Results.emptyResults);
    }
}
