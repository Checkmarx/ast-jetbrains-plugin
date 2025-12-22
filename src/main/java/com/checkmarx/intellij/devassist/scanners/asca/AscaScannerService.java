package com.checkmarx.intellij.devassist.scanners.asca;

import com.checkmarx.ast.asca.ScanResult;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.basescanner.BaseScannerService;
import com.checkmarx.intellij.devassist.configuration.ScannerConfig;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.checkmarx.intellij.service.AscaService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Realtime ASCA scanner service that integrates with the realtime scanner system.
 */
public class AscaScannerService extends BaseScannerService<ScanResult> {
    private static final Logger LOGGER = Utils.getLogger(AscaScannerService.class);
    private final AscaService ascaService;

    /**
     * Creates an ASCA scanner service with the default ASCA realtime configuration.
     */
    public AscaScannerService() {
        super(createConfig());
        this.ascaService = new AscaService();
    }

    /**
     * Builds the default scanner configuration used for ASCA realtime scanning.
     *
     * @return fully populated {@link ScannerConfig} instance for the ASCA engine
     */
    public static ScannerConfig createConfig() {
        return ScannerConfig.builder()
                .engineName(ScanEngine.ASCA.name())
                .configSection(Constants.RealTimeConstants.ASCA_REALTIME_SCANNER)
                .activateKey(Constants.RealTimeConstants.ACTIVATE_ASCA_REALTIME_SCANNER)
                .errorMessage(Constants.RealTimeConstants.ERROR_ASCA_REALTIME_SCANNER)
                .disabledMessage(Constants.RealTimeConstants.ASCA_REALTIME_SCANNER_DISABLED)
                .enabledMessage(Constants.RealTimeConstants.ASCA_REALTIME_SCANNER_START)
                .build();
    }

    /**
     * Determines whether a file should be scanned by ASCA.
     * ASCA typically scans source code files but may have specific exclusions.
     *
     * @param filePath absolute path to the file
     * @param psiFile the PSI file object
     * @return {@code true} if the file should be scanned; {@code false} otherwise
     */
    @Override
    public boolean shouldScanFile(String filePath, PsiFile psiFile) {
        if (!super.shouldScanFile(filePath, psiFile)) {
            LOGGER.debug("ASCA scanner: file not eligible - " + filePath);
            return false;
        }

        // ASCA should scan source code files - exclude common non-source files
        if (isExcludedFileForAscaScanning(filePath)) {
            LOGGER.debug("ASCA scanner: file excluded - " + filePath);
            return false;
        }

        LOGGER.debug("ASCA scanner: file eligible - " + filePath);
        return true;
    }

    /**
     * Checks whether the supplied file path should be excluded from ASCA scanning.
     *
     * @param filePath path to evaluate
     * @return {@code true} if the file should be excluded; {@code false} otherwise
     */
    private boolean isExcludedFileForAscaScanning(String filePath) {
        // Exclude common non-source file types
        String lowerPath = filePath.toLowerCase();

        // Exclude binary, image, and other non-source files
        if (lowerPath.endsWith(".jar") || lowerPath.endsWith(".war") || lowerPath.endsWith(".zip") ||
            lowerPath.endsWith(".tar") || lowerPath.endsWith(".gz") || lowerPath.endsWith(".exe") ||
            lowerPath.endsWith(".dll") || lowerPath.endsWith(".so") || lowerPath.endsWith(".dylib") ||
            lowerPath.endsWith(".png") || lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg") ||
            lowerPath.endsWith(".gif") || lowerPath.endsWith(".bmp") || lowerPath.endsWith(".svg") ||
            lowerPath.endsWith(".pdf") || lowerPath.endsWith(".doc") || lowerPath.endsWith(".docx")) {
            return true;
        }

        // Check manifest files
        List<PathMatcher> manifestMatchers = Constants.RealTimeConstants.MANIFEST_FILE_PATTERNS.stream()
                .map(p -> FileSystems.getDefault().getPathMatcher("glob:" + p))
                .collect(Collectors.toList());

        for (PathMatcher matcher : manifestMatchers) {
            if (matcher.matches(Paths.get(filePath))) {
                return true;
            }
        }

        // Exclude Checkmarx ignore files
        return filePath.contains("/.vscode/.checkmarxIgnored") ||
               filePath.contains("/.vscode/.checkmarxIgnoredTempList") ||
               filePath.contains("\\.vscode\\.checkmarxIgnored") ||
               filePath.contains("\\.vscode\\.checkmarxIgnoredTempList");
    }

    /**
     * Scans the given PsiFile using the ASCA scanner.
     *
     * @param psiFile the PSI file to scan
     * @param uri the file path URI
     * @return ScanResult containing ASCA scan results, or null if scan fails
     */
    @Override
    public com.checkmarx.intellij.devassist.common.ScanResult<ScanResult> scan(@NotNull PsiFile psiFile, @NotNull String uri) {
        if (!shouldScanFile(uri, psiFile)) {
            LOGGER.debug("ASCA scanner: file not eligible for scanning - " + uri);
            return null;
        }

        LOGGER.debug("ASCA scanner: starting scan - " + uri);

        try {
            ScanResult ascaResult = ascaService.runAscaScan(psiFile, psiFile.getProject(), true, "JetBrains");

            if (ascaResult == null) {
                LOGGER.debug("ASCA scanner: no results returned - " + uri);
                return null;
            }

            int issueCount = ascaResult.getScanDetails() != null ? ascaResult.getScanDetails().size() : 0;
            LOGGER.debug("ASCA scanner: scan completed - " + uri + " (" + issueCount + " issues found)");

            return new AscaScanResultAdaptor(ascaResult, uri);

        } catch (Exception e) {
            LOGGER.warn("ASCA scanner: scan error for file: " + uri, e);
            return null;
        }
    }
}
