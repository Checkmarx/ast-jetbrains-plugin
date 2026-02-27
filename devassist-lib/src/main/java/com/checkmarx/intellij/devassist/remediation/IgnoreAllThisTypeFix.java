package com.checkmarx.intellij.devassist.remediation;

import com.checkmarx.intellij.common.resources.CxIcons;
import com.checkmarx.intellij.common.utils.Utils;
import com.checkmarx.intellij.devassist.ignore.IgnoreManager;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.utils.DevAssistConstants;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.checkmarx.intellij.devassist.utils.DevAssistConstants.QUICK_FIX;

/**
 * A quick fix implementation to ignore all issues of a specific type during real-time scanning.
 * This class provides mechanisms to group and apply fixes for particular types of scan issues.
 * It implements the {@link LocalQuickFix} interface, which allows the integration of this fix
 * with IntelliJ's inspection framework.
 * <p>
 * The main functionality includes:
 * - Providing a family name for grouping similar quick fixes.
 * - Applying the fix to ignore all instances of the specified issue type.
 * <p>
 * This class relies on the {@link ScanIssue} object that contains details about the specific issue to ignore.
 * The fix is categorized using the family name provided by `Constants.RealTimeConstants.IGNORE_ALL_OF_THIS_TYPE_FIX_NAME`.
 * <p>
 * It is expected that the scan issue passed at the time of object creation includes enough
 * details to handle the ignoring process properly.
 */
public class IgnoreAllThisTypeFix implements LocalQuickFix, Iconable {

    private static final Logger LOGGER = Utils.getLogger(IgnoreAllThisTypeFix.class);

    @SafeFieldForPreview
    private final ScanIssue scanIssue;

    public IgnoreAllThisTypeFix(ScanIssue scanIssue) {
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
        return DevAssistConstants.IGNORE_ALL_OF_THIS_TYPE_FIX_NAME;
    }

    /**
     * Returns the icon representing this quick fix.
     */
    @Override
    public Icon getIcon(int flags) {
        return CxIcons.STAR_ACTION;
    }

    /**
     * Applies a quick fix for a specified problem descriptor within a project.
     * This method is invoked when the user selects this quick fix action to resolve
     * an associated issue.
     *
     * @param project    the project where the fix is to be applied; must not be null
     * @param descriptor the problem descriptor that represents the issue to be fixed; must not be null
     */
    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        LOGGER.info("applyFix called.." + getFamilyName() + " " + scanIssue.getTitle());
        IgnoreManager.getInstance(project).addAllIgnoredEntry(scanIssue, QUICK_FIX);
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
        return IntentionPreviewInfo.EMPTY;
    }
}
