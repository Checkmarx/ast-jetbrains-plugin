package com.checkmarx.intellij.devassist.remediation;

import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.resources.Resource;
import com.checkmarx.intellij.common.utils.Utils;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.model.Vulnerability;
import com.checkmarx.intellij.devassist.remediation.prompts.CxOneAssistFixPrompts;
import com.checkmarx.intellij.devassist.remediation.prompts.ViewDetailsPrompts;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static com.checkmarx.intellij.devassist.utils.DevAssistConstants.QUICK_FIX;
import static java.lang.String.format;

/**
 * RemediationManager provides remediation options for issues identified during a real-time scan.
 * <p>
 * This class supports applying fixes, viewing details etc. for scan issues detected by different scan engines,
 * such as OSS, ASCA, etc. It interacts with IntelliJ IDEA's project context and uses utility classes
 * for logging, clipboard operations, and prompt generation.
 * <p>
 * Main responsibilities:
 * <ul>
 *   <li>Apply remediation for different scan engine issues</li>
 *   <li>Generate and copy remediation prompts to the clipboard</li>
 *   <li>Log remediation actions</li>
 * </ul>
 */
public final class RemediationManager {

    private static final Logger LOGGER = Utils.getLogger(RemediationManager.class);

    /**
     * Apply remediation for a given scan issue.
     *
     * @param project   the project where the fix is to be applied
     * @param scanIssue the scan issue to fix
     * @param actionId  the action ID for vulnerability-specific fixes
     */
    public void fixWithCxOneAssist(@NotNull Project project, @NotNull ScanIssue scanIssue, String actionId) {
        String prompt = buildRemediationPrompt(scanIssue, actionId);
        applyFix(project, scanIssue, prompt);
    }

    /**
     * Builds the remediation prompt based on scan engine type.
     *
     * @param scanIssue the scan issue to build prompt for
     * @param actionId  the action ID for vulnerability-specific fixes
     * @return the remediation prompt, or null if not applicable
     */
    @Nullable
    private String buildRemediationPrompt(@NotNull ScanIssue scanIssue, String actionId) {
        switch (scanIssue.getScanEngine()) {
            case OSS:
                return buildOSSRemediationPrompt(scanIssue);
            case SECRETS:
                return buildSecretRemediationPrompt(scanIssue);
            case CONTAINERS:
                return buildContainerRemediationPrompt(scanIssue);
            case IAC:
                return buildIACRemediationPrompt(scanIssue, actionId);
            case ASCA:
                return buildASCARemediationPrompt(scanIssue, actionId);
            default:
                return null;
        }
    }

    /**
     * Applies the fix by attempting to send to Copilot AI first, with clipboard fallback.
     *
     * @param project   the project context
     * @param scanIssue the scan issue being fixed
     * @param prompt    the remediation prompt to apply
     */
    private void applyFix(@NotNull Project project, @NotNull ScanIssue scanIssue, @Nullable String prompt) {
        if (prompt == null || prompt.isEmpty()) {
            return;
        }
        LOGGER.info(format("RTS-Fix: %s remediation started for issue: %s, for file: %s", scanIssue.getScanEngine().name(), scanIssue.getTitle(), scanIssue.getFilePath()));
        String notificationTitle = getNotificationTitle(scanIssue.getScanEngine());

        // Try to fix with Copilot AI first (no notifications shown by fixWithAI)
        boolean aiSuccess = fixWithAI(prompt, project);
        if (aiSuccess) {
            LOGGER.info(format("RTS-Fix: %s remediation sent to Copilot for issue: %s, for file: %s", scanIssue.getScanEngine().name(), scanIssue.getTitle(), scanIssue.getFilePath()));
        } else {
            // Fallback: Copy to clipboard with notification when Copilot is not available
            if (DevAssistUtils.copyToClipboardWithNotification(prompt, notificationTitle, Bundle.message(Resource.DEV_ASSIST_COPY_FIX_PROMPT), project)) {
                LOGGER.info(format("RTS-Fix: %s remediation completed (clipboard) for issue: %s, for file: %s", scanIssue.getScanEngine().name(), scanIssue.getTitle(), scanIssue.getFilePath()));
            }
        }
    }

