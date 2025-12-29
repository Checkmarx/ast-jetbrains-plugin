package com.checkmarx.intellij.devassist.ignore;

import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.checkmarx.intellij.devassist.utils.DevAssistConstants;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class IgnoreManager {
    private static final Logger LOG = Utils.getLogger(IgnoreManager.class);
    private static IgnoreManager instance;

    private final Project project;

    private static final List<PathMatcher> MANIFEST_MATCHERS =
            DevAssistConstants.MANIFEST_FILE_PATTERNS.stream()
                    .map(pattern ->
                            FileSystems.getDefault()
                                    .getPathMatcher("glob:" + pattern))
                    .collect(Collectors.toList());

    private IgnoreManager(Project project) {
        this.project = project;
    }

    public static synchronized IgnoreManager getInstance(Project project) {
        if (instance == null || instance.project != project) {
            instance = new IgnoreManager(project);
        }
        return instance;
    }

    /**
     * Adds an entry to the ignore file.
     * Removes the corresponding scan issue from the problem holder.
     * @param detail
     */
    public void addIgnoredEntry(ScanIssue detail) {
        // Convert ScanIssue → IgnoreEntry
        IgnoreEntry newEntry = convertToIgnoreEntry(detail);
        // Update via IgnoreFileManager
        String vulnerabilityKey = createVulnerabilityKey(detail);
        IgnoreFileManager.getInstance(project).updateIgnoreData(vulnerabilityKey, newEntry);
        ProblemHolderService.getInstance(project).removeProblemsFromFile(detail);
    }

    // For ignore all options : TBD
    // lets search for all the map in the problemholderservice
    // and check the vulnerability against all the files i.e. key in the loop
    // This addallignore entry is working for oss and containers
    public void addAllIgnoredEntry(ScanIssue issueToIgnore) {
        if (issueToIgnore == null) return;
        IgnoreEntry newEntry = convertToIgnoreEntry(issueToIgnore);
        String vulnerabilityKey = createVulnerabilityKey(issueToIgnore);
        LOG.info("Adding all ignore entry for vulnerability : " + vulnerabilityKey);
        Map<String, List<ScanIssue>> allIssues =
                ProblemHolderService.getInstance(project).getAllIssues();
        if (allIssues == null || allIssues.isEmpty()) return;

        List<IgnoreEntry.FileRef> fileRefs = new ArrayList<>();
        for (Map.Entry<String, List<ScanIssue>> entry : allIssues.entrySet()) {
            String filePath = entry.getKey();
            if (!matchesManifestPattern(filePath)) continue;

            List<ScanIssue> issues = entry.getValue();
            Iterator<ScanIssue> issueIterator = issues.iterator();
            while (issueIterator.hasNext()) {
                ScanIssue issue = issueIterator.next();
                if (!createVulnerabilityKey(issue).equals(vulnerabilityKey)) continue;

                int line = issue.getLocations().get(0).getLine();
                issueIterator.remove();  // Safe on mutable List
                ProblemHolderService.getInstance(project).removeProblemsFromFile(issue);
                fileRefs.add(new IgnoreEntry.FileRef(filePath, true, line));
            }
        }
        newEntry.setFiles(fileRefs);
        IgnoreFileManager.getInstance(project)
                .updateIgnoreData(vulnerabilityKey, newEntry);
    }


    private static boolean matchesManifestPattern(String filePath) {
        return MANIFEST_MATCHERS.stream()
                .anyMatch(matcher ->
                        matcher.matches(
                                java.nio.file.Path.of(filePath)
                        ));
    }

    public List<IgnoreEntry> getIgnoredEntries() {
        return IgnoreFileManager.getInstance(project).getAllIgnoreEntries();
    }

    public String getIgnoreTempFilePath() {
        return IgnoreFileManager.getInstance(project).getTempListPath().toString();
    }

    private IgnoreEntry convertToIgnoreEntry(ScanIssue detail) {
        String relativePath = IgnoreFileManager.getInstance(project).normalizePath(detail.getFilePath());
        int lineNumber = detail.getLocations().get(0).getLine();

        IgnoreEntry.FileRef fileRef = new IgnoreEntry.FileRef(relativePath, true, lineNumber);
        ArrayList<IgnoreEntry.FileRef> fileRefs = new ArrayList<>();
        fileRefs.add(fileRef);

        IgnoreEntry ignoreEntry = new IgnoreEntry();
        ignoreEntry.setFiles(fileRefs);
        ignoreEntry.setType(detail.getScanEngine());
        ignoreEntry.setPackageManager(detail.getPackageManager());
        ignoreEntry.setPackageName(detail.getTitle());
        ignoreEntry.setPackageVersion(detail.getPackageVersion());
        ignoreEntry.setSeverity(detail.getSeverity());
        ignoreEntry.setDescription(detail.getDescription());
        ignoreEntry.setDateAdded(Instant.now().toString());

        return ignoreEntry;
    }

    private String createVulnerabilityKey(ScanIssue detail) {
        return String.format("%s:%s:%s",
                detail.getPackageManager(),
                detail.getTitle(),
                detail.getPackageVersion());
    }
}
