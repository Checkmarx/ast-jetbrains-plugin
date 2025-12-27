package com.checkmarx.intellij.devassist.inspection;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.problems.ProblemHelper;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
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
    private final Map<String, Long> lastRestartTimeMap = new ConcurrentHashMap<>(); // Track last restart per file
    private final ReentrantLock lock = new ReentrantLock();
    private final CxOneAssistInspectionMgr cxOneAssistInspectionMgr = new CxOneAssistInspectionMgr();

    private CxOneAssistScanScheduler(@NotNull Project project) {
        this.project = project;
    }

    /**
     * Returns the singleton instance of CxOneAssistScanScheduler for the given project.
     *
     * @param project - The IntelliJ Project instance
     * @return the singleton CxOneAssistScanScheduler instance for the project
     */
    static CxOneAssistScanScheduler getInstance(Project project) {
        CxOneAssistScanScheduler existingScheduler = project.getUserData(SCHEDULER_INSTANCE_KEY);
        if (existingScheduler != null) {
            LOGGER.warn(format("RTS: Existing scheduler found for project: %s", project.getName()));
            return existingScheduler;
        }
        CxOneAssistScanScheduler newScheduler = new CxOneAssistScanScheduler(project);
        project.putUserData(SCHEDULER_INSTANCE_KEY, newScheduler);
        return newScheduler;
    }

    /**
     * Schedules a debounced scan for the given file. If a scan is already pending for this file,
     * the previous request is cancelled and a new one is scheduled. The scan will only execute
     * after {@link #SCHEDULED_DELAY} milliseconds of no new scan requests for this file.
     *
     * @param problemHelper - The {@link ProblemHelper} instance containing necessary context for creating problem descriptors
     */
    boolean scheduleScan(@NotNull String filePath, @NotNull ProblemHelper problemHelper) {
        try {
            if (isProjectDisposed("scheduling scan", problemHelper.getFile().getName())) {
                return false;
            }
            lock.lock();
            try {
                // Cancel any pending/running scan for this file
                cancelPendingAndRunningScan(filePath);
                long requestTime = System.currentTimeMillis();
                scanRequestTimeMap.put(filePath, requestTime);
                // Use per-file Alarm for debouncing
                Alarm alarm = fileAlarms.computeIfAbsent(filePath, k -> new Alarm(Alarm.ThreadToUse.POOLED_THREAD, project));
                alarm.cancelAllRequests();
                alarm.addRequest(() -> executeBackgroundScan(filePath, problemHelper, requestTime), SCHEDULED_DELAY);
            } finally {
                lock.unlock();
            }
            return true;
        } catch (Exception e) {
            LOGGER.warn(format("RTS: Failed to schedule scan for %s", filePath), e);
            return false;
        }
    }

    // Cancels any pending and running scan for the given file
    private void cancelPendingAndRunningScan(@NotNull String filePath) {
        lock.lock();
        try {
            Alarm alarm = fileAlarms.get(filePath);
            if (alarm != null) {
                alarm.cancelAllRequests();
                removeProgressIndicator(filePath);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Executes the scan in a background thread and restart the files to update the result on UI scan after completion.
     *
     * @param filePath      - The file path for which to execute the scan
     * @param problemHelper - The {@link ProblemHelper} instance containing necessary context for creating problem descriptors
     * @param requestTime   scan request time
     */
    private void executeBackgroundScan(@NotNull String filePath, @NotNull ProblemHelper problemHelper, long requestTime) {
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
                    runScanWithProgress(filePath, problemHelper, indicator);
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
                LOGGER.info(format("RTS: Scan was canceled for file: %s.", filePath));
            }
        }.queue();
    }

    /**
     * Executes a scan operation for a given file while updating progress indicators to provide feedback during the process.
     *
     * @param filePath      the path of the file to be scanned, cannot be null
     * @param problemHelper a {@link ProblemHelper} instance containing context for creating problem descriptors, cannot be null
     * @param indicator     a {@link ProgressIndicator} instance used for reporting scan progress, cannot be null
     */
    private void runScanWithProgress(@NotNull String filePath, @NotNull ProblemHelper problemHelper, @NotNull ProgressIndicator indicator) {
        try {
            LOGGER.info(format("RTS: Scan started for file: %s", filePath));
            List<ScanIssue> scanIssues = cxOneAssistInspectionMgr.scanFile(
                    problemHelper.getFilePath(), problemHelper.getFile(), ScanEngine.ALL);
            if (isScanCanceled(indicator, filePath)) {
                removeProgressIndicator(filePath);
                return;
            }
            if (scanIssues.isEmpty()) {
                LOGGER.debug(format("RTS: No scan issues for file: %s", filePath));
                resetCachedData(problemHelper);
                restartFileAfterScan(problemHelper);
                removeProgressIndicator(filePath);
                return;
            }
            List<ProblemDescriptor> problemDescriptors = cxOneAssistInspectionMgr.createProblemDescriptors(
                    problemHelper.toBuilder(problemHelper).scanIssueList(scanIssues).build(), Boolean.FALSE);
            if (isScanCanceled(indicator, filePath)) {
                removeProgressIndicator(filePath);
                return;
            }
            cacheScanResults(problemHelper, filePath, scanIssues, problemDescriptors);
            restartFileAfterScan(problemHelper);
            LOGGER.info(format("RTS: Scan completed for file: %s", filePath));
        } catch (Exception e) {
            LOGGER.warn(format("RTS: Exception occurred during scanning file: %s", filePath), e);
        } finally {
            removeProgressIndicator(filePath);
        }
    }

    /**
     * Cancels the progress indicator for the given file if it is running.
     */
    private void removeProgressIndicator(@NotNull String filePath) {
        ProgressIndicator runningIndicator = scanIndicators.remove(filePath);
        if (runningIndicator != null && !runningIndicator.isCanceled()) {
            // runningIndicator.cancel();
            LOGGER.warn(format("RTS: Previous scan for file %s canceled.", filePath));
        }
    }

    /**
     * Checks if the given scan request is outdated based on the latest scan request time.
     */
    private boolean isRequestOutdated(@NotNull String filePath, long requestTime) {
        lock.lock();
        try {
            long latestRequest = scanRequestTimeMap.getOrDefault(filePath, 0L);
            if (latestRequest != requestTime) {
                LOGGER.warn(format("RTS: Newer scan request found for file: %s. Skipping this scan.", filePath));
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
                                  @NotNull List<ScanIssue> scanIssues, @NotNull List<ProblemDescriptor> problems) {
        problemHelper.getProblemHolderService().addScanIssues(filePath, scanIssues);
        problemHelper.getProblemHolderService().addProblemDescriptors(filePath, problems);
    }

    /**
     * Clears the cached scan results.
     */
    private void resetCachedData(@NotNull ProblemHelper problemHelper) {
        ProblemHolderService holderService = problemHelper.getProblemHolderService();
        holderService.addScanIssues(problemHelper.getFilePath(), Collections.emptyList());
        holderService.addProblemDescriptors(problemHelper.getFilePath(), Collections.emptyList());
    }

    /**
     * Restarts the file to update the result on UI scan after completion.
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
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!isProjectDisposed("restarting file after scan", problemHelper.getFile().getName())
                    && problemHelper.getFile().isValid()) {
                DaemonCodeAnalyzer.getInstance(project).restart(problemHelper.getFile());
                LOGGER.warn(format("RTS: DaemonCodeAnalyzer restarted for file: %s", problemHelper.getFile().getName()));
            }
        });
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

    /**
     * Checks if the scan is canceled.
     *
     * @return true if the scan is canceled, false otherwise.
     */
    private boolean isScanCanceled(@NotNull ProgressIndicator indicator, @NotNull String filePath) {
        if (indicator.isCanceled()) {
            LOGGER.info(format("RTS: Scan canceled for file: %s", filePath));
            return true;
        }
        return false;
    }
}
