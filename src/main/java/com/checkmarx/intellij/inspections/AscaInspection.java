package com.checkmarx.intellij.inspections;

import com.checkmarx.ast.asca.ScanDetail;
import com.checkmarx.ast.asca.ScanResult;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.inspections.quickfixes.AscaQuickFix;
import com.checkmarx.intellij.service.AscaService;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * @param file       the file to check
     * @param manager    the inspection manager
     * @param isOnTheFly whether the inspection is on-the-fly
     * @return an array of problem descriptors
     */
    @Override
    public ProblemDescriptor @NotNull [] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
        try {
            if (!settings.isAscaRealtime()) {
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
        } catch (Exception e) {
            logger.warn("Failed to run ASCA scan", e);
            return ProblemDescriptor.EMPTY_ARRAY;
        }
    }

    /**
     * Creates problem descriptors for the given scan details.
     *
     * @param file        the file to check
     * @param manager     the inspection manager
     * @param scanDetails the scan details
     * @param document    the document
     * @param isOnTheFly  whether the inspection is on-the-fly
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
     * @param file       the file to check
     * @param manager    the inspection manager
     * @param detail     the scan detail
     * @param document   the document
     * @param lineNumber the line number
     * @param isOnTheFly whether the inspection is on-the-fly
     * @return a problem descriptor
     */
    private ProblemDescriptor createProblemDescriptor(@NotNull PsiFile file, @NotNull InspectionManager manager, ScanDetail detail, Document document, int lineNumber, boolean isOnTheFly) {
        TextRange problemRange = getTextRangeForLine(document, lineNumber);
        String description = formatDescription(detail.getRuleName(), detail.getRemediationAdvise());
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
     * @param document   the document
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
     * @param document   the document
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
}