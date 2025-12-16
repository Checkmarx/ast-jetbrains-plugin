package com.checkmarx.intellij.devassist.inspection;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.basescanner.ScannerService;
import com.checkmarx.intellij.devassist.common.ScanResult;
import com.checkmarx.intellij.devassist.common.ScannerFactory;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.problems.ProblemDecorator;
import com.checkmarx.intellij.devassist.problems.ProblemHelper;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.checkmarx.intellij.devassist.problems.ScanIssueProcessor;
import com.checkmarx.intellij.devassist.remediation.CxOneAssistFix;
import com.checkmarx.intellij.devassist.ui.ProblemDescription;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * The RealtimeInspection class extends LocalInspectionTool and is responsible for
 * performing real-time inspections of files within a project. It uses various
 * utility classes to scan files, identify issues, and provide problem descriptors
 * for on-the-fly or manual inspections.
 * <p>
 * This class maintains a cache of file modification timestamps to optimize its
 * behavior, avoiding repeated scans of unchanged files. It supports integration
 * with real-time scanner services and provides problem highlights and fixes for
 * identified issues.
 */
public class RealtimeInspection extends LocalInspectionTool {

    private static final Logger LOGGER = Utils.getLogger(RealtimeInspection.class);
    /**
     * Debounce delay in milliseconds. Scans will only trigger after the user stops typing for this duration.
     * This prevents lag during typing by avoiding scans on every keystroke.
     */
    private static final int DEBOUNCE_DELAY_MS = 1000;
    private final Map<String, Long> fileTimeStamp = new ConcurrentHashMap<>();
    private final Map<String, Long> lastScanRequestTime = new ConcurrentHashMap<>();
    private final ScannerFactory scannerFactory = new ScannerFactory();
    private final ProblemDecorator problemDecorator = new ProblemDecorator();
    private final Key<Boolean> key = Key.create(Constants.RealTimeConstants.THEME);
    private final Alarm scanDebounceAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD);

    /**
     * Inspects the given PSI file and identifies potential issues or problems by leveraging
     * scanning services and generating problem descriptors.
     *
     * @param file       the PSI file to be checked; must not be null
     * @param manager    the inspection manager used to create problem descriptors; must not be null
     * @param isOnTheFly a flag that indicates whether the inspection is executed on-the-fly
     * @return an array of {@link ProblemDescriptor} representing the detected issues, or an empty array if no issues were found
     */
    @Override
    public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
        VirtualFile virtualFile = file.getVirtualFile();
        if (Objects.isNull(virtualFile)) {
            LOGGER.warn(format("RTS: VirtualFile object not found for file: %s.", file.getName()));
            resetResults(file.getProject());
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        // On remediation process GitHub Copilot generating the fake file with the name Dummy.txt, so ignoring that file.
        if (isAgentEvent(virtualFile)) {
            LOGGER.warn(format("RTS: Received copilot event for file: %s. Skipping file..", file.getName()));
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        if (!Utils.isUserAuthenticated() || !DevAssistUtils.isAnyScannerEnabled()) {
            LOGGER.warn(format("RTS: User not authenticated or No scanner is enabled, skipping file: %s", file.getName()));
            resetResults(file.getProject());
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (Objects.isNull(document)) {
            LOGGER.warn(format("RTS: Document not found for file: %s.", file.getName()));
            resetResults(file.getProject());
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        List<ScannerService<?>> supportedScanners = getSupportedEnabledScanner(virtualFile.getPath(),file);
        if (supportedScanners.isEmpty()) {
            LOGGER.warn(format("RTS: No supported scanner enabled for this file: %s.", file.getName()));
            resetResults(file.getProject());
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        ProblemHolderService problemHolderService = ProblemHolderService.getInstance(file.getProject());
        /*
         * Check if the file is already scanned and if the problem descriptors are valid.
         * If a file is already scanned and problem descriptors are valid, then return the existing problem descriptors for the enabled scanners.
         */
        if (fileTimeStamp.containsKey(virtualFile.getPath()) && fileTimeStamp.get(virtualFile.getPath()) == (file.getModificationStamp())
                && isProblemDescriptorValid(problemHolderService, virtualFile.getPath(), file)) {
            LOGGER.info(format("RTS: File: %s is already scanned, retrieving existing results.", file.getName()));
            return getExistingProblemsForEnabledScanners(problemHolderService, virtualFile.getPath(), document, file, supportedScanners);
        }
        fileTimeStamp.put(virtualFile.getPath(), file.getModificationStamp());
        file.putUserData(key, DevAssistUtils.isDarkTheme());
        return scanFileAndCreateProblemDescriptors(file, manager, isOnTheFly, supportedScanners, document, problemHolderService, virtualFile);
    }

    /**
     * Clears all problem descriptors and gutter icons for the given project.
     *
     * @param project the project to reset results for
     */
    private void resetResults(Project project) {
        ProblemDecorator.removeAllGutterIcons(project);
        ProblemHolderService.getInstance(project).removeAllProblemDescriptors();
    }

    /**
     * Retrieves all supported instances of {@link ScannerService} for handling real-time scanning
     * of the specified file. The method checks available scanner services to determine if
     * any of them is suited to handle the given file path.
     *
     * @param filePath the path of the file as a string, used to identify an applicable scanner service; must not be null or empty
     * @return an {@link Optional} containing the matching {@link ScannerService} if found, or an empty {@link Optional} if no appropriate service exists
     */
    private List<ScannerService<?>> getSupportedEnabledScanner(String filePath, PsiFile psiFile) {
        List<ScannerService<?>> supportedScanners = scannerFactory.getAllSupportedScanners(filePath, psiFile);
        if (supportedScanners.isEmpty()) {
            LOGGER.warn(format("RTS: No supported scanner found for this file path: %s.", filePath));
            return Collections.emptyList();
        }
        return supportedScanners.stream()
                .filter(scannerService ->
                        DevAssistUtils.isScannerActive(scannerService.getConfig().getEngineName()))
                .collect(Collectors.toList());
    }

    /**
     * Checks if the virtual file is a GitHub Copilot-generated file.
     * E.g., On opening of GitHub Copilot, it's generating the fake file with the name Dummy.txt, so ignoring that file.
     *
     * @param virtualFile - VirtualFile object of the file.
     * @return true if the file is a GitHub Copilot-generated file, false otherwise.
     */
    private boolean isAgentEvent(VirtualFile virtualFile) {
        return Constants.RealTimeConstants.AGENT_DUMMY_FILES.stream()
                .anyMatch(filePath -> filePath.equals(virtualFile.getPath()));
    }

    /**
     * Checks if the problem descriptor for the given file path is valid.
     * Scan file on theme change, as the inspection tooltip doesn't support dynamic icon change in the tooltip description.
     *
     * @param problemHolderService the problem holder service
     * @param path                 the file path
     * @return true if the problem descriptor is valid, false otherwise
     */
    private boolean isProblemDescriptorValid(ProblemHolderService problemHolderService, String path, PsiFile file) {
        if (file.getUserData(key) != null && !Objects.equals(file.getUserData(key), DevAssistUtils.isDarkTheme())) {
            ProblemDescription.reloadIcons(); // reload problem descriptions icons on theme change
            LOGGER.info("RTS: Theme changed, resetting problem descriptors.");
            return false;
        }
        return !problemHolderService.getProblemDescriptors(path).isEmpty();
    }

    /**
     * Gets the existing problem descriptors for the given file path and enabled scanners.
     *
     * @param problemHolderService the problem holder service.
     * @param filePath             the file path.
     * @return the problem descriptors.
     */
    private ProblemDescriptor[] getExistingProblemsForEnabledScanners(ProblemHolderService problemHolderService, String filePath, Document document,
                                                                      PsiFile file, List<ScannerService<?>> supportedEnabledScanners) {
        List<ProblemDescriptor> problemDescriptorsList = problemHolderService.getProblemDescriptors(filePath);
        /*
         * If a file already scanned and after that if scanner settings are changed (enabled/disabled),
         * we need to filter the existing problems and return only those which are related to enabled scanners
         */
        List<ScanEngine> enabledScanners = supportedEnabledScanners.stream()
                .map(scannerService ->
                        ScanEngine.valueOf(scannerService.getConfig().getEngineName().toUpperCase()))
                .collect(Collectors.toList());

        if (problemDescriptorsList.isEmpty() || enabledScanners.isEmpty()) {
            LOGGER.warn(format("RTS: No existing problem descriptors found for file: %s or no enabled scanners found.", filePath));
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        List<ScanIssue> scanIssueList = problemHolderService.getScanIssueByFile(filePath);
        if (scanIssueList.isEmpty()) {
            LOGGER.warn(format("RTS: No existing scan issues found for file: %s.", filePath));
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        List<ProblemDescriptor> enabledScannerProblems = new ArrayList<>();
        for (ProblemDescriptor descriptor : problemDescriptorsList) {
            try {
                CxOneAssistFix cxOneAssistFix = (CxOneAssistFix) descriptor.getFixes()[0];
                if (Objects.nonNull(cxOneAssistFix) && enabledScanners.contains(cxOneAssistFix.getScanIssue().getScanEngine())) {
                    enabledScannerProblems.add(descriptor);
                }
            } catch (Exception e) {
                LOGGER.debug("RTS: Exception occurred while getting existing problems for enabled scanner for file: {} ",
                        filePath, e.getMessage());
                enabledScannerProblems.add(descriptor);
            }
        }
        List<ScanIssue> enabledScanIssueList = scanIssueList.stream()
                .filter(scanIssue -> enabledScanners.contains(scanIssue.getScanEngine()))
                .collect(Collectors.toList());

        // Update gutter icons and problem descriptors for the file according to the latest state of scan settings.
        problemDecorator.restoreGutterIcons(file.getProject(), file, enabledScanIssueList, document);
        problemHolderService.addProblemDescriptors(filePath, enabledScannerProblems);
        return enabledScannerProblems.toArray(new ProblemDescriptor[0]);
    }

    /**
     * Builds a {@link ProblemHelper.ProblemHelperBuilder} instance with the specified parameters.
     *
     * @param file       the PSI file to be scanned
     * @param manager    the inspection manager used to create problem descriptors
     * @param isOnTheFly a flag that indicates whether the inspection is executed on-the-fly
     * @param document   the document containing the file to be scanned
     * @return a {@link ProblemHelper.ProblemHelperBuilder} instance
     */
    private ProblemHelper.ProblemHelperBuilder buildHelper(@NotNull PsiFile file, @NotNull InspectionManager manager,
                                                           boolean isOnTheFly, Document document, List<ScannerService<?>> supportedScanners,
                                                           String path, ProblemHolderService problemHolderService) {
        return ProblemHelper.builder()
                .file(file)
                .manager(manager)
                .isOnTheFly(isOnTheFly)
                .document(document)
                .supportedScanners(supportedScanners)
                .filePath(path)
                .problemHolderService(problemHolderService);
    }

    /**
     * Scans the given PSI file and creates problem descriptors for any identified issues.
     *
     * @param file                 the PsiFile representing the file to be scanned; must not be null
     * @param manager              the inspection manager used to create problem descriptors; must not be null
     * @param isOnTheFly           a flag that indicates whether the inspection is executed on-the-fly
     * @param supportedScanners    the list of supported scanner services
     * @param document             the document containing the file to be scanned
     * @param problemHolderService the problem holder service
     * @param virtualFile          the virtual file
     * @return ProblemDescriptor[] array of problem descriptors
     */
    private ProblemDescriptor[] scanFileAndCreateProblemDescriptors(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly,
                                                                    List<ScannerService<?>> supportedScanners, Document document,
                                                                    ProblemHolderService problemHolderService, VirtualFile virtualFile) {

        ProblemHelper.ProblemHelperBuilder problemHelperBuilder = buildHelper(file, manager, isOnTheFly, document,
                supportedScanners, virtualFile.getPath(), problemHolderService);
        /*
         * Debounce logic: Instead of scanning immediately on every keystroke (which causes lag),
         * we schedule a delayed scan. This allows the user to type smoothly while still getting
         * up-to-date scan results after they pause typing.
         */
        if (isOnTheFly) {
            // Schedule a debounced scan
            scheduleDebouncedScan(problemHelperBuilder);
            // Return existing cached results immediately (no blocking) to avoid lag
            if (problemHolderService.getProblemDescriptors(virtualFile.getPath()) != null
                    && !problemHolderService.getProblemDescriptors(virtualFile.getPath()).isEmpty()) {
                LOGGER.info(format("RTS: Scan is pending for modified file: %s, returning existing results ", file.getName()));
                return getExistingProblemsForEnabledScanners(problemHolderService, virtualFile.getPath(), document, file, supportedScanners);
            }
            // No cached results available, return empty array (scan will run after debounce delay)
            LOGGER.info(format("RTS: Scan scheduled for modified file: %s.", file.getName()));
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        List<ProblemDescriptor> scanResultDescriptors = startScanAndCreateProblemDescriptors(problemHelperBuilder);
        if (scanResultDescriptors.isEmpty()) {
            LOGGER.info(format("RTS: No issues found for file: %s ", file.getName()));
        }
        LOGGER.info(format("RTS: Scanning completed and descriptors created: %s for file: %s", scanResultDescriptors.size(), file.getName()));
        return scanResultDescriptors.toArray(new ProblemDescriptor[0]);
    }

    /**
     * Scans the given PSI file and creates problem descriptors for any identified issues.
     *
     * @param problemHelperBuilder - The {@link ProblemHelper}
     * @return a list of {@link ProblemDescriptor} representing the detected issues, or an empty list if no issues were found
     */
    private List<ProblemDescriptor> startScanAndCreateProblemDescriptors(ProblemHelper.ProblemHelperBuilder problemHelperBuilder) {
        ProblemHelper problemHelper = problemHelperBuilder.build();
        List<ScanResult<?>> allScanResults = problemHelper.getSupportedScanners().stream()
                .map(scannerService ->
                        scanFile(scannerService, problemHelper.getFile(), problemHelper.getFilePath()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<ScanIssue> allScanIssues = allScanResults.stream()
                .flatMap(scanResult -> scanResult.getIssues().stream())
                .collect(Collectors.toList());

        // Adding all scanner issues to the builder.
        problemHelperBuilder.scanIssueList(allScanIssues);
        //Adding problems to the CxFinding window
        problemHelper.getProblemHolderService().addProblems(problemHelper.getFilePath(), allScanIssues);

        //Creating problems
        List<ProblemDescriptor> allProblems = new ArrayList<>(createProblemDescriptors(problemHelperBuilder.build()));
        problemHelper.getProblemHolderService().addProblemDescriptors(problemHelper.getFilePath(), allProblems);
        return allProblems;
    }

    /**
     * Scans the given PSI file at the specified path using an appropriate real-time scanner,
     * if available and active.
     *
     * @param scannerService - ScannerService object of found scan engine
     * @param file           the PsiFile representing the file to be scanned; must not be null
     * @param path           the string representation of the file path to be scanned; must not be null or empty
     * @return a {@link ScanResult} instance containing the results of the scan, or null if no
     * active and suitable scanner is found
     */
    private ScanResult<?> scanFile(ScannerService<?> scannerService, @NotNull PsiFile file, @NotNull String path) {
        try {
            LOGGER.info(format("RTS: Started scanning file: %s using scanner: %s", path, scannerService.getConfig().getEngineName()));
            return scannerService.scan(file, path);
        } catch (Exception e) {
            LOGGER.debug("RTS: Exception occurred while scanning file: {} ", path, e.getMessage());
            return null;
        }
    }

    /**
     * Creates a list of {@link ProblemDescriptor} objects based on the issues identified in the scan result.
     * This method processes the scan issues for the specified file and uses the provided InspectionManager
     * to generate corresponding problem descriptors, if applicable.
     *
     * @param problemHelper - The {@link ProblemHelper}} instance containing necessary context for creating problem descriptors
     * @return a list of {@link ProblemDescriptor}; an empty list is returned if no issues are found or processed successfully
     */
    private List<ProblemDescriptor> createProblemDescriptors(ProblemHelper problemHelper) {
        List<ProblemDescriptor> problems = new ArrayList<>();
        ProblemDecorator.removeAllGutterIcons(problemHelper.getFile().getProject());
        ScanIssueProcessor processor = new ScanIssueProcessor(problemHelper, this.problemDecorator);

        for (ScanIssue scanIssue : problemHelper.getScanIssueList()) {
            ProblemDescriptor descriptor = processor.processScanIssue(scanIssue);
            if (descriptor != null) {
                problems.add(descriptor);
            }
        }
        LOGGER.info(format("RTS: Problem descriptors created: %s for file: %s", problems.size(), problemHelper.getFile().getName()));
        return problems;
    }

    /**
     * Schedules a debounced scan for the given file. If a scan is already pending for this file,
     * the previous request is cancelled and a new one is scheduled. The scan will only execute
     * after {@link #DEBOUNCE_DELAY_MS} milliseconds of no new scan requests for this file.
     *
     * @param problemHelperBuilder - The {@link ProblemHelper} instance containing necessary context for creating problem descriptors
     */
    private void scheduleDebouncedScan(ProblemHelper.ProblemHelperBuilder  problemHelperBuilder) {

        ProblemHelper problemHelper = problemHelperBuilder.build();
        long requestTime = System.currentTimeMillis();
        lastScanRequestTime.put(problemHelper.getFilePath(), requestTime);

        scanDebounceAlarm.addRequest(() -> {
            // Check if this is still the latest request for this file
            Long latestRequestTime = lastScanRequestTime.get(problemHelper.getFilePath());
            if (latestRequestTime == null || latestRequestTime != requestTime) {
                LOGGER.debug(format("RTS: Debounced scan cancelled for file: %s (newer request pending)", problemHelper.getFilePath()));
                return;
            }
            // Check if the project is still valid
            if (problemHelper.getFile().getProject().isDisposed()) {
                LOGGER.debug(format("RTS: Debounced scan cancelled for file: %s (project disposed)", problemHelper.getFilePath()));
                return;
            }
            executeScheduledScan(problemHelperBuilder, problemHelper);
        }, DEBOUNCE_DELAY_MS);
    }

    /**
     * Executes a debounced scan for the given file. This method is invoked after the specified delay
     * @param problemHelperBuilder - The {@link ProblemHelper} instance containing necessary context for creating problem descriptors
     * @param problemHelper - The {@link ProblemHelper} instance containing necessary context for creating problem descriptors
     */
    private void executeScheduledScan(ProblemHelper.ProblemHelperBuilder problemHelperBuilder, ProblemHelper problemHelper) {
        // Execute the scan in a read action on a background thread
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                LOGGER.info(format("RTS: Executing debounced scan for file: %s", problemHelper.getFilePath()));
                ReadAction.run(() -> {
                    if (problemHelper.getFile().getProject().isDisposed()) return;
                    PsiFile psiFile = problemHelper.getFile();
                    // Update timestamp and execute the scan
                    fileTimeStamp.put(problemHelper.getFilePath(), psiFile.getModificationStamp());
                    psiFile.putUserData(key, DevAssistUtils.isDarkTheme());

                    // Perform the actual scan
                    startScanAndCreateProblemDescriptors(problemHelperBuilder);

                    // Clean up pending scan tracking
                    lastScanRequestTime.remove(problemHelper.getFilePath());

                    // Trigger re-inspection to update the UI with new results
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (!problemHelper.getFile().getProject().isDisposed() && psiFile.isValid()) {
                            DaemonCodeAnalyzer.getInstance(problemHelper.getFile().getProject()).restart(psiFile);
                        }
                    });
                    LOGGER.info(format("RTS: Debounced scan completed for file: %s", problemHelper.getFilePath()));
                });
            } catch (Exception e) {
                LOGGER.warn(format("RTS: Exception during debounced scan for file: %s", problemHelper.getFilePath()), e);
            }
        });
    }
}
