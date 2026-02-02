package com.checkmarx.intellij.common.devassist.remediation;

import com.checkmarx.intellij.common.resources.CxIcons;
import com.checkmarx.intellij.common.utils.Utils;
import com.checkmarx.intellij.common.devassist.model.ScanIssue;
import com.checkmarx.intellij.common.devassist.telemetry.TelemetryService;
import com.checkmarx.intellij.common.devassist.utils.DevAssistConstants;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.checkmarx.intellij.common.devassist.utils.DevAssistConstants.QUICK_FIX;
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
public class CxOneAssistFix implements LocalQuickFix, Iconable {

    private static final Logger LOGGER = Utils.getLogger(CxOneAssistFix.class);

    @Getter
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
        return DevAssistConstants.FIX_WITH_CXONE_ASSIST;
    }

    /**
     * Returns the icon representing this quick fix.
     */
    @Override
    public Icon getIcon(int flags) {
        return CxIcons.STAR_ACTION;
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
        TelemetryService.logFixWithCxOneAssistAction(scanIssue);
        new RemediationManager().fixWithCxOneAssist(project, scanIssue, QUICK_FIX);
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
        return IntentionPreviewInfo.EMPTY;
    }
}
