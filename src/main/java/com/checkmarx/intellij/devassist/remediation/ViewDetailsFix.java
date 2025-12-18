package com.checkmarx.intellij.devassist.remediation;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.CxIcons;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.checkmarx.intellij.Constants.RealTimeConstants.QUICK_FIX;
import static java.lang.String.format;

/**
 * A class representing a quick fix that enables users to view details of a scan issue detected during
 * a scanning process. This class implements the `LocalQuickFix` interface, allowing it to be presented
 * as a fix option within IDE inspections or problem lists.
 * <p>
 * This quick fix is primarily used to group and categorize similar fixes with a common family
 * name, and to invoke functionality that provides further details about the associated scan issue.
 * <p>
 * Key behaviors of this class include:
 * - Providing a family name that categorizes this type of quick fix.
 * - Implementing an action to be executed when the quick fix is applied, which in this case is to display details of the scan issue.
 */
public class ViewDetailsFix implements LocalQuickFix, Iconable {

    private static final Logger LOGGER = Utils.getLogger(ViewDetailsFix.class);

    @SafeFieldForPreview
    private final ScanIssue scanIssue;

    /**
     * Constructs a ViewDetailsFix instance to enable users to view details of the provided scan issue.
     * This quick fix allows users to inspect detailed information about a specific issue identified
     * during a scanning process.
     *
     * @param scanIssue the scan issue that this fix targets; includes details such as severity, title, description, locations, and vulnerabilities
     */
    public ViewDetailsFix(ScanIssue scanIssue) {
        super();
        this.scanIssue = scanIssue;
    }

    /**
     * Returns the family name of this quick fix.
     * The family name is used to group similar quick fixes together and is displayed
     * in the "Apply Fix" popup when multiple quick fixes are available.
     *
     * @return a non-null string representing the family name, which categorizes this quick fix
     */
    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
        return Constants.RealTimeConstants.VIEW_DETAILS_FIX_NAME;
    }

    /**
     * Returns the icon representing this quick fix.
     */
    @Override
    public Icon getIcon(int flags) {
        return CxIcons.STAR_ACTION;
    }

    /**
     * Applies the quick fix action for the specified problem descriptor within the given project.
     * This implementation displays details about the scan issue associated with this fix
     *
     * @param project    the project where the fix is to be applied; must not be null
     * @param descriptor the problem descriptor that represents the issue to be fixed; must not be null
     */
    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        LOGGER.info(format("RTS-Fix: Remediation called: %s for issue: %s", getFamilyName(), scanIssue.getTitle()));
        new RemediationManager().viewDetails(project, scanIssue, QUICK_FIX);
    }

}
