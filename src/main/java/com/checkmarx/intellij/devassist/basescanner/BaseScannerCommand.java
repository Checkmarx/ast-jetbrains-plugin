package com.checkmarx.intellij.devassist.basescanner;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.utils.ScannerUtils;
import com.checkmarx.intellij.devassist.configuration.GlobalScannerController;
import com.checkmarx.intellij.devassist.configuration.ScannerConfig;
import com.checkmarx.intellij.devassist.common.ScannerType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class BaseScannerCommand implements ScannerCommand {
    private static final Logger LOGGER = Utils.getLogger(BaseScannerCommand.class);
    public  ScannerConfig config;

    public BaseScannerCommand(@NotNull Disposable parentDisposable, ScannerConfig config, BaseScannerService<?> service){
        Disposer.register(parentDisposable,this);
        this.config = config;
    }

    private GlobalScannerController global() {
        return ApplicationManager.getApplication().getService(GlobalScannerController.class);
    }

    @Override
    public void register(Project project) {
        boolean isActive = getScannerActivationStatus();
        if (!isActive) {
            return;
        }
        if(isScannerRegisteredAlready(project)){
            return;
        }
        global().markRegistered(project,getScannerType());
        LOGGER.info(config.getEnabledMessage() +":"+project.getName());
        initializeScanner();
    }

    public void deregister(Project project){
        if(!global().isRegistered(project,getScannerType())){
            return;
        }
        global().markUnregistered(project,getScannerType());
        LOGGER.info(config.getDisabledMessage() +":"+project.getName());
    }

    private boolean getScannerActivationStatus(){
        return ScannerUtils.isScannerActive(config.getEngineName());
    }

    private boolean isScannerRegisteredAlready(Project project){
        return global().isRegistered(project,getScannerType());
    }

    protected ScannerType getScannerType(){
        return ScannerType.valueOf(config.getEngineName().toUpperCase());
    }

    @Nullable
    protected VirtualFile findVirtualFile(String path) {
        return LocalFileSystem.getInstance().findFileByPath(path);
    }

    protected void initializeScanner() {
    }

    @Override
    public void dispose() {
    }
}
