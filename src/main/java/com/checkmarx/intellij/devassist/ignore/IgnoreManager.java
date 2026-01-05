package com.checkmarx.intellij.devassist.ignore;

import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.common.ScanManager;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.model.Vulnerability;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.checkmarx.intellij.devassist.utils.DevAssistConstants;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * Manages the ignore file (.checkmarxIgnored) within the project's workspace.
 * Handles reading, writing, and updating ignore entries.
 * Monitors the ignore file for changes and updates internal state accordingly.
 * Provides methods to ignore issues and update temporary ignore lists.
 */
public final class IgnoreManager extends ScanManager {
    private static final Logger LOG = Utils.getLogger(IgnoreManager.class);
    private static IgnoreManager instance;
    private final Project project;
    private final ProblemHolderService problemHolder;
    private final IgnoreFileManager ignoreFileManager;

    private static final List<PathMatcher> MANIFEST_MATCHERS =
            DevAssistConstants.MANIFEST_FILE_PATTERNS.stream()
                    .map(pattern ->
                            FileSystems.getDefault()
                                    .getPathMatcher("glob:" + pattern))
                    .collect(Collectors.toList());

    private static final List<PathMatcher> CONTAINER_MATCHERS =
            DevAssistConstants.CONTAINERS_FILE_PATTERNS.stream()
                    .map(pattern ->
                            FileSystems.getDefault()
                                    .getPathMatcher("glob:" + pattern))
                    .collect(Collectors.toList());

