package com.checkmarx.intellij.devassist.basescanner;

import com.checkmarx.intellij.common.utils.Utils;
import com.checkmarx.intellij.devassist.configuration.ScannerConfig;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * BaseScannerCommand is an abstract implementation of the ScannerCommand interface
 * that provides foundational functionality for registering, deregistering, and
 * managing a scanner's lifecycle for a given project. This class serves as a
 * base implementation for custom scanner commands.
 */
public abstract class BaseScannerCommand implements ScannerCommand {
    private static final Logger LOGGER = Utils.getLogger(BaseScannerCommand.class);
    public ScannerConfig config;

    protected BaseScannerCommand(@NotNull Disposable parentDisposable, ScannerConfig config) {
        Disposer.register(parentDisposable, this);
        this.config = config;
    }


    /**
     * Registers the project for the scanner which is invoked
     *
     * @param project - the project for the registration
     */

    @Override
    public void register(Project project) {
        boolean isActive = getScannerActivationStatus();
        if (!isActive) {
            return;
        }
        if (isScannerRegisteredAlready(project)) {
            return;
        }
        DevAssistUtils.globalScannerController().markRegistered(project, getScannerType());
        LOGGER.info(config.getEnabledMessage() + ":" + project.getName());
        initializeScanner();
    }

    /**
     * De-registers the project for the scanner ,
     * This method is called in two cases, either project is closed by the user, or scanner is disabled
     *
     * @param project - the project that is registered
     */

    public void deregister(Project project) {
        if (!DevAssistUtils.globalScannerController().isRegistered(project, getScannerType())) {
            return;
        }
        DevAssistUtils.globalScannerController().markUnregistered(project, getScannerType());
        LOGGER.info(config.getDisabledMessage() + ":" + project.getName());
        if (project.isDisposed()) {
            return;
        }
        ProblemHolderService.getInstance(project)
                .removeAllScanIssuesOfType(getScannerType().toString());
    }


    /**
     * Returns the scanner activationStatus of the scanner engine
     */
    private boolean getScannerActivationStatus() {
        return DevAssistUtils.isScannerActive(config.getEngineName());
    }


    /**
     * Checks if the scanner is registered already for the project
     *
     * @param project is required
     */
    private boolean isScannerRegisteredAlready(Project project) {
        return DevAssistUtils.globalScannerController().isRegistered(project, getScannerType());
    }


    /**
     * This method returns the ScanEngine Type
     *
     * @return ScanEngine
     */
    protected ScanEngine getScannerType() {
        return ScanEngine.valueOf(config.getEngineName().toUpperCase());
    }

    @Nullable
    protected VirtualFile findVirtualFile(String path) {
        return LocalFileSystem.getInstance().findFileByPath(path);
    }

    /**
     * Abstract method to initialize the scanner
     * This method is invoked when the scanner is registered for the project
     */
    protected abstract void initializeScanner();

    @Override
    public void dispose() {
    }
}
