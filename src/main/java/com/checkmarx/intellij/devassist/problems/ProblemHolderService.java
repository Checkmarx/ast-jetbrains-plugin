package com.checkmarx.intellij.devassist.problems;

import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service(Service.Level.PROJECT)
public final class ProblemHolderService {
    // ProblemHolderService

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

    public static ProblemHolderService getInstance(Project project) {
        return project.getService(ProblemHolderService.class);
    }

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
                problems.removeIf(problem -> scannerType.equals(problem.getScanEngine()));
            }
        }
        project.getMessageBus().syncPublisher(ISSUE_TOPIC).onIssuesUpdated(getAllIssues());
    }

    public synchronized List<ProblemDescriptor> getProblemDescriptors(String filePath) {
        return fileProblemDescriptor.getOrDefault(filePath, Collections.emptyList());
    }

    public synchronized void addProblemDescriptors(String filePath, List<ProblemDescriptor> problemDescriptors) {
        fileProblemDescriptor.put(filePath, new ArrayList<>(problemDescriptors));
    }

}
