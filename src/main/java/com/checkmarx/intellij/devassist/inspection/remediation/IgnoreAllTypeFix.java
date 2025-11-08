package com.checkmarx.intellij.devassist.inspection.remediation;

import com.checkmarx.ast.asca.ScanDetail;
import com.checkmarx.intellij.Constants;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import org.jetbrains.annotations.NotNull;

public class IgnoreAllTypeFix implements LocalQuickFix {

    @SafeFieldForPreview
    private final ScanDetail scanDetail;

    public IgnoreAllTypeFix(ScanDetail scanDetail) {
        this.scanDetail = scanDetail;
    }

    /**
     * @return text to appear in "Apply Fix" popup when multiple Quick Fixes exist (in the results of batch code inspection). For example,
     * if the name of the quickfix is "Create template &lt;filename&gt", the return value of getFamilyName() should be "Create template".
     * If the name of the quickfix does not depend on a specific element, simply return {@link #getName()}.
     */
    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
        return "Ignore all of this type";
    }

    /**
     * Called to apply the fix.
     * <p>
     * Please call {@link ProjectInspectionProfileManager#fireProfileChanged()} if inspection profile is changed as result of fix.
     *
     * @param project    {@link Project}
     * @param descriptor problem reported by the tool which provided this quick fix action
     */
    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        showNotification(project, "Ignore all of this type", NotificationType.INFORMATION);
    }

    /**
     * Shows a notification to the user.
     *
     * @param project the current project
     * @param message the message to display
     * @param type    the type of notification
     */
    private void showNotification(Project project, String message, NotificationType type) {
        final String FIX_PROMPT_COPY_FAIL_MSG = "Ignore all of this type";
        ApplicationManager.getApplication().invokeLater(() -> {
            Notification notification = NotificationGroupManager.getInstance()
                    .getNotificationGroup(Constants.NOTIFICATION_GROUP_ID)
                    .createNotification(FIX_PROMPT_COPY_FAIL_MSG, message, type);
            notification.notify(project);
        });
    }
}
