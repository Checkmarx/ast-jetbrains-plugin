package com.checkmarx.intellij.tool.window.actions.selection;

import com.checkmarx.intellij.commands.Scan;
import com.checkmarx.intellij.tool.window.actions.CxToolWindowAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;

import java.util.concurrent.CompletableFuture;

/**
 * Base group containing the project, branch and scan selection groups
 */
public class RootGroup extends DefaultActionGroup implements DumbAware, CxToolWindowAction {

    private final Project project;
    private final ScanSelectionGroup scanSelectionGroup;
    private final ProjectSelectionGroup projectSelectionGroup;

    public RootGroup(Project project) {
        super();
        this.project = project;
        scanSelectionGroup = new ScanSelectionGroup(project);
        projectSelectionGroup = new ProjectSelectionGroup(project, scanSelectionGroup);
        addAll(projectSelectionGroup, scanSelectionGroup);
    }

    /**
     * Override all selections with a scanId from the manual field
     *
     * @param scanId scan id
     */
    public void override(String scanId) {
        setEnabled(false);
        scanSelectionGroup.clear();
        CompletableFuture.supplyAsync(() -> {
            try {
                return Scan.scanShow(scanId);
            } catch (Exception e) {
                return null;
            }
        }).thenAccept(scan -> {
            ApplicationManager.getApplication().invokeLater(() -> {
                if (scan != null) {
                    projectSelectionGroup.override(scan);
                }
                setEnabled(true);
            });
        });
    }

    public void setEnabled(boolean enabled) {
        projectSelectionGroup.setEnabled(enabled);
        scanSelectionGroup.setEnabled(enabled);
        refreshPanel(project);
    }
}
