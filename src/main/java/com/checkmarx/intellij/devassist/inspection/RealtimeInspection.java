package com.checkmarx.intellij.devassist.inspection;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.basescanner.ScannerService;
import com.checkmarx.intellij.devassist.common.ScanResult;
import com.checkmarx.intellij.devassist.common.ScannerFactory;
import com.checkmarx.intellij.devassist.configuration.GlobalScannerController;
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
    private final Map<String, Long> fileTimeStamp = new ConcurrentHashMap<>();
    private final ScannerFactory scannerFactory = new ScannerFactory();
    private final ProblemDecorator problemDecorator = new ProblemDecorator();
    private final Key<Boolean> key = Key.create(Constants.RealTimeConstants.THEME);

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
        if (Objects.isNull(virtualFile) || !DevAssistUtils.isAnyScannerEnabled()) {
            LOGGER.warn(format("RTS: VirtualFile object not found or No scanner is enabled, skipping file: %s", file.getName()));
            resetResults(file.getProject());
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        List<ScannerService<?>> supportedScanners = getSupportedEnabledScanner(virtualFile.getPath());
        if (supportedScanners.isEmpty()) {
            LOGGER.warn(format("RTS: No supported scanner enabled for this file: %s.", file.getName()));
            resetResults(file.getProject());
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        ProblemHolderService problemHolderService = ProblemHolderService.getInstance(file.getProject());
        long currentModificationTime = file.getModificationStamp();
        if (fileTimeStamp.containsKey(virtualFile.getPath()) && fileTimeStamp.get(virtualFile.getPath()) == (currentModificationTime)
                && isProblemDescriptorValid(problemHolderService, virtualFile.getPath(), file)) {
            LOGGER.info(format("RTS: File: %s is already scanned, retrieving existing results.", file.getName()));
            return getExistingProblemsForEnabledScanners(problemHolderService, virtualFile.getPath());
        }
        fileTimeStamp.put(virtualFile.getPath(), currentModificationTime);
        file.putUserData(key, DevAssistUtils.isDarkTheme());
        Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (document == null) return ProblemDescriptor.EMPTY_ARRAY;

        ProblemHelper.ProblemHelperBuilder problemHelperBuilder = buildHelper(file, manager, isOnTheFly, document,
                supportedScanners, virtualFile.getPath(), problemHolderService);

        List<ProblemDescriptor> scanResultDescriptors = startScanAndCreateProblemDescriptors(problemHelperBuilder);
        if (scanResultDescriptors.isEmpty()) {
            LOGGER.info(format("RTS: No issues found for file: %s resetting the editor state", file.getName()));
            resetResults(file.getProject());
        }
        LOGGER.info(format("RTS: Scanning completed and descriptors created: %s for file: %s", scanResultDescriptors.size(), file.getName()));
        return scanResultDescriptors.toArray(new ProblemDescriptor[0]);
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
    private List<ScannerService<?>> getSupportedEnabledScanner(String filePath) {
        List<ScannerService<?>> supportedScanners = scannerFactory.getAllSupportedScanners(filePath);
        if (supportedScanners.isEmpty()) {
            LOGGER.warn(format("RTS: No supported scanner found for this file: %s.", filePath));
            return Collections.emptyList();
        }
        return supportedScanners.stream()
                .filter(scannerService ->
                        DevAssistUtils.isScannerActive(scannerService.getConfig().getEngineName()))
                .collect(Collectors.toList());
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
            LOGGER.info("RTS: Theme changed, resetting problem descriptors");
            return false;
        }
        return !problemHolderService.getProblemDescriptors(path).isEmpty();
    }

    /**
     * Gets the problem descriptors for the given file path and enabled scanners.
     *
     * @param problemHolderService the problem holder service.
     * @param filePath             the file path.
     * @return the problem descriptors.
     */
    private ProblemDescriptor[] getExistingProblemsForEnabledScanners(ProblemHolderService problemHolderService, String filePath) {
        List<ProblemDescriptor> problemDescriptorsList = problemHolderService.getProblemDescriptors(filePath);
        List<ScanEngine> enabledScanners = GlobalScannerController.getInstance().getEnabledScanners();
        if (problemDescriptorsList.isEmpty() || enabledScanners.isEmpty()) return ProblemDescriptor.EMPTY_ARRAY;

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
     * @param problemHelperBuilder - The {@link ProblemHelper}
     * @return a list of {@link ProblemDescriptor} representing the detected issues, or an empty list if no issues were found
     */
    private List<ProblemDescriptor> startScanAndCreateProblemDescriptors(ProblemHelper.ProblemHelperBuilder problemHelperBuilder) {
        ProblemHelper problemHelper = problemHelperBuilder.build();
        List<ProblemDescriptor> allProblems = new ArrayList<>();
        List<ScanIssue> allScanIssues = new ArrayList<>();

        for (ScannerService<?> scannerService : problemHelper.getSupportedScanners()) {
            ScanResult<?> scanResult = scanFile(scannerService, problemHelper.getFile(), problemHelper.getFilePath());
            if (Objects.isNull(scanResult)) continue;
            problemHelperBuilder.scanResult(scanResult);
            allProblems.addAll(createProblemDescriptors(problemHelperBuilder.build()));
            allScanIssues.addAll(scanResult.getIssues());
        }
        problemHelper.getProblemHolderService().addProblemDescriptors(problemHelper.getFilePath(), allProblems);
        problemHelper.getProblemHolderService().addProblems(problemHelper.getFilePath(), allScanIssues);
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
            LOGGER.info(format("RTS: Scanning file: %s using scanner: %s", path, scannerService.getConfig().getEngineName()));
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

        for (ScanIssue scanIssue : problemHelper.getScanResult().getIssues()) {
            ProblemDescriptor descriptor = processor.processScanIssue(scanIssue);
            if (descriptor != null) {
                problems.add(descriptor);
            }
        }
        LOGGER.info(format("RTS: Problem descriptors created: %s for file: %s", problems.size(), problemHelper.getFile().getName()));
        return problems;
    }
}
