package com.checkmarx.intellij.devassist.ignore;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.inspection.CxOneAssistInspectionMgr;
import com.checkmarx.intellij.devassist.inspection.CxOneAssistScanScheduler;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.model.Vulnerability;
import com.checkmarx.intellij.devassist.problems.ProblemHelper;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.checkmarx.intellij.devassist.utils.DevAssistConstants;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

import static com.checkmarx.intellij.devassist.utils.DevAssistConstants.QUICK_FIX;
import static java.lang.String.format;

/**
 * Manages the ignore file (.checkmarxIgnored) within the project's workspace.
 * Handles reading, writing, and updating ignore entries.
 * Monitors the ignore file for changes and updates internal state accordingly.
 * Provides methods to ignore issues and update temporary ignore lists.
 */
public final class IgnoreManager {
    private static final Logger LOGGER = Utils.getLogger(IgnoreManager.class);
    private static IgnoreManager instance;
    private final Project project;
    private final ProblemHolderService problemHolder;
    private final IgnoreFileManager ignoreFileManager;

    public IgnoreManager(Project project) {
        this.project = project;
        this.problemHolder = ProblemHolderService.getInstance(project);
        this.ignoreFileManager = IgnoreFileManager.getInstance(project);
    }

    public static synchronized IgnoreManager getInstance(Project project) {
        if (instance == null || instance.project != project) {
            instance = new IgnoreManager(project);
        }
        return instance;
    }

    /**
     * Adds an entry to the ignore file.
     * After clicking on the ignore this vulnerability butten
     * Removes the corresponding scan issue from the problem holder.
     *
     * @param issueToIgnore The scan issue to ignore
     * @param clickId       The ID of the clicked action or vulnerability, used to retrieve additional details
     */
    public void addIgnoredEntry(ScanIssue issueToIgnore, String clickId) {
        LOGGER.debug(String.format("RTS-Ignore: Adding ignore entry for issue: %s", issueToIgnore.getTitle()));

        String vulnerabilityKey = createJsonKeyForIgnoreEntry(issueToIgnore, clickId);
        if(vulnerabilityKey.isEmpty()){
            LOGGER.debug("RTS-Ignore: Ignoring vulnerability failed. Vulnerability key is empty.");
            return;
        }
        // Convert ScanIssue → IgnoreEntry
        IgnoreEntry ignoreEntry = buildIgnoreEntry(issueToIgnore, clickId);
        if (Objects.isNull(ignoreEntry)) {
            Utils.showNotification(Bundle.message(Resource.IGNORE_FAILED), "", NotificationType.ERROR, project,false,"");
            return;
        }
        LOGGER.debug(String.format("RTS-Ignore: Ignoring %s", vulnerabilityKey));
        ignoreFileManager.updateIgnoreData(vulnerabilityKey, ignoreEntry);
        scanFileAndUpdateResults(issueToIgnore.getFilePath(),issueToIgnore.getScanEngine());
        showIgnoreSuccessNotification(project, issueToIgnore, vulnerabilityKey);
        LOGGER.debug(String.format("RTS-Ignore: Successfully added ignore entry for issue: %s", issueToIgnore.getTitle()));
    }


