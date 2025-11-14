package com.checkmarx.intellij.inspections;

import com.checkmarx.ast.asca.ScanDetail;
import com.checkmarx.ast.asca.ScanResult;
import com.checkmarx.ast.ossrealtime.OssRealtimeScanPackage;
import com.checkmarx.ast.realtime.RealtimeLocation;
import com.checkmarx.intellij.devassist.model.Location;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.ui.ProblemDescription;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.checkmarx.intellij.service.AscaService;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.inspections.quickfixes.AscaQuickFix;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;


import java.util.*;
import java.util.stream.Collectors;

/**
 * Inspection tool for ASCA (AI Secure Coding Assistant).
 */
public class AscaInspection extends LocalInspectionTool {
    @Getter
    @Setter
    private AscaService ascaService = new AscaService();
    private final GlobalSettingsState settings = GlobalSettingsState.getInstance();
    private Map<String, ProblemHighlightType> severityToHighlightMap;
    public static String ASCA_INSPECTION_ID = "ASCA";
    private final Logger logger = Utils.getLogger(AscaInspection.class);


    /**
     * Checks the file for ASCA issues.
     *
     * @param file the file to check
     * @param manager the inspection manager
     * @param isOnTheFly whether the inspection is on-the-fly
     * @return an array of problem descriptors
     */
    @Override
    public ProblemDescriptor @NotNull [] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
        try {
            if (!settings.isAsca()) {
                ProblemHolderService.getInstance(file.getProject())
                        .removeAllProblemsOfType(ScanEngine.ASCA.name());
                return ProblemDescriptor.EMPTY_ARRAY;
            }
            ScanResult scanResult = performAscaScan(file);

            if(scanResult.getScanDetails() == null && scanResult.getError()==null){
                VirtualFile virtualFile = file.getVirtualFile();
                if (virtualFile != null) {
                    ProblemHolderService.getInstance(file.getProject())
                            .addProblems(file.getVirtualFile().getPath(), new ArrayList<>());
                }
            }

            if (isInvalidScan(scanResult)) {
                return ProblemDescriptor.EMPTY_ARRAY;
            }
            Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
            if (document == null) {
                return ProblemDescriptor.EMPTY_ARRAY;
            }

            return createProblemDescriptors(file, manager, scanResult.getScanDetails(), document, isOnTheFly);
        }
        catch (Exception e) {
            logger.warn("Failed to run ASCA scan", e);
            return ProblemDescriptor.EMPTY_ARRAY;
        }

    }

    /**
     * Creates problem descriptors for the given scan details.
     *
     * @param file the file to check
     * @param manager the inspection manager
     * @param scanDetails the scan details
     * @param document the document
     * @param isOnTheFly whether the inspection is on-the-fly
     * @return an array of problem descriptors
     */
    private ProblemDescriptor[] createProblemDescriptors(@NotNull PsiFile file, @NotNull InspectionManager manager, List<ScanDetail> scanDetails, Document document, boolean isOnTheFly) {
        List<ProblemDescriptor> problems = new ArrayList<>();
        for (ScanDetail detail : scanDetails) {
            int lineNumber = detail.getLine();
            if (isLineOutOfRange(lineNumber, document)) {
                continue;
            }
            PsiElement elementAtLine = file.findElementAt(document.getLineStartOffset(lineNumber - 1));
            if (elementAtLine != null) {
                ProblemDescriptor problem = createProblemDescriptor(file, manager, detail, document, lineNumber, isOnTheFly);
                problems.add(problem);
            }
        }

        List<ScanIssue> problemsList = new ArrayList<>(buildCxProblems(scanDetails));
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile != null) {
            ProblemHolderService.getInstance(file.getProject())
                    .addProblems(file.getVirtualFile().getPath(), problemsList);
        }

        return problems.toArray(ProblemDescriptor[]::new);
    }

    /**
     * Creates a problem descriptor for a specific scan detail.
     *
     * @param file the file to check
     * @param manager the inspection manager
     * @param detail the scan detail
     * @param document the document
     * @param lineNumber the line number
     * @param isOnTheFly whether the inspection is on-the-fly
     * @return a problem descriptor
     */
    private ProblemDescriptor createProblemDescriptor(@NotNull PsiFile file, @NotNull InspectionManager manager, ScanDetail detail, Document document, int lineNumber, boolean isOnTheFly) {
        TextRange problemRange = getTextRangeForLine(document, lineNumber);

        String description =  new ProblemDescription().formatDescription(createScanIssue(detail));//formatDescription(detail.getRuleName(), detail.getRemediationAdvise());
        ProblemHighlightType highlightType = determineHighlightType(detail);
        return manager.createProblemDescriptor(
                file, problemRange, description, highlightType, isOnTheFly, new AscaQuickFix(detail));
    }

    public String formatDescription(String ruleName, String remediationAdvise) {
        return String.format(
                "<html><b>%s</b> - %s<br><font color='gray'>%s</font></html>",
                escapeHtml(ruleName), escapeHtml(remediationAdvise), escapeHtml(ASCA_INSPECTION_ID)
        );
    }

    // Helper method to escape HTML special characters for safety
    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Gets the text range for a specific line in the document.
     *
     * @param document the document
     * @param lineNumber the line number
     * @return the text range
     */
    private TextRange getTextRangeForLine(Document document, int lineNumber) {
        int startOffset = document.getLineStartOffset(lineNumber - 1);
        int endOffset = Math.min(document.getLineEndOffset(lineNumber - 1), document.getTextLength());

        String lineText = document.getText(new TextRange(startOffset, endOffset));
        int trimmedStartOffset = startOffset + (lineText.length() - lineText.stripLeading().length());

        return new TextRange(trimmedStartOffset, endOffset);
    }

    /**
     * Checks if the line number is out of range in the document.
     *
     * @param lineNumber the line number
     * @param document the document
     * @return true if the line number is out of range, false otherwise
     */
    private boolean isLineOutOfRange(int lineNumber, Document document) {
        return lineNumber <= 0 || lineNumber > document.getLineCount();
    }

    /**
     * Checks if the scan result is invalid.
     *
     * @param scanResult the scan result
     * @return true if the scan result is invalid, false otherwise
     */
    private boolean isInvalidScan(ScanResult scanResult) {
        return scanResult == null || scanResult.getScanDetails() == null;
    }

    /**
     * Determines the highlight type for a specific scan detail.
     *
     * @param detail the scan detail
     * @return the problem highlight type
     */
    private ProblemHighlightType determineHighlightType(ScanDetail detail) {
        return getSeverityToHighlightMap().getOrDefault(detail.getSeverity(), ProblemHighlightType.WEAK_WARNING);
    }

    /**
     * Gets the map of severity to highlight type.
     *
     * @return the map of severity to highlight type
     */
    private Map<String, ProblemHighlightType> getSeverityToHighlightMap() {
        if (severityToHighlightMap == null) {
            severityToHighlightMap = new HashMap<>();
            severityToHighlightMap.put(Constants.CRITICAL_SEVERITY, ProblemHighlightType.GENERIC_ERROR);
            severityToHighlightMap.put(Constants.HIGH_SEVERITY, ProblemHighlightType.GENERIC_ERROR);
            severityToHighlightMap.put(Constants.MEDIUM_SEVERITY, ProblemHighlightType.WARNING);
            severityToHighlightMap.put(Constants.LOW_SEVERITY, ProblemHighlightType.WEAK_WARNING);
        }
        return severityToHighlightMap;
    }

    /**
     * Performs an ASCA scan on the given file.
     *
     * @param file the file to scan
     * @return the scan result
     */
    private ScanResult performAscaScan(PsiFile file) {
        return ascaService.runAscaScan(file, file.getProject(), false, Constants.JET_BRAINS_AGENT_NAME);
    }

    public static List<ScanIssue> buildCxProblems(List<ScanDetail> details) {
        return details.stream().map(detail -> {
            ScanIssue problem = new ScanIssue();
            problem.setSeverity(detail.getSeverity());
            problem.setScanEngine(ScanEngine.ASCA);
            problem.setTitle(detail.getRuleName());
            problem.setDescription(detail.getDescription());
            problem.setRemediationAdvise(detail.getRemediationAdvise());
            problem.getLocations().add(new Location(detail.getLine(), 0, 1000)); // assume whole line by default
            return problem;
        }).collect(Collectors.toList());
    }

    private ScanIssue createScanIssue(ScanDetail scanDetail) {
        ScanIssue problem = new ScanIssue();

        problem.setTitle(scanDetail.getRuleName());
        problem.setScanEngine(ScanEngine.ASCA);
        problem.setRemediationAdvise(scanDetail.getRemediationAdvise());
        problem.setSeverity(scanDetail.getSeverity());

        return problem;
    }
}