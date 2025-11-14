package com.checkmarx.intellij.devassist.listeners;

import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.problems.ProblemDecorator;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * FileOpenListener class responsible to restore problems after file is opened.
 */
public class FileOpenListener implements FileEditorManagerListener {

    private final ProblemDecorator problemDecorator = new ProblemDecorator();

    @Override
    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        Project project = source.getProject();
        String path = file.getPath();
        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        restoreGutterIcons(project, psiFile, path);
    }

    /**
     * Restores problems for the given file.
     *
     * @param project  the project
     * @param psiFile  the psi file
     * @param filePath the file path
     */
    private void restoreGutterIcons(Project project, PsiFile psiFile, String filePath) {
        if (psiFile == null) return;

        Map<String, List<ScanIssue>> scanIssuesMap = ProblemHolderService.getInstance(project).getAllIssues();
        if (scanIssuesMap.isEmpty()) return;

        List<ScanIssue> scanIssueList = scanIssuesMap.getOrDefault(filePath, List.of());
        if (scanIssueList.isEmpty()) return;

        Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
        if (document == null) return;
        problemDecorator.restoreGutterIcons(project, psiFile, scanIssueList);
    }
}