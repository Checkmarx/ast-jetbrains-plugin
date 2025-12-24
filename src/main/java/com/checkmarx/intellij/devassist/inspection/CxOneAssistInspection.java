package com.checkmarx.intellij.devassist.inspection;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.basescanner.ScannerService;
import com.checkmarx.intellij.devassist.problems.ProblemDecorator;
import com.checkmarx.intellij.devassist.problems.ProblemHelper;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

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
    private final CxOneAssistInspectionMgr cxOneAssistInspectionMgr = new CxOneAssistInspectionMgr();

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
            resetEditorAndResults(file.getProject(), null);
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        // On remediation process GitHub Copilot generating the fake file with the name Dummy.txt, so ignoring that file.
        if (isAgentEvent(virtualFile)) {
            LOGGER.warn(format("RTS: Received copilot event for file: %s. Skipping file..", file.getName()));
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        String filePath = virtualFile.getPath();
        if (!Utils.isUserAuthenticated() || !DevAssistUtils.isAnyScannerEnabled()) {
            LOGGER.warn(format("RTS: User not authenticated or No scanner is enabled, skipping file: %s", file.getName()));
            resetEditorAndResults(file.getProject(), filePath);
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (Objects.isNull(document)) {
            LOGGER.warn(format("RTS: Document not found for file: %s.", file.getName()));
            resetEditorAndResults(file.getProject(), filePath);
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        List<ScannerService<?>> supportedScanners = this.cxOneAssistInspectionMgr.getSupportedScanner(filePath, file);
        if (supportedScanners.isEmpty()) {
            LOGGER.warn(format("RTS: No supported scanner enabled for this file: %s.", file.getName()));
            resetEditorAndResults(file.getProject(), filePath);
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        ProblemHolderService problemHolderService = ProblemHolderService.getInstance(file.getProject());
        FileTimeStampHolder timeStampHolder = FileTimeStampHolder.getInstance(file.getProject());
        if (Objects.isNull(problemHolderService) || Objects.isNull(timeStampHolder)) {
            LOGGER.warn(format("RTS: Problem holder or timestamp holder not found for project: %s.", file.getProject().getName()));
            resetEditorAndResults(file.getProject(), filePath);
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        // Check if a file is modified and existing problem descriptors are valid
        Long cachedStamp = timeStampHolder.getTimeStamp(filePath);
        Long compositeStamp = getCompositeTimeStamp(file, virtualFile, document);
        if (Objects.nonNull(cachedStamp) && cachedStamp.longValue() == compositeStamp.longValue()
                && isProblemDescriptorValid(problemHolderService, filePath)) {
            LOGGER.info(format("RTS: File: %s is already scanned and file not modified, retrieving existing results.", file.getName()));
            return getExistingProblemDescriptors(problemHolderService, filePath, document, file, supportedScanners, manager);
        }
        timeStampHolder.updateTimeStamp(filePath, compositeStamp);
        file.putUserData(CxOneAssistInspectionMgr.THEME_KEY, DevAssistUtils.isDarkTheme());
        return scanFileAndCreateProblemDescriptors(file, manager, isOnTheFly, supportedScanners, document, problemHolderService, filePath);
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
     * @param filePath             the virtual file path of the file to be scanned
     * @return ProblemDescriptor[] array of problem descriptors
     */
    private ProblemDescriptor[] scanFileAndCreateProblemDescriptors(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly,
                                                                    List<ScannerService<?>> supportedScanners, Document document,
                                                                    ProblemHolderService problemHolderService, String filePath) {
        try {
            ProblemHelper.ProblemHelperBuilder problemHelperBuilder = buildHelper(file, manager, isOnTheFly, document,
                    supportedScanners, filePath, problemHolderService);

            // Schedule a debounced scan to avoid excessive scanning during rapid file changes (background scan)
            boolean isScanScheduled = CxOneAssistScanScheduler.getInstance(file.getProject())
                    .scheduleScan(filePath, problemHelperBuilder.build());
            if (isScanScheduled) {
                cxOneAssistInspectionMgr.putScanSourceFlag(file, Boolean.TRUE); // To identify the scan source
                return ProblemDescriptor.EMPTY_ARRAY; // Return empty array as problems will be added after the scheduled scan completes
            }
            LOGGER.info(format("RTS: Failed to schedule the scan for file: %s. Now scanning file using fallback..", file.getName()));
            return cxOneAssistInspectionMgr.startScanAndCreateProblemDescriptors(problemHelperBuilder);
        } catch (Exception exception) {
            LOGGER.warn(format("RTS: Exception occurred while scanning file: %s", filePath), exception);
            return ProblemDescriptor.EMPTY_ARRAY;
        }
    }

    /**
     * Retrieves existing problem descriptors for the given file.
     */
    private ProblemDescriptor[] getExistingProblemDescriptors(ProblemHolderService problemHolderService, String filePath, Document document,
                                                              PsiFile file, List<ScannerService<?>> supportedScanners, InspectionManager manager) {
        ProblemDescriptor[] problemDescriptors = this.cxOneAssistInspectionMgr
                .getExistingProblems(problemHolderService, filePath, document, file, supportedScanners, manager);
        if (Objects.isNull(problemDescriptors) || problemDescriptors.length == 0) {
            resetEditorAndResults(file.getProject(), filePath);
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        return problemDescriptors;
    }

    /**
     * Clears all problem descriptors and gutter icons for the given project.
     *
     * @param project the project to reset results for
     */
    private void resetEditorAndResults(Project project, String filePath) {
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
     *
     * @param problemHolderService the problem holder service
     * @param filePath             the file path
     * @return true if the problem descriptor is valid, false otherwise
     */
    private boolean isProblemDescriptorValid(ProblemHolderService problemHolderService, String filePath) {
        return !problemHolderService.getProblemDescriptors(filePath).isEmpty()
                && !problemHolderService.getScanIssueByFile(filePath).isEmpty();
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
                .problemDecorator(new ProblemDecorator());
    }

    /**
     * Builds a single “composite” stamp by XORing three stamps: PSI modificationStamp, Document modificationStamp, and VFS timeStamp.
     * By combining the modification information from the PSI (), in-memory content (), and the physical file ()
     * to produce a single "composite" value representing all possible sources of file changes. `file``document``virtualFile`
     */
    private Long getCompositeTimeStamp(PsiFile file, VirtualFile virtualFile, Document document) {
        // Build a composite stamp that reflects PSI, Document, and VFS changes
        return file.getModificationStamp() ^ document.getModificationStamp() ^ virtualFile.getTimeStamp();
    }
}
