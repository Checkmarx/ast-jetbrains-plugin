package com.checkmarx.intellij.common.devassist.common;

import com.checkmarx.intellij.common.utils.Utils;
import com.checkmarx.intellij.common.devassist.basescanner.ScannerService;
import com.checkmarx.intellij.common.devassist.model.ScanIssue;
import com.checkmarx.intellij.common.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.common.devassist.utils.ScanEngine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * Abstract class responsible for managing the scanning of files using various scanner services.
 */
public class ScanManager {

    private static final Logger LOGGER = Utils.getLogger(ScanManager.class);
    private final ScannerFactory scannerFactory = new ScannerFactory();

    /**
     * Scans the given file at the specified path using the appropriate scanner service based on the provided scan engine.
     *
     * @param filePath   the path of the file to be scanned
     * @param psiFile    the PsiFile representing the file to be scanned
     * @param scanEngine the scan engine to be used for scanning; if null or ScanEngine.ALL, all supported scanners will be used
     * @return a list of ScanIssue objects representing the issues found during the scan
     */
    public final List<ScanIssue> scanFile(String filePath, PsiFile psiFile, ScanEngine scanEngine) {
        if (Objects.isNull(scanEngine) || scanEngine == ScanEngine.ALL) {
            return scanFileUsingAllSupportedScanners(filePath, psiFile);
        }
        return scanFileUsingSpecificScanner(filePath, psiFile, scanEngine);
    }

    /**
     * Scans the given file using a specific scanner service based on the provided scan engine.
     */
    private List<ScanIssue> scanFileUsingSpecificScanner(String filePath, PsiFile psiFile, ScanEngine scanEngine) {
        ScannerService<?> scannerService = getSpecificSupportedEnabledScanner(filePath, psiFile, scanEngine);
        if (Objects.isNull(scannerService)) {
            LOGGER.warn(format("RTS: %s Scanner not supported or enabled for file path: %s.", scanEngine.name(), filePath));
            return Collections.emptyList();
        }
        ScanResult<?> scanResult = initiateScan(scannerService, psiFile, filePath);
        if (Objects.isNull(scanResult)) {
            LOGGER.warn(format("RTS: No issues found for engine: %s for file: %s ", scannerService.getConfig().getEngineName(), psiFile.getName()));
            return Collections.emptyList();
        }
        return scanResult.getIssues();
    }

    /**
     * Scans the given file using all available scanner services and returns all issues found.
     */
    private List<ScanIssue> scanFileUsingAllSupportedScanners(String filePath, PsiFile psiFile) {
        List<ScanResult<?>> allScanResults = getSupportedEnabledScanner(filePath, psiFile).stream()
                .map(scannerService -> initiateScan(scannerService, psiFile, filePath))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return allScanResults.stream()
                .flatMap(scanResult -> scanResult.getIssues().stream())
                .collect(Collectors.toList());
    }

    /**
     * Scans the given PSI file at the specified path using an appropriate real-time scanner,
     * if available and active.
     *
     * @param scannerService - ScannerService object of found scan engine
     * @param file           the PsiFile representing the file to be scanned; must not be null
     * @param path           the string representation of the file path to be scanned; must not be null or empty
     * @return a {@link ScanResult} instance containing the results of the scan, or null if no
     * active and suitable scanner is found
     */
    private ScanResult<?> initiateScan(ScannerService<?> scannerService, @NotNull PsiFile file, @NotNull String path) {
        try {
            LOGGER.info(format("RTS: Scan initiated using engine: %s for file: %s.", scannerService.getConfig().getEngineName(), path));
            ScanResult<?> scanResult = scannerService.scan(file, path);
            LOGGER.info(format("RTS: Scan completed using engine: %s for file: %s.", scannerService.getConfig().getEngineName(), path));
            return scanResult;
        } catch (Exception e) {
            LOGGER.warn(format("RTS: Exception occurred while scanning file: %s ", path), e);
            return null;
        }
    }

    /**
     * Retrieves all supported instances of {@link ScannerService} for handling real-time scanning
     * of the specified file. The method checks available scanner services to determine if
     * any of them is suited to handle the given file path.
     *
     * @param filePath the path of the file as a string, used to identify an applicable scanner service; must not be null or empty
     * @return an {@link Optional} containing the matching {@link ScannerService} if found, or an empty {@link Optional} if no appropriate service exists
     */
    protected final List<ScannerService<?>> getSupportedEnabledScanner(String filePath, PsiFile psiFile) {
        List<ScannerService<?>> supportedScanners = scannerFactory.getAllSupportedScanners(filePath, psiFile);
        if (supportedScanners.isEmpty()) {
            LOGGER.warn(format("RTS: No supported scanner found for this file path: %s.", filePath));
            return Collections.emptyList();
        }
        return supportedScanners.stream()
                .filter(scannerService ->
                        DevAssistUtils.isScannerActive(scannerService.getConfig().getEngineName()))
                .collect(Collectors.toList());
    }

    /**
     * Retrieves a specific supported and enabled scanner service for the given file path and scan engine.
     * If no supported scanner is found or the scanner is not active, this method returns null.
     *
     * @param filePath   the path of the file to be scanned; must not be null or empty
     * @param psiFile    the PsiFile representation of the file to be scanned; must not be null
     * @param scanEngine the scan engine to be used for scanning; must not be null
     * @return the specific {@link ScannerService} instance that is supported and enabled for the given
     * file path and scan engine, or null if no such service is available
     */
    private ScannerService<?> getSpecificSupportedEnabledScanner(String filePath, PsiFile psiFile, ScanEngine scanEngine) {
        ScannerService<?> scannerService = this.scannerFactory.getSupportedScannerUsingScanEngine(filePath, psiFile, scanEngine);
        if (Objects.isNull(scannerService)) {
            LOGGER.warn(format("RTS: No supported scanner found for engine: %s for file path: %s.", scanEngine.name(), filePath));
            return null;
        }
        if (!DevAssistUtils.isScannerActive(scannerService.getConfig().getEngineName())) {
            LOGGER.warn(format("RTS: %s Scanner is not active for file path: %s.", scanEngine.name(), filePath));
            return null;
        }
        return scannerService;
    }
}
