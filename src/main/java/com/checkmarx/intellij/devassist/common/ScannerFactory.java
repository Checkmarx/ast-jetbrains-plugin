package com.checkmarx.intellij.devassist.common;

import com.checkmarx.intellij.devassist.basescanner.ScannerService;
import com.checkmarx.intellij.devassist.scanners.oss.OssScannerService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ScannerFactory {

    private final List<ScannerService<?>> scannerServices;

    public ScannerFactory() {
        scannerServices = List.of(new OssScannerService());
    }

    public Optional<ScannerService<?>> findRealTimeScanner(String file) {
        return scannerServices.stream().filter(scanner -> scanner.shouldScanFile(file)).findFirst();
    }

    /**
     * Returns all the real-time scanners that support the given file
     * @param file - file path to be scanned
     * @return - list of supported scanners
     */
    public List<ScannerService<?>> getAllSupportedScanners(String file) {
        List<ScannerService<?>> allSupportedScanners = new ArrayList<>();
        scannerServices.stream().filter(scanner ->
                scanner.shouldScanFile(file))
                .findFirst()
                .ifPresent(allSupportedScanners::add);
        return allSupportedScanners;
    }
}
