package com.checkmarx.intellij.devassist.problems;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.util.SeverityLevel;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.messages.Topic;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A service that manages scan issues and problem descriptors for files within a project.
 * This service is responsible for storing, updating, and notifying listeners about issues
 * detected in files during scans, as well as handling problem descriptors linked to these files.
 */
@Service(Service.Level.PROJECT)
public final class ProblemHolderService {
    // Scan issues for each file
    private final Map<String, List<ScanIssue>> fileToIssues = new ConcurrentHashMap<>();

    // Problem descriptors for each file to avoid display empty problems
    private final Map<String, List<ProblemDescriptor>> fileProblemDescriptor = new ConcurrentHashMap<>();
    public static final Topic<IssueListener> ISSUE_TOPIC = new Topic<>("ISSUES_UPDATED", IssueListener.class);

    public interface IssueListener {
        void onIssuesUpdated(Map<String, List<ScanIssue>> issues);
    }

    private final Project project;

    public ProblemHolderService(Project project) {
        this.project = project;
    }

    /**
     * Returns the instance of this service for the given project.
     *
     * @param project the project.
     * @return the instance of this service for the given project.
     */
    public static ProblemHolderService getInstance(Project project) {
        return project.getService(ProblemHolderService.class);
    }

    /**
     * Adds problems for the given file.
     *
     * @param filePath the file path.
     * @param problems the scan issues.
     */
    public synchronized void addScanIssues(String filePath, List<ScanIssue> problems) {
        fileToIssues.put(filePath, new ArrayList<>(problems));
        // Notify subscribers immediately
        syncWithCxOneFindings();
    }

    /**
     * Removes scan issues for the given file.
     *
     * @param filePath virtual file path.
     */
    public synchronized void removeScanIssues(String filePath) {
        if (Objects.nonNull(filePath) && !filePath.isEmpty() && fileToIssues.containsKey(filePath)) {
            fileToIssues.remove(filePath);
            syncWithCxOneFindings();
        }
    }

    public synchronized Map<String, List<ScanIssue>> getAllIssues() {
        return Collections.unmodifiableMap(fileToIssues);
    }

    public void removeAllScanIssuesOfType(String scannerType) {
        for (Map.Entry<String, List<ScanIssue>> entry : getAllIssues().entrySet()) {
            List<ScanIssue> problems = entry.getValue();
            if (problems != null) {
                problems.removeIf(problem -> scannerType.equals(problem.getScanEngine().name()));
            }
        }
        syncWithCxOneFindings();
    }

