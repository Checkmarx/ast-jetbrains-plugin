package com.checkmarx.intellij.inspections;

import com.checkmarx.ast.asca.ScanDetail;
import com.checkmarx.ast.asca.ScanResult;
import com.checkmarx.intellij.ASCA.AscaService;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.inspections.quickfixes.AscaQuickFix;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.intellij.codeInspection.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Inspection tool for ASCA (AI Secure Coding Assistant).
 */
public class AscaInspection extends LocalInspectionTool {
    private final GlobalSettingsState settings = GlobalSettingsState.getInstance();
    private Map<String, ProblemHighlightType> severityToHighlightMap;

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
        if (!settings.isAsca()) {
            return ProblemDescriptor.EMPTY_ARRAY;
        }

        ScanResult scanResult = performAscaScan(file);
        if (isInvalidScan(scanResult)) {
            return ProblemDescriptor.EMPTY_ARRAY;
        }

        Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (document == null) {
            return ProblemDescriptor.EMPTY_ARRAY;
        }

        return createProblemDescriptors(file, manager, scanResult.getScanDetails(), document, isOnTheFly);
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
        String description = Strings.join(detail.getRuleName(), " - ", detail.getRemediationAdvise());
        ProblemHighlightType highlightType = determineHighlightType(detail);

        return manager.createProblemDescriptor(
                file, problemRange, description, highlightType, isOnTheFly, new AscaQuickFix(detail));
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
        return new TextRange(startOffset, endOffset);
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
            severityToHighlightMap.put(Constants.ASCA_CRITICAL_SEVERITY, ProblemHighlightType.GENERIC_ERROR);
            severityToHighlightMap.put(Constants.ASCA_HIGH_SEVERITY, ProblemHighlightType.GENERIC_ERROR);
            severityToHighlightMap.put(Constants.ASCA_MEDIUM_SEVERITY, ProblemHighlightType.WARNING);
            severityToHighlightMap.put(Constants.ASCA_LOW_SEVERITY, ProblemHighlightType.WEAK_WARNING);
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
        return new AscaService().runAscaScan(file, file.getProject(), false, Constants.JET_BRAINS_AGENT_NAME);
    }
}