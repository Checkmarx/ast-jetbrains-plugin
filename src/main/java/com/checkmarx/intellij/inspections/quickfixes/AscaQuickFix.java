package com.checkmarx.intellij.inspections.quickfixes;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Utils;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.application.ApplicationManager;
import com.checkmarx.ast.asca.ScanDetail;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.datatransfer.StringSelection;

import static com.checkmarx.intellij.inspections.AscaInspection.ASCA_INSPECTION_ID;

/**
 * Quick fix implementation for ASCA issues.
 */
public class AscaQuickFix implements LocalQuickFix {
    @SafeFieldForPreview
    private final ScanDetail detail;
    private final Logger LOGGER = Utils.getLogger(AscaQuickFix.class);
    private final String FAILED_COPY_FIX_PROMPT = "Failed to copy the fix prompt to the clipboard.";

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
        return "ASCA - Copy fix prompt";
    }

    /**
     * Applies the quick fix to the given problem descriptor.
     *
     * @param project the current project
     * @param descriptor the problem descriptor
     */
    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        try {
            final String FIX_PROMPT_COPY_SUCCESS_MSG = "Fix prompt copied to clipboard.\n" +
                    "Paste this prompt into GitHub Copilot to get a remediated code snippet.";
            // Retrieve the problematic line and the description
            String problematicLine = detail.getProblematicLine();
            String description = descriptor.getDescriptionTemplate();

            // Generate a prompt for GPT
            String prompt = generateFixPrompt(problematicLine, description);

            // Copy the prompt to the system clipboard
            copyToClipboard(prompt);

            // Show a notification to the user indicating that the prompt was copied
            showNotification(project, FIX_PROMPT_COPY_SUCCESS_MSG, NotificationType.INFORMATION);
        } catch (Exception e) {
            LOGGER.warn(FAILED_COPY_FIX_PROMPT, e);
        }
    }

    /**
     * Shows a notification to the user.
     *
     * @param project the current project
     * @param message the message to display
     * @param type the type of notification
     */
    private void showNotification(Project project, String message, NotificationType type) {
        final String FIX_PROMPT_COPY_FAIL_MSG = "Fix prompt copied";
        ApplicationManager.getApplication().invokeLater(() -> {
            Notification notification = NotificationGroupManager.getInstance()
                    .getNotificationGroup(Constants.NOTIFICATION_GROUP_ID)
                    .createNotification(FIX_PROMPT_COPY_FAIL_MSG, message, type);
            notification.notify(project);
        });
    }

    public String stripHtml(String htmlText) {
        if (htmlText == null) {
            return "";
        }
        // Remove HTML tags
        String plainText = htmlText.replaceAll("<[^>]*>", "");

        // Remove "ASCA" suffix, if it exists
        if (plainText.endsWith(ASCA_INSPECTION_ID)) {
            plainText = plainText.substring(0, plainText.length() - 4).trim(); // Remove "ASCA" and trim any trailing space
        }

        return unescapeHtml(plainText);
    }

    private String unescapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }


    /**
     * Generates a fix prompt based on the problematic line and description.
     *
     * @param problematicLine the problematic line of code
     * @param description the description of the issue
     * @return the generated fix prompt
     */
    private String generateFixPrompt(String problematicLine, String description) {
        final String FIX_PROMPT = "Please address the following issue:\n\n" +
                "Code snippet with potential issue:\n%s\n\n" +
                "Issue description:\n%s\n\n" +
                "Provide a fix to make this code safer and more secure.";
        return String.format(FIX_PROMPT, problematicLine.trim(), stripHtml(description.trim())
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

            showNotification(null, FAILED_COPY_FIX_PROMPT, NotificationType.ERROR);
        }
    }
}