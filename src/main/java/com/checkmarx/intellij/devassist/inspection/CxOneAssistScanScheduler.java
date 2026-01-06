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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final Map<String, Object> perFileLocks = new ConcurrentHashMap<>();
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
    static CxOneAssistScanScheduler getInstance(Project project) {
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
     *
     * @param problemHelper - The {@link ProblemHelper} instance containing necessary context for creating problem descriptors
     */
    boolean scheduleScan(@NotNull String filePath, @NotNull ProblemHelper problemHelper) {
        try {
            if (isProjectDisposed("scheduling scan", filePath)) {
                return false;
            }
            Object fileLock = perFileLocks.computeIfAbsent(filePath, k -> new Object());
            synchronized (fileLock) {
                cancelPendingAndRunningScan(filePath);
                long requestTime = System.currentTimeMillis();
                scanRequestTimeMap.put(filePath, requestTime);

                int adaptiveDelay = calculateAdaptiveDelay(filePath, requestTime);

                Alarm alarm = fileAlarms.computeIfAbsent(filePath, k -> new Alarm(Alarm.ThreadToUse.POOLED_THREAD, project));
                alarm.addRequest(() -> executeBackgroundScan(filePath, problemHelper, requestTime), adaptiveDelay);
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
    private void executeBackgroundScan(@NotNull String filePath, @NotNull ProblemHelper problemHelper, long requestTime) {
        Object fileLock = perFileLocks.get(filePath);
        synchronized (fileLock) {
            if (isRequestOutdated(filePath, requestTime)) {
                return;
            }
        }
        try {
            executorService.submit(() ->
                    // Submitting scan to be handled asynchronously without locking the main thread
                    new Task.Backgroundable(project, Bundle.message(Resource.STARTING_CHECKMARX_SCAN), true) {
                        @Override
                        public void run(@NotNull ProgressIndicator indicator) {
                            indicator.setText("Checkmarx is Scanning File: " + problemHelper.getFile().getName());
                            indicator.setIndeterminate(true);
                            scanIndicators.put(filePath, indicator); // Track the progress indicator for the scan
                            runScan(filePath, problemHelper);
                        }
                    }.queue());
        } catch (Exception e) {
            LOGGER.warn(format("RTS: Failed to execute scan for file: %s", filePath), e);
        }
    }

    /**
     * Runs the scan for the given file and caches the results.
     */
    private void runScan(@NotNull String filePath, @NotNull ProblemHelper problemHelper) {
        try {
            LOGGER.info(format("RTS: Scheduled scan started for file: %s", filePath));
            List<ScanIssue> scanIssues = cxOneAssistInspectionMgr.scanFile(
                    problemHelper.getFilePath(), problemHelper.getFile(), ScanEngine.ALL);
            if (scanIssues.isEmpty()) {
                resetCachedData(problemHelper);
                LOGGER.info(format("RTS: Scheduled scan completed with no issues for file: %s", filePath));
            } else {
                ApplicationManager.getApplication().invokeLater(() -> {
                    List<ProblemDescriptor> descriptors = cxOneAssistInspectionMgr.createProblemDescriptorsWithoutDecoration(
                            problemHelper.toBuilder(problemHelper).scanIssueList(scanIssues).build());

                    cacheScanResults(problemHelper, filePath, scanIssues, descriptors);
                    LOGGER.info(format("RTS: Scheduled scan completed for file: %s", filePath));
                }, ModalityState.NON_MODAL); // UI decoration should be done in a write-safe non-modal state
            }
            cxOneAssistInspectionMgr.updateScanSourceFlag(problemHelper.getFile(), Boolean.TRUE);
        } catch (Exception e) {
            LOGGER.error(format("RTS: Error while running scheduled scan for the file: %s", filePath), e);
        } finally {
            removeProgressIndicator(filePath);
            restartFileAfterScan(problemHelper);
        }
    }

    /**
     * Calculates an adaptive delay for debouncing based on recent activity for the file.
     *
     * @param filePath    The path of the file being modified.
     * @param requestTime The timestamp of the current scan request.
     * @return The calculated delay time in milliseconds.
     */
    private int calculateAdaptiveDelay(@NotNull String filePath, long requestTime) {
        Long lastRequestTime = scanRequestTimeMap.get(filePath);
        if (lastRequestTime == null) {
            return SCHEDULED_DELAY;
        }
        long timeDifference = requestTime - lastRequestTime;

        // Dynamic debouncing logic: shorten delay for rapid edits, maintain base delay otherwise
        return (int) Math.max(200, Math.min(SCHEDULED_DELAY, timeDifference * 2));
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
            runningIndicator.cancel(); // double cancellation for safe measure
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
     * Checks if the project is disposed of.
     *
     * @return true if the project is disposed, false otherwise.
     */
    private boolean isProjectDisposed(String action, String fileName) {
        if (project.isDisposed()) {
            LOGGER.warn(format("RTS: Project disposed during %s. for file: %s", action, fileName));
            removeProgressIndicator(fileName);
            shutdownExecutorService();
            return true;
        }
        return false;
    }

    /**
     * Gracefully shuts down the ExecutorService used by this scheduler. If the shutdown process
     * takes too long, it forces a shutdown.
     * <p>
     * This method first invokes the {@code shutdown()} method on the ExecutorService, which prevents
     * new tasks from being submitted while allowing previously submitted tasks to complete. It then
     * waits for a specified timeout period to allow ongoing tasks to finish execution. If the tasks
     * do not terminate within the timeout, the {@code shutdownNow()} method is called to attempt
     * to stop all actively executing tasks and halt the processing of waiting tasks. Additionally,
     * if the current thread is interrupted during this process, the method restores the interrupted
     * status and forces the ExecutorService to shut down immediately.
     * <p>
     * Thread-safety: This method is thread-safe.
     * <p>
     * Exceptions:
     * - Catches {@link InterruptedException} to handle any interruptions during the shutdown process
     * and ensures proper handling of the interrupted state.
     */
    public void shutdownExecutorService() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
        }
    }
}