    /**
     * Adds an entry to the ignore file for all occurrences of the specified issue.
     * This method performs the following steps:
     * 1. Creates a vulnerability key for the given issue
     * 2. Gets all issues from the problem holder and creates a deep copy
     * 3. Creates an ignore entry for the issue
     * 4. Iterates through all issues and adds matching ones to the ignore list
     * 5. Updates the ignore file and removes the issues from the problem holder
     *
     * @param issueToIgnore The scan issue to ignore across all files
     * @param clickId       The ID that was clicked to trigger the ignore action
     */
    public void addAllIgnoredEntry(ScanIssue issueToIgnore, String clickId) {
        LOGGER.debug(String.format("RTS-Ignore: Adding ignore entry for issue: %s", issueToIgnore.getTitle()));
        String vulnerabilityKey = createJsonKeyForIgnoreEntry(issueToIgnore, clickId);
        LOGGER.debug("RTS-Ignore: Ignoring all vulnerabilities for: " + vulnerabilityKey);
        Map<String, List<ScanIssue>> allIssues = new HashMap<>();
        for (Map.Entry<String, List<ScanIssue>> entry : problemHolder.getAllIssues().entrySet()) {
            allIssues.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        if (allIssues.isEmpty()) return;
        IgnoreEntry ignoreEntry = buildIgnoreEntry(issueToIgnore, clickId);
        if (Objects.isNull(ignoreEntry)) {
            Utils.showNotification(Bundle.message(Resource.IGNORE_FAILED), "", NotificationType.ERROR, project,false,"");
            return;
        }
        List<IgnoreEntry.FileReference> fileRefs = new ArrayList<>();
        for (List<ScanIssue> issues : allIssues.values()) {  // Safe: allIssues never mutates
            issues.removeIf(issue -> {
                if (!createJsonKeyForIgnoreEntry(issue, clickId).equals(vulnerabilityKey)) return false;
                // Mutate LIVE problemHolder (async-safe)
                fileRefs.add(new IgnoreEntry.FileReference(
                        ignoreFileManager.normalizePath(issue.getFilePath()),
                        true,
                        issue.getLocations().get(0).getLine()));
                scanFileAndUpdateResults(issue.getFilePath(), issue.getScanEngine());
                return true;
            });
        }
        ignoreEntry.setFiles(fileRefs);
        ignoreFileManager.updateIgnoreData(vulnerabilityKey, ignoreEntry);
        LOGGER.debug(String.format("RTS-Ignore: Successfully added ignore entry for issue: %s", issueToIgnore.getTitle()));
        showIgnoreSuccessNotification(project, issueToIgnore, vulnerabilityKey);
    }


    /**
     * Revives a single ignored vulnerability.
     * Shows a notification with an "Undo" option that allows the user to restore the ignored state.
     * The revive operation is performed first, then the user can undo it if desired.
     * This follows the same pattern as the VS Code extension's revivePackage method.
     *
     * @param entryToRevive The ignore entry to revive
     */
    public void reviveSingleEntry(IgnoreEntry entryToRevive) {
        LOGGER.debug(format("RTS-Ignore: Reviving entry: %s", entryToRevive.getPackageName()));
        Map<String, IgnoreEntry> ignoredEntries = new HashMap<>(ignoreFileManager.getIgnoreData());

        // Count active files before reviving
        int fileCount = (int) entryToRevive.getFiles().stream()
                .filter(IgnoreEntry.FileReference::isActive)
                .count();
        // Perform the revive operation (sets all file references to inactive)
        boolean success = ignoreFileManager.reviveEntry(entryToRevive);
        if (!success) {
            Utils.showNotification(Bundle.message(Resource.REVIVE_FAILED), entryToRevive.getPackageName(), NotificationType.ERROR, project,false,"");
            LOGGER.warn(format("RTS-Ignore: Failed to revive entry: %s", entryToRevive.getPackageName()));
            return;
        }
        // Trigger rescan for affected files
        triggerRescanForEntry(entryToRevive);
        // Show notification with undo option
        showReviveUndoNotification(entryToRevive, fileCount, ignoredEntries);
        LOGGER.debug(format("RTS-Ignore: Successfully revived entry: %s", entryToRevive.getPackageName()));
    }

    /**
     * Revives multiple ignored vulnerabilities in bulk.
     * Shows a summary notification to the user and triggers rescans for all affected files.
     *
     * @param entriesToRevive List of package keys to revive
     */
    public void reviveMultipleEntries(List<IgnoreEntry> entriesToRevive) {
        if (entriesToRevive == null || entriesToRevive.isEmpty()) {
            LOGGER.warn("RTS-Ignore: No package keys provided for bulk revive");
            return;
        }
        int successCount = 0;
        int totalFileCount = 0;
        List<IgnoreEntry> failedIgnoreEntry = new ArrayList<>();

        for (IgnoreEntry entryToRevive : entriesToRevive) {
            int fileCount = (int) entryToRevive.getFiles().stream()
                    .filter(IgnoreEntry.FileReference::isActive)
                    .count();
            boolean success = ignoreFileManager.reviveEntry(entryToRevive);
            if (success) {
                successCount++;
                totalFileCount += fileCount;
                // Trigger rescan for affected files
                triggerRescanForEntry(entryToRevive);
                LOGGER.debug(String.format("RTS-Ignore: Successfully revived entry: %s", entryToRevive.getTitle()));
            } else {
                failedIgnoreEntry.add(entryToRevive);
                LOGGER.warn(String.format("RTS-Ignore: Failed to revive entry: %s", entryToRevive.getTitle()));
            }
        }
        // Show summary notification
        if (successCount > 0) {
            String message;
            if (successCount == 1) {
                message = String.format("Revived 1 vulnerability in %d file%s",
                        totalFileCount, totalFileCount == 1 ? "" : "s");
            } else {
                message = String.format("Revived %d vulnerabilities in %d file%s",
                        successCount, totalFileCount, totalFileCount == 1 ? "" : "s");
            }
            if (!failedIgnoreEntry.isEmpty()) {
                message += String.format(" (%d failed)", failedIgnoreEntry.size());
            }
            Utils.showNotification(message, "", NotificationType.INFORMATION, project,false,"");
        } else {
            Utils.showNotification("Failed to revive entries", "", NotificationType.ERROR, project,false,"");
        }
    }

    /**
     * Triggers rescan for files affected by the revived entry.
     * Iterates through all file references and schedules a rescan for each revived file.
     *
     * @param entry The ignore entry containing file references to rescan
     */
    private void triggerRescanForEntry(IgnoreEntry entry) {
        for (IgnoreEntry.FileReference fileRef : entry.getFiles()) {
                String fullPath = Paths.get(Objects.requireNonNull(project.getBasePath()),
                        fileRef.getPath()).toString().replace("\\", "/");
                VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fullPath);
                if (vFile != null) {
                    // Trigger rescan based on scanner type
                    scanFileAndUpdateResults(fullPath, entry.getType());
                } else {
                    LOGGER.warn(String.format("RTS-Ignore: Could not find file for rescan: %s", fullPath));
                }
            }
    }

