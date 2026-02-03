package com.checkmarx.intellij.devassist.inspection;

import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.resources.Resource;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.problems.ProblemHelper;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import static com.checkmarx.intellij.devassist.utils.DevAssistConstants.Keys.SCHEDULER_INSTANCE_KEY;
import static java.lang.String.format;

/**
 * Schedule and execute debounced scans for files in the background.
 * If multiple scan requests are made for the same file within a short period,
 * only the latest request will be executed after a delay. This helps to avoid
 * redundant scans and improves performance.
 */
public class CxOneAssistScanScheduler {

    private static final Logger LOGGER = Logger.getInstance(CxOneAssistScanScheduler.class);
    private static final int SCHEDULED_DELAY = 1000;

    private final Project project;
    // Use per-file Alarm for precise debouncing and cancellation
    private final Map<String, Alarm> fileAlarms = new ConcurrentHashMap<>();
    // Use per-file ProgressIndicator for cancellation and progress feedback
    private final Map<String, ProgressIndicator> scanIndicators = new ConcurrentHashMap<>();
    private final Map<String, Long> scanRequestTimeMap = new ConcurrentHashMap<>();
    private final Map<String, Long> lastRestartTimeMap = new ConcurrentHashMap<>(); // Track the last restart per file
    private final ReentrantLock lock = new ReentrantLock();
    private final CxOneAssistInspectionMgr cxOneAssistInspectionMgr = new CxOneAssistInspectionMgr();


    /**
     * Private constructor.
     *
     * @param project - The IntelliJ Project instance
     */
    private CxOneAssistScanScheduler(@NotNull Project project) {
        this.project = project;
    }

    /**
     * Returns the singleton instance of CxOneAssistScanScheduler for the given project.
     *
     * @param project - The IntelliJ Project instance
     * @return the singleton CxOneAssistScanScheduler instance for the project
     */
    public static CxOneAssistScanScheduler getInstance(Project project) {
        CxOneAssistScanScheduler existingScheduler = project.getUserData(SCHEDULER_INSTANCE_KEY);
        if (existingScheduler != null) return existingScheduler;
        CxOneAssistScanScheduler newScheduler = new CxOneAssistScanScheduler(project);
        project.putUserData(SCHEDULER_INSTANCE_KEY, newScheduler);
        return newScheduler;
    }

    /**
     * Schedules a debounced scan for the given file. If a scan is already pending for this file,
     * the previous request is canceled and a new one is scheduled. Uses adaptive debouncing to handle
     * rapid file modifications effectively.
     * <p>
     * Its update the scan results for all supported engines.
     *
     * @param filePath      - The file path for which to schedule the scan
     * @param problemHelper - The {@link ProblemHelper} instance containing necessary context for creating problem descriptors
     * @param scanEngine    - The scan engine to be used for scanning the file (e.g., OSS, ASCA, ALL)
     */
    public boolean scheduleScan(@NotNull String filePath, @NotNull ProblemHelper problemHelper, ScanEngine scanEngine) {
        try {
            if (isProjectDisposed("scheduling scan", filePath)) {
                return false;
            }
            if (Objects.isNull(scanEngine)) {
                LOGGER.warn(format("RTS: Cant schedule scan! scan engine is not available for file: %s", filePath));
                return false;
            }
            lock.lock();
            try {
                // Cancel any pending/running scan for this file before scheduling a new one
                cancelPendingAndRunningScan(filePath);
                long requestTime = System.currentTimeMillis();
                scanRequestTimeMap.put(filePath, requestTime);
                // Use per-file Alarm for debouncing
                Alarm alarm = fileAlarms.computeIfAbsent(filePath, k -> new Alarm(Alarm.ThreadToUse.POOLED_THREAD, project));
                alarm.addRequest(() -> executeBackgroundScan(filePath, problemHelper, requestTime, scanEngine), SCHEDULED_DELAY);
            } finally {
                lock.unlock();
            }
            return true;
        } catch (Exception e) {
            LOGGER.warn(format("RTS: Failed to schedule scan for %s", filePath), e);
            return false;
        }
    }