    /**
     * Sends a fix prompt to GitHub Copilot for automated remediation.
     * <p>
     * This method attempts to:
     * <ol>
     *   <li>Open GitHub Copilot Chat</li>
     *   <li>Switch to Agent mode</li>
     *   <li>Paste and send the prompt automatically</li>
     * </ol>
     * <p>
     * This method does NOT show any notifications - the caller is responsible for
     * handling success/failure notifications.
     *
     * @param prompt  the fix prompt to send to Copilot
     * @param project the project context
     * @return true if Copilot was successfully opened and prompt initiated, false otherwise
     */
    private boolean fixWithAI(@NotNull String prompt, @NotNull Project project) {
        try {
            CopilotIntegration.IntegrationResult result =
                    CopilotIntegration.openCopilotWithPromptDetailed(prompt, project, null);

            if (result.isSuccess()) {
                LOGGER.debug("Fix with AI: Copilot integration initiated successfully");
                return true;
            } else {
                LOGGER.debug("Fix with AI: Copilot not available - " + result.getMessage());
                return false;
            }
        } catch (Exception exception) {
            LOGGER.debug("Failed to fix with AI: ", exception);
            return false;
        }
    }

    /**
     * View details for a given scan issue.
     *
     * @param project   the project where the fix is to be applied
     * @param scanIssue the scan issue to view details for
     * @param actionId  the action ID for vulnerability-specific details
     */
    public void viewDetails(@NotNull Project project, @NotNull ScanIssue scanIssue, String actionId) {
        String prompt = buildExplanationPrompt(scanIssue, actionId);
        applyViewDetails(project, scanIssue, prompt);
    }

    /**
     * Builds the explanation prompt based on scan engine type.
     *
     * @param scanIssue the scan issue to build prompt for
     * @param actionId  the action ID for vulnerability-specific details
     * @return the explanation prompt, or null if not applicable
     */
    @Nullable
    private String buildExplanationPrompt(@NotNull ScanIssue scanIssue, String actionId) {
        switch (scanIssue.getScanEngine()) {
            case OSS:
                return buildOSSExplanationPrompt(scanIssue);
            case SECRETS:
                return buildSecretExplanationPrompt(scanIssue);
            case CONTAINERS:
                return buildContainerExplanationPrompt(scanIssue);
            case IAC:
                return buildIACExplanationPrompt(scanIssue, actionId);
            case ASCA:
                return buildASCAExplanationPrompt(scanIssue, actionId);
            default:
                return null;
        }
    }

    /**
     * Applies the view details by attempting to send to Copilot AI first, with clipboard fallback.
     *
     * @param project   the project context
     * @param scanIssue the scan issue being explained
     * @param prompt    the explanation prompt to apply
     */
    private void applyViewDetails(@NotNull Project project, @NotNull ScanIssue scanIssue, @Nullable String prompt) {
        if (prompt == null || prompt.isEmpty()) {
            return;
        }
        LOGGER.info(format("RTS-ViewDetails: %s explanation started for issue: %s, for file: %s", scanIssue.getScanEngine().name(), scanIssue.getTitle(), scanIssue.getFilePath()));
        String notificationTitle = getNotificationTitle(scanIssue.getScanEngine());

        // Try to send to Copilot AI first (no notifications shown by fixWithAI)
        boolean aiSuccess = fixWithAI(prompt, project);
        if (aiSuccess) {
            LOGGER.info(format("RTS-ViewDetails: %s explanation sent to Copilot for issue: %s, for file: %s", scanIssue.getScanEngine().name(), scanIssue.getTitle(), scanIssue.getFilePath()));
        } else {
            // Fallback: Copy to clipboard with notification when Copilot is not available
            if (DevAssistUtils.copyToClipboardWithNotification(prompt, notificationTitle, Bundle.message(Resource.DEV_ASSIST_COPY_VIEW_DETAILS_PROMPT), project)) {
                LOGGER.info(format("RTS-ViewDetails: %s explanation completed (clipboard) for issue: %s, for file: %s", scanIssue.getScanEngine().name(), scanIssue.getTitle(), scanIssue.getFilePath()));
            }
        }
    }

