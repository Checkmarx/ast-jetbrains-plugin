package com.checkmarx.intellij.tool.window.actions;

import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.commands.Scan;
import com.checkmarx.intellij.tool.window.CxToolWindowPanel;
import com.checkmarx.intellij.tool.window.actions.selection.RootGroup;
import com.checkmarx.intellij.tool.window.actions.selection.ScanSelectionGroup;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class StartScanAction extends AnAction implements CxToolWindowAction {
    @Getter
    @Setter
    private static boolean enabled = true;

    private Project workspaceProject;
    private PropertiesComponent propertiesComponent;

    private CxToolWindowPanel cxToolWindowPanel;

    private static Task.Backgroundable creatingScanTask;

    private ScheduledExecutorService pollScanExecutor;

    public StartScanAction() {
        super(Bundle.messagePointer(Resource.START_SCAN_ACTION));
    }

    /**
     * {@inheritDoc}
     * Start scan
     */
    @SneakyThrows
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        cxToolWindowPanel = getCxToolWindowPanel(e);
        workspaceProject = e.getProject();
        propertiesComponent = PropertiesComponent.getInstance(Objects.requireNonNull(workspaceProject));
        Repository repository = getRootRepository(workspaceProject);
        String storedBranch = propertiesComponent.getValue(Constants.SELECTED_BRANCH_PROPERTY);
        String storedProject = propertiesComponent.getValue(Constants.SELECTED_PROJECT_PROPERTY);
        boolean matchBranch = storedBranch.equals(repository.getCurrentBranchName());
        System.out.println("StoredProject: " + storedProject);
        System.out.println("Project: " +repository.getPresentableUrl());
        //Change it by checking if the files in the project exist
        boolean matchProject = storedProject.equals(workspaceProject.getName()) || repository.getPresentableUrl().endsWith(StringUtils.substringAfterLast(storedProject, "/"));

        if(matchBranch && matchProject) {
            startScan();
        } else {
            System.out.println(" Scan no eligible. matchBranch: " + matchBranch + " matchProject: " + matchProject);
            System.out.println(" ===> Stored Branch: " + storedBranch);
            System.out.println(" ===> Repo Branch: " + repository.getCurrentBranchName());
            System.out.println(" ===> Stored Project: " + StringUtils.substringAfterLast(storedProject, "/"));
            System.out.println(" ===> Presentable Project Url: " + repository.getPresentableUrl());

            if (!matchProject) {
                Utils.notifyScan("Current project doesn't match", "Project in workspace doesn't match checkmarx project. Do you want to proceed?", workspaceProject, this::startScan, NotificationType.WARNING, "Scan anyway");
            } else {
                Utils.notifyScan("Current branch doesn't match", "Git branch doesn't match checkmarx branch. Do you want to proceed?", workspaceProject, this::startScan, NotificationType.WARNING, "Scan anyway");
            }
        }
    }

    private void startScan() {
        String storedBranch = propertiesComponent.getValue(Constants.SELECTED_BRANCH_PROPERTY);
        String storedProject = propertiesComponent.getValue(Constants.SELECTED_PROJECT_PROPERTY);

        System.out.println(" Initiating Scan...");
        System.out.println(" Source Path: " + Paths.get(Objects.requireNonNull(workspaceProject.getBasePath())));
        System.out.println(" Project Name: " + storedProject);
        System.out.println(" Project Branch: " + storedBranch);

        setEnabled(false);

        creatingScanTask = new Task.Backgroundable(workspaceProject, "Creating scan...") {
            @SneakyThrows
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                com.checkmarx.ast.scan.Scan scan = Scan.scanCreate(Paths.get(Objects.requireNonNull(workspaceProject.getBasePath())).toString(), storedProject, storedBranch);
                CancelScanAction.setEnabled(true);
                System.out.println("Scan created with id: " + scan.getId() + " with status: " + scan.getStatus());
                propertiesComponent.setValue("RunningScanId", scan.getId());
                Thread.sleep(1000);
                pollScan(scan.getId(), indicator);
            }

            @SneakyThrows
            @Override
            public void onCancel() {
                super.onCancel();
                pollScanExecutor.shutdown();
            }
        };

        ProgressManager.getInstance().run(creatingScanTask);
    }

    private void pollScan(String scanId, ProgressIndicator indicator) {
        indicator.setText("Scan running...");
        pollScanExecutor = Executors.newScheduledThreadPool(1);
        pollScanExecutor.scheduleAtFixedRate(pollingScan(scanId), 0, 10, TimeUnit.SECONDS);
        do {
            indicator.setText("Scan running...");
        } while (!pollScanExecutor.isTerminated());
    }

    private Runnable pollingScan(String scanId) {
        return () -> {
            try {
                com.checkmarx.ast.scan.Scan scan = Scan.scanShow(scanId);
                boolean isScanRunning = !scan.getStatus().toLowerCase(Locale.ROOT).equals("completed") && !scan.getStatus().toLowerCase(Locale.ROOT).equals("partial");

                if(isScanRunning){
                    System.out.println("Scan running...");
                } else {
                    System.out.println("Scan finished with status: " + scan.getStatus());
                    pollScanExecutor.shutdown();
                    CancelScanAction.setEnabled(false);
                    setEnabled(true);
                    Utils.notifyScan("Scan finished with status " + scan.getStatus(), "Do you want to reload results?", workspaceProject, () -> reloadResults(scan), NotificationType.INFORMATION, "Reload checkmarx results");
                }
            } catch (IOException | URISyntaxException | InterruptedException | CxConfig.InvalidCLIConfigException | CxException e) {
                e.printStackTrace();
            }
        };
    }

    private void reloadResults(com.checkmarx.ast.scan.Scan scan) {
        System.out.println("Reloading results...");
        RootGroup rootGroup = cxToolWindowPanel.getRootGroup();
        ScanSelectionGroup scanSelectionGroup = rootGroup.getScanSelectionGroup();
        scanSelectionGroup.refresh(scan.getProjectId(), scan.getBranch(), true);
    }

    @Nullable
    protected final Repository getRootRepository(Project project) {
        List<Repository> repositories = VcsRepositoryManager.getInstance(project)
                .getRepositories()
                .stream()
                .sorted(Comparator.comparing(r -> r.getRoot()
                        .toNioPath()))
                .collect(Collectors.toList()); // TODO: check if we can use unmodified list
        Repository repository = null;
        if (CollectionUtils.isNotEmpty(repositories)) {
            repository = repositories.get(0);
            for (int i = 1; i < repositories.size(); i++) {
                if (!repositories.get(i).getRoot().toNioPath().startsWith(repository.getRoot().toNioPath())) {
                    repository = null;
                    break;
                }
            }
        }
        return repository;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
        e.getPresentation().setEnabled(isEnabled());
    }

    public static void cancelStartScanAction() {
        creatingScanTask.onCancel();
    }
}
