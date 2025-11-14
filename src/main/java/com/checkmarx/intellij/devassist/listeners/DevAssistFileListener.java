package com.checkmarx.intellij.devassist.listeners;

import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.problems.ProblemDecorator;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * DevAssistFileListener is responsible for listening for file open events and restoring
 */
public class DevAssistFileListener {

    private static final ProblemDecorator PROBLEM_DECORATOR_INSTANCE = new ProblemDecorator();

    private DevAssistFileListener() {
        // Private constructor to prevent instantiation
    }

    public static void register(Project project) {
        MessageBusConnection connection = project.getMessageBus().connect();
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override
            public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                Project project = source.getProject();
                String path = file.getPath();
                PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
                restoreGutterIcons(project, psiFile, path);
            }

            @Override
            public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                removeProblemDescriptor(source.getProject(), file.getPath());
            }
        });
    }

    /**
     * Restores problems for the given file.
     *
     * @param project  the project
     * @param psiFile  the psi file
     * @param filePath the file path
     */
    private static void restoreGutterIcons(Project project, PsiFile psiFile, String filePath) {
        if (psiFile == null) return;

        ProblemHolderService problemHolderService = ProblemHolderService.getInstance(project);

        List<ProblemDescriptor> problemDescriptorList = problemHolderService.getProblemDescriptors(filePath);
        if (problemDescriptorList.isEmpty()) {
            return;
        }
        Map<String, List<ScanIssue>> scanIssuesMap = ProblemHolderService.getInstance(project).getAllIssues();
        if (scanIssuesMap.isEmpty()) return;

        List<ScanIssue> scanIssueList = scanIssuesMap.getOrDefault(filePath, List.of());
        if (scanIssueList.isEmpty()) return;

        Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
        if (document == null) return;
        PROBLEM_DECORATOR_INSTANCE.restoreGutterIcons(project, psiFile, scanIssueList);
    }

    /**
     * Removes all problem descriptors for the given file.
     * @param project the project
     * @param path the file path
     */
    public static void removeProblemDescriptor(Project project, String path) {
        if (Objects.isNull(path) || path.isEmpty()) return;
        ProblemHolderService.getInstance(project).removeProblemDescriptorsForFile(path);
    }
}