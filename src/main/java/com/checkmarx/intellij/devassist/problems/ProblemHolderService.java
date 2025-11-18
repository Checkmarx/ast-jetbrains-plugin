package com.checkmarx.intellij.devassist.problems;

import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.messages.Topic;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing problems and problem descriptors.
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
     * @param project the project.
     * @return the instance of this service for the given project.
     */
    public static ProblemHolderService getInstance(Project project) {
        return project.getService(ProblemHolderService.class);
    }

    /**
     * Adds problems for the given file.
     * @param filePath the file path.
     * @param problems the scan issues.
     */
    public synchronized void addProblems(String filePath, List<ScanIssue> problems) {
        fileToIssues.put(filePath, new ArrayList<>(problems));
        // Notify subscribers immediately
        project.getMessageBus().syncPublisher(ISSUE_TOPIC).onIssuesUpdated(getAllIssues());
    }

    public synchronized Map<String, List<ScanIssue>> getAllIssues() {
        return Collections.unmodifiableMap(fileToIssues);
    }

    public void removeAllProblemsOfType(String scannerType) {
        for (Map.Entry<String, List<ScanIssue>> entry : getAllIssues().entrySet()) {
            List<ScanIssue> problems = entry.getValue();
            if (problems != null) {
                problems.removeIf(problem -> scannerType.equals(problem.getScanEngine().name()));
            }
        }
        project.getMessageBus().syncPublisher(ISSUE_TOPIC).onIssuesUpdated(getAllIssues());
    }

    /**
     * Returns the scan issues for the given file.
     * @param filePath the file path.
     * @return the scan issues.
     */
    public synchronized List<ScanIssue> getScanIssueByFile(String filePath) {
        return fileToIssues.getOrDefault(filePath, Collections.emptyList());
    }

    /**
     * Returns the problem descriptors for the given file.
     * @param filePath the file path.
     * @return the problem descriptors.
     */
    public List<ProblemDescriptor> getProblemDescriptors(String filePath) {
        return fileProblemDescriptor.getOrDefault(filePath, Collections.emptyList());
    }

    /**
     * Adds problem descriptors for the given file.
     * @param filePath the file path.
     * @param problemDescriptors the problem descriptors.
     */
    public void addProblemDescriptors(String filePath, List<ProblemDescriptor> problemDescriptors) {
        fileProblemDescriptor.put(filePath, new ArrayList<>(problemDescriptors));
    }

    /**
     * Removes all problem descriptors for the given file.
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
     * @param file the PSI file.
     * @param problemsList the list of problems.
     */
    public static void addToCxOneFindings(PsiFile file, List<ScanIssue> problemsList) {
        getInstance(file.getProject()).addProblems(file.getVirtualFile().getPath(), problemsList);
    }
}
