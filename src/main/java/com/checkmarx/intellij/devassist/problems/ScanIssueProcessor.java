package com.checkmarx.intellij.devassist.problems;

import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.util.SeverityLevel;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Helper class responsible for processing individual scan issues and creating problem descriptors.
 * This class encapsulates the logic for validating, processing, and highlighting scan issues.
 */
@RequiredArgsConstructor
public class ScanIssueProcessor {

    private static final Logger LOGGER = Logger.getInstance(ScanIssueProcessor.class);

    private final ProblemDecorator problemDecorator;
    private final PsiFile file;
    private final InspectionManager manager;
    private final Document document;
    private final boolean isOnTheFly;

    /**
     * Processes a single scan issue and returns a problem descriptor if applicable.
     *
     * @param scanIssue the scan issue to process
     * @return a ProblemDescriptor if the issue is valid and should be reported, null otherwise
     */
    public ProblemDescriptor processScanIssue(@NotNull ScanIssue scanIssue) {
        if (!isValidScanIssue(scanIssue)) {
            LOGGER.debug("RTS: Scan issue does not have location: {}", scanIssue.getTitle());
            return null;
        }
        int problemLineNumber = scanIssue.getLocations().get(0).getLine();

        if (!isValidLineAndSeverity(problemLineNumber, scanIssue)) {
            LOGGER.debug("RTS: Invalid Issue, it does not contains valid line: {} or severity: {} ", problemLineNumber, scanIssue.getSeverity());
            return null;
        }
        try {
            return processValidIssue(scanIssue, problemLineNumber);
        } catch (Exception e) {
            LOGGER.error("RTS: Exception occurred while processing scan issue: {}, Exception: {}", scanIssue.getTitle(), e.getMessage());
            return null;
        }
    }

    /**
     * Validates that the scan issue has valid locations.
     */
    private boolean isValidScanIssue(ScanIssue scanIssue) {
        return scanIssue.getLocations() != null && !scanIssue.getLocations().isEmpty();
    }

    /**
     * Validates the line number and severity of the scan issue.
     */
    private boolean isValidLineAndSeverity(int lineNumber, ScanIssue scanIssue) {
        if (DevAssistUtils.isLineOutOfRange(lineNumber, document)) {
            return false;
        }
        String severity = scanIssue.getSeverity();
        return severity != null && !severity.isBlank();
    }

    /**
     * Processes a valid scan issue, creates problem descriptor and adds gutter icon.
     */
    private ProblemDescriptor processValidIssue(ScanIssue scanIssue, int problemLineNumber) {
        boolean isProblem = isProblem(scanIssue.getSeverity().toLowerCase());

        ProblemDescriptor problemDescriptor = null;
        if (isProblem) {
            problemDescriptor = createProblemDescriptor(scanIssue, problemLineNumber);
        }
        highlightIssueIfNeeded(scanIssue, problemLineNumber, isProblem);
        return problemDescriptor;
    }

    /**
     * Creates a problem descriptor for the given scan issue.
     */
    private ProblemDescriptor createProblemDescriptor(ScanIssue scanIssue, int lineNumber) {
        try {
            return ProblemBuilder.build(file, manager, scanIssue, document, lineNumber, isOnTheFly);
        } catch (Exception e) {
            LOGGER.error("RTS: Failed to create problem descriptor for: {} ", scanIssue.getTitle(), e.getMessage());
            return null;
        }
    }

    /**
     * Highlights the issue line and adds a gutter icon if a valid PSI element exists.
     */
    private void highlightIssueIfNeeded(ScanIssue scanIssue, int problemLineNumber, boolean isProblem) {
        PsiElement elementAtLine = file.findElementAt(document.getLineStartOffset(problemLineNumber));
        if (elementAtLine != null) {
            problemDecorator.highlightLineAddGutterIconForProblem(
                    file.getProject(), file, scanIssue, isProblem, problemLineNumber
            );
        }
    }

    /**
     * Checks if the scan package is a problem.
     *
     * @param severity - the severity of the scan package e.g. "high", "medium", "low", etc.
     * @return true if the scan package is a problem, false otherwise
     */
    private boolean isProblem(String severity) {
        if (severity.equalsIgnoreCase(SeverityLevel.OK.getSeverity())) {
            return false;
        } else return !severity.equalsIgnoreCase(SeverityLevel.UNKNOWN.getSeverity());
    }
}
