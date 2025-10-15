package com.checkmarx.intellij.realtimeScanners.registry;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.realtimeScanners.configuration.ConfigurationManager;
import com.checkmarx.intellij.realtimeScanners.scanners.oss.OssScannerCommand;
import com.intellij.openapi.Disposable;
import com.checkmarx.intellij.realtimeScanners.basescanner.ScannerCommand;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;


public final class ScannerRegistry implements Disposable {

    private final Map<String, ScannerCommand> scannerMap = new HashMap<>();
    private ConfigurationManager configurationManager;

    @Getter
    private final Project project;

    public ScannerRegistry( @NotNull Project project,@NotNull Disposable parentDisposable){
        this.project=project;
        Disposer.register(parentDisposable,this);
        this.setScanner(Constants.RealTimeConstants.OSS_REALTIME_SCANNER_ENGINE_NAME,new OssScannerCommand(this,project));
    }
    public  ScannerRegistry(@NotNull Project project){
       this(project,project);
    }

    public void setScanner(String id, ScannerCommand scanner){
        Disposer.register(this, scanner);
        this.scannerMap.put(id,scanner);
    }


    public void registerAllScanners(Project project){
        scannerMap.values().forEach(scanner->scanner.register(project));
    }

    public void deregisterAllScanners(){
        scannerMap.values().forEach(ScannerCommand::dispose);
    }

    public ScannerCommand getScanner(String id){
        return this.scannerMap.get(id);
    }

    @Override
    public void dispose() {
      this.deregisterAllScanners();
    }
}
