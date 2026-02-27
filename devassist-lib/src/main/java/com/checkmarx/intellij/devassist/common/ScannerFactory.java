package com.checkmarx.intellij.devassist.common;

import com.checkmarx.intellij.devassist.basescanner.ScannerService;
import com.checkmarx.intellij.devassist.scanners.asca.AscaScannerService;
import com.checkmarx.intellij.devassist.scanners.containers.ContainerScannerService;
import com.checkmarx.intellij.devassist.scanners.iac.IacScannerService;
import com.checkmarx.intellij.devassist.scanners.oss.OssScannerService;
import com.checkmarx.intellij.devassist.scanners.secrets.SecretsScannerService;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.psi.PsiFile;

import java.util.ArrayList;
import java.util.List;

public class ScannerFactory {

    private final List<ScannerService<?>> scannerServices;

    public ScannerFactory() {
        scannerServices = List.of(new OssScannerService(), new ContainerScannerService(), new SecretsScannerService(), new IacScannerService(), new AscaScannerService());
    }


    /**
     * Returns all the real-time scanners that support the given file
     *
     * @param file - file path to be scanned
     * @return - list of supported scanners
     */
    public List<ScannerService<?>> getAllSupportedScanners(String file, PsiFile psiFile) {
        List<ScannerService<?>> allSupportedScanners = new ArrayList<>();
        scannerServices.stream().filter(scannerService -> scannerService.shouldScanFile(file, psiFile)).forEach(allSupportedScanners::add);
        return allSupportedScanners;
    }

    /**
     * Returns the scanner service that supports the given scan engine and file path
     *
     * @param filePath   - file virtual file path to be scanned
     * @param psiFile    - PsiFile representing the file to be scanned
     * @param scanEngine - scan engine to be used for scanning
     * @return - scanner service that supports the given scan engine and file path
     */
    public ScannerService<?> getSupportedScannerUsingScanEngine(String filePath, PsiFile psiFile, ScanEngine scanEngine) {
        return scannerServices.stream().filter(
                        scannerService -> scannerService.getConfig().getEngineName().equalsIgnoreCase(scanEngine.name())
                                && scannerService.shouldScanFile(filePath, psiFile))
                .findFirst().orElse(null);
    }
}
