package com.checkmarx.intellij.realtimeScanners.basescanner;

import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.realtime.RealtimeScannerManager;
import com.checkmarx.intellij.realtimeScanners.common.debouncer.DebouncerImpl;
import com.checkmarx.intellij.realtimeScanners.common.FileChangeHandler;
import com.checkmarx.intellij.realtimeScanners.configuration.ScannerConfig;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
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
    private static final Map<Project, Map<RealtimeScannerManager.ScannerKind, Boolean>> initializedPerProject = new ConcurrentHashMap<>();
    private final  Map<Project,MessageBusConnection>projectConnections= new ConcurrentHashMap<>();
    private final Map<Project, List<DocumentListener>> documentListenersPerProject = new ConcurrentHashMap<>();
    private final Map<Project, EditorFactoryListener> editorFactoryListenersPerProject = new ConcurrentHashMap<>();


    public BaseScannerCommandImpl(@NotNull Disposable parentDisposable, ScannerConfig config,  BaseScannerService service, RealtimeScannerManager realtimeScannerManager){
        Disposer.register(parentDisposable,this);
        this.config=config;
        DebouncerImpl documentDebounce = new DebouncerImpl(this);
        this.handler=  new FileChangeHandler(documentDebounce,1000);
        this.scannerService=service;
        this.scannerManager=realtimeScannerManager;
    }

    @Override
    public void register(Project project){
        boolean isActive=this.scannerManager.isScannerActive(config.getEngineName());
        boolean isInitialized = initializedPerProject.computeIfAbsent(project, p -> new EnumMap<>(RealtimeScannerManager.ScannerKind.class))
                .getOrDefault(RealtimeScannerManager.ScannerKind.valueOf(config.getEngineName().toUpperCase()), false);
        LOGGER.info("the value of isActive-->"+isActive);

        if(!isActive){
            this.disposeAllProjects();
            LOGGER.info(config.getDisabledMessage());
            return;
        }

        if(!isInitialized) {
            LOGGER.info(config.getEnabledMessage());
            this.initializeScanner(project);
            initializedPerProject.get(project).put(RealtimeScannerManager.ScannerKind.valueOf(config.getEngineName().toUpperCase()), true);

        }
    }

    public void disposeAllProjects(){
        LOGGER.info("Disposing scanner for all projects");
        for (Project project : new ArrayList<>(initializedPerProject.keySet())) {
            disposeProject(project);
        }
        initializedPerProject.clear();
        projectConnections.clear();
        documentListenersPerProject.clear();
        editorFactoryListenersPerProject.clear();
    }


    public void disposeProject(Project project){
        disposePerProjectConnection(project);

        List<DocumentListener> listeners = documentListenersPerProject.remove(project);
        if (listeners != null) {
            for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
                for (DocumentListener listener : listeners) {
                    editor.getDocument().removeDocumentListener(listener);
                }
            }
        }

        editorFactoryListenersPerProject.remove(project);
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

    protected void initializeScanner(Project project){
        this.registerScanOnChangeText(project);
        this.registerScanOnFileOpen(project);
    }

    protected void registerScanOnFileOpen(Project project){
        if(projectConnections.containsKey(project)) return;
        MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();

        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override
            public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                try {
                    Document document=getDocument(file);
                    String path = file.getPath();
                    if (document != null && path!=null) {
                        LOGGER.info("File opened");
                        scannerService.scan(document,path);
                    }
                } catch (Exception e) {
                    LOGGER.warn(e);
                }
            }
        });
        projectConnections.put(project,connection);
    }
    protected void registerScanOnChangeText(Project project) {
        List<DocumentListener> listeners = new ArrayList<>();

        for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
            DocumentListener listener = attachDocumentListener(editor);
            listeners.add(listener);
        }

        EditorFactoryListener editorFactoryListener = new EditorFactoryListener() {
            @Override
            public void editorCreated(@NotNull EditorFactoryEvent event) {
                DocumentListener listener = attachDocumentListener(event.getEditor());
                listeners.add(listener);
            }

            @Override
            public void editorReleased(@NotNull EditorFactoryEvent event) {
                Document document = event.getEditor().getDocument();
                listeners.removeIf(listener -> {
                    document.removeDocumentListener(listener);
                    return true;
                });
            }
        };

        // Register listener
        EditorFactory.getInstance().addEditorFactoryListener(editorFactoryListener, project);

        // Store references
        documentListenersPerProject.put(project, listeners);
        editorFactoryListenersPerProject.put(project, editorFactoryListener);
    }

    private DocumentListener  attachDocumentListener(Editor editor){
        Document document = editor.getDocument();
        DocumentListener listener = new DocumentListener() {
            @Override
            public void documentChanged(@NotNull DocumentEvent event) {
                try {
                    String uri = getPath(document).orElse(null);
                    if(uri==null){
                        return;
                    }
                    handler.onTextChanged(uri,()->{
                        LOGGER.info("Text Changed");
                      scannerService.scan(document,uri);
                    });
                }
                catch (Exception e){
                    LOGGER.warn(e);
                }
            }
        };
        document.addDocumentListener(listener, this);
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