    private IgnoreManager(Project project) {
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
     * Removes the corresponding scan issue from the problem holder.
     * @param detail
     */
    public void addIgnoredEntry(ScanIssue detail) {
        // Convert ScanIssue → IgnoreEntry
        IgnoreEntry newEntry = convertToIgnoreEntry(detail);
        // Update via IgnoreFileManager
        String vulnerabilityKey = createVulnerabilityKey(detail);
        ignoreFileManager.updateIgnoreData(vulnerabilityKey, newEntry);
        problemHolder.ignoreProblemsInFile(detail);
        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(detail.getFilePath());
        problemHolder.removeProblemDescriptorByLine(detail.getFilePath(), detail.getLocations().get(0).getLine());
        if (virtualFile != null) {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
            refreshAfterIgnore(psiFile, detail.getFilePath(), detail.getLocations().get(0).getLine());
        }
    }

    private void refreshAfterIgnore(PsiFile file, String filePath, int lineNumber) {
        ProblemHolderService.getInstance(file.getProject()).removeProblemDescriptorByLine(filePath, lineNumber);

        Editor editor = FileEditorManager.getInstance(file.getProject()).getSelectedTextEditor();
        if (editor != null) {
            CaretModel caret = editor.getCaretModel();
            LogicalPosition pos = caret.getLogicalPosition();

            // 🔥 1 PIXEL MOVE → Triggers everything
            caret.moveToLogicalPosition(new LogicalPosition(pos.line, pos.column + 1));
            caret.moveToLogicalPosition(pos);  // Back

            DaemonCodeAnalyzer.getInstance(file.getProject()).restart(file);
        }
    }


    /**
     * Adds an entry to the ignore file for all occurrences of the specified issue.
     * Removes the corresponding scan issues from the problem holder.
     * @param issueToIgnore
     */
    public void addAllIgnoredEntry(ScanIssue issueToIgnore) {
        String vulnerabilityKey = createVulnerabilityKey(issueToIgnore);
        LOG.info("Adding all ignore entry for vulnerability : " + vulnerabilityKey);
        Map<String, List<ScanIssue>> allIssues = problemHolder.getAllIssues();
        if (allIssues.isEmpty()) return;
        IgnoreEntry ignoreEntry = convertToIgnoreEntry(issueToIgnore);
        List<IgnoreEntry.FileRef> fileRefs = new ArrayList<>();
        for (List<ScanIssue> issues : allIssues.values()) {
            Iterator<ScanIssue> issueIterator = issues.iterator();
            while (issueIterator.hasNext()) {
                ScanIssue issue = issueIterator.next();
                if (!createVulnerabilityKey(issue).equals(vulnerabilityKey)) continue;
                issueIterator.remove();  // Safe on mutable List
                problemHolder.ignoreProblemsInFile(issue);
                fileRefs.add(new IgnoreEntry.FileRef(
                        ignoreFileManager.normalizePath(issue.getFilePath()),
                        true,
                        issue.getLocations().get(0).getLine()));
            }
        }
        ignoreEntry.setFiles(fileRefs);
        ignoreFileManager
                .updateIgnoreData(vulnerabilityKey, ignoreEntry);
    }

    public List<IgnoreEntry> getIgnoredEntries() {
        return ignoreFileManager.getAllIgnoreEntries();
    }

    public String getIgnoreTempFilePath() {
        return ignoreFileManager.getTempListPath().toString();
    }

    /**
     * Converts a scan issue to an ignore entry.
     * @param detail
     * @return
     */
    private IgnoreEntry convertToIgnoreEntry(ScanIssue detail) {
        if(detail.getScanEngine() == ScanEngine.CONTAINERS) {
            return convertToIgnoredEntryContainers(detail);
        }
        String relativePath = ignoreFileManager.normalizePath(detail.getFilePath());
        int line = detail.getLocations().get(0).getLine();
        String vulnerabilityKey = createVulnerabilityKey(detail);

        IgnoreEntry entry = IgnoreFileManager.ignoreData.computeIfAbsent(vulnerabilityKey, k -> {

            IgnoreEntry.FileRef fileRef = new IgnoreEntry.FileRef(relativePath, true, line);
            ArrayList<IgnoreEntry.FileRef> fileRefs = new ArrayList<>();
            fileRefs.add(fileRef);

            IgnoreEntry ignoreEntry = new IgnoreEntry();
            ignoreEntry.setType(detail.getScanEngine());
            ignoreEntry.setPackageManager(detail.getPackageManager());
            ignoreEntry.setPackageName(detail.getTitle());
            ignoreEntry.setPackageVersion(detail.getPackageVersion());
            ignoreEntry.setSimilarityId(detail.getSimilarityId());
            ignoreEntry.setRuleId(detail.getRuleId());
            ignoreEntry.setFiles(fileRefs);
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
     * Converts a scan issue to an ignored entry for containers.
     * If entry is issue is existing, then update the file and line number.
     * @param detail
     * @return
     */
    public IgnoreEntry convertToIgnoredEntryContainers(ScanIssue detail) {
        String relativePath = ignoreFileManager.normalizePath(detail.getFilePath());
        String packageKey = detail.getTitle() + ":" + detail.getImageTag();
        int line = detail.getLocations().get(0).getLine();

        IgnoreEntry entry = IgnoreFileManager.ignoreData.computeIfAbsent(packageKey, k -> {
            IgnoreEntry ignoreEntry = new IgnoreEntry();
            ignoreEntry.setType(detail.getScanEngine());
            ignoreEntry.setPackageName(packageKey);
            ignoreEntry.setImageName(detail.getTitle());
            ignoreEntry.setImageTag(detail.getImageTag());
            ignoreEntry.setFiles(List.of(new IgnoreEntry.FileRef(relativePath, true, line)));
            return ignoreEntry;
        });
        ifExistingIssue(detail, entry);
        Optional.ofNullable(detail.getSeverity()).ifPresent(entry::setSeverity);
        Optional.ofNullable(detail.getDescription()).ifPresent(entry::setDescription);
        entry.setDateAdded(Instant.now().toString());

        return entry;
    }

    private void ifExistingIssue(ScanIssue detail, IgnoreEntry entry) {
        String relativePath = ignoreFileManager.normalizePath(detail.getFilePath());
        int line = detail.getLocations().get(0).getLine();
        Optional<IgnoreEntry.FileRef> existing = entry.getFiles().stream()
                .filter(f -> f.getPath().equals(relativePath) && f.getLine() == line)
                .findFirst();
        if (existing.isPresent()) {
            existing.get().setActive(true);
        } else {
            entry.getFiles().add(new IgnoreEntry.FileRef(relativePath, true, line));
        }
    }

    /**
     * Creates a unique key for the given scan issue.
     * @param detail
     * @return
     */
    private String createVulnerabilityKey(ScanIssue detail) {
        String relativePath = ignoreFileManager.normalizePath(detail.getFilePath());
        switch (detail.getScanEngine()) {
            case OSS:
                return format("%s:%s:%s", detail.getPackageManager(), detail.getTitle(), detail.getPackageVersion());
            case CONTAINERS:
                // imageName:imageTag
                return format("%s:%s", detail.getTitle(), detail.getImageTag());
            case SECRETS:
                // title:secretValue:filePath
                return format("%s:%s:%s", detail.getTitle(), detail.getSecretValue(), relativePath);
            case ASCA:
                // ruleName:ruleId:filePath
                return format("%s:%s:%s", detail.getTitle(), detail.getRuleId(),  relativePath);
            case IAC:
                // title:similarityId:filePath
                return format("%s:%s:%s", detail.getTitle(), detail.getSimilarityId(), relativePath);
            default:
                LOG.warn("Unknown scan engine: " + detail.getScanEngine() + ", using fallback key");
                return format("%s:%s", detail.getScanEngine(), detail.getTitle());
        }
    }

    private static boolean matchesManifestPattern(String filePath) {
        return MANIFEST_MATCHERS.stream()
                .anyMatch(matcher ->
                        matcher.matches(
                                java.nio.file.Path.of(filePath)
                        ));
    }

    private static boolean matchesContainerPattern(String filePath) {
        return CONTAINER_MATCHERS.stream()
                .anyMatch(matcher ->
                        matcher.matches(java.nio.file.Path.of(filePath)));
    }
}
