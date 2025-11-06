package com.checkmarx.intellij.realtimeScanners.basescanner;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.realtimeScanners.common.ScannerUtils;
import com.checkmarx.intellij.realtimeScanners.configuration.GlobalScannerController;
import com.checkmarx.intellij.realtimeScanners.configuration.RealtimeScannerManager;
import com.checkmarx.intellij.realtimeScanners.configuration.ScannerConfig;
import com.checkmarx.intellij.realtimeScanners.common.ScannerKind;
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
        ScannerKind kind = ScannerKind.valueOf(config.getEngineName().toUpperCase());
        if (!isActive) {
            return;
        }
        if(global().isRegistered(project,kind)){
            return;
        }

        LOGGER.info(config.getEnabledMessage() +":"+project.getName());
        initializeScanner(project);
        global().markRegistered(project,kind);
    }

    public void deregister(Project project){
        ScannerKind kind = ScannerKind.valueOf(config.getEngineName().toUpperCase());
        if(!global().isRegistered(project,kind)){
            return;
        }
        global().markUnregistered(project, kind);
        LOGGER.info(config.getDisabledMessage() +":"+project.getName());
    }

    private boolean getScannerActivationStatus(){
        return ScannerUtils.isScannerActive(config.getEngineName());
    }

    @Nullable
    protected VirtualFile findVirtualFile(String path) {
        return LocalFileSystem.getInstance().findFileByPath(path);
    }

    protected void initializeScanner(Project project) {
    }

    @Override
    public void dispose() {
    }
}
