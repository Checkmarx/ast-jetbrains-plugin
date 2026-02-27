package com.checkmarx.intellij.devassist.problems;

import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.remediation.DevAssistFix;
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

    // Scan issues and problem descriptors for each file
    private final Map<String, List<ScanIssue>> fileToIssues = new ConcurrentHashMap<>();
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
     * @param filePath   the file path.
     * @param scanIssues the scan issues.
     */
    public void addScanIssues(String filePath, List<ScanIssue> scanIssues) {
        fileToIssues.put(filePath, new ArrayList<>(scanIssues));
        // Notify subscribers immediately
        syncWithCxOneFindings();
    }

    /**
     * Returns the scan issues for the given file.
     *
     * @param filePath the file path.
     * @return the scan issues.
     */
    public List<ScanIssue> getScanIssueByFile(String filePath) {
        return Collections.unmodifiableList(fileToIssues.getOrDefault(filePath, List.of()));
    }

    /**
     * Returns all scan issues.
     */
    public Map<String, List<ScanIssue>> getAllIssues() {
        return Collections.unmodifiableMap(fileToIssues);
    }

    /**
     * Removes scan issues for the given file.
     *
     * @param filePath virtual file path.
     */
    public void removeScanIssues(String filePath) {
        if (Objects.nonNull(filePath) && !filePath.isEmpty() && fileToIssues.containsKey(filePath)) {
            fileToIssues.remove(filePath);
            syncWithCxOneFindings();
        }
    }

    /**
     * Removes all scan issues of a given scanner type.
     */
    public void removeAllScanIssuesOfType(String scannerType) {
        fileToIssues.values().forEach(scanIssues -> {
            if (Objects.nonNull(scanIssues) && !scanIssues.isEmpty()) {
                scanIssues.removeIf(scanIssueObj -> scannerType.equals(scanIssueObj.getScanEngine().name()));
            }
        });
        syncWithCxOneFindings();
    }

    /**
     * Removes all scan issues of a given scanner type for a file.
     *
     * @param scannerType the scanner type. e.g., OSS, ASCA etc.
     */
    public void removeScanIssuesByFileAndScanner(String scannerType, String filePath) {
        if (fileToIssues.isEmpty() || Objects.isNull(filePath) || filePath.isEmpty()
                || Objects.isNull(scannerType) || scannerType.isEmpty() || !fileToIssues.containsKey(filePath)) {
            return;
        }
        List<ScanIssue> scanIssuesList = fileToIssues.get(filePath);
        if (Objects.isNull(scanIssuesList) || scanIssuesList.isEmpty()) {
            return;
        }
        scanIssuesList.removeIf(scanIssue -> scannerType.equalsIgnoreCase(scanIssue.getScanEngine().name()));
        syncWithCxOneFindings();
    }

    /**
     * Merges new scan issues into the existing list for the given file path.
     *
     * @param filePath  the file path.
     * @param newIssues the new issues to add.
     */
    public void mergeScanIssues(String filePath, List<ScanIssue> newIssues) {
        fileToIssues.compute(filePath, (key, existingIssues) -> {
            List<ScanIssue> updatedList = (Objects.isNull(existingIssues) || existingIssues.isEmpty())
                    ? new ArrayList<>()
                    : new ArrayList<>(existingIssues);
            updatedList.addAll(newIssues);
            return updatedList;
        });
        syncWithCxOneFindings();
    }

    /**
     * Returns the problem descriptors for the given file.
     *
     * @param filePath the file path.
     * @return the problem descriptors.
     */
    public List<ProblemDescriptor> getProblemDescriptors(String filePath) {
        return Collections.unmodifiableList(fileProblemDescriptor.getOrDefault(filePath, List.of()));
    }

    /**
     * Adds problem descriptors for the given file.
     *
     * @param filePath           the file path.
     * @param problemDescriptors the problem descriptors.
     */
    public void addProblemDescriptors(String filePath, List<ProblemDescriptor> problemDescriptors) {
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
     * Removes all problem descriptors for the given file by scanner type.
     *
     * @param filePath   - virtual file path
     * @param scanEngine - scan engine type
     */
    public void removeProblemDescriptorsForFileByScanner(String filePath, ScanEngine scanEngine) {
        if (fileProblemDescriptor.isEmpty() || Objects.isNull(scanEngine) || Objects.isNull(filePath) ||
                filePath.isEmpty() || !fileProblemDescriptor.containsKey(filePath)) {
            return;
        }
        List<ProblemDescriptor> problemDescriptors = fileProblemDescriptor.get(filePath);
        if (Objects.isNull(problemDescriptors) || problemDescriptors.isEmpty()) {
            return;
        }
        problemDescriptors.removeIf(descriptor -> {
            DevAssistFix fix = (DevAssistFix) descriptor.getFixes()[0];
            return fix != null && scanEngine.name().equalsIgnoreCase(fix.getScanIssue().getScanEngine().name());
        });
    }

    /**
     * Merges new problem descriptors into the existing list for the given file path.
     *
     * @param filePath    the file path.
     * @param newProblems the new problem descriptors to add.
     */
    public void mergeProblemDescriptors(String filePath, List<ProblemDescriptor> newProblems) {
        fileProblemDescriptor.compute(filePath, (key, existingProblems) -> {
            // Creating a new combined list to avoid modifying shared references
            List<ProblemDescriptor> updatedList = (Objects.isNull(existingProblems) || existingProblems.isEmpty())
                    ? new ArrayList<>()
                    : new ArrayList<>(existingProblems);
            updatedList.addAll(newProblems);
            return updatedList;
        });
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
