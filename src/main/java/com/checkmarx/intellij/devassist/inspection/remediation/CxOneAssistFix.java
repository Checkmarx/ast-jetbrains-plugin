package com.checkmarx.intellij.devassist.inspection.remediation;

import com.checkmarx.ast.asca.ScanDetail;
import com.checkmarx.intellij.Constants;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import org.jetbrains.annotations.NotNull;

public class CxOneAssistFix implements LocalQuickFix {

    @SafeFieldForPreview
    private /*final*/ ScanDetail scanDetail;
/*
    public CxOneAssistFix(ScanDetail scanDetail) {
        super();
        this.scanDetail = scanDetail;
    }*/

    @NotNull
    @Override
    public String getFamilyName() {
        return "Fix with CxOne Assist";
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
        System.out.println("applyFix called..");
        showNotification(project, "Fix with CxOne assist remediation: " + scanDetail.getFileName(), NotificationType.INFORMATION);
    }

    /**
     * Shows a notification to the user.
     *
     * @param project the current project
     * @param message the message to display
     * @param type    the type of notification
     */
    private void showNotification(Project project, String message, NotificationType type) {
        final String FIX_PROMPT_COPY_FAIL_MSG = "Fix with CxOne assist";
        ApplicationManager.getApplication().invokeLater(() -> {
            Notification notification = NotificationGroupManager.getInstance()
                    .getNotificationGroup(Constants.NOTIFICATION_GROUP_ID)
                    .createNotification(FIX_PROMPT_COPY_FAIL_MSG, message, type);
            notification.notify(project);
        });
    }


}
