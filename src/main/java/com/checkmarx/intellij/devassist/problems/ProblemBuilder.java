package com.checkmarx.intellij.devassist.problems;

import com.checkmarx.intellij.devassist.inspection.remediation.CxOneAssistFix;
import com.checkmarx.intellij.devassist.inspection.remediation.IgnoreAllThisTypeFix;
import com.checkmarx.intellij.devassist.inspection.remediation.IgnoreVulnerabilityFix;
import com.checkmarx.intellij.devassist.inspection.remediation.ViewDetailsFix;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.ui.ProblemDescription;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * The ProblemBuilder class is a utility class responsible for constructing
 * ProblemDescriptor objects based on specific scan issues identified within a PsiFile.
 * It encapsulates the logic to derive necessary problem details such as text range,
 * description, and highlight type, delegating specific computations to an instance
 * of ProblemManager.
 * <p>
 * This class cannot be instantiated.
 */
public class ProblemBuilder {

    private static final ProblemManager problemManager = new ProblemManager();
    private static final ProblemDescription problemDescription = new ProblemDescription();

    /**
     * Private constructor to prevent instantiation.
     */
    private ProblemBuilder() {
    }

    /**
     * Builds a ProblemDescriptor for the given scan issue.
     *
     * @param file       the PsiFile being inspected
     * @param manager    the InspectionManager
     * @param scanIssue  the scan issue
     * @param document   the document
     * @param lineNumber the line number where the problem was found
     * @param isOnTheFly whether the inspection is on-the-fly
     * @return a ProblemDescriptor instance
     */
    static ProblemDescriptor build(@NotNull PsiFile file,
                                   @NotNull InspectionManager manager,
                                   @NotNull ScanIssue scanIssue,
                                   @NotNull Document document,
                                   int lineNumber,
                                   boolean isOnTheFly) {
        TextRange problemRange = problemManager.getTextRangeForLine(document, lineNumber);
        String description = problemDescription.formatDescription(scanIssue);
        ProblemHighlightType highlightType = problemManager.determineHighlightType(scanIssue);

        return manager.createProblemDescriptor(
                file,
                problemRange,
                description,
                highlightType,
                isOnTheFly,
                new CxOneAssistFix(scanIssue),
                new ViewDetailsFix(scanIssue),
                new IgnoreVulnerabilityFix(scanIssue),
                new IgnoreAllThisTypeFix(scanIssue)
        );
    }
}
