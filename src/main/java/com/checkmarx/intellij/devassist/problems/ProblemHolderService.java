package com.checkmarx.intellij.devassist.problems;

import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.remediation.CxOneAssistFix;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.messages.Topic;

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
    private final Map<String, List<ScanIssue>> fileToIssues = new LinkedHashMap<>();

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

    /**
     * Removes all scan issues of a given scanner type.
     *
     * @param scannerType the scanner type. e.g., OSS, ASCA etc.
     */
    public void removeAllScanIssuesOfType(String scannerType) {
        Map<String, List<ScanIssue>> allIssues = getAllIssues();
        if (allIssues.isEmpty()) return;
        for (Map.Entry<String, List<ScanIssue>> entry : allIssues.entrySet()) {
            List<ScanIssue> problems = entry.getValue();
            if (problems != null && !problems.isEmpty()) {
                problems.removeIf(problem -> scannerType.equals(problem.getScanEngine().name()));
            }
        }
        syncWithCxOneFindings();
    }

    /**
     * Removes all scan issues of a given scanner type for a file.
     *
     * @param scannerType the scanner type. e.g., OSS, ASCA etc.
     */
    public void removeScanIssuesByFileAndScanner(String scannerType, String filePath) {
        Map<String, List<ScanIssue>> allIssues = getAllIssues();
        if (allIssues.isEmpty()) return;
        allIssues.values().stream()
                .filter(problems -> problems != null && !problems.isEmpty() && Objects.nonNull(filePath) && !filePath.isEmpty())
                .forEach(problems -> problems.removeIf(problem -> scannerType.equals(problem.getScanEngine().name())
                        && filePath.equals(problem.getFilePath())));
        syncWithCxOneFindings();
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
     * Removes problem descriptors for the given file by scanner.
     */
    public void removeProblemDescriptorsForFileByScanner(String filePath, ScanEngine scanEngine) {
        if (fileProblemDescriptor.isEmpty()) return;

        if (Objects.nonNull(scanEngine) && Objects.nonNull(filePath) && !filePath.isEmpty()) {
            getProblemDescriptors(filePath).removeIf(descriptor -> {
                CxOneAssistFix cxOneAssistFix = (CxOneAssistFix) descriptor.getFixes()[0];
                return Objects.nonNull(cxOneAssistFix) && scanEngine.name().equals(cxOneAssistFix.getScanIssue().getScanEngine().name());
            });
        }
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
}