    /**
     * Builds remediation prompt for an OSS issue.
     */
    private String buildOSSRemediationPrompt(ScanIssue scanIssue) {
        return CxOneAssistFixPrompts.buildSCARemediationPrompt(scanIssue.getTitle(), scanIssue.getPackageVersion(),
                scanIssue.getPackageManager(), scanIssue.getSeverity());
    }

    /**
     * Builds remediation prompt for a Secret issue.
     */
    private String buildSecretRemediationPrompt(ScanIssue scanIssue) {
        return CxOneAssistFixPrompts.buildSecretRemediationPrompt(scanIssue.getTitle(),
                scanIssue.getDescription(),
                scanIssue.getSeverity());
    }

    /**
     * Builds remediation prompt for a container issue.
     */
    private String buildContainerRemediationPrompt(ScanIssue scanIssue) {
        return CxOneAssistFixPrompts.buildContainersRemediationPrompt(scanIssue.getFileType(),
                scanIssue.getTitle(),
                scanIssue.getImageTag(),
                scanIssue.getSeverity());
    }

    /**
     * Builds remediation prompt for a IAC issue.
     */
    private String buildIACRemediationPrompt(ScanIssue scanIssue, String actionId) {
        if (Objects.isNull(actionId) || actionId.isEmpty()) {
            LOGGER.warn(format("RTS-Fix: Remediation failed. Action id is not found for IAC issue: %s.", scanIssue.getTitle()));
            return null;
        }
        Vulnerability vulnerability = DevAssistUtils.getVulnerabilityDetails(scanIssue,
                actionId.equals(QUICK_FIX) ? scanIssue.getScanIssueId() : actionId);

        if (Objects.isNull(vulnerability)) {
            LOGGER.warn(format("RTS-Fix: Remediation failed. Vulnerability details not found for IAC issue: %s.", actionId));
            return null;
        }

        return CxOneAssistFixPrompts.buildIACRemediationPrompt(
                actionId.equals(QUICK_FIX) ? scanIssue.getTitle() : vulnerability.getTitle(),
                actionId.equals(QUICK_FIX) ? scanIssue.getDescription() : vulnerability.getDescription(),
                actionId.equals(QUICK_FIX) ? scanIssue.getSeverity() : vulnerability.getSeverity(),
                scanIssue.getFileType(),
                vulnerability.getExpectedValue(),
                vulnerability.getActualValue(),
                scanIssue.getProblematicLineNumber()
        );
    }

    /**
     * Builds remediation prompt for an ASCA issue.
     *
     * @param scanIssue the scan issue to fix
     * @param actionId  the specific vulnerability ID to fix, or QUICK_FIX for general remediation
     */
    private String buildASCARemediationPrompt(ScanIssue scanIssue, String actionId) {
        if (Objects.isNull(actionId) || actionId.isEmpty()) {
            LOGGER.warn(format("RTS-Fix: Remediation failed. Action id is not found for ASCA issue: %s.", scanIssue.getTitle()));
            return null;
        }
        Vulnerability vulnerability = DevAssistUtils.getVulnerabilityDetails(scanIssue,
                actionId.equals(QUICK_FIX) ? scanIssue.getScanIssueId() : actionId);

        if (Objects.isNull(vulnerability)) {
            LOGGER.warn(format("RTS-Fix: Remediation failed. Vulnerability details not found for ASCA issue: %s.", actionId));
            return null;
        }

        return CxOneAssistFixPrompts.buildASCARemediationPrompt(
                actionId.equals(QUICK_FIX) ? scanIssue.getTitle() : vulnerability.getTitle(),
                actionId.equals(QUICK_FIX) ? scanIssue.getDescription() : vulnerability.getDescription(),
                actionId.equals(QUICK_FIX) ? scanIssue.getSeverity() : vulnerability.getSeverity(),
                actionId.equals(QUICK_FIX) ? scanIssue.getRemediationAdvise() : vulnerability.getRemediationAdvise(),
                scanIssue.getProblematicLineNumber());
    }

