package com.checkmarx.intellij.inspections;

import com.checkmarx.ast.asca.ScanDetail;
import com.checkmarx.ast.asca.ScanResult;
import com.checkmarx.intellij.service.AscaService;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.inspections.quickfixes.AscaQuickFix;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
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
public class AscaGlobalInspection extends GlobalInspectionTool {
    @Getter
    @Setter
    private AscaService ascaService = new AscaService();
    private final GlobalSettingsState settings = GlobalSettingsState.getInstance();
    private Map<String, ProblemHighlightType> severityToHighlightMap;
    public static String ASCA_INSPECTION_ID = "ASCA";
    private final Logger logger = Utils.getLogger(AscaInspection.class);

    @Override
    public void runInspection(@NotNull AnalysisScope scope,
                              @NotNull InspectionManager manager,
                              @NotNull GlobalInspectionContext globalContext,
                              @NotNull ProblemDescriptionsProcessor processor) {

        if (!settings.isAsca()) {
            return;
        }

        Project project = globalContext.getProject();

        scope.accept(virtualFile -> {
            ApplicationManager.getApplication().runReadAction(() -> {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
                if (psiFile != null) {
                    ScanResult scanResult = ascaService.runAscaScan(
                            psiFile,
                            project,
                            false,
                            Constants.JET_BRAINS_AGENT_NAME
                    );

                    if (scanResult != null && scanResult.getScanDetails() != null) {
                        Document document = PsiDocumentManager.getInstance(psiFile.getProject()).getDocument(psiFile);
                        if (document != null) {
                            // Create problem descriptors for the scan results
                            ProblemDescriptor[] descriptors = createProblemDescriptors(psiFile, manager, scanResult.getScanDetails(), document, false);
                            // Add the problem descriptors to the processor
                            for (ProblemDescriptor descriptor : descriptors) {
                                processor.addProblemElement(
                                        globalContext.getRefManager().getReference(psiFile),
                                        descriptor
                                );
                            }
                        }
                    }
                }

            });
            return true; // continue scanning
        });
    }

    @Override
    public @NotNull String getDisplayName() {
        return "ASCA Global Inspection";
    }

    @Override
    public @NotNull String getShortName() {
        return "Asca";
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
        return ascaService.runAscaScan(file, file.getProject(), false, Constants.JET_BRAINS_AGENT_NAME);
    }

    private TextRange getPreciseTextRange(Document document, int lineNumber, ScanDetail detail) {
        int lineNum = detail.getLine(); // 1-based line number
        String problematicCode = detail.getProblematicLine();
        int lineIndex = lineNumber - 1;
        int lineStartOffset = document.getLineStartOffset(lineIndex);
        int lineEndOffset = document.getLineEndOffset(lineIndex);

        // Extract line text
        String lineText = document.getText(new TextRange(lineStartOffset, lineEndOffset));
        System.out.println("Check -----------------"+lineText+" ");
        // Find problematic substring inside the line
        int startColumn = lineText.indexOf(problematicCode);
        System.out.println("Check start column---------------"+startColumn+" ");
        int endColumn = 0;
        if (startColumn >= 0) {
            endColumn = startColumn + problematicCode.length();
            // Convert to file offsets
            int startOffset = lineStartOffset + startColumn;
            int endOffset = lineStartOffset + endColumn;
            return new TextRange(startOffset, endOffset);
        }
        // Fallback: mark whole line (current behavior)
        return new TextRange(lineStartOffset, lineEndOffset);
    }

}

