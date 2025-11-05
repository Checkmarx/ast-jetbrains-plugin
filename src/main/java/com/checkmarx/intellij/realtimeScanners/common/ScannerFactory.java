package com.checkmarx.intellij.realtimeScanners.common;

import com.checkmarx.intellij.realtimeScanners.basescanner.BaseScannerService;
import com.checkmarx.intellij.realtimeScanners.basescanner.ScannerService;
import com.checkmarx.intellij.realtimeScanners.scanners.oss.OssScannerService;

import java.util.List;
import java.util.Optional;

public class ScannerFactory {

    private  final List<ScannerService<?>> serviceMap;

    public ScannerFactory(){
        serviceMap= List.of(
                new OssScannerService()
                // Add others as needed
        );
    }

    public Optional<ScannerService<?>> findApplicationScanner(String file){
        return  serviceMap.stream().filter(scanner->scanner.shouldScanFile(file)).findFirst();
    }

}
