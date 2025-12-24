package com.checkmarx.intellij.devassist.inspection;

import com.checkmarx.intellij.Constants;
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
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * Manager class for handling inspections and problem descriptors related to CxOneAssist.
 */
public final class CxOneAssistInspectionMgr extends ScanManager {

    private static final Logger LOGGER = Utils.getLogger(CxOneAssistInspectionMgr.class);
    private static final Key<Boolean> SCAN_SOURCE = Key.create("SCAN_SOURCE");
    public static final Key<Boolean> THEME_KEY = Key.create(Constants.RealTimeConstants.THEME);
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

        boolean isFromScheduledScan = file.getUserData(SCAN_SOURCE) != null && Objects.equals(file.getUserData(SCAN_SOURCE), Boolean.TRUE);
        if (isFromScheduledScan) {
            LOGGER.info(format("RTS: Retrieving existing results after scheduled scan completes for file: %s.", file.getName()));
            return getCachedProblemsOnScheduledScanCompletion(problemHolderService, filePath, document, file);
        }
        if (isThemeChanged(file)) {
            LOGGER.info(format("RTS: Theme has been changed, reloading the icons and creating the problem descriptors" +
                    " with new icons for the file: %s using existing scan results.", file.getName()));
            return createProblemDescriptorsOnThemeChanged(problemHolderService, filePath, document, file, supportedEnabledScanners, manager);
        }
        return getCachedProblemDescriptorsForNonModifiedFile(problemHolderService, filePath, document, file, supportedEnabledScanners);
    }

    /**
     * Gets the existing problem descriptors for the given file path and enabled scanners.
     * This method is used when the file is not modified after scheduled scan completion.
     */
    private ProblemDescriptor @NotNull [] getCachedProblemDescriptorsForNonModifiedFile(ProblemHolderService problemHolderService, String filePath, Document document, PsiFile file, List<ScannerService<?>> supportedEnabledScanners) {
        LOGGER.info(format("RTS: Started retrieving problem descriptor for non modified file: %s.", file.getName()));
        /*
         * If a file already scanned and after that if scanner settings are changed (enabled/disabled),
         * we need to filter the existing problems and return only those which are related to enabled scanners
         */
        List<ScanEngine> enabledScanners = supportedEnabledScanners.stream()
                .map(scannerService ->
                        ScanEngine.valueOf(scannerService.getConfig().getEngineName().toUpperCase()))
                .collect(Collectors.toList());

        List<ProblemDescriptor> problemDescriptorsList = problemHolderService.getProblemDescriptors(filePath);

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
        LOGGER.info(format("RTS: Completed retrieving problem descriptor for non modified file: %s.", file.getName()));
        return enabledScannerProblems.toArray(new ProblemDescriptor[0]);
    }

    /**
     * Gets the existing problem descriptors for the given file path after scheduled scan completion.
     *
     * @param problemHolderService the problem holder service.
     * @param filePath             the file path.
     * @return the problem descriptors.
     */
    private ProblemDescriptor[] getCachedProblemsOnScheduledScanCompletion(ProblemHolderService problemHolderService, String filePath, Document document, PsiFile file) {

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
     * Creates problem descriptors when the theme has changed. As the inspection tooltip doesn't support dynamic icon change in the tooltip description
     */
    private ProblemDescriptor[] createProblemDescriptorsOnThemeChanged(ProblemHolderService problemHolderService, String filePath, Document document,
                                                                       PsiFile file, List<ScannerService<?>> supportedScanners, InspectionManager manager) {
        LOGGER.info(format("RTS: Started problem descriptor creation using existing scan results on theme changed for the file: %s.", file.getName()));
        // Reload problem descriptions icons on theme change, as the inspection tooltip doesn't support dynamic icon change in the tooltip description.
        ProblemDescription.reloadIcons();

        ProblemHelper problemHelper = ProblemHelper.builder(file, file.getProject())
                .manager(manager)
                .isOnTheFly(true)
                .document(document)
                .supportedScanners(supportedScanners)
                .filePath(filePath)
                .problemHolderService(problemHolderService)
                .problemDecorator(new ProblemDecorator())
                .scanIssueList(problemHolderService.getScanIssueByFile(filePath))
                .build();

        List<ProblemDescriptor> problemList = createProblemDescriptors(problemHelper, true);
        problemHolderService.addProblemDescriptors(filePath, problemList);
        LOGGER.info(format("RTS: Completed problem descriptor creation using existing scan results on theme changed for the file: %s.", file.getName()));
        return problemList.toArray(new ProblemDescriptor[0]);
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
     * Sets the scan source flag for the given file.
     *
     * @param file {@link PsiFile} to set the flag for
     * @param flag boolean flag indicating if the scan is from scheduled scan
     */
    public void putScanSourceFlag(PsiFile file, Boolean flag) {
        file.putUserData(SCAN_SOURCE, flag);
    }

    /**
     * Checks if the theme has changed since the last inspection of the given file.
     */
    private boolean isThemeChanged(PsiFile file) {
        return file.getUserData(THEME_KEY) != null
                && !Objects.equals(file.getUserData(THEME_KEY), DevAssistUtils.isDarkTheme());
    }
}