    /**
     * Retrieves all ignore entries from the ignore file.
     * @return list of ignore entries
     */
    public List<IgnoreEntry> getIgnoredEntries() {
        return ignoreFileManager.getAllIgnoreEntries();
    }

    public String getIgnoreTempFilePath() {
        return ignoreFileManager.getTempListPath().toString();
    }

    /**
     * Scans the given file related to the specified scan issue and updates the analysis results.
     * This method schedules a scan for the provided file path, processes issues using a problem helper,
     * and ensures that inspections are triggered if a scan is not scheduled successfully.
     *
     * @param filePath   The path of the file to be scanned
     * @param scanEngine The scan engine to be used for scanning the file
     */
    public void scanFileAndUpdateResults(String filePath, ScanEngine scanEngine) {
        try {
            ApplicationManager.getApplication().invokeLater(() -> {
                PsiFile psiFile = DevAssistUtils.getPsiFileByFilePath(project, filePath);
                if (Objects.isNull(psiFile)) return;

                InspectionManager inspectionManager = InspectionManager.getInstance(project);
                if (Objects.isNull(inspectionManager)) return;

                Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
                if (Objects.isNull(document)) return;

                ProblemHelper problemHelper = ProblemHelper.builder(psiFile, project)
                        .filePath(filePath)
                        .problemHolderService(problemHolder)
                        .isOnTheFly(true)
                        .manager(inspectionManager)
                        .document(document)
                        .build();

                boolean isScanScheduled = CxOneAssistScanScheduler.getInstance(project)
                        .scheduleScan(filePath, problemHelper, scanEngine);

                if (!isScanScheduled) {
                    LOGGER.debug("RTS-Ignore: Scan not scheduled, triggering inspection after ignoring vulnerability for file: {}.", filePath);
                    // trigger inspection if a scan is not scheduled
                    new CxOneAssistInspectionMgr().triggerInspection(project);
                }
            }, ModalityState.NON_MODAL);
        } catch (Exception e) {
            LOGGER.warn(format("RTS-Ignore: Exception occurred while trigger scan after ignoring vulnerability for file :%s ", filePath), e);
        }
    }

    /**
     * Converts a scan issue to an ignore entry.
     *
     * @param detail
     * @return
     */
    private IgnoreEntry buildIgnoreEntry(ScanIssue detail, String clickId) {
        switch (detail.getScanEngine()) {
            case IAC:
                return convertToIgnoredEntryIac(detail, clickId);
            case ASCA:
                return convertToIgnoredEntryAsca(detail, clickId);
            default:
                return convertToIgnoredEntry(detail, clickId);
        }
    }


