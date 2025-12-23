package com.checkmarx.intellij.devassist.remediation;

import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.intellij.codeInsight.highlighting.TooltipLinkHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

import static com.checkmarx.intellij.Constants.RealTimeConstants.SEPERATOR;
import static java.lang.String.format;

/**
 * Handler for remediation actions triggered from tooltips in the editor.
 * This class extends TooltipLinkHandler to process specific remediation links
 * such as fixing issues, viewing details, or ignoring certain types of issues.
 */
public class RemediationLinkHandler extends TooltipLinkHandler {

    private static final Logger LOGGER = Utils.getLogger(RemediationLinkHandler.class);

    private static final String FIX = "copyfixprompt";
    private static final String VIEW_DETAILS = "viewdetails";
    private static final String IGNORE_THIS_TYPE = "ignorethis";
    private static final String IGNORE_ALL_OF_THIS_TYPE = "ignoreallofthis";

    private final RemediationManager remediationManager = new RemediationManager();

    /**
     * Handles a link click on a tooltip.
     *
     * @param link   part of link's href attribute after registered prefix.
     * @param editor an editor in which a tooltip with a link was shown.
     * @return true if the link was handled successfully, false otherwise.
     */
    @Override
    public boolean handleLink(@NotNull String link, @NotNull Editor editor) {
        Project project = editor.getProject();
        if (Objects.isNull(project)) {
            LOGGER.debug("RTS: Remediation action failed. project object is null.");
            return false;
        }
        if (!link.contains(SEPERATOR)) {
            LOGGER.warn(format("RTS: Remediation action link is not valid: %s.", link));
            return false;
        }
        String[] linkData = link.split(SEPERATOR);
        String scanIssueId = extractIssueId(linkData);
        if (scanIssueId.isEmpty()) {
            LOGGER.warn(format("RTS: Scan issue id not found for remediation action: %s.", link));
            return false;
        }
        LOGGER.info(format("RTS: Remediation action: %s is called for problem with issue-id: %s.", link, scanIssueId));

        ScanIssue scanIssue = getScanIssue(editor, project, scanIssueId);
        if (Objects.isNull(scanIssue)) {
            LOGGER.warn(format("RTS: Remediation action failed. Scan issue is not found for the given issue-id: %s.", scanIssueId));
            return false;
        }
        return handleActions(extractAction(linkData), project, scanIssue);
    }

    /**
     * Handles specific remediation actions for a given scan issue in a project.
     * Depending on the provided action link, it performs appropriate actions
     * such as applying a fix, viewing issue details, or ignoring the issue type.
     *
     * @param link      the action link representing the remediation action to be performed
     * @param project   the project where the scan issue is identified
     * @param scanIssue the scan issue on which the action is performed
     * @return true if the action is successfully handled, false otherwise
     */
    private boolean handleActions(String link, Project project, ScanIssue scanIssue) {
        switch (link) {
            case FIX:
                remediationManager.fixWithCxOneAssist(project, scanIssue);
                break;
            case VIEW_DETAILS:
                remediationManager.viewDetails(project, scanIssue);
                break;
            case IGNORE_THIS_TYPE:
            case IGNORE_ALL_OF_THIS_TYPE:
                break;
            default:
                LOGGER.warn(format("RTS: Remediation action %s is not supported.", link));
                return false;
        }
        return true;
    }

    /**
     * Extracts the issue id from the link.
     *
     * @param linkData action link with issue id
     * @return scan issue id
     */
    private String extractIssueId(String[] linkData) {
        return Objects.nonNull(linkData) ? linkData[1] : "";
    }

    /**
     * Extracts the action from the link.
     *
     * @param linkData action link with issue id
     * @return remediation action
     */
    private String extractAction(String[] linkData) {
        return Objects.nonNull(linkData) ? linkData[0] : "";
    }

    /**
     * Retrieves a specific scan issue based on the provided issue ID and the current editor context.
     * The method identifies the file associated with the editor and fetches the corresponding
     * scan issues from the ProblemHolderService. If a matching issue ID is found, it returns the
     * associated scan issue.
     *
     * @param editor  the editor instance representing the current context, must not be null
     * @param issueId the unique identifier of the scan issue to retrieve
     * @return the {@link ScanIssue} matching the given issue ID, or null if no match is found
     */
    private ScanIssue getScanIssue(@NotNull Editor editor, Project project, String issueId) {
        try {
            String decodedString = DevAssistUtils.decodeBase64(issueId);
            VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
            if (Objects.isNull(file)) {
                LOGGER.debug("RTS: VirtualFile not found for the given editor to handle the link.");
                return null;
            }
            ProblemHolderService problemHolderService = ProblemHolderService.getInstance(project);
            if (Objects.isNull(problemHolderService)) {
                LOGGER.debug("RTS: ProblemHolderService not found for the given project to handle the link.");
                return null;
            }
            List<ScanIssue> scanIssueList = problemHolderService.getScanIssueByFile(file.getPath());
            if (scanIssueList.isEmpty()) {
                LOGGER.debug("RTS: No scan issues found for the given file scan issue-id: %s to handle the link.", issueId);
                return null;
            }
            return scanIssueList.stream()
                    .filter(issue -> issue.getTitle().equals(decodedString))
                    .findFirst()
                    .orElse(null);
        } catch (Exception exception) {
            LOGGER.debug("RTS: Exception occurred while retrieving scan issue", exception);
            return null;
        }
    }
}
