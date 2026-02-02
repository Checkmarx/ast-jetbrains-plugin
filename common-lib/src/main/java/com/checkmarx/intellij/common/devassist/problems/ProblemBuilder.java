package com.checkmarx.intellij.common.devassist.problems;

import com.checkmarx.intellij.common.devassist.model.ScanIssue;
import com.checkmarx.intellij.common.devassist.remediation.CxOneAssistFix;
import com.checkmarx.intellij.common.devassist.remediation.IgnoreAllThisTypeFix;
import com.checkmarx.intellij.common.devassist.remediation.IgnoreVulnerabilityFix;
import com.checkmarx.intellij.common.devassist.remediation.ViewDetailsFix;
import com.checkmarx.intellij.common.devassist.ui.ProblemDescription;
import com.checkmarx.intellij.common.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.common.devassist.utils.ScanEngine;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The ProblemBuilder class is a utility class responsible for constructing
 * ProblemDescriptor objects based on specific scan issues identified within a PsiFile.
 * It encapsulates the logic to derive necessary problem details such as text range,
 * description, and highlight type, delegating specific computations to an instance
 * of ProblemDecorator.
 * <p>
 * This class cannot be instantiated.
 */
public final class ProblemBuilder {

    private static final ProblemDescription PROBLEM_DESCRIPTION_INSTANCE = new ProblemDescription();

    /**
     * Private constructor to prevent instantiation.
     */
    private ProblemBuilder() {
    }

    /**
     * Builds a ProblemDescriptor for the given scan issue.
     *
     * @param problemHelper     the problem helper containing relevant scan issue information
     * @param scanIssue         the scan issue
     * @param problemLineNumber the line number where the problem was found
     * @return a ProblemDescriptor instance
     */
    static ProblemDescriptor build(@NotNull ProblemHelper problemHelper, @NotNull ScanIssue scanIssue, int problemLineNumber) {

        TextRange problemRange = DevAssistUtils.getTextRangeForLine(problemHelper.getDocument(), problemLineNumber);
        String description = PROBLEM_DESCRIPTION_INSTANCE.formatDescription(scanIssue);

        return problemHelper.getManager().createProblemDescriptor(
                problemHelper.getFile(),
                problemRange,
                description,
                ProblemHighlightType.GENERIC_ERROR,
                problemHelper.isOnTheFly(),
                getFixes(scanIssue)
        );
    }
    
    private static LocalQuickFix[] getFixes(ScanIssue scanIssue) {
        List<LocalQuickFix> localQuickFixes = new ArrayList<>();
        localQuickFixes.add(new CxOneAssistFix(scanIssue));
        localQuickFixes.add(new ViewDetailsFix(scanIssue));
        localQuickFixes.add(new IgnoreVulnerabilityFix(scanIssue));
        if (ScanEngine.CONTAINERS.equals(scanIssue.getScanEngine()) ||  ScanEngine.OSS.equals(scanIssue.getScanEngine())) {
            localQuickFixes.add(new IgnoreAllThisTypeFix(scanIssue));
        }
        return localQuickFixes.toArray(new LocalQuickFix[0]);
    }
}
