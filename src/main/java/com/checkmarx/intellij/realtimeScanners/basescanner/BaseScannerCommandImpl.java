package com.checkmarx.intellij.realtimeScanners.basescanner;

import com.checkmarx.intellij.Utils;
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
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import java.util.Optional;


public class BaseScannerCommandImpl implements ScannerCommand {

    private final FileChangeHandler handler;
    private static final Logger LOGGER = Utils.getLogger(BaseScannerCommandImpl.class);
    private MessageBusConnection connection;
    public  ScannerConfig config;

    public BaseScannerCommandImpl(@NotNull Disposable parentDisposable, ScannerConfig config){
        Disposer.register(parentDisposable,this);
        this.config=config;
        DebouncerImpl documentDebounce = new DebouncerImpl(this);
        this.handler=  new FileChangeHandler(documentDebounce,1000);
    }

    @Override
    public void register(){
       LOGGER.info(config.getEnabledMessage());
       this.initializeScanner();
    }

    @Override
    public void dispose() {
       this.handler.dispose();
       if(connection!=null){
           connection.disconnect();
           connection=null;
       }
    }

    protected void initializeScanner(){
        this.registerScanOnChangeText();
        this.registerScanOnFileOpen();
    }

    protected void registerScanOnFileOpen(){
        connection= ApplicationManager.getApplication().getMessageBus().connect();
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override
            public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                try {
                    Document document=getDocument(file);
                    if (document != null) {
                        // TODO: Add the logic here
                       LOGGER.info("File opened");
                    }
                } catch (Exception e) {
                    LOGGER.warn(e);
                }
            }
        });
    }
    protected void registerScanOnChangeText(){
         for(Editor editor: EditorFactory.getInstance().getAllEditors()){
             attachDocumentListener(editor);
         }
        EditorFactory.getInstance().addEditorFactoryListener(new EditorFactoryListener() {
            @Override
            public void editorCreated( EditorFactoryEvent event) {
                attachDocumentListener(event.getEditor());
            }
        }, this);
    }

    private void attachDocumentListener(Editor editor){
        Document document=editor.getDocument();
        document.addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(@NotNull DocumentEvent event) {
                try {
                    String uri = getUri(document).orElse(null);
                    if(uri==null){
                        return;
                    }
                    handler.onTextChanged(uri,()->{
                        LOGGER.info("Text changed--> "+uri);
                    });
                }
                catch (Exception e){
                    LOGGER.warn(e);
                }
            }
        },this);
    }

    private Optional<String> getUri(Document document) {
        VirtualFile file = getVirtualFile(document);
        return file != null ? Optional.of(file.getUrl()) : Optional.empty();
    }

    private  Document getDocument( @NotNull VirtualFile file ){
        return FileDocumentManager.getInstance().getDocument(file);
    }

    private  VirtualFile getVirtualFile( @NotNull Document doc ){
        return FileDocumentManager.getInstance().getFile(doc);
    }
}
