package com.checkmarx.intellij.devassist.scanners.secrets;

import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.ast.secretsrealtime.SecretsRealtimeResults;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.basescanner.BaseScannerService;
import com.checkmarx.intellij.devassist.common.ScanResult;
import com.checkmarx.intellij.devassist.configuration.ScannerConfig;
import com.checkmarx.intellij.devassist.ignore.IgnoreManager;
import com.checkmarx.intellij.devassist.telemetry.TelemetryService;
import com.checkmarx.intellij.devassist.utils.DevAssistConstants;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.checkmarx.intellij.settings.global.CxWrapperFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Realtime Secrets scanner Class that does temporary file handling,
 * and invocation of the Checkmarx Secrets realtime scanning engine.
 */
public class SecretsScannerService extends BaseScannerService<SecretsRealtimeResults> {
    private static final Logger LOGGER = Utils.getLogger(SecretsScannerService.class);

    /**
     * Creates a Secrets scanner service with the default Secrets realtime configuration.
     */
    public SecretsScannerService() {
        super(createConfig());
    }

    /**
     * Builds the default scanner configuration used for Secrets realtime scanning.
     *
     * @return fully populated {@link ScannerConfig} instance for the Secrets engine
     */
    public static ScannerConfig createConfig() {
        return ScannerConfig.builder()
                .engineName(ScanEngine.SECRETS.name())
                .configSection(DevAssistConstants.SECRETS_REALTIME_SCANNER)
                .activateKey(DevAssistConstants.ACTIVATE_SECRETS_REALTIME_SCANNER)
                .errorMessage(DevAssistConstants.ERROR_SECRETS_REALTIME_SCANNER)
                .disabledMessage(DevAssistConstants.SECRETS_REALTIME_SCANNER_DISABLED)
                .enabledMessage(DevAssistConstants.SECRETS_REALTIME_SCANNER_START)
                .build();
    }

    /**
     * Determines whether a file should be scanned by validating the base checks.
     * Unlike OSS scanner, secrets scanner should scan most file types except specific exclusions.
     *
     * @param filePath absolute path to the file
     * @return {@code true} if the file should be scanned; {@code false} otherwise
     */
    public boolean shouldScanFile(String filePath, PsiFile psiFile) {
        if (!super.shouldScanFile(filePath, psiFile)) {
            LOGGER.debug("Secrets scanner: file not eligible - " + filePath);
            return false;
        }
        boolean isExcluded = this.isExcludedFileForSecretsScanning(filePath);
        if (isExcluded) {
            LOGGER.debug("Secrets scanner: file excluded - " + filePath);
            return false;
        }
        LOGGER.debug("Secrets scanner: file eligible - " + filePath);
        return true;
    }

    /**
     * Checks whether the supplied file path should be excluded from secrets scanning.
     * Based on the TypeScript implementation, we exclude manifest files and ignore files.
     *
     * @param filePath path to evaluate
     * @return {@code true} if the file should be excluded; {@code false} otherwise
     */
    private boolean isExcludedFileForSecretsScanning(String filePath) {
        // Check if it's a manifest file (similar to TypeScript implementation)
        List<PathMatcher> manifestMatchers = DevAssistConstants.MANIFEST_FILE_PATTERNS.stream()
                .map(p -> FileSystems.getDefault().getPathMatcher("glob:" + p))
                .collect(Collectors.toList());

        for (PathMatcher matcher : manifestMatchers) {
            if (matcher.matches(Paths.get(filePath))) {
                return true;
            }
        }

        // Check if it's a realtime ignore file
        return filePath.contains("/.vscode/.checkmarxIgnored") ||
               filePath.contains("/.vscode/.checkmarxIgnoredTempList") ||
               filePath.contains("\\.vscode\\.checkmarxIgnored") ||
               filePath.contains("\\.vscode\\.checkmarxIgnoredTempList");
    }

    /**
     * Creates a deterministic, filesystem-safe file name for storing the file in the temp directory.
     *
     * @param relativePath path of the file relative to the project
     * @return sanitized temp file name containing the original base name and a hash suffix
     */
    private String toSafeTempFileName(@NotNull String relativePath) {
        String baseName = Paths.get(relativePath).getFileName().toString();
        String hash = this.generateFileHash(relativePath);
        return baseName + "-" + hash + ".tmp";
    }