    /**
     * Converts a ScanIssue to an IgnoreEntry for Infrastructure as Code (IaC) scan engine.
     * This method creates an IgnoreEntry with IaC-specific attributes including similarity ID,
     * package name, and file references. It handles validation of input parameters and logs warnings
     * if required data is missing.
     *
     * @param detail  The ScanIssue containing the IaC vulnerability details to be ignored
     * @param clickId The ID of the clicked action that triggered the ignore, used for vulnerability lookup.
     *                Can be either a quick fix ID or specific vulnerability ID
     * @return IgnoreEntry containing the converted IaC issue data, or null if required data is missing
     * or validation fails
     */
    public IgnoreEntry convertToIgnoredEntryIac(ScanIssue detail, String clickId) {
        if (Objects.isNull(clickId) || clickId.isEmpty()) {
            LOGGER.warn(format("RTS-Ignore: Ignore IAC issue failed. Action id is not found for IAC issue: %s.", detail.getTitle()));
            return null;
        }
        Vulnerability vulnerability = DevAssistUtils.getVulnerabilityDetails(detail,
                clickId.equals(QUICK_FIX) ? detail.getScanIssueId() : clickId);
        if (Objects.isNull(vulnerability)) {
            LOGGER.warn(format("RTS-Ignore: Ignore IAC issue failed. Vulnerability details not found for IAC issue: %s.", clickId));
            return null;
        }
        String relativePath = ignoreFileManager.normalizePath(detail.getFilePath());
        int line = detail.getLocations().get(0).getLine();
        IgnoreEntry entry = ignoreFileManager.getIgnoreData().computeIfAbsent(createJsonKeyForIgnoreEntry(detail, clickId), k -> {
            IgnoreEntry ignoreEntry = new IgnoreEntry();
            ignoreEntry.setType(detail.getScanEngine());
            ignoreEntry.setPackageName(vulnerability.getTitle());
            ignoreEntry.setSimilarityId(vulnerability.getSimilarityId());
            ignoreEntry.setSeverity(vulnerability.getSeverity());
            ignoreEntry.setDescription(vulnerability.getDescription());
            ignoreEntry.setFiles(List.of(new IgnoreEntry.FileReference(relativePath, true, line)));
            return ignoreEntry;
        });
        ifExistingIssue(detail, entry);
        entry.setDateAdded(Instant.now().toString());
        return entry;
    }

    /**
     * Converts a ScanIssue to an IgnoreEntry for ASCA scan engine.
     * This method creates an IgnoreEntry with ASCA-specific attributes including rule ID, severity,
     * package name, and file references. It handles validation of input parameters and logs warnings
     * if required data is missing.
     *
     * @param detail  The ScanIssue containing the ASCA vulnerability details to be ignored
     * @param clickId The ID of the clicked action or vulnerability, used to retrieve additional details
     * @return IgnoreEntry containing the converted ASCA issue data, or null if required data is missing
     */
    public IgnoreEntry convertToIgnoredEntryAsca(ScanIssue detail, String clickId) {

        if (Objects.isNull(clickId) || clickId.isEmpty()) {
            LOGGER.warn(format("RTS-Ignore: Ignore ASCA issue failed. Action id is not found for ASCA issue: %s.", detail.getTitle()));
            return null;
        }
        Vulnerability vulnerability = DevAssistUtils.getVulnerabilityDetails(detail,
                clickId.equals(QUICK_FIX) ? detail.getScanIssueId() : clickId);

        if (Objects.isNull(vulnerability)) {
            LOGGER.warn(format("RTS-Ignore: Ignore ASCA issue failed. Vulnerability details not found for ASCA issue: %s.", clickId));
            return null;
        }
        String relativePath = ignoreFileManager.normalizePath(detail.getFilePath());
        int line = detail.getLocations().get(0).getLine();
        IgnoreEntry entry = ignoreFileManager.getIgnoreData().computeIfAbsent(createJsonKeyForIgnoreEntry(detail, clickId), k -> {
            IgnoreEntry ignoreEntry = new IgnoreEntry();
            ignoreEntry.setType(detail.getScanEngine());
            ignoreEntry.setPackageName(vulnerability.getTitle());
            ignoreEntry.setRuleId(detail.getRuleId());
            ignoreEntry.setSeverity(vulnerability.getSeverity());
            ignoreEntry.setDescription(vulnerability.getDescription());
            ignoreEntry.setFiles(List.of(new IgnoreEntry.FileReference(relativePath, true, line)));
            return ignoreEntry;
        });
        ifExistingIssue(detail, entry);
        entry.setDateAdded(Instant.now().toString());

        return entry;
    }

