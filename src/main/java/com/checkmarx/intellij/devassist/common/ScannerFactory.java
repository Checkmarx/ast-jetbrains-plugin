package com.checkmarx.intellij.devassist.common;

import com.checkmarx.intellij.devassist.basescanner.ScannerService;
import com.checkmarx.intellij.devassist.scanners.oss.OssScannerService;
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
