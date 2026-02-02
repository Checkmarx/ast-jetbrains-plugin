package com.checkmarx.intellij.common.devassist.registry;

import com.checkmarx.intellij.common.devassist.basescanner.ScannerCommand;
import com.checkmarx.intellij.common.devassist.scanners.asca.AscaScannerCommand;
import com.checkmarx.intellij.common.devassist.scanners.containers.ContainerScannerCommand;
import com.checkmarx.intellij.common.devassist.scanners.iac.IacScannerCommand;
import com.checkmarx.intellij.common.devassist.scanners.oss.OssScannerCommand;
import com.checkmarx.intellij.common.devassist.scanners.secrets.SecretsScannerCommand;
import com.checkmarx.intellij.common.devassist.utils.ScanEngine;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Project-level service whichKeeps a registry keyed by scanner ID,
 * wires them up to the IntelliJ disposer,
 * and exposes helpers for registering/de-registering scanners on demand.
 */
@Service(Service.Level.PROJECT)
public final class ScannerRegistry implements Disposable {

    private final Map<String, ScannerCommand> scannerMap = new ConcurrentHashMap<>();

    @Getter
    private final Project project;

    /**
     * Stores the project reference, registers this registry for disposal with the project,
     * and initializes all supported scanners.
     *
     * @param project current IntelliJ project
     */
    public ScannerRegistry( @NotNull Project project){
        this.project=project;
        Disposer.register(this,project);
        scannerInitialization();
    }

    /**
     * Populates the registry with every scanner the plugin currently supports.
     * New scanners should be added here to be available project-wide.
     */
    private void scannerInitialization(){
        this.setScanner(ScanEngine.OSS.name(), new OssScannerCommand(this,project));
        this.setScanner(ScanEngine.CONTAINERS.name(),new ContainerScannerCommand(this,project));
        this.setScanner(ScanEngine.SECRETS.name(), new SecretsScannerCommand(this,project));
        this.setScanner(ScanEngine.IAC.name(), new IacScannerCommand(this,project));
        this.setScanner(ScanEngine.ASCA.name(), new AscaScannerCommand(this,project));
    }

    /**
     * Adds a scanner implementation under the given ID and ensures it will be
     * disposed when the registry itself is disposed.
     *
     * @param id      unique scanner identifier
     * @param scanner scanner command implementation
     */
    private void setScanner(String id, ScannerCommand scanner){
        Disposer.register(this, scanner);
        this.scannerMap.put(id,scanner);
    }

    /**
     * Registers all known scanners with the provided project so they can start listening
     * for IDE events immediately.
     *
     * @param project target project for scanner registration
     */

    public void registerAllScanners(Project project){
        scannerMap.values().forEach(scanner->scanner.register(project));
    }

    /**
     * De-registers every scanner from the stored project, effectively stopping
     * all realtime scanning activity.
     */
    public void deregisterAllScanners(){
        scannerMap.values().forEach(scanner->scanner.deregister(project));
    }

    /**
     * Registers a single scanner identified by ID, if it exists in the registry.
     *
     * @param scannerId scanner identifier
     */
    public void registerScanner(String scannerId){
        ScannerCommand scanner= getScanner(scannerId);
        if(scanner!=null) scanner.register(project);
    }


    /**
     * De-registers and disposes a single scanner identified by ID, if present.
     *
     * @param scannerId scanner identifier
     */
    public void deregisterScanner(String scannerId){
        ScannerCommand scanner= getScanner(scannerId);
        if(scanner!=null){
            scanner.deregister(project);
            scanner.dispose();
        }
    }

    /**
     * Retrieves the scanner command registered under the given ID.
     *
     * @param scannerId scanner identifier
     * @return scanner command instance or {@code null} when not found
     */

    public ScannerCommand getScanner(String scannerId){
        return this.scannerMap.get(scannerId);
    }


    /**
     * Cleans up the registry by de-registering every scanner and clearing the map.
     * Invoked automatically when the IntelliJ disposer tears down the service.
     */
    @Override
    public void dispose() {
      this.deregisterAllScanners();
      scannerMap.clear();
    }

}
