package com.checkmarx.intellij.devassist.remediation;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.model.Vulnerability;
import com.checkmarx.intellij.devassist.remediation.prompts.CxOneAssistFixPrompts;
import com.checkmarx.intellij.devassist.remediation.prompts.ViewDetailsPrompts;
import com.checkmarx.intellij.devassist.utils.DevAssistConstants;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

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
     */
    public void fixWithCxOneAssist(@NotNull Project project, @NotNull ScanIssue scanIssue, String actionId) {
        String prompt = null;
        switch (scanIssue.getScanEngine()) {
            case OSS:
                prompt = buildOSSRemediationPrompt(scanIssue);
                break;
            case SECRETS:
                prompt = buildSecretRemediationPrompt(scanIssue);
                break;
            case CONTAINERS:
                prompt = buildContainerRemediationPrompt(scanIssue);
                break;
            case IAC:
                prompt = buildIACRemediationPrompt(scanIssue, actionId);
                break;
            case ASCA:
                prompt = buildASCARemediationPrompt(scanIssue, actionId);
                break;
            default:
                break;
        }

        if (prompt == null || prompt.isEmpty()) {
            return;
        }

        LOGGER.info(format("RTS-Fix: Remediation started for file: %s for %s issue: %s",
                scanIssue.getFilePath(), scanIssue.getScanEngine().name(), scanIssue.getTitle()));

        String notificationTitle = getNotificationTitle(scanIssue.getScanEngine());

        // Try to fix with Copilot AI first
        boolean aiSuccess = DevAssistUtils.fixWithAI(
                prompt,
                notificationTitle,
                Bundle.message(Resource.DEV_ASSIST_FIX_WITH_AI_SUCCESS),
                Bundle.message(Resource.DEV_ASSIST_FIX_WITH_AI_COPILOT_NOT_FOUND),
                project);

        if (aiSuccess) {
            LOGGER.info(format("RTS-Fix-AI: Remediation sent to Copilot for file: %s for %s issue: %s",
                    scanIssue.getFilePath(), scanIssue.getScanEngine().name(), scanIssue.getTitle()));
        } else {
            // Fallback: Copy to clipboard with notification when Copilot is not available
            if (DevAssistUtils.copyToClipboardWithNotification(prompt, notificationTitle,
                    Bundle.message(Resource.DEV_ASSIST_COPY_FIX_PROMPT), project)) {
                LOGGER.info(format("RTS-Fix: Remediation completed (clipboard) for file: %s for %s issue: %s",
                        scanIssue.getFilePath(), scanIssue.getScanEngine().name(), scanIssue.getTitle()));
            }
        }
    }

    /**
     * View details for a given scan issue.
     *
     * @param project   the project where the fix is to be applied
     * @param scanIssue the scan issue to view details for
     */
    public void viewDetails(@NotNull Project project, @NotNull ScanIssue scanIssue, String actionId) {
        switch (scanIssue.getScanEngine()) {
            case OSS:
                explainOSSDetails(project, scanIssue);
                break;
            case SECRETS:
                explainSecretDetails(project, scanIssue);
                break;
            case CONTAINERS:
                explainContainerDetails(project, scanIssue);
                break;
            case IAC:
                explainIACDetails(project, scanIssue, actionId);
                break;
            case ASCA:
                explainASCADetails(project, scanIssue, actionId);
                break;
            default:
                break;
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
     * Explain the details of an OSS issue.
     *
     * @param project   the project where the fix is to be applied
     * @param scanIssue the scan issue to view details for
     */
    private void explainOSSDetails(Project project, ScanIssue scanIssue) {
        LOGGER.info(format("RTS-Fix: Viewing details for file: %s for OSS Issue: %s", scanIssue.getFilePath(), scanIssue.getTitle()));
        String prompt = ViewDetailsPrompts.buildSCAExplanationPrompt(scanIssue.getTitle(),
                scanIssue.getPackageVersion(),
                scanIssue.getSeverity(),
                scanIssue.getVulnerabilities());
        if (DevAssistUtils.copyToClipboardWithNotification(prompt, getNotificationTitle(scanIssue.getScanEngine()),
                Bundle.message(Resource.DEV_ASSIST_COPY_VIEW_DETAILS_PROMPT), project)) {
            LOGGER.info(format("RTS-Fix: Viewing details completed for file: %s for OSS Issue: %s",
                    scanIssue.getFilePath(), scanIssue.getTitle()));
        }
    }

    /**
     * Explain the details of a secret issue.
     *
     * @param project   the project where the fix is to be applied
     * @param scanIssue the scan issue to view details for
     */
    private void explainSecretDetails(Project project, ScanIssue scanIssue) {
        LOGGER.info(format("RTS-Fix: Viewing details for file: %s for secret issue: %s", scanIssue.getFilePath(), scanIssue.getTitle()));
        String prompt = ViewDetailsPrompts.buildSecretsExplanationPrompt(scanIssue.getTitle(),
                scanIssue.getDescription(),
                scanIssue.getSeverity());
        if (DevAssistUtils.copyToClipboardWithNotification(prompt, getNotificationTitle(scanIssue.getScanEngine()),
                Bundle.message(Resource.DEV_ASSIST_COPY_VIEW_DETAILS_PROMPT), project)) {
            LOGGER.info(format("RTS-Fix: Viewing details completed for file: %s for secret issue: %s",
                    scanIssue.getFilePath(), scanIssue.getTitle()));
        }
    }

    /**
     * Explain the details of a container issue.
     *
     * @param project   the project where the fix is to be applied
     * @param scanIssue the scan issue to view details for
     */
    private void explainContainerDetails(Project project, ScanIssue scanIssue) {
        LOGGER.info(format("RTS-Fix: Viewing details for file: %s for container issue: %s", scanIssue.getFilePath(), scanIssue.getTitle()));
        String prompt = ViewDetailsPrompts.buildContainersExplanationPrompt(scanIssue.getFileType(),
                scanIssue.getTitle(),
                scanIssue.getImageTag(),
                scanIssue.getSeverity());
        if (DevAssistUtils.copyToClipboardWithNotification(prompt, getNotificationTitle(scanIssue.getScanEngine()),
                Bundle.message(Resource.DEV_ASSIST_COPY_VIEW_DETAILS_PROMPT), project)) {
            LOGGER.info(format("RTS-Fix: Viewing details completed for file: %s for container issue: %s",
                    scanIssue.getFilePath(), scanIssue.getTitle()));
        }
    }

    /**
     * Explain the details of an IAC issue.
     *
     * @param project   the project where the fix is to be applied
     * @param scanIssue the scan issue to view details for
     */
    private void explainIACDetails(Project project, ScanIssue scanIssue, String actionId) {
        if (Objects.isNull(actionId) || actionId.isEmpty()) {
            LOGGER.warn(format("RTS-Fix: Explain IAC issue failed. Action id is not found for IAC issue: %s.", scanIssue.getTitle()));
            return;
        }
        Vulnerability vulnerability = DevAssistUtils.getVulnerabilityDetails(scanIssue,
                actionId.equals(QUICK_FIX) ? scanIssue.getScanIssueId() : actionId);

        if (Objects.isNull(vulnerability)) {
            LOGGER.warn(format("RTS-Fix: Explain IAC issue failed. Vulnerability details not found for IAC issue: %s.", actionId));
            return;
        }
        LOGGER.info(format("RTS-Fix: Viewing details for file: %s for IAC issue is started: %s",
                scanIssue.getFilePath(), actionId.equals(QUICK_FIX) ? scanIssue.getTitle() : vulnerability.getTitle()));

        String prompt = ViewDetailsPrompts.buildIACExplanationPrompt(
                actionId.equals(QUICK_FIX) ? scanIssue.getTitle() : vulnerability.getTitle(),
                actionId.equals(QUICK_FIX) ? scanIssue.getDescription() : vulnerability.getDescription(),
                actionId.equals(QUICK_FIX) ? scanIssue.getSeverity() : vulnerability.getSeverity(),
                scanIssue.getFileType(),
                vulnerability.getExpectedValue(),
                vulnerability.getActualValue()
        );
        if (DevAssistUtils.copyToClipboardWithNotification(prompt, getNotificationTitle(scanIssue.getScanEngine()),
                Bundle.message(Resource.DEV_ASSIST_COPY_VIEW_DETAILS_PROMPT), project)) {
            LOGGER.info(format("RTS-Fix: Viewing details completed for file: %s for IAC issue: %s",
                    scanIssue.getFilePath(), scanIssue.getTitle()));
        }
    }


    /**
     * Explain the details of an ASCA issue.
     *
     * @param project   the project where the fix is to be applied
     * @param scanIssue the scan issue to view details for
     * @param actionId  the specific vulnerability ID to view details for, or QUICK_FIX for general explanation
     */
    private void explainASCADetails(Project project, ScanIssue scanIssue, String actionId) {
        if (Objects.isNull(actionId) || actionId.isEmpty()) {
            LOGGER.warn(format("RTS-Fix: Explain ASCA issue failed. Action id is not found for ASCA issue: %s.", scanIssue.getTitle()));
            return;
        }
        Vulnerability vulnerability = DevAssistUtils.getVulnerabilityDetails(scanIssue,
                actionId.equals(QUICK_FIX) ? scanIssue.getScanIssueId() : actionId);

        if (Objects.isNull(vulnerability)) {
            LOGGER.warn(format("RTS-Fix: Explain ASCA issue failed. Vulnerability details not found for ASCA issue: %s.", actionId));
            return;
        }
        LOGGER.info(format("RTS-Fix: Viewing details for file: %s for ASCA issue is started: %s",
                scanIssue.getFilePath(), actionId.equals(QUICK_FIX) ? scanIssue.getTitle() : vulnerability.getTitle()));

        String prompt = ViewDetailsPrompts.buildASCAExplanationPrompt(
                actionId.equals(QUICK_FIX) ? scanIssue.getTitle() : vulnerability.getTitle(),
                actionId.equals(QUICK_FIX) ? scanIssue.getDescription() : vulnerability.getDescription(),
                actionId.equals(QUICK_FIX) ? scanIssue.getSeverity() : vulnerability.getSeverity());

        if (DevAssistUtils.copyToClipboardWithNotification(prompt, getNotificationTitle(scanIssue.getScanEngine()),
                Bundle.message(Resource.DEV_ASSIST_COPY_VIEW_DETAILS_PROMPT), project)) {
            LOGGER.info(format("RTS-Fix: Viewing details completed for file: %s for ASCA issue: %s",
                    scanIssue.getFilePath(), scanIssue.getTitle()));
        }
    }

    /**
     * Get the notification title for the given scan engine.
     */
    private String getNotificationTitle(ScanEngine scanEngine) {
        return DevAssistConstants.CX_AGENT_NAME + " - " + scanEngine.name();
    }
}
