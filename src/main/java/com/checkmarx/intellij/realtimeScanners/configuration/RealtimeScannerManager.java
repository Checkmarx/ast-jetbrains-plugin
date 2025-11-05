package com.checkmarx.intellij.realtimeScanners.configuration;

import com.checkmarx.intellij.realtimeScanners.registry.ScannerRegistry;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.checkmarx.intellij.realtimeScanners.common.ScannerKind;
import org.jetbrains.annotations.NotNull;


/**
 * Manager: starts/stops realtime scanners based on settings toggles.
 * Uses ConfigurationManager to listen to specific realtime checkbox changes
 */

@Service(Service.Level.PROJECT)
public  final class RealtimeScannerManager implements Disposable {
    private final Project project;
    private final ScannerRegistry registry;

    public RealtimeScannerManager(@NotNull Project project) {
        this.project = project;
        this.registry= new ScannerRegistry(project,this,this);
    }

    private GlobalScannerController global() {
        return ApplicationManager.getApplication().getService(GlobalScannerController.class);
    }

    public synchronized void updateFromGlobal(@NotNull GlobalScannerController controller) {
        for (ScannerKind kind : ScannerKind.values()) {
            boolean shouldBeEnabled = controller.isScannerGloballyEnabled(kind);
            if (shouldBeEnabled) start(kind);
            else stop(kind);
        }
    }

    public void start(ScannerKind kind) {
        registry.registerScanner(kind.name());
    }

    public void stop(ScannerKind kind) {
        registry.deregisterScanner(kind.name());
    }

    public void stopAll() {
        for (ScannerKind kind : ScannerKind.values()) {
            stop(kind);
        }
    }

    public boolean isScannerActive(String engineName) {
        if (engineName == null) return false;
        try {
            ScannerKind kind = ScannerKind.valueOf(engineName.toUpperCase());
            return global().isScannerGloballyEnabled(kind);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    @Override
    public void dispose() {
        stopAll();
    }
}