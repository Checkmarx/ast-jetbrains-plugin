package com.checkmarx.intellij.realtimeScanners.basescanner;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.realtimeScanners.configuration.GlobalScannerController;
import com.checkmarx.intellij.realtimeScanners.configuration.RealtimeScannerManager;
import com.checkmarx.intellij.realtimeScanners.configuration.ScannerConfig;
import com.checkmarx.intellij.realtimeScanners.common.ScannerKind;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;



public class BaseScannerCommand implements ScannerCommand {


    private static final Logger LOGGER = Utils.getLogger(BaseScannerCommand.class);

    public  ScannerConfig config;
    private final BaseScannerService scannerService;
    private final RealtimeScannerManager scannerManager;


    public BaseScannerCommand(@NotNull Disposable parentDisposable, ScannerConfig config, BaseScannerService service, RealtimeScannerManager realtimeScannerManager){
        Disposer.register(parentDisposable,this);
        this.config = config;
        this.scannerService = service;
        this.scannerManager = realtimeScannerManager;
    }

    private GlobalScannerController global() {
        return ApplicationManager.getApplication().getService(GlobalScannerController.class);
    }

    @Override
    public void register(Project project) {
        boolean isActive = getScannerActivationStatus();
        ScannerKind kind = ScannerKind.valueOf(config.getEngineName().toUpperCase());

        if (!isActive) {
           // disposeScannerForAllProjects(kind);
            global().markUnregistered(project, kind);
            LOGGER.info(config.getDisabledMessage() +":"+project.getName());
            return;
        }
        if(global().isRegistered(project,kind)){
            return;
        }
        LOGGER.info(config.getEnabledMessage() +":"+project.getName());
        initializeScanner(project);
        global().markRegistered(project,kind);
    }

    private boolean getScannerActivationStatus(){
        return scannerManager.isScannerActive(config.getEngineName());
    }



    @Nullable
    protected VirtualFile findVirtualFile(String path) {
        return LocalFileSystem.getInstance().findFileByPath(path);
    }

    protected void initializeScanner(Project project) {

//        ProjectManager.getInstance().addProjectManagerListener(project, new ProjectManagerListener() {
//        });
    }



   /* private void registerDocumentListeners(Project project) {
        List<DocumentListener> listeners = new ArrayList<>();

        for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
            if (isEditorOfProject(editor, project)) {
                listeners.add(attachDocumentListener(editor.getDocument(), project));
            }
        }
        documentListeners.put(project, listeners);
    }


    private void registerFileOpenListener(Project project) {
        if (scannerConnections.containsKey(project)) return;
        MessageBusConnection connection = project.getMessageBus().connect(project);
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override
            public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                if(!getScannerActivationStatus()){
                    return;
                }
                Document document = getDocument(file);
                if (document != null && isDocumentOfProject(document, project)) {
                    attachDocumentListener(document, project);
                    ReadAction.nonBlocking(() -> scannerService.scan(document, file.getPath())).inSmartMode(project).expireWith(project).submit(com.intellij.util.concurrency.AppExecutorUtil.getAppExecutorService());
                    LOGGER.info("File Opened " + file.getPath());
                }
            }
        });
        scannerConnections.put(project, connection);
    }


    private boolean isDocumentOfProject(Document document, Project project) {
        return ReadAction.compute(() ->
                PsiDocumentManager.getInstance(project).getPsiFile(document) != null
        );
    }

    private boolean isEditorOfProject(Editor editor, Project project) {
        VirtualFile file = getVirtualFile(editor.getDocument());
        if (file == null) return false;
        return ProjectFileIndex.getInstance(project).isInContent(file);
    }

    private DocumentListener attachDocumentListener(Document document, Project project) {
        DocumentListener listener = new DocumentListener() {
            @Override
            public void documentChanged(@NotNull DocumentEvent event) {
                if(!getScannerActivationStatus()){
                    return;
                }
                Optional<String>documentOpt= getPath(document);
                if(documentOpt.isEmpty()){
                    return;
                }
                String documentPath= documentOpt.get();

                handler.onTextChanged(documentPath, () -> {
                    LOGGER.info("Text changed");
                    scannerService.scan(document, getPath(document).orElse(""));
                });
            }
        };
        document.addDocumentListener(listener);
        documentListeners.computeIfAbsent(project, p -> new ArrayList<>()).add(listener);
        return listener;
    }

    private Optional<String> getPath(Document document) {
        VirtualFile file = getVirtualFile(document);
        return file != null ? Optional.of(file.getPath()) : Optional.empty();
    }

    protected  Document getDocument( @NotNull VirtualFile file ){
        return FileDocumentManager.getInstance().getDocument(file);
    }


    public void disposeScannerForAllProjects(RealtimeScannerManager.ScannerKind kind) {
        for(Project project: ProjectManager.getInstance().getOpenProjects()) {
            disposeScannerListener(project);
        }
    }

    public void disposeScannerListener(Project project) {
        MessageBusConnection conn = scannerConnections.remove(project);
        if (conn != null) conn.disconnect();
        List<DocumentListener> docListeners = documentListeners.remove(project);
        if (docListeners != null) {
            for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
                if (isEditorOfProject(editor, project)) {
                    docListeners.forEach(editor.getDocument()::removeDocumentListener);
                }
            }
        }
    }*/

    @Override
    public void dispose() {

    }
}
