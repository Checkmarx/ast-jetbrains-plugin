package com.checkmarx.intellij.devassist.inspection;

import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.problems.ProblemHelper;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

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
    private static final Key<CxOneAssistScanScheduler> INSTANCE_KEY = Key.create("CX_ONE_ASSIST_SCANNER_SCHEDULER");

    private CxOneAssistScanScheduler(@NotNull Project project) {
        this.project = project;
        this.alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, project);
    }

    static CxOneAssistScanScheduler getInstance(Project project) {
        CxOneAssistScanScheduler existingScanScheduler = project.getUserData(INSTANCE_KEY);
        if (Objects.nonNull(existingScanScheduler)) {
            LOGGER.debug("RTS: CxOneAssistScanScheduler instance already exists for project: {}", project.getName());
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
                LOGGER.debug("RTS: Project {} is disposed while scheduling scan, skipping scan for file: {}", filePath, project.getName());
                return true;
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
            long latestScanRequestTimeMillis = scanRequestTimeMap.getOrDefault(filePath, 0L);
            if (!Objects.equals(latestScanRequestTimeMillis, scanRequestTimeMillis)) {
                //Scan is already scheduled and in progress but received new scan request within scheduled dealy time, so skipping this event.
                LOGGER.debug("RTS: Scan is already scheduled for file: {}. Skipping this event.", filePath);
                return;
            }
            ApplicationManager.getApplication().executeOnPooledThread(() -> ReadAction.run(() -> {
                LOGGER.info(format("RTS: Executing scan for file: %s", filePath));
                if (isProjectDisposed()) {
                    LOGGER.debug("RTS: Project {} is disposed while executing scan, skipping scan for file: {}", filePath, project.getName());
                    return;
                }
                if (Objects.isNull(problemHelper.getFile()) || !problemHelper.getFile().isValid()) {
                    LOGGER.debug("RTS: PsiFile is invalid for file: {}", filePath);
                    return;
                }
                // Run heavy scanner work outside read action
                ApplicationManager.getApplication().executeOnPooledThread(
                        () -> scanFileInBackground(problemHelper, project)
                );
            }));
        } catch (Exception exception) {
            LOGGER.warn(format("RTS: Exception occurred while executing scan for file: %s", filePath), exception);
        }
    }

    /**
     * Scans the given file in the background and adds the scan issues to the problem holder.
     * The scan is performed in a separate thread to avoid blocking the UI thread.
     */
    private void scanFileInBackground(ProblemHelper problemHelper, Project project) {
        if (isProjectDisposed()) {
            return;
        }
        List<ScanIssue> allScanIssues = CxOneAssistInspection.scanFileAndGetAllIssues(problemHelper);
        if (allScanIssues.isEmpty()) {
            LOGGER.warn(format("RTS: No scan issues found for file: %s", problemHelper.getFilePath()));
            return;
        }
        problemHelper.getProblemHolderService().addProblems(problemHelper.getFilePath(), allScanIssues);

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