    /**
     * Removes specific scan issue from files where it appears.
     * The method matches files by comparing their names and removes the specified issue
     * from all matching files. If removing the issue results in an empty issue list
     * for a file, that file entry is removed completely.
     *
     * @param scanIssueToRemove the scan issue to be removed from files
     */
    public void removeProblemsFromFile(ScanIssue scanIssueToRemove) {
        if (scanIssueToRemove == null) return;
        String targetPath = scanIssueToRemove.getFilePath();
        Iterator<Map.Entry<String, List<ScanIssue>>> iterator = fileToIssues.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, List<ScanIssue>> entry = iterator.next();
            String mapPath = entry.getKey();
            // Match if both paths end with same filename
            if (targetPath.endsWith(new File(mapPath).getName()) ||
                    mapPath.endsWith(new File(targetPath).getName())) {
                List<ScanIssue> issues = entry.getValue();
                issues.removeIf(issue -> issue.equals(scanIssueToRemove));
                if (issues.isEmpty()) {
                    iterator.remove();
                }
            }
        }
        project.getMessageBus().syncPublisher(ISSUE_TOPIC).onIssuesUpdated(getAllIssues());
    }

    /**
     * Update the severity of the scan issue to ignore.
     * This will mark the issue as ignored in the UI and not appear in the problem window
     * @param scanIssueToIgnore
     */
    public void updateScanIssueForIgnoreVulnerability(ScanIssue scanIssueToIgnore) {
        for (Map.Entry<String, List<ScanIssue>> entry : fileToIssues.entrySet()) {
            if(scanIssueToIgnore.getFilePath().isEmpty()) continue;
            if (scanIssueToIgnore.getFilePath().equalsIgnoreCase(entry.getKey())) {
                List<ScanIssue> issues = entry.getValue();
                issues.replaceAll(issue -> {
                    if (issue.equals(scanIssueToIgnore)) {
                        issue.setSeverity(SeverityLevel.IGNORED.getSeverity());
                        return issue;
                    }
                    return issue;
                });
            }
        }
        project.getMessageBus().syncPublisher(ISSUE_TOPIC).onIssuesUpdated(getAllIssues());
    }



    /**
     * Returns the scan issues for the given file.
     *
     * @param filePath the file path.
     * @return the scan issues.
     */
    public synchronized List<ScanIssue> getScanIssueByFile(String filePath) {
        return fileToIssues.getOrDefault(filePath, Collections.emptyList());
    }

    /**
     * Returns the problem descriptors for the given file.
     *
     * @param filePath the file path.
     * @return the problem descriptors.
     */
    public synchronized List<ProblemDescriptor> getProblemDescriptors(String filePath) {
        return fileProblemDescriptor.getOrDefault(filePath, Collections.emptyList());
    }

    /**
     * Adds problem descriptors for the given file.
     *
     * @param filePath           the file path.
     * @param problemDescriptors the problem descriptors.
     */
    public synchronized void addProblemDescriptors(String filePath, List<ProblemDescriptor> problemDescriptors) {
        fileProblemDescriptor.put(filePath, new ArrayList<>(problemDescriptors));
    }

    /**
     * Removes all problem descriptors for the given file.
     *
     * @param filePath the file path.
     */
    public void removeProblemDescriptorsForFile(String filePath) {
        fileProblemDescriptor.remove(filePath);
    }

    /**
     * Clears all problem descriptors.
     */
    public void removeAllProblemDescriptors() {
        fileProblemDescriptor.clear();
    }

    /**
     * Adds problems to the CxOne findings for the given file.
     *
     * @param file         the PSI file.
     * @param problemsList the list of problems.
     */
    public static void addToCxOneFindings(PsiFile file, List<ScanIssue> problemsList) {
        getInstance(file.getProject()).addScanIssues(file.getVirtualFile().getPath(), problemsList);
    }

    /**
     * Synchronizes the current issues with CxOne findings.
     */
    private void syncWithCxOneFindings() {
        project.getMessageBus().syncPublisher(ISSUE_TOPIC).onIssuesUpdated(getAllIssues());
    }

    /**
     * Removes a specific problem descriptor from the given file by line number.
     *
     * @param filePath the file path.
     * @param lineNumber the line number of the problem descriptor to remove (1-based).
     * @param issueId (clickId) ScanIssueId Take here the clickId, and compare that inside problemdescriptor
     */
    public void removeProblemDescriptorByLine(String filePath, int lineNumber) {
        List<ProblemDescriptor> descriptors = fileProblemDescriptor.get(filePath);
        if (descriptors == null || descriptors.isEmpty()) {
            return;
        }
        // Use ProblemDescriptor.getLineNumber() directly (0-based)
        Iterator<ProblemDescriptor> iterator = descriptors.iterator();
        boolean removed = false;
        //* use stream api below/ or try to remove directly from concurrentmap
        while (iterator.hasNext()) {
            ProblemDescriptor descriptor = iterator.next();
            if (descriptor.getLineNumber() == lineNumber - 1) {  // Convert 1-based to 0-based
                iterator.remove();
                removed = true;
                break;  // Remove only first match per line
            }
        }
        if (removed) {
            fileProblemDescriptor.put(filePath, descriptors);
        }
    }

    // How many problemdescriptor on same line
    // Iterate on that - And in localQuickFix get ScanIssue , check id in the scanissu, and if mathcing remove that problemdescriptor
    // Check how we can trigger particular engine for file
}
