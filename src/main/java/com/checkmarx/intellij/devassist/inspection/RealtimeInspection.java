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
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
        if(!DevAssistUtils.isInternetConnectivityActive()){
            return ProblemDescriptor.EMPTY_ARRAY;
        }

        VirtualFile virtualFile = file.getVirtualFile();
        List<ScanEngine> enabledScanners = DevAssistUtils.globalScannerController().getEnabledScanners();

        if (Objects.isNull(virtualFile) || enabledScanners.isEmpty()) {
            LOGGER.warn(format("RTS: No scanner is enabled, skipping file: %s", file.getName()));
            problemDecorator.removeAllGutterIcons(file);
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        String path= virtualFile.getPath();
        List<ScannerService<?>> supportedScanners = getSupportedScanner(path);

        if (supportedScanners.isEmpty() || !isRealTimeScannerActive(supportedScanners, enabledScanners)) {
            LOGGER.warn(format("RTS: No supported scanner found or scanner inactive for this file: %s.", file.getName()));
            problemDecorator.removeAllGutterIcons(file);
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        ProblemHolderService problemHolderService = ProblemHolderService.getInstance(file.getProject());
        long currentModificationTime = file.getModificationStamp();

        if (fileTimeStamp.containsKey(path) && fileTimeStamp.get(path) == (currentModificationTime)
                && isProblemDescriptorValid(problemHolderService, path, file)) {
            return getProblemsForEnabledScanners(problemHolderService, enabledScanners, path);
        }
        fileTimeStamp.put(path, currentModificationTime);
        file.putUserData(key, DevAssistUtils.isDarkTheme());

        Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (document == null) return ProblemDescriptor.EMPTY_ARRAY;

        ProblemHelper.ProblemHelperBuilder problemHelperBuilder = buildHelper(file, manager, isOnTheFly, document);
        problemHelperBuilder.supportedScanners(supportedScanners);
        problemHelperBuilder.filePath(path);
        problemHelperBuilder.problemHolderService(problemHolderService);
        return scanFileAndCreateProblemDescriptors(problemHelperBuilder);
    }

    /**
     * Retrieves all supported instances of {@link ScannerService} for handling real-time scanning
     * of the specified file. The method checks available scanner services to determine if
     * any of them is suited to handle the given file path.
     *
     * @param filePath the path of the file as a string, used to identify an applicable scanner service; must not be null or empty
     * @return an {@link Optional} containing the matching {@link ScannerService} if found, or an empty {@link Optional} if no appropriate service exists
     */
    private List<ScannerService<?>> getSupportedScanner(String filePath) {
        return scannerFactory.getAllSupportedScanners(filePath);
    }

    /**
     * Checks if the supported real-time scanner is active for the given {@link ScannerService}.
     *
     * @param supportedScanners the list of supported {@link ScannerService} instances for the file
     * @param enabledScanners   the list of enabled {@link ScanEngine} instances for the project
     * @return true if the real-time scanner corresponding to the given scanner service is active, false otherwise
     */
    private boolean isRealTimeScannerActive(List<ScannerService<?>> supportedScanners, List<ScanEngine> enabledScanners) {
        return enabledScanners.stream().anyMatch(engine ->
                supportedScanners.stream().anyMatch(scannerService ->
                        scannerService.getConfig().getEngineName().toUpperCase().equals(engine.name())));
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
            return false;
        }
        return !problemHolderService.getProblemDescriptors(path).isEmpty();
    }

    /**
     * Gets the problem descriptors for the given file path and enabled scanners.
     *
     * @param problemHolderService the problem holder service.
     * @param enabledScanners      the list of enabled scanners.
     * @param filePath             the file path.
     * @return the problem descriptors.
     */
    private ProblemDescriptor[] getProblemsForEnabledScanners(ProblemHolderService problemHolderService, List<ScanEngine> enabledScanners, String filePath) {
        List<ProblemDescriptor> problemDescriptorsList = problemHolderService.getProblemDescriptors(filePath);

        if (problemDescriptorsList.isEmpty()) return ProblemDescriptor.EMPTY_ARRAY;

        List<ProblemDescriptor> filteredProblems = new ArrayList<>();
        for (ProblemDescriptor descriptor : problemDescriptorsList) {
            try {
                CxOneAssistFix cxOneAssistFix = (CxOneAssistFix) descriptor.getFixes()[0];
                if (Objects.nonNull(cxOneAssistFix) && enabledScanners.contains(cxOneAssistFix.getScanIssue().getScanEngine())) {
                    filteredProblems.add(descriptor);
                }
            } catch (Exception e) {
                LOGGER.debug("RTS: Exception occurred while getting existing problems for enabled scanner for file: {} ",
                        filePath, e.getMessage());
                filteredProblems.add(descriptor);
            }
        }
        problemHolderService.addProblemDescriptors(filePath, filteredProblems);
        return filteredProblems.toArray(new ProblemDescriptor[0]);
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
    private ProblemHelper.ProblemHelperBuilder buildHelper(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly, Document document) {
        return ProblemHelper.builder().file(file).manager(manager).isOnTheFly(isOnTheFly).document(document);
    }

    /**
     * Scans the given PSI file and creates problem descriptors for any identified issues.
     *
     * @param problemHelperBuilder - The {@link ProblemHelper}
     * @return an array of {@link ProblemDescriptor} representing the detected issues, or an empty array if no issues were found
     */
    private ProblemDescriptor[] scanFileAndCreateProblemDescriptors(ProblemHelper.ProblemHelperBuilder problemHelperBuilder) {
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
        return allProblems.toArray(new ProblemDescriptor[0]);
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
        this.problemDecorator.removeAllGutterIcons(problemHelper.getFile());
        ScanIssueProcessor processor = new ScanIssueProcessor(problemHelper, this.problemDecorator);

        for (ScanIssue scanIssue : problemHelper.getScanResult().getIssues()) {
            ProblemDescriptor descriptor = processor.processScanIssue(scanIssue);
            if (descriptor != null) {
                problems.add(descriptor);
            }
        }
        LOGGER.debug("RTS: Problem descriptors created: {} for file: {}", problems.size(), problemHelper.getFile().getName());
        return problems;
    }
}
