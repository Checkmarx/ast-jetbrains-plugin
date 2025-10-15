package com.checkmarx.intellij.realtimeScanners.basescanner;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.realtime.RealtimeScannerManager;
import com.checkmarx.intellij.realtimeScanners.common.debouncer.DebouncerImpl;
import com.checkmarx.intellij.realtimeScanners.common.FileChangeHandler;
import com.checkmarx.intellij.realtimeScanners.configuration.ScannerConfig;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class BaseScannerCommandImpl implements ScannerCommand {

    private final FileChangeHandler handler;
    private static final Logger LOGGER = Utils.getLogger(BaseScannerCommandImpl.class);

    public  ScannerConfig config;
    private final BaseScannerService scannerService;
    private final RealtimeScannerManager scannerManager;

    private final static Map<Project, EnumMap<RealtimeScannerManager.ScannerKind, Boolean>> initializedPerProject = new ConcurrentHashMap<>();
    private final Map<Project, List<DocumentListener>> documentListenersPerProject = new ConcurrentHashMap<>();
    private final Map<Project, MessageBusConnection> projectConnections = new ConcurrentHashMap<>();

    public BaseScannerCommandImpl(@NotNull Disposable parentDisposable, ScannerConfig config,  BaseScannerService service, RealtimeScannerManager realtimeScannerManager){
        Disposer.register(parentDisposable,this);
        this.config=config;
        DebouncerImpl documentDebounce = new DebouncerImpl(this);
        this.handler=  new FileChangeHandler(documentDebounce,1000);
        this.scannerService=service;
        this.scannerManager=realtimeScannerManager;
    }

    @Override
    public void register(Project project) {
        boolean isActive = scannerManager.isScannerActive(config.getEngineName());
        RealtimeScannerManager.ScannerKind kind = RealtimeScannerManager.ScannerKind.valueOf(config.getEngineName().toUpperCase());
        Map<RealtimeScannerManager.ScannerKind, Boolean> perProjectMap = initializedPerProject.computeIfAbsent(project, p -> new EnumMap<>(RealtimeScannerManager.ScannerKind.class));

        if (!isActive) {
            disposeAllProjects();
            LOGGER.info(config.getDisabledMessage());
            return;
        }
        if (Boolean.TRUE.equals(perProjectMap.get(kind))) {
            return;
        }
        perProjectMap.put(kind,true);
        LOGGER.info(config.getEnabledMessage());
        initializeScanner(project);
    }

    protected void initializeScanner(Project project) {
        registerDocumentListeners(project);
        registerFileOpenListener(project);
        ProjectManager.getInstance().addProjectManagerListener(project, new ProjectManagerListener() {
            @Override
            public void projectClosing(Project p) {
                disposeProject(p);
            }
        });
    }

    private void registerDocumentListeners(Project project) {
        List<DocumentListener> listeners = new ArrayList<>();

        for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
            if (isEditorOfProject(editor, project)) {
                listeners.add(attachDocumentListener(editor.getDocument(), project));
            }
        }
        documentListenersPerProject.put(project, listeners);
    }

    public void disposeAllProjects() {
        for (Project project : new ArrayList<>(initializedPerProject.keySet())) {
            disposeProject(project);
        }
    }

    public void disposeProject(Project project) {
        MessageBusConnection conn = projectConnections.remove(project);
        if (conn != null) conn.disconnect();
        List<DocumentListener> docListeners = documentListenersPerProject.remove(project);
        if (docListeners != null) {
            for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
                if (isEditorOfProject(editor, project)) {
                    docListeners.forEach(editor.getDocument()::removeDocumentListener);
                }
            }
        }
        initializedPerProject.remove(project);
    }

    @Override
    public void dispose() {
        this.handler.dispose();
        for (Project project : projectConnections.keySet()) {
            disposePerProjectConnection(project);
        }
        projectConnections.clear();
    }

    private void registerFileOpenListener(Project project) {
        if (projectConnections.containsKey(project)) return;

        MessageBusConnection connection = project.getMessageBus().connect();
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override
            public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                if (!scannerManager.isScannerActive(config.getEngineName())) return;
                Document document = getDocument(file);
                if (document != null && isDocumentOfProject(document, project)) {
                    attachDocumentListener(document, project);
                    LOGGER.info("File Opened" + file.getPath());
                    scannerService.scan(document, file.getPath());
                }
            }
        });
        projectConnections.put(project, connection);
    }

    private boolean isDocumentOfProject(Document document, Project project) {
        VirtualFile file = getVirtualFile(document);
        return file != null && ProjectUtil.guessProjectForFile(file) == project;
    }



    private boolean isEditorOfProject(Editor editor, Project project) {
        VirtualFile file = getVirtualFile(editor.getDocument());
        return file != null && ProjectUtil.guessProjectForFile(file) == project;
    }

    private DocumentListener attachDocumentListener(Document document, Project project) {
        DocumentListener listener = new DocumentListener() {
            @Override
            public void documentChanged(@NotNull DocumentEvent event) {
                if (!scannerManager.isScannerActive(config.getEngineName())) return;
                handler.onTextChanged(Objects.requireNonNull(getPath(document).orElse(null)), () -> {
                    LOGGER.info("Text changed");
                    scannerService.scan(document, getPath(document).orElse(""));
                });
            }
        };
        document.addDocumentListener(listener);
        documentListenersPerProject.computeIfAbsent(project, p -> new ArrayList<>()).add(listener);
        return listener;
    }


    private Optional<String> getPath(Document document) {
        VirtualFile file = getVirtualFile(document);
        return file != null ? Optional.of(file.getPath()) : Optional.empty();
    }

    protected  Document getDocument( @NotNull VirtualFile file ){
        return FileDocumentManager.getInstance().getDocument(file);
    }

    protected   VirtualFile getVirtualFile( @NotNull Document doc ){
        return FileDocumentManager.getInstance().getFile(doc);
    }

    @Nullable
    protected VirtualFile findVirtualFile(String path) {
        return LocalFileSystem.getInstance().findFileByPath(path);
    }

    private  void disposePerProjectConnection(Project project){
     MessageBusConnection conn= projectConnections.get(project);
      if(conn!=null){
        conn.disconnect();
      }
      initializedPerProject.remove(project);
    }

}