    /**
     * ScanIssue to IgnoreEntry conversion for remaining scanners (OSS, CONTAINERS, SECRETS)
     *
     * @param detail
     * @param clickId
     * @return
     */
    public IgnoreEntry convertToIgnoredEntry(ScanIssue detail, String clickId) {
        String relativePath = ignoreFileManager.normalizePath(detail.getFilePath());
        String vulnerabilityKey = createJsonKeyForIgnoreEntry(detail, clickId);
        int line = detail.getLocations().get(0).getLine();
        IgnoreEntry entry = ignoreFileManager.getIgnoreData().computeIfAbsent(vulnerabilityKey, k -> {
            IgnoreEntry.FileReference fileRef = new IgnoreEntry.FileReference(relativePath, true, line);
            ArrayList<IgnoreEntry.FileReference> fileReference = new ArrayList<>();
            fileReference.add(fileRef);
            IgnoreEntry ignoreEntry = new IgnoreEntry();
            ignoreEntry.setType(detail.getScanEngine());
            ignoreEntry.setPackageManager(detail.getPackageManager());
            if (detail.getScanEngine() == ScanEngine.CONTAINERS) {
                ignoreEntry.setPackageName(detail.getTitle() + ":" + detail.getImageTag());
                ignoreEntry.setImageName(detail.getTitle());
                ignoreEntry.setImageTag(detail.getImageTag());
            } else {
                ignoreEntry.setPackageName(detail.getTitle());
            }
            ignoreEntry.setPackageVersion(detail.getPackageVersion());
            ignoreEntry.setSimilarityId(detail.getSimilarityId());
            ignoreEntry.setRuleId(detail.getRuleId());
            ignoreEntry.setFiles(fileReference);
            ignoreEntry.setSecretValue(detail.getSecretValue());

            return ignoreEntry;
        });
        ifExistingIssue(detail, entry);
        Optional.ofNullable(detail.getSeverity()).ifPresent(entry::setSeverity);
        Optional.ofNullable(detail.getDescription()).ifPresent(entry::setDescription);
        entry.setDateAdded(Instant.now().toString());

        return entry;
    }

    /**
     * Updates or adds file reference for an existing issue in the ignore entry.
     * If a file reference with matching path and line already exists, sets it to active.
     * Otherwise, adds a new file reference to the ignore entry.
     *
     * @param detail The scan issue containing file path and line information
     * @param entry  The ignore entry to update with file references
     */
    private void ifExistingIssue(ScanIssue detail, IgnoreEntry entry) {
        String relativePath = ignoreFileManager.normalizePath(detail.getFilePath());
        int line = detail.getLocations().get(0).getLine();
        // Ensure files list is mutable
        if (!(entry.getFiles() instanceof ArrayList)) {
            entry.setFiles(new ArrayList<>(entry.getFiles()));
        }
        Optional<IgnoreEntry.FileReference> existing = entry.getFiles().stream()
                .filter(fileReference -> fileReference.getPath().equals(relativePath) && fileReference.getLine() == line)
                .findFirst();
        if (existing.isPresent()) {
            existing.get().setActive(true);
        } else {
            entry.getFiles().add(new IgnoreEntry.FileReference(relativePath, true, line));
        }
    }

    /**
     * Creates a unique key for the given scan issue.
     *
     * @param detail
     * @return
     */
    private String createJsonKeyForIgnoreEntry(ScanIssue detail, String clickId) {
        String relativePath = ignoreFileManager.normalizePath(detail.getFilePath());
        Vulnerability vulnerability;
        switch (detail.getScanEngine()) {
            case OSS:
                return formatJsonKeyForIgnoreEntry(detail.getScanEngine(), detail.getPackageManager(), detail.getTitle(), detail.getPackageVersion());
            case CONTAINERS:
                // imageName:imageTag
                return formatJsonKeyForIgnoreEntry(detail.getScanEngine(), detail.getTitle(), detail.getImageTag(), "");
            case SECRETS:
                // title:secretValue:filePath
                return formatJsonKeyForIgnoreEntry(detail.getScanEngine(), detail.getTitle(), detail.getSecretValue(), relativePath);
            case IAC:
                vulnerability = DevAssistUtils.getVulnerabilityDetails(detail,
                        clickId.equals(QUICK_FIX) ? detail.getScanIssueId() : clickId);
                return Objects.nonNull(vulnerability) ?
                formatJsonKeyForIgnoreEntry(detail.getScanEngine(), vulnerability.getTitle(), vulnerability.getSimilarityId(), relativePath): "";
            case ASCA:
                vulnerability = DevAssistUtils.getVulnerabilityDetails(detail,
                        clickId.equals(QUICK_FIX) ? detail.getScanIssueId() : clickId);
                return Objects.nonNull(vulnerability) ?
                formatJsonKeyForIgnoreEntry(detail.getScanEngine(), vulnerability.getTitle(), detail.getRuleId().toString(), relativePath) : "";
            default:
                LOGGER.warn("Unknown scan engine: " + detail.getScanEngine() + ", using fallback key");
                return formatJsonKeyForIgnoreEntry(detail.getScanEngine(), "", "", detail.getTitle());
        }
    }