    /**
     * Executes the scan in a background thread and restart the files to update the result on UI scan after completion.
     *
     * @param filePath      - The file path for which to execute the scan
     * @param problemHelper - The {@link ProblemHelper} instance containing necessary context for creating problem descriptors
     * @param requestTime   scan request time
     */
    private void executeBackgroundScan(@NotNull String filePath, @NotNull ProblemHelper problemHelper, long requestTime, ScanEngine scanEngine) {
        if (isRequestOutdated(filePath, requestTime)) {
            return;
        }
        // Submit the task to execute with a progress bar
        new Task.Backgroundable(project, Bundle.message(Resource.STARTING_CHECKMARX_SCAN), true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setText("Checkmarx is Scanning File : " + problemHelper.getFile().getName());
                indicator.setIndeterminate(true);
                scanIndicators.put(filePath, indicator); // Track this scan
                try {
                    runScan(filePath, problemHelper, scanEngine);
                } catch (Exception e) {
                    LOGGER.warn(format("RTS: Error occurred while executing scan for file: %s", filePath), e);
                } finally {
                    removeProgressIndicator(filePath);
                }
            }

            // Add cancellation support through ProgressIndicator stop
            @Override
            public void onCancel() {
                removeProgressIndicator(filePath);
            }
        }.queue();
    }

    /**
     * Executes a scan operation for a given file while updating progress indicators to provide feedback during the process.
     *
     * @param filePath      the path of the file to be scanned cannot be null
     * @param problemHelper a {@link ProblemHelper} instance containing context for creating problem descriptors, cannot be null
     */
    private void runScan(@NotNull String filePath, @NotNull ProblemHelper problemHelper, ScanEngine scanEngine) {
        try {
            LOGGER.info(format("RTS: Scheduled scan started for file: %s", filePath));
            List<ScanIssue> scanIssues = cxOneAssistInspectionMgr.scanFile(
                    problemHelper.getFilePath(), problemHelper.getFile(), scanEngine);

            if (scanIssues.isEmpty()) {
                LOGGER.info(format("RTS: Scheduled scan completed with no issues for file: %s", filePath));
                resetCachedData(problemHelper, scanEngine);
            } else {
                ApplicationManager.getApplication().invokeLater(() -> {
                    List<ProblemDescriptor> descriptors = cxOneAssistInspectionMgr.createProblemDescriptorsWithoutDecoration(
                            problemHelper.toBuilder(problemHelper).scanIssueList(scanIssues).build());

                    cacheScanResults(problemHelper, filePath, scanIssues, descriptors, scanEngine);
                }, ModalityState.NON_MODAL);
            }
            ApplicationManager.getApplication().runReadAction(() ->
                    cxOneAssistInspectionMgr.updateScanSourceFlag(problemHelper.getFile(), Boolean.TRUE)); // To identify the scan source
            LOGGER.info(format("RTS: Scheduled scan completed for file: %s", filePath));
        } catch (Exception e) {
            LOGGER.warn(format("RTS: Exception occurred while running scheduled scan for the file: %s", filePath), e);
        } finally {
            restartFileAfterScan(problemHelper);
        }
    }

    /**
     * Cancels any pending and running scan for the given file.
     *
     * @param filePath - The file path for which to cancel the scan
     */
    private void cancelPendingAndRunningScan(@NotNull String filePath) {
        lock.lock();
        try {
            Alarm alarm = fileAlarms.get(filePath);
            if (alarm != null) {
                alarm.cancelAllRequests();
                removeProgressIndicator(filePath);
            }
        } catch (Exception e) {
            LOGGER.warn(format("RTS: Failed to cancel the previous schedule request for file: %s", filePath), e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Cancels the progress indicator for the given file if it is running.
     */
    private void removeProgressIndicator(@NotNull String filePath) {
        ProgressIndicator runningIndicator = scanIndicators.remove(filePath);
        if (runningIndicator != null && !runningIndicator.isCanceled()) {
            runningIndicator.cancel(); // Cancel the running scan
            LOGGER.warn(format("RTS: Previous scan is canceled for file %s.", filePath));
        }
    }

    /**
     * Determines if a scan request is outdated by comparing the request time with the most recent scan request time
     * associated with the specified file path. If the request time does not match the latest request time, the request
     * is considered outdated.
     *
     * @param filePath    the path of the file for which the scan request is being evaluated must not be null
     * @param requestTime the timestamp of the scan request
     * @return true if the scan request is outdated, false otherwise
     */
    private boolean isRequestOutdated(@NotNull String filePath, long requestTime) {
        lock.lock();
        try {
            long latestRequest = scanRequestTimeMap.getOrDefault(filePath, 0L);
            if (latestRequest != requestTime) {
                LOGGER.warn(format("RTS: Newer scan request found. Skipping this scan for file: %s.", filePath));
                return true;
            }
        } finally {
            lock.unlock();
        }
        return false;
    }

    /**
     * Caches the scan results for future use.
     */
    private void cacheScanResults(@NotNull ProblemHelper problemHelper, @NotNull String filePath,
                                  @NotNull List<ScanIssue> scanIssues, @NotNull List<ProblemDescriptor> problems, ScanEngine scanEngine) {
        ProblemHolderService holderService = problemHelper.getProblemHolderService();
        if (scanEngine == ScanEngine.ALL) {
            holderService.addScanIssues(filePath, scanIssues);
            holderService.addProblemDescriptors(filePath, problems);
        } else {
            holderService.removeScanIssuesByFileAndScanner(scanEngine.name(), filePath);
            holderService.mergeScanIssues(filePath, scanIssues);
            holderService.removeProblemDescriptorsForFileByScanner(problemHelper.getFilePath(), scanEngine);
            holderService.mergeProblemDescriptors(filePath, problems);
        }
    }

    /**
     * Clears the cached scan results.
     */
    private void resetCachedData(@NotNull ProblemHelper problemHelper, ScanEngine scanEngine) {
        ProblemHolderService holderService = problemHelper.getProblemHolderService();
        if (scanEngine == ScanEngine.ALL) {
            holderService.addScanIssues(problemHelper.getFilePath(), Collections.emptyList());
            holderService.addProblemDescriptors(problemHelper.getFilePath(), Collections.emptyList());
        } else {
            holderService.removeScanIssuesByFileAndScanner(scanEngine.name(), problemHelper.getFilePath());
            holderService.removeProblemDescriptorsForFileByScanner(problemHelper.getFilePath(), scanEngine);
        }
    }

    /**
     * Restarts the file to update the result on UI scan after completion.
     * <p>
     * Ensures that the file is restarted in a write-safe context.
     */
    private void restartFileAfterScan(@NotNull ProblemHelper problemHelper) {
        String filePath = problemHelper.getFilePath();
        long now = System.currentTimeMillis();
        Long lastRestart = lastRestartTimeMap.get(filePath);
        // Avoid redundant restarts within the 500 ms window
        if (lastRestart != null && now - lastRestart < 500) {
            return;
        }
        lastRestartTimeMap.put(filePath, now);

        // Ensure this logic executes in a write-safe context
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!isProjectDisposed("restarting file after scan", filePath)
                    && problemHelper.getFile().isValid()) {
                DaemonCodeAnalyzer.getInstance(project).restart(problemHelper.getFile());
                LOGGER.warn(format("RTS: DaemonCodeAnalyzer restarted for file: %s", problemHelper.getFile().getName()));
            }
        }, ModalityState.NON_MODAL); // Ensure the UI writing is safe in a non-modal state
    }

    /**
     * Checks if the project is disposed.
     *
     * @return true if the project is disposed, false otherwise.
     */
    private boolean isProjectDisposed(String action, String fileName) {
        if (project.isDisposed()) {
            LOGGER.warn(format("RTS: Project disposed during %s. for file: %s", action, fileName));
            removeProgressIndicator(fileName);
            return true;
        }
        return false;
    }
}
