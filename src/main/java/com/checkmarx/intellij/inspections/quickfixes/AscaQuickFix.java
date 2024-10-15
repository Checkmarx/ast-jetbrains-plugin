package com.checkmarx.intellij.inspections.quickfixes;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.application.ApplicationManager;
import com.checkmarx.ast.asca.ScanDetail;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.datatransfer.StringSelection;

public class AscaQuickFix implements LocalQuickFix {
    private final ScanDetail detail;

    public AscaQuickFix(ScanDetail detail) {
        this.detail = detail;
    }

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
        return "Copy fix prompt";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        // Retrieve the problematic line and the description
        String problematicLine = detail.getProblematicLine();
        String description = descriptor.getDescriptionTemplate();

        // Generate a prompt for GPT
        String prompt = generateFixPrompt(problematicLine, description);

        // Copy the prompt to the system clipboard
        copyToClipboard(prompt);

        // Show a notification to the user indicating that the prompt was copied
        showNotification(project);
    }

    private void showNotification(Project project) {
        ApplicationManager.getApplication().invokeLater(() -> {
            Notification notification = NotificationGroupManager.getInstance()
                    .getNotificationGroup("Checkmarx.Notifications")
                    .createNotification("Fix prompt copied", "The fix prompt has been successfully copied to the clipboard.", NotificationType.INFORMATION);
            notification.notify(project);
        });
    }

    private String generateFixPrompt(String problematicLine, String description) {
        return String.format(
                "Please address the following issue:\n\n" +
                        "Code snippet with potential issue:\n%s\n\n" +
                        "Issue description:\n%s\n\n" +
                        "Provide a fix to make this code safer and more secure.",
                problematicLine.trim(), description.trim()
        );
    }

    private void copyToClipboard(String prompt) {
        StringSelection stringSelection = new StringSelection(prompt);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
    }
}
