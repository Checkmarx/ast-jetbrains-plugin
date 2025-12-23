package com.checkmarx.intellij.devassist.inspection;

import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.problems.ProblemHelper;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.String.format;

/**
 * Manages and schedules debounced scans for files in a project, ensuring that scan requests are handled efficiently
 * to reduce redundant processing. The class uses a delay mechanism to debounce repeated scan requests for the same file.
 */
public class CxOneAssistScanScheduler {

    private static final Logger LOGGER = Logger.getInstance(CxOneAssistScanScheduler.class);

    private static final int SCHEDULED_DELAY = 1000; // 1-second delay
    private final Project project;
    private final Alarm alarm;
    private final Map<String, Long> scanRequestTimeMap = new ConcurrentHashMap<>();
    private static final Key<CxOneAssistScanScheduler> INSTANCE_KEY = Key.create("CX_ONE_ASSIST_SCAN_SCHEDULER");

    // Track running scan indicators per file for cancellation
    private final Map<String, ProgressIndicator> scanIndicators = new ConcurrentHashMap<>();

    private CxOneAssistScanScheduler(@NotNull Project project) {
        this.project = project;
        this.alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, project);
    }

    static CxOneAssistScanScheduler getInstance(Project project) {
        CxOneAssistScanScheduler existingScanScheduler = project.getUserData(INSTANCE_KEY);
        if (Objects.nonNull(existingScanScheduler)) {
            LOGGER.debug("RTS: Schedule-Scan: CxOneAssistScanScheduler instance already exists for project: {}", project.getName());
            return existingScanScheduler;
        }
        CxOneAssistScanScheduler cxOneAssistScanScheduler = new CxOneAssistScanScheduler(project);
        project.putUserData(INSTANCE_KEY, cxOneAssistScanScheduler);
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
                        filePath, project.getName());
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
            alarm.addRequest(() -> executeScan(filePath, problemHelper, scanRequestTimeMillis), SCHEDULED_DELAY);
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
    private void executeScan(@NotNull String filePath, ProblemHelper problemHelper, long scanRequestTimeMillis) {
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
                    // Always clear previous results before scan
                    problemHelper.getProblemHolderService().addProblems(problemHelper.getFilePath(), Collections.emptyList());
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (!project.isDisposed() && problemHelper.getFile().isValid()) {
                            DaemonCodeAnalyzer.getInstance(project).restart(problemHelper.getFile());
                        }
                    });
                    // Run heavy scanner work outside read action, with cancellation support
                    runBackgroundScan(problemHelper, project, filePath, indicator);
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
    private void runBackgroundScan(ProblemHelper problemHelper, Project project, String filePath, ProgressIndicator indicator) {
        if (indicator.isCanceled()) return;
        List<ScanIssue> allScanIssues = CxOneAssistInspection.scanFileAndGetAllIssues(problemHelper);
        if (indicator.isCanceled()) return;
        // Always clear previous results, even if no issues found
        // Remove issues for this file
        problemHelper.getProblemHolderService().addProblems(problemHelper.getFilePath(), Collections.emptyList());
        // Remove problem descriptors for this file
        problemHelper.getProblemHolderService().removeProblemDescriptorsForFile(problemHelper.getFilePath());
        if (allScanIssues.isEmpty()) {
            LOGGER.debug(format("RTS: Schedule-Scan: No scan issues found from scanner service for file: %s.",
                    problemHelper.getFile().getName()));
            // Refresh UI to remove stale highlights
            ApplicationManager.getApplication().invokeLater(() -> {
                if (!project.isDisposed() && problemHelper.getFile().isValid()) {
                    DaemonCodeAnalyzer.getInstance(project).restart(problemHelper.getFile());
                }
            });
            return;
        }
        ProblemHelper.ProblemHelperBuilder problemHelperBuilder = problemHelper.toBuilder(problemHelper);
        problemHelperBuilder.scanIssueList(allScanIssues);
        // Caching all scan issues
        problemHelper.getProblemHolderService().addProblems(problemHelper.getFilePath(), allScanIssues);
        List<ProblemDescriptor> allProblems = CxOneAssistInspection.createProblemDescriptors(problemHelperBuilder.build(), false);
        if (indicator.isCanceled()) return;
        if (allProblems.isEmpty()) {
            LOGGER.debug(format("RTS: Schedule-Scan: No Problem found and created for file: %s. ", problemHelper.getFile().getName()));
            // UI already refreshed above
            return;
        }
        // Caching all problem descriptors
        problemHelper.getProblemHolderService().addProblemDescriptors(problemHelper.getFilePath(), allProblems);

        scanRequestTimeMap.remove(filePath);
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!project.isDisposed() && problemHelper.getFile().isValid()) {
                DaemonCodeAnalyzer.getInstance(project).restart(problemHelper.getFile());
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
