package com.checkmarx.intellij.devassist.listeners;

import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.configuration.GlobalScannerController;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.problems.ProblemDecorator;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
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
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * DevAssistFileListener is responsible for listening for file open events and restoring
 */
public class DevAssistFileListener {

    private static final Logger LOGGER = Utils.getLogger(DevAssistFileListener.class);
    private static final ProblemDecorator PROBLEM_DECORATOR_INSTANCE = new ProblemDecorator();

    private DevAssistFileListener() {
        // Private constructor to prevent instantiation
    }

    /**
     * Registers the file listener to the given project.
     *
     * @param project the project
     */
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

        List<ScanEngine> enabledScanEngines = GlobalScannerController.getInstance().getEnabledScanners();
        if (enabledScanEngines.isEmpty()) {
            LOGGER.warn(format("RTS-Listener: No scanner is enabled, skipping restoring gutter icons for file: %s", psiFile.getName()));
            return;
        }
        ProblemHolderService problemHolderService = ProblemHolderService.getInstance(project);

        List<ProblemDescriptor> problemDescriptorList = problemHolderService.getProblemDescriptors(filePath);
        if (problemDescriptorList.isEmpty()) {
            return;
        }
        Map<String, List<ScanIssue>> scanIssuesMap = ProblemHolderService.getInstance(project).getAllIssues();
        if (scanIssuesMap.isEmpty()) return;

        List<ScanIssue> scanIssueList = scanIssuesMap.getOrDefault(filePath, List.of());
        if (scanIssueList.isEmpty()) return;

        List<ScanIssue> enabledEngineScanIssues = getScanIssuesForEnabledScanner(enabledScanEngines, scanIssueList);
        if (enabledEngineScanIssues.isEmpty()) return;

        Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
        if (document == null) return;
        PROBLEM_DECORATOR_INSTANCE.restoreGutterIcons(project, psiFile, enabledEngineScanIssues, document);
    }

    /**
     * Filters the given scan issue list for the enabled scanner.
     *
     * @param enabledScanEngines - list of enabled scanner
     * @param scanIssueList      - list of scan issue
     * @return - filtered list of scan issue
     */
    private static List<ScanIssue> getScanIssuesForEnabledScanner(List<ScanEngine> enabledScanEngines, List<ScanIssue> scanIssueList) {
        return scanIssueList.stream()
                .filter(scanIssue -> enabledScanEngines.stream()
                        .anyMatch(engine -> scanIssue.getScanEngine().equals(engine)))
                .collect(Collectors.toList());
    }

    /**
     * Removes all problem descriptors for the given file.
     *
     * @param project the project
     * @param path    the file path
     */
    public static void removeProblemDescriptor(Project project, String path) {
        if (Objects.isNull(path) || path.isEmpty()) return;
        ProblemHolderService.getInstance(project).removeProblemDescriptorsForFile(path);
    }
}