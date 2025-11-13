package com.checkmarx.intellij.devassist.inspection.remediation;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import static java.lang.String.format;

/**
 * The `CxOneAssistFix` class implements the `LocalQuickFix` interface and provides a specific fix
 * for issues detected during scans. This class is used to apply a remediation action
 * related to a particular scan issue identified by the scanning engine.
 * <p>
 * The class leverages a `ScanIssue` object, which encapsulates details of the issue, including
 * its title, severity, and other relevant diagnostic information.
 * <p>
 * Functionality of the class includes:
 * - Defining a family name that represents the type of fix.
 * - Providing an implementation to apply the fix within a given project in response to a specific problem descriptor.
 * <p>
 * This fix is categorized under the "Fix with CXOne Assist" family for easy identification and grouping.
 */
public class CxOneAssistFix implements LocalQuickFix {

    private static final Logger LOGGER = Utils.getLogger(CxOneAssistFix.class);

    @SafeFieldForPreview
    private final ScanIssue scanIssue;

    /**
     * Constructs a CxOneAssistFix instance to provide a remediation action for the specified scan issue.
     * This fix is used to address issues identified during a scan.
     *
     * @param scanIssue the scan issue that this fix targets; includes details such as severity, title, and description
     */
    public CxOneAssistFix(ScanIssue scanIssue) {
        super();
        this.scanIssue = scanIssue;
    }

    /**
     * Returns the family name of the fix, which is used to categorize and identify
     * this fix within the scope of available remediation actions.
     *
     * @return a non-null string representing the family name of the fix
     */
    @NotNull
    @Override
    public String getFamilyName() {
        return Constants.RealTimeConstants.FIX_WITH_CXONE_ASSIST;
    }

    /**
     * Applies a fix for a specified problem descriptor within a project.
     *
     * @param project    the project where the fix is to be applied
     * @param descriptor the problem descriptor that represents the issue to be fixed
     */
    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        LOGGER.info(format("RTS-Fix: Remediation called: %s for issue: %s", getFamilyName(), scanIssue.getTitle()));
        switch (scanIssue.getScanEngine()) {
            case OSS:
                applyOSSRemediation();
                break;
            case ASCA:
                applyASCARemediation();
                break;
            default:
                break;
        }
    }

    private void applyOSSRemediation() {
        LOGGER.info(format("RTS-Fix: Remediation started for OSS Issue: %s", scanIssue.getTitle()));
    }

    private void applyASCARemediation() {
        LOGGER.info(format("RTS-Fix: Remediation started for ASCA Issue: %s", scanIssue.getTitle()));
    }
}
