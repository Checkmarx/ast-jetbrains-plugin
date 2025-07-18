package com.checkmarx.intellij.tool.window.actions.selection;

import com.checkmarx.ast.scan.Scan;
import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.BranchChangeListener;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Action group for selecting a branch in the UI.
 */
public class BranchSelectionGroup extends BaseSelectionGroup {

    private static final Logger LOGGER = Utils.getLogger(BranchSelectionGroup.class);

    private final ScanSelectionGroup scanSelectionGroup;

    private String projectId = null;
    private List<String> branches = Collections.emptyList();

    public BranchSelectionGroup(@NotNull Project project,
                                @NotNull ScanSelectionGroup scanSelectionGroup) {
        super(project);
        this.scanSelectionGroup = scanSelectionGroup;
        project.getMessageBus().connect().subscribe(BranchChangeListener.VCS_BRANCH_CHANGED, new OnBranchChange());
    }

    @Override
    protected @NotNull String getTitle() {
        if (getChildrenCount() == 0) {
            return Bundle.message(Resource.BRANCH_SELECT_PREFIX) + ": " + (isEnabled() ? setDefaultBranch() : "...");
        }
        String storedBranch = propertiesComponent.getValue(Constants.SELECTED_BRANCH_PROPERTY);
        return Bundle.message(Resource.BRANCH_SELECT_PREFIX)
               + ": "
               + (StringUtils.isBlank(storedBranch) ? setDefaultBranch() : storedBranch);
    }

    private String setDefaultBranch() {
        if(branches == null || branches.isEmpty())  {
            return NONE_SELECTED;
        }
        String activeBranch = getActiveBranch();
        if(activeBranch == null) {
            return NONE_SELECTED;
        }
        if(branches.contains(activeBranch)) {
            updateActiveBranchAndFetchScans(activeBranch);
            return activeBranch;
        }
        updateLocalBranch();
        return branches.get(0);

    }

    private void updateLocalBranch() {
        propertiesComponent.setValue(Constants.SELECTED_BRANCH_PROPERTY, Constants.USE_LOCAL_BRANCH);
        scanSelectionGroup.clear();
        setEnabled(true);
        refreshPanel(project);
    }


    @Override
    protected void clear() {
        projectId = null;
        branches = Collections.emptyList();
        scanSelectionGroup.clear();
        propertiesComponent.setValue(Constants.SELECTED_BRANCH_PROPERTY, null);
        removeAll();
    }

    @Override
    protected void override(Scan scan) {
        propertiesComponent.setValue(Constants.SELECTED_BRANCH_PROPERTY, scan.getBranch());
        scanSelectionGroup.override(scan);
    }

    /**
     * Remove all children and repopulate by getting a project's list of branches.
     *
     * @param projectId selected project
     * @param inherit   whether to inherit the branch
     */
    public void refresh(String projectId, boolean inherit) {
        setEnabled(false);
        removeAll();
        this.projectId = projectId;
        CompletableFuture.supplyAsync(() -> {
            List<String> branches = null;
            try {
                boolean isSCMProject = Utils.getRootRepository(project) != null;
                branches = com.checkmarx.intellij.commands.Project.getBranches(UUID.fromString(projectId), isSCMProject);
            } catch (Exception e) {
                LOGGER.warn(e);
                LOGGER.error(Resource.CANNOT_FIND_BRANCH + e.getMessage());
            }
            return Optional.ofNullable(branches).orElse(Collections.emptyList());
        }).thenAccept((List<String> branches) -> ApplicationManager.getApplication().invokeLater(() -> {
            this.branches = branches;
            String storedBranch = propertiesComponent.getValue(Constants.SELECTED_BRANCH_PROPERTY);
            String activeBranch = getActiveBranch();
            branches.forEach(branch -> add(new Action(projectId, branch)));
            for (String branch : branches) {
                if(Objects.equals(branch, activeBranch)) {
                    updateActiveBranchAndFetchScans(branch);
                    break;
                }
                else if (branch.equals(storedBranch) && !branch.equals(Constants.USE_LOCAL_BRANCH)) {
                    refreshScanGroup(projectId, branch, false);
                }
            }
            setEnabled(true);
            refreshPanel(project);
        }));
    }

