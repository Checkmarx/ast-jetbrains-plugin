package com.checkmarx.intellij.devassist.remediation;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.remediation.prompts.CxOneAssistFixPrompts;
import com.checkmarx.intellij.devassist.remediation.prompts.ViewDetailsPrompts;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import static java.lang.String.format;

/**
 * RemediationManager provides remediation options for issues identified during a real-time scan.
 * <p>
 * This class supports applying fixes, viewing details etc for scan issues detected by different scan engines,
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
    private static final String CX_AGENT_NAME = Constants.RealTimeConstants.CX_AGENT_NAME;

    /**
     * Apply remediation for a given scan issue.
     *
     * @param project   the project where the fix is to be applied
     * @param scanIssue the scan issue to fix
     */
    public void fixWithCxOneAssist(@NotNull Project project, @NotNull ScanIssue scanIssue) {
        switch (scanIssue.getScanEngine()) {
            case OSS:
                applyOSSRemediation(project, scanIssue);
                break;
            case ASCA:
                applyASCARemediation(project, scanIssue);
                break;
            default:
                break;
        }
    }

    /**
     * View details for a given scan issue.
     *
     * @param project   the project where the fix is to be applied
     * @param scanIssue the scan issue to view details for
     */
    public void viewDetails(@NotNull Project project, @NotNull ScanIssue scanIssue) {
        switch (scanIssue.getScanEngine()) {
            case OSS:
                explainOSSDetails(project, scanIssue);
                break;
            case ASCA:
                applyASCARemediation(project, scanIssue);
                break;
            default:
                break;
        }
    }

    /**
     * Applies remediation for an OSS issue.
     */
    private void applyOSSRemediation(Project project, ScanIssue scanIssue) {
        LOGGER.info(format("RTS-Fix: Remediation started for file: %s for OSS Issue: %s",
                scanIssue.getFilePath(), scanIssue.getTitle()));
        String scaPrompt = CxOneAssistFixPrompts.scaRemediationPrompt(scanIssue.getTitle(), scanIssue.getPackageVersion(),
                scanIssue.getPackageManager(), scanIssue.getSeverity());
        if (DevAssistUtils.copyToClipboardWithNotification(scaPrompt, CX_AGENT_NAME,
                Bundle.message(Resource.DEV_ASSIST_COPY_FIX_PROMPT), project)) {
            LOGGER.info(format("RTS-Fix: Remediation completed for file: %s for OSS Issue: %s",
                    scanIssue.getFilePath(), scanIssue.getTitle()));
        }
    }

    /**
     * Applies remediation for an ASCA issue.
     *
     * @param scanIssue the scan issue to fix
     */
    private void applyASCARemediation(Project project, ScanIssue scanIssue) {
        LOGGER.info(format("RTS-Fix: Remediation started for file: %s for ASCA Issue: %s",
                scanIssue.getFilePath(), scanIssue.getTitle()));
    }

    /**
     * Explain the details of an OSS issue.
     *
     * @param project   the project where the fix is to be applied
     * @param scanIssue the scan issue to view details for
     */
    private void explainOSSDetails(Project project, ScanIssue scanIssue) {
        LOGGER.info(format("RTS-Fix: Viewing details for file: %s for OSS Issue: %s", scanIssue.getFilePath(), scanIssue.getTitle()));
        String scaPrompt = ViewDetailsPrompts.generateSCAExplanationPrompt(scanIssue.getTitle(),
                scanIssue.getPackageVersion(),
                scanIssue.getSeverity(),
                scanIssue.getVulnerabilities());
        if (DevAssistUtils.copyToClipboardWithNotification(scaPrompt, CX_AGENT_NAME,
                Bundle.message(Resource.DEV_ASSIST_COPY_VIEW_DETAILS_PROMPT), project)) {
            LOGGER.info(format("RTS-Fix: Viewing details completed for file: %s for OSS Issue: %s",
                    scanIssue.getFilePath(), scanIssue.getTitle()));
        }
    }
}
