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
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * The CxOneAssistInspection class extends LocalInspectionTool and is responsible for
 * performing real-time inspections of files within a project. It uses various
 * utility classes to scan files, identify issues, and provide problem descriptors
 * for on-the-fly or manual inspections.
 * <p>
 * This class maintains a cache of file modification timestamps to optimize its
 * behavior, avoiding repeated scans of unchanged files. It supports integration
 * with real-time scanner services and provides problem highlights and fixes for
 * identified issues.
 */
public class CxOneAssistInspection extends LocalInspectionTool {

    private static final Logger LOGGER = Utils.getLogger(CxOneAssistInspection.class);
    private static final Key<Boolean> THEME_KEY = Key.create(Constants.RealTimeConstants.THEME);
    private static final Key<Boolean> SCAN_SOURCE = Key.create("SCAN_SOURCE");
    private final Map<String, Long> fileTimeStamp = new ConcurrentHashMap<>();
    private final ScannerFactory scannerFactory = new ScannerFactory();
    private final ProblemDecorator problemDecorator = new ProblemDecorator();


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
        List<ScannerService<?>> supportedScanners = getSupportedEnabledScanner(virtualFile.getPath(), file);
        if (supportedScanners.isEmpty()) {
            LOGGER.warn(format("RTS: No supported scanner enabled for this file: %s.", file.getName()));
            resetResults(file.getProject());
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        ProblemHolderService problemHolderService = ProblemHolderService.getInstance(file.getProject());
        if (Objects.isNull(problemHolderService)) {
            LOGGER.warn(format("RTS: Problem holder service not found for project: %s.", file.getProject().getName()));
            resetResults(file.getProject());
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        /*
         * Check if a file is already scanned and not modified and problem descriptors are valid,
         * then return the existing problem descriptors for the enabled scanners.
         */
        if (fileTimeStamp.containsKey(virtualFile.getPath()) && fileTimeStamp.get(virtualFile.getPath()) == (file.getModificationStamp())
                && isProblemDescriptorValid(problemHolderService, virtualFile.getPath(), file)) {
            LOGGER.info(format("RTS: File: %s is already scanned, retrieving existing results.", file.getName()));
            return getExistingProblems(problemHolderService, virtualFile.getPath(), document, file, supportedScanners);
        }
        fileTimeStamp.put(virtualFile.getPath(), file.getModificationStamp());
        file.putUserData(THEME_KEY, DevAssistUtils.isDarkTheme());
        return scanFileAndCreateProblemDescriptors(file, manager, isOnTheFly, supportedScanners, document, problemHolderService, virtualFile);
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
        try {
            ProblemHelper.ProblemHelperBuilder problemHelperBuilder = buildHelper(file, manager, isOnTheFly, document,
                    supportedScanners, virtualFile.getPath(), problemHolderService);

            // Schedule a debounced scan to avoid excessive scanning during rapid file changes (background scan)
            boolean isScanScheduled = CxOneAssistScanScheduler.getInstance(file.getProject())
                    .scheduleScan(virtualFile.getPath(), problemHelperBuilder.build());
            if (isScanScheduled) {
                file.putUserData(SCAN_SOURCE, Boolean.TRUE); // To identify the scan source
                return ProblemDescriptor.EMPTY_ARRAY; // Return empty array as problems will be added after the scheduled scan completes
            }
            LOGGER.info(format("RTS: Failed to schedule the scan for file: %s. Now scanning file using fallback..", file.getName()));
            return startScanAndCreateProblemDescriptors(problemHelperBuilder);
        } catch (Exception exception) {
            LOGGER.warn(format("RTS: Exception occurred while scanning file: %s", virtualFile.getPath()), exception);
            return ProblemDescriptor.EMPTY_ARRAY;
        }
    }

    /**
     * Scans the given PSI file and creates problem descriptors for any identified issues.
     *
     * @param problemHelperBuilder - The {@link ProblemHelper}
     * @return an array of {@link ProblemDescriptor} representing the detected issues, or an empty array if no issues were found
     */
    private ProblemDescriptor[] startScanAndCreateProblemDescriptors(ProblemHelper.ProblemHelperBuilder problemHelperBuilder) {
        ProblemHelper problemHelper = problemHelperBuilder.build();
        List<ScanIssue> allScanIssues = scanFileAndGetAllIssues(problemHelper);

        if (allScanIssues.isEmpty()) {
            LOGGER.info(format("RTS: No scan issues found for file: %s.", problemHelper.getFile().getName()));
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        problemHelperBuilder.scanIssueList(allScanIssues);
        //Caching all the issues in the problem holder service
        problemHelper.getProblemHolderService().addProblems(problemHelper.getFilePath(), allScanIssues);

        //Creating problems
        List<ProblemDescriptor> allProblems = new ArrayList<>(createProblemDescriptors(problemHelperBuilder.build(), true));
        if (allProblems.isEmpty()) {
            LOGGER.info(format("RTS: Problem not found for file: %s. ", problemHelper.getFile().getName()));
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        //Caching all the problem descriptor in the problem holder service
        problemHelper.getProblemHolderService().addProblemDescriptors(problemHelper.getFilePath(), allProblems);
        LOGGER.info(format("RTS: Scanning completed for file: %s and %s problem descriptors created.", problemHelper.getFile().getName(), allProblems.size()));
        return allProblems.toArray(new ProblemDescriptor[0]);
    }

    /**
     * Creates a list of {@link ProblemDescriptor} instances by processing scan issues for the provided {@link ProblemHelper}
     * and decorating the UI accordingly.
     *
     * @param problemHelper the {@link ProblemHelper} containing the information needed to process and generate problem descriptors;
     *                      must not be null and should include a list of scan issues to process.
     * @return a {@link List} of {@link ProblemDescriptor} instances representing the detected issues, or an empty list if no issues are found.
     */
    public static List<ProblemDescriptor> createProblemDescriptors(ProblemHelper problemHelper, boolean isDecoratorEnabled) {
        if (isDecoratorEnabled) {
            // if decorator is enabled, remove all existing gutter icons before adding new ones
            ProblemDecorator.removeAllGutterIcons(problemHelper.getFile().getProject());
        }
        List<ProblemDescriptor> problems = new ArrayList<>();
        ScanIssueProcessor processor = new ScanIssueProcessor(problemHelper, problemHelper.getProblemDecorator());

        for (ScanIssue scanIssue : problemHelper.getScanIssueList()) {
            ProblemDescriptor descriptor = processor.processScanIssue(scanIssue, isDecoratorEnabled);
            if (descriptor != null) {
                problems.add(descriptor);
            }
        }
        LOGGER.info(format("RTS: Problem descriptors created: %s for file: %s", problems.size(), problemHelper.getFile().getName()));
        return problems;
    }

    /**
     * Scans the given file using all available scanner services and returns all issues found.
     *
     * @param problemHelper - The {@link ProblemHelper}
     * @return a list of {@link ScanIssue}
     */
    public static List<ScanIssue> scanFileAndGetAllIssues(ProblemHelper problemHelper) {
        List<ScanResult<?>> allScanResults = problemHelper.getSupportedScanners().stream()
                .map(scannerService ->
                        scanFile(scannerService, problemHelper.getFile(), problemHelper.getFilePath()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        LOGGER.info(format("RTS: Scan completed for all the enabled scanners for file: %s ", problemHelper.getFile().getName()));

        return allScanResults.stream()
                .flatMap(scanResult -> scanResult.getIssues().stream())
                .collect(Collectors.toList());
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
    public static ScanResult<?> scanFile(ScannerService<?> scannerService, @NotNull PsiFile file, @NotNull String path) {
        try {
            LOGGER.info(format("RTS: Scan initiated for engine: %s for file: %s.", scannerService.getConfig().getEngineName(), path));
            return scannerService.scan(file, path);
        } catch (Exception e) {
            LOGGER.debug("RTS: Exception occurred while scanning file: {} ", path, e.getMessage());
            return null;
        }
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
        if (file.getUserData(THEME_KEY) != null && !Objects.equals(file.getUserData(THEME_KEY), DevAssistUtils.isDarkTheme())) {
            ProblemDescription.reloadIcons(); // reload problem descriptions icons on theme change
            LOGGER.info("RTS: Theme changed, reloading icons for the theme and resetting the problem descriptors.");
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
    private ProblemDescriptor[] getExistingProblems(ProblemHolderService problemHolderService, String filePath, Document document,
                                                    PsiFile file, List<ScannerService<?>> supportedEnabledScanners) {

        boolean isFromScheduledScan = file.getUserData(SCAN_SOURCE) != null && Objects.equals(file.getUserData(SCAN_SOURCE), Boolean.TRUE);
        if (isFromScheduledScan) {
            LOGGER.info(format("RTS: Retrieving existing results after scheduled scan completes for file: %s.", file.getName()));
            return getExistingProblemsOnScheduledScanCompletion(problemHolderService, filePath, document, file);
        }
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
        List<ProblemDescriptor> enabledScannerProblems = getEnabledScannerProblems(filePath, problemDescriptorsList, enabledScanners);
        List<ScanIssue> enabledScanIssueList = scanIssueList.stream()
                .filter(scanIssue -> enabledScanners.contains(scanIssue.getScanEngine()))
                .collect(Collectors.toList());

        // Update gutter icons and problem descriptors for the file according to the latest state of scan settings.
        problemDecorator.decorateUI(file.getProject(), file, enabledScanIssueList, document);
        problemHolderService.addProblemDescriptors(filePath, enabledScannerProblems);
        return enabledScannerProblems.toArray(new ProblemDescriptor[0]);
    }

    /**
     * Gets the existing problem descriptors for the given file path after scheduled scan completion.
     *
     * @param problemHolderService the problem holder service.
     * @param filePath             the file path.
     * @return the problem descriptors.
     */
    private ProblemDescriptor[] getExistingProblemsOnScheduledScanCompletion(ProblemHolderService problemHolderService, String filePath, Document document, PsiFile file) {

        List<ProblemDescriptor> problemDescriptorsList = problemHolderService.getProblemDescriptors(filePath);
        if (problemDescriptorsList.isEmpty()) {
            LOGGER.warn(format("RTS: No problem descriptors found for file: %s after schedule scan completion.", filePath));
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        List<ScanIssue> scanIssueList = problemHolderService.getScanIssueByFile(filePath);
        if (scanIssueList.isEmpty()) {
            LOGGER.warn(format("RTS: No existing scan issues found for file: %s after schedule scan completion.", filePath));
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        // Update gutter icons and problem descriptors for the file according to the latest state of scan settings.
        problemDecorator.decorateUI(file.getProject(), file, scanIssueList, document);
        file.putUserData(SCAN_SOURCE, Boolean.FALSE);
        return problemDescriptorsList.toArray(new ProblemDescriptor[0]);
    }

    /**
     * Gets the existing problem descriptors for the given file path and enabled scanners.
     */
    private @NotNull List<ProblemDescriptor> getEnabledScannerProblems(String filePath, List<ProblemDescriptor> problemDescriptorsList,
                                                                       List<ScanEngine> enabledScanners) {
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
        return enabledScannerProblems;
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
        return ProblemHelper.builder(file, file.getProject())
                .manager(manager)
                .isOnTheFly(isOnTheFly)
                .document(document)
                .supportedScanners(supportedScanners)
                .filePath(path)
                .problemHolderService(problemHolderService)
                .problemDecorator(problemDecorator);
    }
}