    private void updateActiveBranchAndFetchScans(String branchName) {
        propertiesComponent.setValue(Constants.SELECTED_BRANCH_PROPERTY, branchName);
        refreshScanGroup(projectId, branchName, false);
    }
    /**
     * Repopulate the scan selection according to the given branch
     *  @param projectId selected project
     * @param branch    selected branch
     * @param autoSelectLatest flag indication if latest scan should be auto selected
     */
    private void refreshScanGroup(String projectId, String branch, boolean autoSelectLatest) {
        scanSelectionGroup.refresh(projectId, branch, autoSelectLatest);
        refreshPanel(project);
    }

    /**
     * Get branch name if there is one repository identifiable as the root repository.
     * Get all active repositories and sort them by path.
     * If all other repos' paths have the first repo's path as a prefix, then the first repo is a root repo.
     *
     * @return active branch name or null
     */
    @Nullable
    public String getActiveBranch() {
        return Utils.getRootRepository(project) == null ? null : Objects.requireNonNull(Utils.getRootRepository(project)).getCurrentBranchName();
    }

    /**
     * Action performed when a branch is selected
     */
    private class Action extends AnAction implements DumbAware {

        private final String projectId;

        public Action(String projectId, String branch) {
            super(branch);
            getTemplatePresentation().setText(() -> branch, false);
            this.projectId = projectId;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            String branch = getTemplatePresentation().getText();
            propertiesComponent.setValue(Constants.SELECTED_BRANCH_PROPERTY, branch);
            scanSelectionGroup.clear();
            if(!branch.equals(Constants.USE_LOCAL_BRANCH)) {
                refreshScanGroup(projectId, branch, true);
            }
            else {
                refreshPanel(project);
            }
        }
    }

    /**
     * Listener executed when the user changes branch on the VCS widget.
     */
    private class OnBranchChange implements BranchChangeListener {

        /**
         * {@inheritDoc}
         * Log the event and ignore it, we should only change Checkmarx One branch when the branch change is confirmed.
         */
        @Override
        public void branchWillChange(@NotNull String branchName) {
            LOGGER.info("Got branch will change event for " + branchName);
        }

        /**
         * {@inheritDoc}
         * Trigger a branch change in Checkmarx One if the selected branch matches an available Checkmarx One branch for the current project.
         */
        @Override
        public void branchHasChanged(@NotNull String branchName) {
            LOGGER.info("Got branch has changed event for " + branchName);
            if (projectId == null) {
                LOGGER.info("No project selected, ignoring branch change");
                return;
            }
            if (branches.isEmpty()) {
                LOGGER.info("Empty branch list for project " + projectId + ", ignoring branch change");
                return;
            }
            // branchName can have the remote (e.g. origin/master)
            // so we use getActiveBranch to always get the local name
            // if active branch returns null we don't have a root repo, so we don't enable the change
            branchName = getActiveBranch();
            if (branchName == null) {
                LOGGER.info("Unable to determine the root repo, ignoring branch change");
                return;
            }
            if (branchName.equals(propertiesComponent.getValue(Constants.SELECTED_BRANCH_PROPERTY))) {
                LOGGER.info("New branch name is equal to current branch, ignoring branch change");
                return;
            }
            for (String branch : branches) {
                if (branch.equals(branchName)) {
                    LOGGER.info("Matching branch found. Switching the branch to " + branchName);
                    propertiesComponent.setValue(Constants.SELECTED_BRANCH_PROPERTY,
                                                 branchName);
                    refreshScanGroup(projectId, branchName, true);
                    return;
                }
            }
            LOGGER.info("No matching branch found for " + branchName);
        }
    }
}
