package com.checkmarx.intellij.devassist.configuration;

import com.checkmarx.intellij.devassist.registry.ScannerRegistry;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * ScannerLifeCycleManager is Project level service i.e it is distinct for each project
 * Manages the Lifecycle of Scanner for the project
 * Triggers the Start and Stop of the Scanner for  Project based on global settings
 */

@Service(Service.Level.PROJECT)
public final class ScannerLifeCycleManager implements Disposable {

    @Getter
    private final Project project;


    public ScannerLifeCycleManager(@NotNull Project project) {
        this.project = project;
    }

    private ScannerRegistry scannerRegistry() {
        return this.project.getService(ScannerRegistry.class);
    }

    public synchronized void updateFromGlobal(@NotNull GlobalScannerController controller) {
        for (ScanEngine kind : ScanEngine.values()) {
            boolean isEnabled = controller.isScannerGloballyEnabled(kind);
            if (isEnabled) start(kind);
            else stop(kind);
        }
    }

    public void start(ScanEngine kind) {
        scannerRegistry().registerScanner(kind.name());
    }

    public void stop(ScanEngine kind) {
        scannerRegistry().deregisterScanner(kind.name());
    }

    public void stopAll() {
        for (ScanEngine kind : ScanEngine.values()) {
            stop(kind);
        }
    }

    @Override
    public void dispose() {
        stopAll();
    }
}