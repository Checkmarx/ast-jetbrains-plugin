package com.checkmarx.intellij.realtimeScanners.registry;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.realtimeScanners.scanners.oss.OssScannerCommand;
import com.intellij.openapi.Disposable;
import com.checkmarx.intellij.realtimeScanners.basescanner.ScannerCommand;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;


public class ScannerRegistry implements Disposable {

    private final Map<String, ScannerCommand> scannerMap = new HashMap<>();

    public ScannerRegistry(@NotNull Disposable parentDisposable){
        Disposer.register(parentDisposable,this);
        this.setScanner(Constants.RealTimeConstants.OSS_REALTIME_SCANNER_ENGINE_NAME,new OssScannerCommand(this));
    }

    public void setScanner(String id, ScannerCommand scanner){
        Disposer.register(this, scanner);
        this.scannerMap.put(id,scanner);
    }

    public void registerAllScanners(){

        scannerMap.values().forEach(ScannerCommand::register);
    }

    public void deregisterAllScanners(){
        scannerMap.values().forEach(ScannerCommand::dispose);
    }

    public ScannerCommand getScanner(String id){
        return this.scannerMap.get(id);
    }

    @Override
    public void dispose() {

    }
}
