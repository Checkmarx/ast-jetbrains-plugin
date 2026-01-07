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
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

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
        // Convert ScanIssue → IgnoreEntry
        IgnoreEntry ignoreEntry = buildIgnoreEntry(issueToIgnore, clickId);
        if (Objects.isNull(ignoreEntry)) {
            Utils.showNotification(Bundle.message(Resource.IGNORE_FAILED), "", NotificationType.ERROR, project);
            return;
        }
        // Update via IgnoreFileManager
        String vulnerabilityKey = createJsonKeyForIgnoreEntry(issueToIgnore, clickId);
        LOGGER.debug(String.format("RTS-Ignore: Ignoring %s", vulnerabilityKey));
        ignoreFileManager.updateIgnoreData(vulnerabilityKey, ignoreEntry);
        scanFileAndUpdateResults(issueToIgnore);
        showIgnoreSuccessNotification(issueToIgnore, project);
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
            Utils.showNotification(Bundle.message(Resource.IGNORE_FAILED), "", NotificationType.ERROR, project);
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
                scanFileAndUpdateResults(issue);
                return true;
            });
        }
        ignoreEntry.setFiles(fileRefs);
        ignoreFileManager.updateIgnoreData(vulnerabilityKey, ignoreEntry);
        LOGGER.debug(String.format("RTS-Ignore: Successfully added ignore entry for issue: %s", issueToIgnore.getTitle()));
        showIgnoreSuccessNotification(issueToIgnore, project);
    }

    /**
     * Retrieves all ignore entries from the ignore file.
     *
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
     * @param scanIssue The scan issue containing details about the file to be scanned,
     *                  including the file path, scan engine information, and associated vulnerabilities.
     */
    public void scanFileAndUpdateResults(ScanIssue scanIssue) {
        try {
            ApplicationManager.getApplication().invokeLater(() -> {
                PsiFile psiFile = DevAssistUtils.getPsiFileByFilePath(project, scanIssue.getFilePath());
                if (Objects.isNull(psiFile)) return;

                InspectionManager inspectionManager = InspectionManager.getInstance(project);
                if (Objects.isNull(inspectionManager)) return;

                ProblemHelper problemHelper = ProblemHelper.builder(psiFile, project)
                        .filePath(scanIssue.getFilePath())
                        .problemHolderService(problemHolder)
                        .isOnTheFly(true)
                        .manager(inspectionManager)
                        .build();

                boolean isScanScheduled = CxOneAssistScanScheduler.getInstance(project)
                        .scheduleScan(scanIssue.getFilePath(), problemHelper, scanIssue.getScanEngine());

                if (!isScanScheduled) {
                    LOGGER.debug("RTS-Ignore: Scan not scheduled, triggering inspection after ignoring vulnerability for file: {}.", scanIssue.getFilePath());
                    // trigger inspection if a scan is not scheduled
                    new CxOneAssistInspectionMgr().triggerInspection(project);
                }
            }, ModalityState.NON_MODAL);
        } catch (Exception e) {
            LOGGER.warn(format("RTS-Ignore: Exception occurred while trigger scan after ignoring vulnerability for file :%s ", scanIssue.getTitle()), e);
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
        IgnoreEntry entry = IgnoreFileManager.ignoreData.computeIfAbsent(createJsonKeyForIgnoreEntry(detail, clickId), k -> {
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
        IgnoreEntry entry = IgnoreFileManager.ignoreData.computeIfAbsent(createJsonKeyForIgnoreEntry(detail, clickId), k -> {
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
        IgnoreEntry entry = IgnoreFileManager.ignoreData.computeIfAbsent(vulnerabilityKey, k -> {
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
                return format("%s:%s:%s", detail.getPackageManager(), detail.getTitle(), detail.getPackageVersion());
            case CONTAINERS:
                // imageName:imageTag
                return format("%s:%s", detail.getTitle(), detail.getImageTag());
            case SECRETS:
                // title:secretValue:filePath
                return format("%s:%s:%s", detail.getTitle(), detail.getSecretValue(), relativePath);
            case IAC:
                vulnerability = DevAssistUtils.getVulnerabilityDetails(detail,
                        clickId.equals(QUICK_FIX) ? detail.getScanIssueId() : clickId);
                return format("%s:%s:%s", vulnerability.getTitle(), vulnerability.getSimilarityId(), relativePath);
            case ASCA:
                vulnerability = DevAssistUtils.getVulnerabilityDetails(detail,
                        clickId.equals(QUICK_FIX) ? detail.getScanIssueId() : clickId);
                return format("%s:%s:%s", vulnerability.getTitle(), detail.getRuleId(), relativePath);
            default:
                LOGGER.warn("Unknown scan engine: " + detail.getScanEngine() + ", using fallback key");
                return format("%s:%s", detail.getScanEngine(), detail.getTitle());
        }
    }

    private void showIgnoreSuccessNotification(ScanIssue detail, Project project) {
        switch (detail.getScanEngine()) {
            case OSS:
                Utils.showNotification("Package", detail.getTitle() + "@" + detail.getPackageVersion() + " " + Bundle.message(Resource.IGNORE_SUCCESS), NotificationType.INFORMATION, project);
                break;
            case SECRETS:
                Utils.showNotification("Secret", detail.getTitle() + " " + Bundle.message(Resource.IGNORE_SUCCESS), NotificationType.INFORMATION, project);
                break;
            case ASCA:
                Utils.showNotification("ASCA rule", detail.getTitle() + " " + Bundle.message(Resource.IGNORE_SUCCESS), NotificationType.INFORMATION, project);
                break;
            case CONTAINERS:
                Utils.showNotification("Container", detail.getTitle() + "@" + detail.getImageTag() + " " + Bundle.message(Resource.IGNORE_SUCCESS), NotificationType.INFORMATION, project);
                break;
            case IAC:
                Utils.showNotification("IaC finding", detail.getTitle() + " " + Bundle.message(Resource.IGNORE_SUCCESS), NotificationType.INFORMATION, project);
                break;
            default:
                Utils.showNotification(Bundle.message(Resource.IGNORE_SUCCESS), "", NotificationType.INFORMATION, project);
                break;

        }
    }

}
