package com.checkmarx.intellij.devassist.inspection;

import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.problems.ProblemHelper;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

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

    private static final int SCHEDULED_DELAY = 1000; // 1-second delay
    private final Project project;
    private final Alarm alarm;
    private final Map<String, Long> scanRequestTimeMap = new ConcurrentHashMap<>();

    // Track running scan indicators per file for cancellation
    private final Map<String, ProgressIndicator> scanIndicators = new ConcurrentHashMap<>();
    private final CxOneAssistInspectionMgr cxOneAssistInspectionMgr = new CxOneAssistInspectionMgr();

    private CxOneAssistScanScheduler(@NotNull Project project) {
        this.project = project;
        this.alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, project);
    }

    /**
     * Returns the singleton instance of CxOneAssistScanScheduler for the given project.
     *
     * @param project - The IntelliJ Project instance
     * @return the singleton CxOneAssistScanScheduler instance for the project
     */
    static CxOneAssistScanScheduler getInstance(Project project) {
        CxOneAssistScanScheduler existingScanScheduler = project.getUserData(SCHEDULER_INSTANCE_KEY);
        if (Objects.nonNull(existingScanScheduler)) {
            LOGGER.debug("RTS: Schedule-Scan: CxOneAssistScanScheduler instance already exists for project: {}", project.getName());
            return existingScanScheduler;
        }
        CxOneAssistScanScheduler cxOneAssistScanScheduler = new CxOneAssistScanScheduler(project);
        project.putUserData(SCHEDULER_INSTANCE_KEY, cxOneAssistScanScheduler);
        return cxOneAssistScanScheduler;
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
            if (isProjectDisposed()) {
                LOGGER.debug("RTS: Project {} is disposed while scheduling scan, skipping background scan for file: {}",
                        project.getName(), filePath);
                return false;
            }
            int cancelledRequestCount = alarm.cancelAllRequests();
            LOGGER.debug("RTS: Cancelled {} pending scan requests before scheduling new scan for file: {}",
                    cancelledRequestCount, filePath);

            // Cancel any running scan for this file
            ProgressIndicator prevIndicator = scanIndicators.remove(filePath);
            if (prevIndicator != null) {
                prevIndicator.cancel();
                LOGGER.debug("RTS: Cancelled previous running scan for file: {}", filePath);
            }
            long scanRequestTimeMillis = System.currentTimeMillis();
            this.scanRequestTimeMap.put(filePath, scanRequestTimeMillis);
            alarm.addRequest(() -> executeBackgroundScan(filePath, problemHelper, scanRequestTimeMillis), SCHEDULED_DELAY);
            return true;
        } catch (Exception exception) {
            LOGGER.warn(format("RTS: Exception occurred while scheduling scan for %s", filePath), exception);
            return false;
        }
    }

    /**
     * Executes a debounced scan for the given file. This method is invoked after the specified delay
     *
     * @param filePath      - The path of the file to be scanned
     * @param problemHelper - The {@link ProblemHelper} instance containing necessary context for creating problem descriptors
     */
    private void executeBackgroundScan(@NotNull String filePath, ProblemHelper problemHelper, long scanRequestTimeMillis) {
        try {
            synchronized (scanRequestTimeMap) {
                long latestScanRequestTimeMillis = scanRequestTimeMap.getOrDefault(filePath, 0L);
                if (latestScanRequestTimeMillis != scanRequestTimeMillis) {
                    LOGGER.debug("RTS: A newer scan request is already scheduled for file: {}. Skipping this event.", filePath);
                    return;
                }
            }
            // Create a new ProgressIndicator for this scan
            ProgressIndicator indicator = new ProgressIndicatorBase();
            scanIndicators.put(filePath, indicator);
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                try {
                    LOGGER.info(format("RTS: Executing scheduled scan for file: %s", filePath));
                    if (isProjectDisposed()) {
                        LOGGER.debug("RTS: Project {} is disposed while executing scan, skipping scan for file: {}",
                                project.getName(), filePath);
                        return;
                    }
                    if (Objects.isNull(problemHelper.getFile()) || !problemHelper.getFile().isValid()) {
                        LOGGER.debug("RTS: Schedule-Scan: PsiFile is invalid for file: {}", filePath);
                    }
                    // Run scan in the background
                    runScan(problemHelper, indicator);
                    LOGGER.info(format("RTS: Completed scheduled scan for file: %s", filePath));
                } catch (Exception e) {
                    LOGGER.warn(format("RTS: Schedule-Scan: Exception occurred while scanning file in background. File: %s", filePath), e);
                } finally {
                    scanIndicators.remove(filePath);
                }
            });
        } catch (Exception exception) {
            LOGGER.warn(format("RTS: Schedule-Scan: Exception occurred while executing scan for file: %s", filePath), exception);
        }
    }

    /**
     * Scans the given file in the background and adds the scan issues to the problem holder.
     * The scan is performed in a separate thread to avoid blocking the UI thread.
     */
    private void runScan(ProblemHelper problemHelper, ProgressIndicator indicator) {
        if (indicator.isCanceled()) return;
        List<ScanIssue> allScanIssues = cxOneAssistInspectionMgr.initiateScan(problemHelper.getFilePath(), problemHelper.getFile(), ScanEngine.ALL);
        if (indicator.isCanceled()) return;

        if (allScanIssues.isEmpty()) {
            LOGGER.debug(format("RTS: Schedule-Scan: No scan issues found from scanner service for file: %s.",
                    problemHelper.getFile().getName()));
            resetCachedData(problemHelper);
            restartDaemonCodeAnalyzer(problemHelper);
            return;
        }
        ProblemHelper.ProblemHelperBuilder problemHelperBuilder = problemHelper.toBuilder(problemHelper);
        problemHelperBuilder.scanIssueList(allScanIssues);

        // Creating problem descriptors from scan issues
        List<ProblemDescriptor> allProblems = cxOneAssistInspectionMgr.createProblemDescriptors(problemHelperBuilder.build(), false);
        if (indicator.isCanceled()) return;
        if (allProblems.isEmpty()) {
            LOGGER.debug(format("RTS: Schedule-Scan: No Problem found and created for file: %s. ", problemHelper.getFile().getName()));
            resetCachedData(problemHelper);
            restartDaemonCodeAnalyzer(problemHelper);
            return;
        }
        // Caching all updated scan issues
        problemHelper.getProblemHolderService().addScanIssues(problemHelper.getFilePath(), allScanIssues);
        // Caching all problem descriptors
        problemHelper.getProblemHolderService().addProblemDescriptors(problemHelper.getFilePath(), allProblems);
        restartDaemonCodeAnalyzer(problemHelper);
    }

    /**
     * Resets cached scan issues and problem descriptors for the given file in the ProblemHolderService.
     *
     * @param problemHelper - The {@link ProblemHelper} instance containing necessary context for the file
     */
    private void resetCachedData(ProblemHelper problemHelper) {
        // Reset scan issues to empty list
        problemHelper.getProblemHolderService().addScanIssues(problemHelper.getFilePath(), Collections.emptyList());
        // Reset problem descriptors to empty list
        problemHelper.getProblemHolderService().addProblemDescriptors(problemHelper.getFilePath(), Collections.emptyList());
    }

    /**
     * Restarts the DaemonCodeAnalyzer for the given file to refresh inspections and display updated problems.
     *
     * @param problemHelper - The {@link ProblemHelper} instance containing necessary context for the file
     */
    private void restartDaemonCodeAnalyzer(ProblemHelper problemHelper) {
        scanRequestTimeMap.remove(problemHelper.getFilePath());
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!project.isDisposed() && problemHelper.getFile().isValid()) {
                DaemonCodeAnalyzer.getInstance(project).restart(problemHelper.getFile());
                LOGGER.debug("RTS: Restarted DaemonCodeAnalyzer (inspection) after scheduled scan for file: {}",
                        problemHelper.getFile().getName());
            }
        });
    }

    /**
     * Checks if the project is disposed.
     *
     * @return true if the project is disposed, false otherwise.
     */
    private boolean isProjectDisposed() {
        return project.isDisposed();
    }
}
