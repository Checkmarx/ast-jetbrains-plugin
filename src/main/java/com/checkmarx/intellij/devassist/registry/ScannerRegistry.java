package com.checkmarx.intellij.devassist.registry;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.devassist.scanners.oss.OssScannerCommand;
import com.intellij.openapi.Disposable;
import com.checkmarx.intellij.devassist.basescanner.ScannerCommand;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service(Service.Level.PROJECT)
public final class ScannerRegistry implements Disposable {

    private final Map<String, ScannerCommand> scannerMap = new ConcurrentHashMap<>();

    @Getter
    private final Project project;

    public ScannerRegistry( @NotNull Project project){
        this.project=project;
        Disposer.register(this,project);
        scannerInitialization();
    }

    private void scannerInitialization(){
        this.setScanner(Constants.RealTimeConstants.OSS_REALTIME_SCANNER_ENGINE_NAME,new OssScannerCommand(this,project));
    }

    private void setScanner(String id, ScannerCommand scanner){
        Disposer.register(this, scanner);
        this.scannerMap.put(id,scanner);
    }

    public void registerAllScanners(Project project){
        scannerMap.values().forEach(scanner->scanner.register(project));
    }

    public void deregisterAllScanners(){
        scannerMap.values().forEach(ScannerCommand::dispose);
    }

    public void registerScanner(String Id){
        ScannerCommand scanner= getScanner(Id);
        if(scanner!=null) scanner.register(project);
    }

    public void deregisterScanner(String Id){
        ScannerCommand scanner= getScanner(Id);
        if(scanner!=null){
            scanner.deregister(project);
            scanner.dispose();
        }
    }

    public ScannerCommand getScanner(String id){
        return this.scannerMap.get(id);
    }

    @Override
    public void dispose() {
      this.deregisterAllScanners();
      scannerMap.clear();
    }

}