    /**
     * Builds explanation prompt for an OSS issue.
     */
    private String buildOSSExplanationPrompt(ScanIssue scanIssue) {
        return ViewDetailsPrompts.buildSCAExplanationPrompt(scanIssue.getTitle(),
                scanIssue.getPackageVersion(),
                scanIssue.getSeverity(),
                scanIssue.getVulnerabilities());
    }

    /**
     * Builds explanation prompt for a Secret issue.
     */
    private String buildSecretExplanationPrompt(ScanIssue scanIssue) {
        return ViewDetailsPrompts.buildSecretsExplanationPrompt(scanIssue.getTitle(),
                scanIssue.getDescription(),
                scanIssue.getSeverity());
    }

    /**
     * Builds explanation prompt for a container issue.
     */
    private String buildContainerExplanationPrompt(ScanIssue scanIssue) {
        return ViewDetailsPrompts.buildContainersExplanationPrompt(scanIssue.getFileType(),
                scanIssue.getTitle(),
                scanIssue.getImageTag(),
                scanIssue.getSeverity());
    }

    /**
     * Builds explanation prompt for an IAC issue.
     */
    private String buildIACExplanationPrompt(ScanIssue scanIssue, String actionId) {
        if (Objects.isNull(actionId) || actionId.isEmpty()) {
            LOGGER.warn(format("RTS-ViewDetails: Explanation failed. Action id is not found for IAC issue: %s.", scanIssue.getTitle()));
            return null;
        }
        Vulnerability vulnerability = DevAssistUtils.getVulnerabilityDetails(scanIssue,
                actionId.equals(QUICK_FIX) ? scanIssue.getScanIssueId() : actionId);

        if (Objects.isNull(vulnerability)) {
            LOGGER.warn(format("RTS-ViewDetails: Explanation failed. Vulnerability details not found for IAC issue: %s.", actionId));
            return null;
        }

        return ViewDetailsPrompts.buildIACExplanationPrompt(
                actionId.equals(QUICK_FIX) ? scanIssue.getTitle() : vulnerability.getTitle(),
                actionId.equals(QUICK_FIX) ? scanIssue.getDescription() : vulnerability.getDescription(),
                actionId.equals(QUICK_FIX) ? scanIssue.getSeverity() : vulnerability.getSeverity(),
                scanIssue.getFileType(),
                vulnerability.getExpectedValue(),
                vulnerability.getActualValue()
        );
    }

    /**
     * Builds explanation prompt for an ASCA issue.
     *
     * @param scanIssue the scan issue to explain
     * @param actionId  the specific vulnerability ID to explain, or QUICK_FIX for general explanation
     */
    private String buildASCAExplanationPrompt(ScanIssue scanIssue, String actionId) {
        if (Objects.isNull(actionId) || actionId.isEmpty()) {
            LOGGER.warn(format("RTS-ViewDetails: Explanation failed. Action id is not found for ASCA issue: %s.", scanIssue.getTitle()));
            return null;
        }
        Vulnerability vulnerability = DevAssistUtils.getVulnerabilityDetails(scanIssue,
                actionId.equals(QUICK_FIX) ? scanIssue.getScanIssueId() : actionId);

        if (Objects.isNull(vulnerability)) {
            LOGGER.warn(format("RTS-ViewDetails: Explanation failed. Vulnerability details not found for ASCA issue: %s.", actionId));
            return null;
        }

        return ViewDetailsPrompts.buildASCAExplanationPrompt(
                actionId.equals(QUICK_FIX) ? scanIssue.getTitle() : vulnerability.getTitle(),
                actionId.equals(QUICK_FIX) ? scanIssue.getDescription() : vulnerability.getDescription(),
                actionId.equals(QUICK_FIX) ? scanIssue.getSeverity() : vulnerability.getSeverity());
    }

    /**
     * Get the notification title for the given scan engine.
     */
    private String getNotificationTitle(ScanEngine scanEngine) {
        return DevAssistUtils.getAgentName() + " - " + scanEngine.name();
    }
}
