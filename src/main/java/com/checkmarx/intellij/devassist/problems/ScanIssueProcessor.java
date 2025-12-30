package com.checkmarx.intellij.devassist.problems;

import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Helper class responsible for processing individual scan issues and creating problem descriptors.
 * This class encapsulates the logic for validating, processing, and highlighting scan issues.
 */
@RequiredArgsConstructor
public class ScanIssueProcessor {

    private static final Logger LOGGER = Logger.getInstance(ScanIssueProcessor.class);

    private final PsiFile file;
    private final Document document;
    private final ProblemHelper problemHelper;

    public ScanIssueProcessor(ProblemHelper problemHelper) {
        this.file = problemHelper.getFile();
        this.document = problemHelper.getDocument();
        this.problemHelper = problemHelper;
    }

    /**
     * Processes a single scan issue and returns a problem descriptor if applicable.
     *
     * @param scanIssue the scan issue to process
     * @return a ProblemDescriptor if the issue is valid and should be reported, null otherwise
     */
    public ProblemDescriptor processScanIssue(@NotNull ScanIssue scanIssue, boolean isDecoratorEnabled) {
        if (!isValidLocation(scanIssue)) {
            LOGGER.debug("RTS: Scan issue does not have location: {}", scanIssue.getTitle());
            return null;
        }
        int problemLineNumber = scanIssue.getLocations().get(0).getLine();

        if (!isValidLineAndSeverity(problemLineNumber, scanIssue)) {
            LOGGER.debug("RTS: Invalid Issue, it does not contains valid line: {} or severity: {} ", problemLineNumber, scanIssue.getSeverity());
            return null;
        }
        try {
            return processValidIssue(scanIssue, problemLineNumber, isDecoratorEnabled);
        } catch (Exception e) {
            LOGGER.error("RTS: Exception occurred while processing scan issue: {}, Exception: {}", scanIssue.getTitle(), e.getMessage());
            return null;
        }
    }

    /**
     * Validates that the scan issue has valid locations.
     */
    private boolean isValidLocation(ScanIssue scanIssue) {
        return scanIssue.getLocations() != null && !scanIssue.getLocations().isEmpty();
    }

    /**
     * Validates the line number and severity of the scan issue.
     */
    private boolean isValidLineAndSeverity(int lineNumber, ScanIssue scanIssue) {
        if (DevAssistUtils.isLineOutOfRange(lineNumber, document)) {
            return false;
        }
        return scanIssue.getSeverity() != null && !scanIssue.getSeverity().isBlank();
    }

    /**
     * Processes a valid scan issue, creates problem descriptor and adds gutter icon.
     */
    private ProblemDescriptor processValidIssue(ScanIssue scanIssue, int problemLineNumber, boolean isDecoratorEnabled) {
        boolean isProblem = DevAssistUtils.isProblem(scanIssue.getSeverity().toLowerCase());

        ProblemDescriptor problemDescriptor = null;
        if (isProblem) {
            problemDescriptor = createProblemDescriptor(scanIssue, problemLineNumber);
        }
        if (isDecoratorEnabled) { // Decorator is enabled, decorating the issue.
            highlightIssueIfNeeded(scanIssue, problemLineNumber, isProblem);
        }
        return problemDescriptor;
    }

    /**
     * Creates a problem descriptor for the given scan issue.
     */
    private ProblemDescriptor createProblemDescriptor(ScanIssue scanIssue, int problemLineNumber) {
        try {
            return ProblemBuilder.build(problemHelper, scanIssue, problemLineNumber);
        } catch (Exception e) {
            LOGGER.error("RTS: Failed to create problem descriptor for: {} ", scanIssue.getTitle(), e.getMessage());
            return null;
        }
    }

    /**
     * Highlights the issue line and adds a gutter icon if a valid PSI element exists.
     */
    private void highlightIssueIfNeeded(ScanIssue scanIssue, int problemLineNumber, boolean isProblem) {
        PsiElement elementAtLine = DevAssistUtils.getPsiElement(file, document, problemLineNumber);
        if (Objects.isNull(elementAtLine)) {
            LOGGER.debug("RTS: Skipping to add gutter icon, Failed to find PSI element for line : {}", problemLineNumber, scanIssue.getTitle());
            return;
        }
        ProblemDecorator problemDecorator = problemHelper.getProblemDecorator();
        if (Objects.isNull(problemDecorator)) {
            problemDecorator = new ProblemDecorator();
        }
        problemDecorator.highlightLineAddGutterIconForProblem(problemHelper, scanIssue, isProblem, problemLineNumber);
    }
}
