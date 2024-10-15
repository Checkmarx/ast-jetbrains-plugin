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

/**
 * Quick fix implementation for ASCA issues.
 */
public class AscaQuickFix implements LocalQuickFix {
    @SafeFieldForPreview
    private final ScanDetail detail;

    /**
     * Constructor for AscaQuickFix.
     *
     * @param detail the scan detail associated with the issue
     */
    public AscaQuickFix(ScanDetail detail) {
        this.detail = detail;
    }

    /**
     * Returns the family name of the quick fix.
     *
     * @return the family name
     */
    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
        return "Copy fix prompt";
    }

    /**
     * Applies the quick fix to the given problem descriptor.
     *
     * @param project the current project
     * @param descriptor the problem descriptor
     */
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
        showNotification(project, "The fix prompt has been successfully copied to the clipboard.", NotificationType.INFORMATION);
    }

    /**
     * Shows a notification to the user.
     *
     * @param project the current project
     * @param message the message to display
     * @param type the type of notification
     */
    private void showNotification(Project project, String message, NotificationType type) {
        ApplicationManager.getApplication().invokeLater(() -> {
            Notification notification = NotificationGroupManager.getInstance()
                    .getNotificationGroup("Checkmarx.Notifications")
                    .createNotification("Fix prompt copied", message, type);
            notification.notify(project);
        });
    }

    /**
     * Generates a fix prompt based on the problematic line and description.
     *
     * @param problematicLine the problematic line of code
     * @param description the description of the issue
     * @return the generated fix prompt
     */
    private String generateFixPrompt(String problematicLine, String description) {
        return String.format(
                "Please address the following issue:\n\n" +
                        "Code snippet with potential issue:\n%s\n\n" +
                        "Issue description:\n%s\n\n" +
                        "Provide a fix to make this code safer and more secure.",
                problematicLine.trim(), description.trim()
        );
    }

    /**
     * Copies the given prompt to the system clipboard.
     *
     * @param prompt the prompt to copy
     */
    private void copyToClipboard(String prompt) {
        StringSelection stringSelection = new StringSelection(prompt);
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
        }
        catch (Exception e) {
            showNotification(null, "Failed to copy the fix prompt to the clipboard.", NotificationType.ERROR);
        }
    }
}