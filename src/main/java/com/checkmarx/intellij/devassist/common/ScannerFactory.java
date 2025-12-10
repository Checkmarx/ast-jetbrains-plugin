package com.checkmarx.intellij.devassist.common;

import com.checkmarx.intellij.devassist.basescanner.ScannerService;
import com.checkmarx.intellij.devassist.scanners.containers.ContainerScannerService;
import com.checkmarx.intellij.devassist.scanners.iac.IacScannerService;
import com.checkmarx.intellij.devassist.scanners.oss.OssScannerService;
import com.checkmarx.intellij.devassist.scanners.secrets.SecretsScannerService;
import com.intellij.psi.PsiFile;

import java.util.ArrayList;
import java.util.List;

public class ScannerFactory {

    private final List<ScannerService<?>> scannerServices;

    public ScannerFactory() {
        scannerServices = List.of(new OssScannerService(), new ContainerScannerService(), new SecretsScannerService(), new IacScannerService());
    }


    /**
     * Returns all the real-time scanners that support the given file
     * @param file - file path to be scanned
     * @return - list of supported scanners
     */
    public List<ScannerService<?>> getAllSupportedScanners(String file, PsiFile psiFile) {
        List<ScannerService<?>> allSupportedScanners = new ArrayList<>();
        scannerServices.stream().filter(scanner ->
                scanner.shouldScanFile(file,psiFile)).forEach(allSupportedScanners::add);
        return allSupportedScanners;
    }
}
