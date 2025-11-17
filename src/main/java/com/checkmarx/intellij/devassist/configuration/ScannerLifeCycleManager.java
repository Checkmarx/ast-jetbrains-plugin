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

@Getter
@Service(Service.Level.PROJECT)
public final class ScannerLifeCycleManager implements Disposable {

    private final Project project;


    /**
     * Stores the owning project so scanners can be registered/deregistered against it.
     *
     * @param project IntelliJ project that this lifecycle manager serves
     */
    public ScannerLifeCycleManager(@NotNull Project project) {
        this.project = project;
    }


    /**
     * Retrieves the project-scoped {@link ScannerRegistry} used to manage scanner commands.
     *
     * @return scanner registry for the current project
     */
    private ScannerRegistry scannerRegistry() {
        return this.project.getService(ScannerRegistry.class);
    }


    /**
     * Synchronizes every scanner’s state with the latest global settings. For each engine,
     * starts it when globally enabled and stops it otherwise.
     *
     * @param controller global controller supplying enablement flags
     */
    public synchronized void updateFromGlobal(@NotNull GlobalScannerController controller) {
        for (ScanEngine type : ScanEngine.values()) {
            boolean isEnabled = controller.isScannerGloballyEnabled(type);
            if (isEnabled) start(type);
            else stop(type);
        }
    }

    /**
     * Starts the specified scanner type by registering it with the project’s registry.
     *
     * @param scannerType scanner engine to start
     */
    public void start(ScanEngine scannerType) {
        scannerRegistry().registerScanner(scannerType.name());
    }

    /**
     * Stops (de-registers) the specified scanner type for this project.
     *
     * @param scannerType scanner engine to stop
     */
    public void stop(ScanEngine scannerType) {
        scannerRegistry().deregisterScanner(scannerType.name());
    }

    /**
     * Stops all scanner types for this project, regardless of their previous state.
     */
    public void stopAll() {
        for (ScanEngine type : ScanEngine.values()) {
            stop(type);
        }
    }

    @Override
    public void dispose() {
        stopAll();
    }
}