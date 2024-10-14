package com.checkmarx.intellij.listeners;

import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.settings.global.GlobalSettingsComponent;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.util.Timer;
import java.util.TimerTask;

public class AscaFileEditorListener implements FileEditorManagerListener {

    private static final Logger LOGGER = Utils.getLogger(AscaFileEditorListener.class);
    private Document currentDocument;
    private final Timer timer = new Timer();
    private TimerTask pendingTask;

    @Override
    public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        GlobalSettingsState globalSettings = GlobalSettingsState.getInstance();
        if (!globalSettings.isAsca()) {
            return;
        }
        Project project = event.getManager().getProject();
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();

        if (editor != null) {
            Document document = editor.getDocument();
            VirtualFile virtualFile = FileEditorManager.getInstance(project).getSelectedFiles()[0];

            // Reset the listener if the document changes
            if (currentDocument != document) {
                currentDocument = document;
                registerDocumentListener(document, virtualFile, project);
            }
        }
    }

    private void registerDocumentListener(Document document, VirtualFile virtualFile, Project project) {
        document.addDocumentListener(new com.intellij.openapi.editor.event.DocumentListener() {
            @Override
            public void documentChanged(@NotNull com.intellij.openapi.editor.event.DocumentEvent event) {
                if (pendingTask != null) {
                    pendingTask.cancel();
                }

                pendingTask = new TimerTask() {
                    @Override
                    public void run() {
                        triggerInspection(virtualFile, project);
                    }
                };

                timer.schedule(pendingTask, 2000);
            }
        });
    }

    private void triggerInspection(VirtualFile virtualFile, Project project) {
        // Ensure the PSI file access is done inside a read action
        ApplicationManager.getApplication().runReadAction(() -> {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);

            if (psiFile != null) {
                LOGGER.info("Triggering ASCA inspection for file: " + virtualFile.getPath());

                // Trigger the inspection by restarting the DaemonCodeAnalyzer for the file
                //DaemonCodeAnalyzer.getInstance(project).restart(psiFile);
            }
        });
    }

}
