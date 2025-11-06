package com.checkmarx.intellij.realtimeScanners.common;

import com.checkmarx.intellij.realtimeScanners.basescanner.ScannerService;
import com.checkmarx.intellij.realtimeScanners.scanners.oss.OssScannerService;
import java.util.List;
import java.util.Optional;

public class ScannerFactory {

    private final List<ScannerService<?>> scannerServices;

    public ScannerFactory(){
        scannerServices= List.of(new OssScannerService());
    }
    public  Optional<ScannerService<?>> findRealTimeScanner(String file){
        return  scannerServices.stream().filter(scanner->scanner.shouldScanFile(file)).findFirst();
    }
}