    /**
     * Generates a short hash based on the file path and the current time to avoid collisions.
     *
     * @param relativePath path whose value participates in the hash
     * @return hexadecimal hash string suitable for filenames
     */
    private String generateFileHash(@NotNull String relativePath) {
        try {
            LocalTime time = LocalTime.now();
            String timeSuffix = String.format("%02d%02d", time.getMinute(), time.getSecond());
            String combined = relativePath + timeSuffix + UUID.randomUUID().toString().substring(0,5);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            LOGGER.debug("Secrets scanner: using fallback hash method");
            return Integer.toHexString((relativePath + System.currentTimeMillis()).hashCode());
        }
    }

    /**
     * Resolves the temporary sub-folder path allocated to the supplied PSI file.
     *
     * @param file file PSI file being scanned
     * @return path pointing to a unique temp directory per file
     */
    protected Path getTempSubFolderPath(@NotNull PsiFile file) {
        String baseTempPath = super.getTempSubFolderPath(DevAssistConstants.SECRETS_REALTIME_SCANNER_DIRECTORY);
        String relativePath = file.getName();
        return Paths.get(baseTempPath, toSafeTempFileName(relativePath));
    }

    /**
     * Persists the file into the temporary directory for scanning.
     *
     * @param tempSubFolder    destination temp directory
     * @param originalFilePath original file path (used for logging and file naming)
     * @param file             PSI file containing the file contents
     * @return optional containing the path to the temp file when saved successfully
     * @throws IOException if writing the file fails
     */
    private Optional<String> saveFileForScanning(Path tempSubFolder, @NotNull String originalFilePath, PsiFile file) throws IOException {
        String fileText = DevAssistUtils.getFileContent(file);

        if (fileText == null || fileText.isBlank()) {
            LOGGER.debug("Secrets scanner: no content found - " + originalFilePath);
            return Optional.empty();
        }

        Path originalPath = Paths.get(originalFilePath);
        String fileName = originalPath.getFileName().toString();
        Path tempFilePath = Paths.get(tempSubFolder.toString(), fileName);
        Files.writeString(tempFilePath, fileText, StandardCharsets.UTF_8);
        return Optional.of(tempFilePath.toString());
    }

    /**
     * Scans the given Psi file using SecretsScanner wrapper method.
     *
     * @param file - the file to scan
     * @param uri  - the file path
     * @return ScanResult of type SecretsRealtimeResults (TODO: Replace with actual type)
     */
    public ScanResult<SecretsRealtimeResults> scan(@NotNull PsiFile file, @NotNull String uri) {
        if (!this.shouldScanFile(uri,file)) {
            return null;
        }

        Path tempSubFolder = this.getTempSubFolderPath(file);
        try {
            this.createTempFolder(tempSubFolder);
            Optional<String> tempFilePath = this.saveFileForScanning(tempSubFolder, uri, file);
            if (tempFilePath.isEmpty()) {
                LOGGER.debug("Secrets scanner: failed to save file - " + uri);
                return null;
            }

            LOGGER.debug("Secrets scanner: starting scan - " + uri);
            IgnoreManager ignoreManager = IgnoreManager.getInstance(file.getProject());
            String ignoreFilePath = ignoreManager.getIgnoreTempFilePath();
            SecretsRealtimeResults scanResults = CxWrapperFactory.build().secretsRealtimeScan(tempFilePath.get(), ignoreFilePath);

            if (scanResults == null) {
                LOGGER.debug("Secrets scanner: no results returned - " + uri);
                return null;
            }

            int secretCount = scanResults.getSecrets() != null ? scanResults.getSecrets().size() : 0;
            LOGGER.debug("Secrets scanner: scan completed - " + uri + " (" + secretCount + " secrets found)");

            if (secretCount > 0) {
                var secrets = scanResults.getSecrets();
                for (int index = 0; index < secrets.size(); index++) {
                    var secret = secrets.get(index);
                    LOGGER.debug("Secret " + (index + 1) + ": " + secret.getTitle() + " [" + secret.getSeverity() + "]");
                }
            }
            SecretsScanResultAdaptor scanResultAdaptor = new SecretsScanResultAdaptor(scanResults);
            TelemetryService.logScanResults(scanResultAdaptor, ScanEngine.SECRETS);
            return scanResultAdaptor;

            return new SecretsScanResultAdaptor(scanResults, uri);

        } catch (IOException | CxException | InterruptedException e) {
            LOGGER.debug("Secrets scanner: scan error", e);
        } finally {
            LOGGER.debug("Secrets scanner: cleaning up temp folder");
            deleteTempFolder(tempSubFolder);
        }
        return null;
    }
}
