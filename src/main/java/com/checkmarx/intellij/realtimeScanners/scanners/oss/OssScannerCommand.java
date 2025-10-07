package com.checkmarx.intellij.realtimeScanners.scanners.oss;

import com.checkmarx.intellij.realtimeScanners.basescanner.BaseScannerCommandImpl;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

public class OssScannerCommand extends BaseScannerCommandImpl {

    public OssScannerCommand(@NotNull Disposable parentDisposable){
        super(parentDisposable);
    }

    @Override
    protected void initializeScanner() {
        super.initializeScanner();
        scanAllManifestFilesInFolder();
    }

    private void scanAllManifestFilesInFolder(){

    }



}
