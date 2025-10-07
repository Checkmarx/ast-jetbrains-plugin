package com.checkmarx.intellij.service;

import com.checkmarx.intellij.tool.window.adapters.VulnerabilityIssue;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;

import java.util.*;

@Service(Service.Level.PROJECT)
public final class ProblemHolderService {

    private final Map<String, List<VulnerabilityIssue>> fileToIssues = new HashMap<>();

    public static final Topic<IssueListener> ISSUE_TOPIC =
            new Topic<>("ASCA_ISSUES_UPDATED", IssueListener.class);
    public interface IssueListener {
        void onIssuesUpdated(Map<String, List<VulnerabilityIssue>> issues);
    }

    private final Project project;

    public ProblemHolderService(Project project) {
        this.project = project;
    }

    public static ProblemHolderService getInstance(Project project) {
        return project.getService(ProblemHolderService.class);
    }

    public synchronized void addProblems(String filePath, List<VulnerabilityIssue> problems) {
        fileToIssues.put(filePath, new ArrayList<>(problems));
        // Notify subscribers immediately
        project.getMessageBus().syncPublisher(ISSUE_TOPIC).onIssuesUpdated(getAllIssues());
    }

    public synchronized Map<String, List<VulnerabilityIssue>> getAllIssues() {
        return Collections.unmodifiableMap(fileToIssues);
    }

}

