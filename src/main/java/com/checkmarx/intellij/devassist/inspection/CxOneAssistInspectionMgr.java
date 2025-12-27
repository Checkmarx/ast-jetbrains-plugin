package com.checkmarx.intellij.devassist.inspection;

import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.basescanner.ScannerService;
import com.checkmarx.intellij.devassist.common.ScanManager;
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
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.checkmarx.intellij.devassist.utils.DevAssistConstants.Keys.SCAN_SOURCE_KEY;
import static com.checkmarx.intellij.devassist.utils.DevAssistConstants.Keys.THEME_KEY;
import static java.lang.String.format;

/**
 * Manager class for handling inspections and problem descriptors related to CxOneAssist.
 */
public final class CxOneAssistInspectionMgr extends ScanManager {

    private static final Logger LOGGER = Utils.getLogger(CxOneAssistInspectionMgr.class);
    private final ProblemDecorator problemDecorator = new ProblemDecorator();

    /**
     * Scans the given PSI file and creates problem descriptors for any identified issues.
     *
     * @param problemHelperBuilder - The {@link ProblemHelper}
     * @return an array of {@link ProblemDescriptor} representing the detected issues, or an empty array if no issues were found
     */
    public ProblemDescriptor[] startScanAndCreateProblemDescriptors(ProblemHelper.ProblemHelperBuilder problemHelperBuilder) {
        ProblemHelper problemHelper = problemHelperBuilder.build();

        LOGGER.info(format("RTS: Scan started for file: %s.", problemHelper.getFile().getName()));

        List<ScanIssue> allScanIssues = scanFile(problemHelper.getFilePath(), problemHelper.getFile(), ScanEngine.ALL);
        if (allScanIssues.isEmpty()) {
            LOGGER.info(format("RTS: No scan issues found for file: %s.", problemHelper.getFile().getName()));
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        problemHelperBuilder.scanIssueList(allScanIssues);
        //Caching all the issues in the problem holder service
        problemHelper.getProblemHolderService().addScanIssues(problemHelper.getFilePath(), allScanIssues);

        //Creating problems
        List<ProblemDescriptor> allProblems = new ArrayList<>(createProblemDescriptors(problemHelperBuilder.build(), Boolean.TRUE));
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
    public List<ProblemDescriptor> createProblemDescriptors(ProblemHelper problemHelper, boolean isDecoratorEnabled) {
        if (problemHelper.getScanIssueList().isEmpty()) {
            LOGGER.warn(format("RTS: No scan issues found to create problem descriptor for file: %s.", problemHelper.getFile().getName()));
            return Collections.emptyList();
        }
        if (isDecoratorEnabled) {
            // if decorator is enabled, remove all existing gutter icons before adding new ones
            ProblemDecorator.removeAllHighlighters(problemHelper.getFile().getProject());
        }
        List<ProblemDescriptor> problems = new ArrayList<>();
        ScanIssueProcessor processor = new ScanIssueProcessor(problemHelper);

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
     * Gets all supported and enabled scanners for the given file path.
     *
     * @param filePath the file path.
     * @return the list of supported and enabled scanners.
     */
    public List<ScannerService<?>> getSupportedScanner(String filePath, PsiFile psiFile) {
        return getSupportedEnabledScanner(filePath, psiFile);
    }

    /**
     * Gets the existing problem descriptors for the given file path and enabled scanners.
     *
     * @param problemHolderService the problem holder service.
     * @param filePath             the file path.
     * @return the problem descriptors.
     */
    public ProblemDescriptor[] getExistingProblems(ProblemHolderService problemHolderService, String filePath, Document document,
                                                   PsiFile file, List<ScannerService<?>> supportedEnabledScanners, InspectionManager manager) {

        boolean isFromScheduledScan = file.getUserData(SCAN_SOURCE_KEY) != null && Objects.equals(file.getUserData(SCAN_SOURCE_KEY), Boolean.TRUE);
        if (isFromScheduledScan) {
            LOGGER.info(format("RTS: Retrieving existing results after scheduled scan completes for file: %s.", file.getName()));
            return getCachedProblemsOnScheduledScanCompletion(problemHolderService, filePath, document, file);
        }
        ProblemHelper problemHelper = ProblemHelper.builder(file, file.getProject())
                .manager(manager)
                .isOnTheFly(true)
                .document(document)
                .filePath(filePath)
                .problemHolderService(problemHolderService)
                .supportedScanners(supportedEnabledScanners)
                .problemDecorator(new ProblemDecorator())
                .build();

        return getCachedProblemDescriptorsForNonModifiedFile(problemHelper, filePath, file);
    }

    /**
     * Gets the existing problem descriptors for the given file path and enabled scanners.
     * This method is used when the file is not modified after scheduled scan completion.
     */
    private ProblemDescriptor @NotNull [] getCachedProblemDescriptorsForNonModifiedFile(ProblemHelper problemHelper, String filePath, PsiFile file) {
        LOGGER.info(format("RTS: Started retrieving problem descriptor for non modified file: %s.", file.getName()));
        updateScanSourceFlag(file, Boolean.FALSE);
        /*
         * If a file already scanned and after that if scanner settings are changed (enabled/disabled),
         * we need to filter the existing problems and return only those which are related to enabled scanners
         */
        List<ScanEngine> enabledScanEngines = problemHelper.getSupportedScanners().stream()
                .map(scannerService ->
                        ScanEngine.valueOf(scannerService.getConfig().getEngineName().toUpperCase()))
                .collect(Collectors.toList());

        if (enabledScanEngines.isEmpty()) {
            LOGGER.warn(format("RTS: No enabled scan engines found for file: %s.", filePath));
            resetEditorAndResults(file.getProject(), filePath);
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        List<ScanIssue> scanIssueList = problemHelper.getProblemHolderService().getScanIssueByFile(filePath);
        if (scanIssueList.isEmpty()) {
            LOGGER.warn(format("RTS: No existing scan issues found for file: %s.", filePath));
            resetEditorAndResults(file.getProject(), filePath);
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        List<ProblemDescriptor> problemDescriptorsList = problemHelper.getProblemHolderService().getProblemDescriptors(filePath);
        if (problemDescriptorsList.isEmpty()) {
            // File dose doesn't have any existing problem descriptors, but it may have existing scan results (unknown or ok).
            LOGGER.warn(format("RTS: No existing problem descriptors found for file: %s or no enabled scanners found.", filePath));
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        // Check if any new scanner is enabled or any scanner is disabled by a user
        // Get all scan engines from existing scan issues
        List<ScanEngine> existingScanEngineList = scanIssueList.stream().map(ScanIssue::getScanEngine).collect(Collectors.toList());

        // Check if any engine from existing scan results is not present in current enabled scan engine list
        boolean hasDisabledEngines = existingScanEngineList.stream().anyMatch(engine -> !enabledScanEngines.contains(engine));

        // Check if any new scan engine is enabled
        boolean hasNewEnabledEngines = enabledScanEngines.stream().anyMatch(engine -> !existingScanEngineList.contains(engine));
        boolean isThemeChanged = isThemeChanged(file); // Check if the theme has changed

        // if scan engines state and theme are not changed, return the existing problem descriptor list
        if (!hasDisabledEngines && !hasNewEnabledEngines && !isThemeChanged) {
            LOGGER.info(format("RTS: No change in scanners state and theme, Returning existing problem descriptors for file: %s.", file.getName()));
            return problemDescriptorsList.toArray(new ProblemDescriptor[0]);
        }
        List<ProblemDescriptor> updatedScannerProblems = new ArrayList<>(problemDescriptorsList);
        List<ScanIssue> updatedScanIssueList = new ArrayList<>(scanIssueList);

        // If any new scanner is enabled, create problem descriptors for those
        if (hasNewEnabledEngines) {
            LOGGER.info(format("RTS: New enabled scanners detected! Creating problem descriptors for new scanners for file: %s." +
                    " (This functionality currently not supported))", file.getName()));
            // need to trigger a scan for new scanners here
        }
        /*
         * If some of the scan engines disabled and results already loaded with the old state,
         *  then update results and problem descriptors with the new state of scan engines.
         */
        if (hasDisabledEngines) {
            LOGGER.info(format("RTS: Some scan engines are now disabled for file: %s.", file.getName()));
            updatedScanIssueList = scanIssueList.stream()
                    .filter(scanIssue -> enabledScanEngines.contains(scanIssue.getScanEngine()))
                    .collect(Collectors.toList());
        }
        if (isThemeChanged) {
            ProblemHelper updatedHelper = problemHelper.toBuilder(problemHelper).scanIssueList(updatedScanIssueList).build();
            updatedScannerProblems = createProblemDescriptorsOnThemeChanged(updatedHelper);
        } else if (hasDisabledEngines) {
            updatedScannerProblems = getEnabledScannerProblems(filePath, problemDescriptorsList, enabledScanEngines);
        }
        LOGGER.info(format("RTS: Decorating UI as per the latest state of the scanners using existing results for file: %s.", file.getName()));

        // Update gutter icons and problem descriptors for the file according to the latest state of scan settings.
        problemDecorator.decorateUI(file.getProject(), file, updatedScanIssueList, problemHelper.getDocument());

        problemHelper.getProblemHolderService().addScanIssues(filePath, updatedScanIssueList);
        problemHelper.getProblemHolderService().addProblemDescriptors(filePath, updatedScannerProblems);

        LOGGER.info(format("RTS: Completed retrieving problem descriptor for non modified file: %s.", file.getName()));
        return !updatedScannerProblems.isEmpty() ? updatedScannerProblems.toArray(new ProblemDescriptor[0]) : ProblemDescriptor.EMPTY_ARRAY;
    }

    /**
     * Gets the existing problem descriptors for the given file path after scheduled scan completion.
     *
     * @param problemHolderService the problem holder service.
     * @param filePath             the file path.
     * @return the problem descriptors.
     */
    private ProblemDescriptor[] getCachedProblemsOnScheduledScanCompletion(ProblemHolderService problemHolderService, String filePath, Document document, PsiFile file) {

        List<ScanIssue> scanIssueList = problemHolderService.getScanIssueByFile(filePath);
        if (scanIssueList.isEmpty()) {
            LOGGER.warn(format("RTS: No existing scan issues found after schedule scan completion for file: %s.", filePath));
            resetEditorAndResults(file.getProject(), filePath);
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        // Update gutter icons and problem descriptors for the file according to the latest state of scan settings.
        problemDecorator.decorateUI(file.getProject(), file, scanIssueList, document);
        updateScanSourceFlag(file, Boolean.FALSE);

        // Problem descriptors already cached, if no problem descriptor found means all results received with OK or Unknown status
        List<ProblemDescriptor> problemDescriptorsList = problemHolderService.getProblemDescriptors(filePath);
        if (problemDescriptorsList.isEmpty()) {
            LOGGER.warn(format("RTS: No problem descriptors found after schedule scan completion." +
                    " Only Ok or Unknow results available for the file: %s .", filePath));
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        return problemDescriptorsList.toArray(new ProblemDescriptor[0]);
    }

    /**
     * Creates problem descriptors when the theme has changed. As the inspection tooltip doesn't support dynamic icon change in the tooltip description
     */
    private List<ProblemDescriptor> createProblemDescriptorsOnThemeChanged(ProblemHelper problemHelper) {
        LOGGER.info(format("RTS: Started problem descriptor creation using existing scan results on theme changed for the file: %s.", problemHelper.getFile().getName()));
        // Reload problem descriptions icons on theme change, as the inspection tooltip doesn't support dynamic icon change in the tooltip description.
        ProblemDescription.reloadIcons();

        List<ProblemDescriptor> problemList = createProblemDescriptors(problemHelper, Boolean.FALSE);
        LOGGER.info(format("RTS: Completed problem descriptor creation using existing scan results on theme changed for the file: %s.", problemHelper.getFile().getName()));
        return problemList;
    }

    /**
     * Gets the existing problem descriptors for the given file path and enabled scanners.
     */
    private @NotNull List<ProblemDescriptor> getEnabledScannerProblems(String filePath, List<ProblemDescriptor> problemDescriptorsList,
                                                                       List<ScanEngine> enabledScanEngines) {
        List<ProblemDescriptor> enabledScannerProblems = new ArrayList<>();
        for (ProblemDescriptor descriptor : problemDescriptorsList) {
            try {
                CxOneAssistFix cxOneAssistFix = (CxOneAssistFix) descriptor.getFixes()[0];
                if (Objects.nonNull(cxOneAssistFix) && enabledScanEngines.contains(cxOneAssistFix.getScanIssue().getScanEngine())) {
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
     * Sets the scan source flag for the given file.
     *
     * @param file {@link PsiFile} to set the flag for
     * @param flag boolean flag indicating if the scan is from scheduled scan
     */
    public void updateScanSourceFlag(PsiFile file, Boolean flag) {
        file.putUserData(SCAN_SOURCE_KEY, flag);
    }

    /**
     * Checks if the theme has changed since the last inspection of the given file.
     */
    private boolean isThemeChanged(PsiFile file) {
        return file.getUserData(THEME_KEY) != null
                && !Objects.equals(file.getUserData(THEME_KEY), DevAssistUtils.isDarkTheme());
    }

    /**
     * Clears all problem descriptors and gutter icons for the given project.
     *
     * @param project  the project to reset results for
     * @param filePath the file path to reset results for
     */
    public void resetEditorAndResults(Project project, String filePath) {
        if (project.isDisposed()) {
            return;
        }
        ProblemDecorator.removeAllHighlighters(project);
        ProblemHolderService problemHolderService = ProblemHolderService.getInstance(project);

        if (Objects.nonNull(problemHolderService) && Objects.nonNull(filePath) && !filePath.isEmpty()) {
            problemHolderService.removeProblemDescriptorsForFile(filePath);
            problemHolderService.removeScanIssues(filePath);
        }
        LOGGER.debug(format("RTS: Resetting editor state (remove icons and problems) for project: %s.", project.getName()));
    }

    /*private List<ScanEngine> getNewEnabledScanners(List<ScanEngine> enabledScanners, List<ScanEngine> scanEngineList) {
        List<ScanEngine> newScanEngines = scanEngineList.stream()
                .filter(engine -> !enabledScanners.contains(engine))
                .collect(Collectors.toList());
        List<ScanIssue> newEngineScanIssueList = new ArrayList<>();
        for (ScanEngine newEngine : newScanEngines) {
            List<ScanIssue> newEngineScanIssue = scanFile(filePath, file, newEngine);
            if (!newEngineScanIssue.isEmpty()) {
                newEngineScanIssueList.addAll(newEngineScanIssue);
            }
        }
        List<ScanIssue> newScanIssues = scanFile(filePath, file, ScanEngine.ALL).stream()
                .filter(scanIssue -> newEnabledScanners.contains(scanIssue.getScanEngine()))
                .collect(Collectors.toList());
        if (!newScanIssues.isEmpty()) {
            ProblemHelper problemHelper = ProblemHelper.builder(file, file.getProject())
                    .manager(ProblemHelper.getInspectionManager(file.getProject()))
                    .isOnTheFly(true)
                    .document(document)
                    .supportedScanners(supportedEnabledScanners)
                    .filePath(filePath)
                    .problemHolderService(problemHolderService)
                    .problemDecorator(problemDecorator)
                    .scanIssueList(newScanIssues)
                    .build();
    }*/
}
