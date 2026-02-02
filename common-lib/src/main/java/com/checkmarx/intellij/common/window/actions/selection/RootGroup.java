package com.checkmarx.intellij.common.window.actions.selection;

import com.checkmarx.intellij.common.commands.Scan;
import com.checkmarx.intellij.common.window.actions.CxToolWindowAction;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Base group containing the project, branch and scan selection groups
 */
public class RootGroup extends DefaultActionGroup implements DumbAware, CxToolWindowAction {

    private final Project project;
    @Getter
    private final ScanSelectionGroup scanSelectionGroup;
    @Getter
    private final BranchSelectionGroup branchSelectionGroup;
    private final ProjectSelectionGroup projectSelectionGroup;
    private final ResetSelectionAction resetSelectionAction;

    public RootGroup(@NotNull Project project, @Nullable ResetSelectionAction resetSelectionAction) {
        super();
        this.project = project;
        this.resetSelectionAction = resetSelectionAction;
        scanSelectionGroup = new ScanSelectionGroup(project);
        branchSelectionGroup = new BranchSelectionGroup(project, scanSelectionGroup);
        projectSelectionGroup = new ProjectSelectionGroup(project,
                                                          branchSelectionGroup,
                                                          scanSelectionGroup,
                                                          resetSelectionAction);
        addAll(projectSelectionGroup, branchSelectionGroup, scanSelectionGroup);
    }

    /**
     * Override all selections with a scanId from the manual field
     *
     * @param scanId scan id
     */
    public void override(String scanId) {
        setEnabled(false);
        branchSelectionGroup.clear();
        scanSelectionGroup.clear();
        CompletableFuture.supplyAsync(() -> {
            try {
                return Scan.scanShow(scanId);
            } catch (Exception e) {
                return null;
            }
        }).thenAccept(scan -> ApplicationManager.getApplication().invokeLater(() -> {
            if (scan != null) {
                projectSelectionGroup.override(scan);
            }
            setEnabled(true);
        }));
    }

    public void setEnabled(boolean enabled) {
        projectSelectionGroup.setEnabled(enabled);
        branchSelectionGroup.setEnabled(enabled);
        scanSelectionGroup.setEnabled(enabled);
        if (resetSelectionAction != null) {
            resetSelectionAction.setEnabled(enabled);
        }
        refreshPanel(project);
    }


    public void reset() {
        projectSelectionGroup.clear();
        branchSelectionGroup.clear();
        scanSelectionGroup.clear();
        refreshPanel(project);
        projectSelectionGroup.refresh();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
}
