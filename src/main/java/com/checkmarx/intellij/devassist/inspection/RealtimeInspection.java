package com.checkmarx.intellij.devassist.inspection;

import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.basescanner.ScannerService;
import com.checkmarx.intellij.devassist.common.ScanResult;
import com.checkmarx.intellij.devassist.common.ScannerFactory;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.problems.ProblemDecorator;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.checkmarx.intellij.devassist.problems.ScanIssueProcessor;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
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
        String path = file.getVirtualFile().getPath();
        if(path.isEmpty()){
           return ProblemDescriptor.EMPTY_ARRAY;
        }
        Optional<ScannerService<?>> scannerService = getScannerService(path);

        if (scannerService.isEmpty() || !isRealTimeScannerActive(scannerService.get())) {
            LOGGER.warn(format("RTS: Scanner is not active for file: %s.", file.getName()));
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        ProblemHolderService problemHolderService = ProblemHolderService.getInstance(file.getProject());
        long currentModificationTime = file.getModificationStamp();

        if (fileTimeStamp.containsKey(path) && fileTimeStamp.get(path) == (currentModificationTime)) {
            return problemHolderService.getProblemDescriptors(path).toArray(new ProblemDescriptor[0]);
        }
        fileTimeStamp.put(path, currentModificationTime);

        Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (document == null) return ProblemDescriptor.EMPTY_ARRAY;

        ScanResult<?> scanResult = scanFile(scannerService.get(), file, path);
        if (Objects.isNull(scanResult)) return ProblemDescriptor.EMPTY_ARRAY;

        List<ProblemDescriptor> problems = createProblemDescriptors(file, manager, isOnTheFly, scanResult, document);
        problemHolderService.addProblemDescriptors(path, problems);
        ProblemHolderService.addToCxOneFindings(file, scanResult.getIssues());
        return problems.toArray(new ProblemDescriptor[0]);
    }

    /**
     * Retrieves an appropriate instance of {@link ScannerService} for handling real-time scanning
     * of the specified file. The method checks available scanner services to determine if
     * any of them is suited to handle the given file path.
     *
     * @param filePath the path of the file as a string, used to identify an applicable scanner service; must not be null or empty
     * @return an {@link Optional} containing the matching {@link ScannerService} if found, or an empty {@link Optional} if no appropriate service exists
     */
    private Optional<ScannerService<?>> getScannerService(String filePath) {
        return scannerFactory.findRealTimeScanner(filePath);
    }

    /**
     * Checks if the real-time scanner is active for the given {@link ScannerService}.
     *
     * @param scannerService the scanner service whose active status is to be checked; must not be null
     * @return true if the real-time scanner corresponding to the given scanner service is active, false otherwise
     */
    private boolean isRealTimeScannerActive(ScannerService<?> scannerService) {
        return DevAssistUtils.isScannerActive(scannerService.getConfig().getEngineName());
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
        return scannerService.scan(file, path);
    }

    /**
     * Creates a list of {@link ProblemDescriptor} objects based on the issues identified in the scan result.
     * This method processes the scan issues for the specified file and uses the provided InspectionManager
     * to generate corresponding problem descriptors, if applicable.
     *
     * @param file       the {@link PsiFile} being inspected; must not be null
     * @param manager    the {@link InspectionManager} used to create problem descriptors; must not be null
     * @param isOnTheFly a flag that indicates whether the inspection is executed on-the-fly
     * @param scanResult the result of the scan containing issues to process
     * @param document   the {@link Document} object representing the content of the file
     * @return a list of {@link ProblemDescriptor}; an empty list is returned if no issues are found or processed successfully
     */
    private List<ProblemDescriptor> createProblemDescriptors(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly,
                                                             ScanResult<?> scanResult, Document document) {
        List<ProblemDescriptor> problems = new ArrayList<>();
        this.problemDecorator.removeAllGutterIcons(file);
        ScanIssueProcessor processor = new ScanIssueProcessor(this.problemDecorator, file, manager, document, isOnTheFly);

        for (ScanIssue scanIssue : scanResult.getIssues()) {
            ProblemDescriptor descriptor = processor.processScanIssue(scanIssue);
            if (descriptor != null) {
                problems.add(descriptor);
            }
        }
        LOGGER.debug("RTS: Problem descriptors created: {} for file: {}", problems.size(), file.getName());
        return problems;
    }
}
