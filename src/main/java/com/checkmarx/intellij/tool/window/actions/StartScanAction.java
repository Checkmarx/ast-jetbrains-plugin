package com.checkmarx.intellij.tool.window.actions;

import com.checkmarx.ast.results.result.Result;
import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.commands.Scan;
import com.checkmarx.intellij.commands.TenantSetting;
import com.checkmarx.intellij.tool.window.CxToolWindowPanel;
import com.checkmarx.intellij.tool.window.actions.selection.BranchSelectionGroup;
import com.checkmarx.intellij.tool.window.actions.selection.ScanSelectionGroup;
import com.intellij.dvcs.repo.Repository;
import com.intellij.ide.ActivityTracker;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Collections;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class StartScanAction extends AnAction implements CxToolWindowAction {

    private static final Logger LOGGER = Utils.getLogger(StartScanAction.class);

    private boolean isPollingScan = false;
    private boolean scanTriggered = false;
    @Setter
    private static Boolean userHasPermissionsToScan;
    // state variable used to check if a scan is running when IDE restarts
    private boolean actionInitialized = false;

    private Project workspaceProject;
    private PropertiesComponent propertiesComponent;
    private CxToolWindowPanel cxToolWindowPanel;
    private ScheduledExecutorService pollScanExecutor;

    private static Task.Backgroundable pollScanTask = null;

    public StartScanAction() {
        super(Bundle.messagePointer(Resource.START_SCAN_ACTION));
    }

    public static Boolean getUserHasPermissionsToScan() {
        if (userHasPermissionsToScan == null) {
            try {
                userHasPermissionsToScan = TenantSetting.isScanAllowed();
            } catch (Exception ex) {
                userHasPermissionsToScan = false;
            }
        }
        return userHasPermissionsToScan;
    }

    /**
     * {@inheritDoc}
     * Start scan
     */
    @SneakyThrows
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Repository repository = Utils.getRootRepository(workspaceProject);
        boolean matchProject = isAstProjectMatchesWorkspaceProject();
        // Case it is a git repo check for project and branch match
        if (repository != null) {
            String storedBranch = Optional.ofNullable(propertiesComponent.getValue(Constants.SELECTED_BRANCH_PROPERTY)).orElse(Utils.EMPTY);
            if(storedBranch.equals(Constants.USE_LOCAL_BRANCH)) {
                storedBranch = getActiveBranch(workspaceProject);
            }
            boolean matchBranch = storedBranch.equals(Objects.requireNonNull(repository).getCurrentBranchName());
            if(matchBranch && matchProject) {
                createScan();
            } else {
                if (!matchProject) {
                    Utils.notifyScan(msg(Resource.PROJECT_DOES_NOT_MATCH_TITLE), msg(Resource.PROJECT_DOES_NOT_MATCH_QUESTION), workspaceProject, this::createScan, NotificationType.WARNING, msg(Resource.ACTION_SCAN_ANYWAY));
                } else {
                    Utils.notifyScan(msg(Resource.BRANCH_DOES_NOT_MATCH_TITLE), msg(Resource.BRANCH_DOES_NOT_MATCH_QUESTION), workspaceProject, this::createScan, NotificationType.WARNING, msg(Resource.ACTION_SCAN_ANYWAY));
                }
            }
        }
        // Case it is not a git repo, only check for project match
        else{
            if (matchProject) {
                createScan();
            } else{
                Utils.notifyScan(msg(Resource.PROJECT_DOES_NOT_MATCH_TITLE), msg(Resource.PROJECT_DOES_NOT_MATCH_QUESTION), workspaceProject, this::createScan, NotificationType.WARNING, msg(Resource.ACTION_SCAN_ANYWAY));
            }
        }
    }

    /**
     * Check if project in workspace matches the selected checkmarx plugin project
     *
     * @return True if matches. False otherwise
     */
    private boolean isAstProjectMatchesWorkspaceProject() {
        // Get the selected project from propertiesComponent
        String pluginProjectName = propertiesComponent.getValue("Checkmarx.SelectedProject");
        String workspaceProjectName = getRepositoryProjectName();

        // Return true if the selected project matches the expected project name
        return Utils.isNotBlank(pluginProjectName) &&
                workspaceProjectName != null &&
                pluginProjectName.equals(workspaceProjectName);
    }

    /**
     * Helper method to retrieve the repository project name
     *
     * @return The repository project name or null if unavailable
     */
    private String getRepositoryProjectName() {
        Repository repository = Utils.getRootRepository(workspaceProject);
        if (repository == null) {
            return null;
        }

        String repositoryInfo = repository.toLogString();
        int myUrlsIndex = repositoryInfo.indexOf("myUrls=[");
        if (myUrlsIndex != -1) {
            int start = myUrlsIndex + "myUrls=[".length();
            int end = repositoryInfo.indexOf("]", start);
            if (end != -1) {
                String url = repositoryInfo.substring(start, end).split(",")[0];
                return url.replaceFirst(".*://[a-zA-Z0-9.]+/", "").replaceFirst("\\.git$", "");
            }
        }
        return null;
    }

    /**
     * Create a backgroundable task which creates a new scan
     */
    private void createScan() {
        scanTriggered = true;

        Task.Backgroundable creatingScanTask = new Task.Backgroundable(workspaceProject, msg(Resource.CREATING_SCAN_TITLE)) {
            @SneakyThrows
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                String storedBranch = propertiesComponent.getValue(Constants.SELECTED_BRANCH_PROPERTY);
                if(storedBranch.equals(Constants.USE_LOCAL_BRANCH)) {
                    storedBranch = getActiveBranch(workspaceProject);
                }
                String storedProject = propertiesComponent.getValue(Constants.SELECTED_PROJECT_PROPERTY);

                LOGGER.info(msg(Resource.STARTING_SCAN_IDE, storedProject, storedBranch));

                com.checkmarx.ast.scan.Scan scan = Scan.scanCreate(Paths.get(Objects.requireNonNull(workspaceProject.getBasePath())).toString(), storedProject, storedBranch);



                LOGGER.info(msg(Resource.SCAN_CREATED_IDE, scan.getId(), scan.getStatus()));

                propertiesComponent.setValue(Constants.RUNNING_SCAN_ID_PROPERTY, scan.getId());
                ActivityTracker.getInstance().inc();
                pollScan(scan.getId());
                refreshBranchSelection(scan);
            }

            @Override
            public void onFinished() {
                super.onFinished();
                scanTriggered = false;
            }
        };

        ProgressManager.getInstance().run(creatingScanTask);
    }

    private void refreshBranchSelection(com.checkmarx.ast.scan.Scan scan) {
        BranchSelectionGroup branchSelectionGroup = cxToolWindowPanel.getRootGroup().getBranchSelectionGroup();
        if (branchSelectionGroup != null) {
            branchSelectionGroup.refresh(scan.getProjectId(), false);
        } else {
            LOGGER.warn("Unable to refresh branches: No branch selection available");
        }
    }



    /**
     * Create a backgroundable task which polls a scan to verify its status.
     *
     * @param scanId - scan id
     */
    public void pollScan(String scanId) {
        isPollingScan = true;

        pollScanTask = new Task.Backgroundable(workspaceProject, msg(Resource.SCAN_RUNNING_TITLE)) {
            @SneakyThrows
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                pollScanExecutor = Executors.newScheduledThreadPool(1);
                pollScanExecutor.scheduleAtFixedRate(pollingScan(scanId), 0, 15, TimeUnit.SECONDS);
                // Keep backgroundable task alive so that it isn't removed while the scan is still running
                do {
                    indicator.setText(msg(Resource.SCAN_RUNNING_TITLE));
                } while (!pollScanExecutor.isTerminated());
            }

            @SneakyThrows
            @Override
            public void onCancel() {
                super.onCancel();
                if (pollScanExecutor != null) {
                    pollScanExecutor.shutdown();
                }
            }

            @Override
            public void onFinished() {
                super.onFinished();
                isPollingScan = false;
                pollScanExecutor = null;
            }
        };

        ProgressManager.getInstance().run(pollScanTask);
    }

    /**
     * Polls a scan to check weather it already finished or not
     *
     * @param scanId - scan id to poll
     * @return - Runnable
     */
    private Runnable pollingScan(String scanId) {
        return () -> {
            try {
                com.checkmarx.ast.scan.Scan scan = Scan.scanShow(scanId);
                boolean isScanRunning = scan.getStatus().toLowerCase(Locale.ROOT).equals(Constants.SCAN_STATUS_RUNNING);

                if(isScanRunning){
                    LOGGER.info(msg(Resource.SCAN_RUNNING, scanId));
                } else {
                    LOGGER.info(msg(Resource.SCAN_FINISHED, scan.getStatus().toLowerCase()));
                    propertiesComponent.setValue(Constants.RUNNING_SCAN_ID_PROPERTY, Utils.EMPTY);
                    ActivityTracker.getInstance().inc();
                    pollScanExecutor.shutdown();

                    if(scan.getStatus().toLowerCase(Locale.ROOT).equals(Constants.SCAN_STATUS_COMPLETED)) {
                        Utils.notifyScan(msg(Resource.SCAN_FINISHED, scan.getStatus().toLowerCase()), msg(Resource.SCAN_FINISHED_LOAD_RESULTS), workspaceProject, () -> loadResults(scan), NotificationType.INFORMATION, msg(Resource.LOAD_CX_RESULTS));

                    }
                }
            } catch (IOException | URISyntaxException | InterruptedException | CxException e) {
                LOGGER.error(msg(Resource.ERROR_POLLING_SCAN, e.getMessage()), e);
            }
        };
    }

    /**
     * Load checkmarx plugin with results of triggered scan
     *
     * @param scan - checkmarx scan
     */
    private void loadResults(com.checkmarx.ast.scan.Scan scan) {
        LOGGER.info(msg(Resource.LOAD_RESULTS, scan.getId()));
        ScanSelectionGroup scanSelectionGroup = cxToolWindowPanel.getRootGroup().getScanSelectionGroup();
        propertiesComponent.setValue(Constants.SELECTED_BRANCH_PROPERTY, scan.getBranch());
        scanSelectionGroup.refresh(scan.getProjectId(), scan.getBranch(), true);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        try {
            super.update(e);

            e.getPresentation().setVisible(getUserHasPermissionsToScan());

            cxToolWindowPanel = getCxToolWindowPanel(e);
            workspaceProject = e.getProject();
            propertiesComponent = PropertiesComponent.getInstance(Objects.requireNonNull(workspaceProject));
            boolean isScanRunning = Utils.isNotBlank(propertiesComponent.getValue(Constants.RUNNING_SCAN_ID_PROPERTY));
            String storedProject = propertiesComponent.getValue(Constants.SELECTED_PROJECT_PROPERTY);
            String storedBranch = propertiesComponent.getValue(Constants.SELECTED_BRANCH_PROPERTY);

            boolean projectAndBranchSelected = Utils.isNotBlank(storedProject) && Utils.isNotBlank(storedBranch);

            // Check if IDE was restarted and there's a scan still running
            if (isScanRunning && !isPollingScan && !actionInitialized) {
                pollScan(propertiesComponent.getValue(Constants.RUNNING_SCAN_ID_PROPERTY));
            }

            actionInitialized = true;

            e.getPresentation().setEnabled(!isScanRunning && !isPollingScan && !scanTriggered && projectAndBranchSelected);
        }
        catch (Exception ex) {
            e.getPresentation().setEnabled(false);
        }
    }

    /**
     * Cancel backgroundable task
     */
    public static void cancelRunningScan() {
        pollScanTask.onCancel();
    }

    /**
     * Get bundled message from resource
     *
     * @param resource - resource message
     * @param params - message parameters
     * @return - message
     */
    private static String msg(Resource resource, Object... params) {
        return Bundle.message(resource, params);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    public String getActiveBranch(Project project) {
        return Utils.getRootRepository(project) == null ? null : Objects.requireNonNull(Utils.getRootRepository(project)).getCurrentBranchName();
    }
}
