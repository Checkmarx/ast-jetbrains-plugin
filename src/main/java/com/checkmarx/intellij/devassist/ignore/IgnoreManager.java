package com.checkmarx.intellij.devassist.ignore;

import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.common.ScanManager;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.checkmarx.intellij.devassist.utils.DevAssistConstants;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.lang.String.format;

public final class IgnoreManager extends ScanManager {
    private static final Logger LOG = Utils.getLogger(IgnoreManager.class);
    private static IgnoreManager instance;

    private final Project project;

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

        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(detail.getFilePath());
        ProblemHolderService.getInstance(project).removeProblemDescriptorByLine(detail.getFilePath(), detail.getLocations().get(0).getLine());

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

        ProblemHolderService problemHolder = ProblemHolderService.getInstance(project);
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
                problemHolder.removeProblemsFromFile(issue);
                fileRefs.add(new IgnoreEntry.FileRef(
                        IgnoreFileManager.getInstance(project).normalizePath(issue.getFilePath()),
                        true,
                        issue.getLocations().get(0).getLine()
                ));
            }
        }
        ignoreEntry.setFiles(fileRefs);
        IgnoreFileManager.getInstance(project)
                .updateIgnoreData(vulnerabilityKey, ignoreEntry);
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
        ScanEngine scanEngine = detail.getScanEngine();
        switch (scanEngine) {
            case OSS:
                return format("%s:%s:%s",
                        detail.getPackageManager() != null ? detail.getPackageManager() : "unknown",
                        detail.getTitle(),
                        detail.getPackageVersion());

            case CONTAINERS:
                // Match VSCode: imageName:imageTag
                return format("%s:%s",
                        detail.getTitle(),  // imageName
                        detail.getImageTag());  // imageTag

            case SECRETS:
                // title:secretValue (or hash if too long)
                return format("%s:%s",
                        detail.getTitle(),
                        detail.getSecretValue(),
                        detail.getFilePath());

            case ASCA:
                // ruleName:ruleId
                return format("%s:%s:%s",
                        detail.getTitle(),  // ruleName
                        detail.getRuleId(), // ruleId as string
                        detail.getFilePath());

            case IAC:
                // title:similarityId
                return format("%s:%s",
                        detail.getTitle(),
                        detail.getSimilarityId(),  // similarityId
                        detail.getFilePath());

            default:
                LOG.warn("Unknown scan engine: " + scanEngine + ", using fallback key");
                return format("%s:%s", detail.getScanEngine(), detail.getTitle());
        }
    }

}