    private String formatJsonKeyForIgnoreEntry(ScanEngine scanEngine, String title, String packageName, String path){
        if (scanEngine == ScanEngine.CONTAINERS) {
            return format("%s:%s", title, packageName);
        } else {
            return format("%s:%s:%s", title, packageName, path);
        }

    }

    private void showIgnoreSuccessNotification(Project project, ScanIssue detail, String vulnerabilityKey) {
        switch (detail.getScanEngine()) {
            case OSS:
                Utils.showNotification("Package", detail.getTitle() + "@" + detail.getPackageVersion() + " " + Bundle.message(Resource.IGNORE_SUCCESS), NotificationType.INFORMATION, project,false,"");
                break;
            case SECRETS:
                Utils.showNotification("Secret", detail.getTitle() + " " + Bundle.message(Resource.IGNORE_SUCCESS), NotificationType.INFORMATION, project,false,"");
                break;
            case ASCA:
                Utils.showNotification("ASCA rule", vulnerabilityKey.split(":", 2)[0] + " " + Bundle.message(Resource.IGNORE_SUCCESS), NotificationType.INFORMATION, project,false,"");
                break;
            case CONTAINERS:
                Utils.showNotification("Container", detail.getTitle() + "@" + detail.getImageTag() + " " + Bundle.message(Resource.IGNORE_SUCCESS), NotificationType.INFORMATION, project,false,"");
                break;
            case IAC:
                Utils.showNotification("IaC finding", vulnerabilityKey.split(":", 2)[0] + " " + Bundle.message(Resource.IGNORE_SUCCESS), NotificationType.INFORMATION, project,false,"");
                break;
            default:
                Utils.showNotification(Bundle.message(Resource.IGNORE_SUCCESS), "", NotificationType.INFORMATION, project,false,"");
                break;

        }
    }

    private void showReviveUndoNotification(IgnoreEntry entryToRevive, int fileCount, Map<String, IgnoreEntry> ignoredEntries) {
        String message = format("%s", entryToRevive.getPackageName());
        Notification notification = NotificationGroupManager.getInstance()
                .getNotificationGroup(com.checkmarx.intellij.Constants.NOTIFICATION_GROUP_ID)
                .createNotification(message, format("vulnerability has been revived in %d file%s", fileCount, fileCount == 1 ? "" : "s"), NotificationType.INFORMATION);
        // Add undo action that restores the ignored state
        notification.addAction(NotificationAction.createSimple(
                DevAssistConstants.UNDO,
                () -> {
                    LOGGER.debug(format("RTS-Ignore: Undoing revive for entry: %s", entryToRevive.getPackageName()));
                    // Restore all files to active (ignored) state
                    for (IgnoreEntry.FileReference file : entryToRevive.getFiles()) {
                        file.setActive(true);
                    }
                    for (Map.Entry<String, IgnoreEntry> mapEntry : ignoredEntries.entrySet()) {
                        if (mapEntry.getValue().equals(entryToRevive)) {  // Use content comparison
                            ignoreFileManager.updateIgnoreData(mapEntry.getKey(), entryToRevive);
                            triggerRescanForEntry(entryToRevive);
                            LOGGER.debug(format("RTS-Ignore: Successfully undone revive for entry: %s", entryToRevive.getPackageName()));
                            break;
                        }
                    }
                    // Expire the notification
                    notification.expire();
                }
        ));
        notification.notify(project);
    }

}
