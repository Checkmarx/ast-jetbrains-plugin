package com.checkmarx.intellij.service;

import com.checkmarx.intellij.realtimeScanners.dto.CxProblems;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;

import java.util.*;

@Service(Service.Level.PROJECT)
public final class ProblemHolderService {
                    //ProblemHolderService

    private final Map<String, List<CxProblems>> fileToIssues = new HashMap<>();

    public static final Topic<IssueListener> ISSUE_TOPIC =
            new Topic<>("ASCA_ISSUES_UPDATED", IssueListener.class);
    public interface IssueListener {
        void onIssuesUpdated(Map<String, List<CxProblems>> issues);
    }

    private final Project project;

    public ProblemHolderService(Project project) {
        this.project = project;
    }

    public static ProblemHolderService getInstance(Project project) {
        return project.getService(ProblemHolderService.class);
    }

    public synchronized void addProblems(String filePath, List<CxProblems> problems) {
        fileToIssues.put(filePath, new ArrayList<>(problems));
        // Notify subscribers immediately
        project.getMessageBus().syncPublisher(ISSUE_TOPIC).onIssuesUpdated(getAllIssues());
    }

    public synchronized Map<String, List<CxProblems>> getAllIssues() {
        return Collections.unmodifiableMap(fileToIssues);
    }

}

