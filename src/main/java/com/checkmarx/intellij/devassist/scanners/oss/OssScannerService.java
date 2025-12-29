package com.checkmarx.intellij.devassist.scanners.oss;

import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.basescanner.BaseScannerService;
import com.checkmarx.intellij.devassist.common.ScanResult;
import com.checkmarx.intellij.devassist.configuration.ScannerConfig;
import com.checkmarx.intellij.devassist.ignore.IgnoreManager;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.checkmarx.intellij.settings.global.CxWrapperFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import com.checkmarx.ast.ossrealtime.OssRealtimeResults;
import org.jetbrains.annotations.NotNull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Realtime OSS manifest scanner Class that does temporary file handling,
 * and invocation of the Checkmarx OSS realtime scanning engine.
 */
public class OssScannerService extends BaseScannerService<OssRealtimeResults> {
    private static final Logger LOGGER = Utils.getLogger(OssScannerService.class);

    /**
     * Creates an OSS scanner service with the default OSS realtime configuration.
     */
    public OssScannerService() {
        super(createConfig());
    }


    /**
     * Builds the default scanner configuration used for OSS realtime scanning.
     *
     * @return fully populated {@link ScannerConfig} instance for the OSS engine
     */
    public static ScannerConfig createConfig() {
        return ScannerConfig.builder()
                .engineName(ScanEngine.OSS.name())
                .configSection(Constants.RealTimeConstants.OSS_REALTIME_SCANNER)
                .activateKey(Constants.RealTimeConstants.ACTIVATE_OSS_REALTIME_SCANNER)
                .errorMessage(Constants.RealTimeConstants.ERROR_OSS_REALTIME_SCANNER)
                .disabledMessage(Constants.RealTimeConstants.OSS_REALTIME_SCANNER_DISABLED)
                .enabledMessage(Constants.RealTimeConstants.OSS_REALTIME_SCANNER_START)
                .build();
    }


    /**
     * Determines whether a file should be scanned by validating the base checks and
     * ensuring it matches a supported manifest pattern.
     *
     * @param filePath absolute path to the file
     * @return {@code true} if the file should be scanned; {@code false} otherwise
     */
    public boolean shouldScanFile(String filePath,PsiFile psiFile) {
        if (!super.shouldScanFile(filePath,psiFile)) {
            return false;
        }
        return this.isManifestFilePatternMatching(filePath);
    }


    /**
     * Checks whether the supplied file path matches any of the manifest glob patterns.
     *
     * @param filePath path to evaluate
     * @return {@code true} if a manifest pattern matches; {@code false} otherwise
     */
    private boolean isManifestFilePatternMatching(String filePath) {
        List<PathMatcher> pathMatchers = Constants.RealTimeConstants.MANIFEST_FILE_PATTERNS.stream()
                .map(p -> FileSystems.getDefault().getPathMatcher("glob:" + p))
                .collect(Collectors.toList());
        for (PathMatcher pathMatcher : pathMatchers) {
            if (pathMatcher.matches(Paths.get(filePath))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates a deterministic, filesystem-safe file name for storing the manifest in the temp directory.
     *
     * @param relativePath path of the manifest relative to the project
     * @return sanitized temp file name containing the original base name and a hash suffix
     */
    private String toSafeTempFileName(@NotNull String relativePath) {
        String baseName = Paths.get(relativePath).getFileName().toString();
        String hash = this.generateFileHash(relativePath);
        return baseName + "-" + hash + ".tmp";
    }

    /**
     * Generates a short hash based on the manifest path and the current time to avoid collisions.
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
            LOGGER.debug("Using alternative method of generating hashCode for temporary file");
            return Integer.toHexString((relativePath + System.currentTimeMillis()).hashCode());
        }
    }

    /**
     * Resolves the temporary sub-folder path allocated to the supplied PSI file.
     *
     * @param file manifest PSI file being scanned
     * @return path pointing to a unique temp directory per file
     */
    protected Path getTempSubFolderPath(@NotNull PsiFile file) {
        String baseTempPath = super.getTempSubFolderPath(Constants.RealTimeConstants.OSS_REALTIME_SCANNER_DIRECTORY);
        String relativePath = file.getName();
        return Paths.get(baseTempPath, toSafeTempFileName(relativePath));
    }

    /**
     * Persists the main manifest file into the temporary directory for scanning.
     *
     * @param tempSubFolder    destination temp directory
     * @param originalFilePath original manifest path (used for logging and file naming)
     * @param file             PSI file containing the manifest contents
     * @return optional containing the path to the temp manifest file when saved successfully
     * @throws IOException if writing the file fails
     */
    private Optional<String> saveMainManifestFile(Path tempSubFolder, @NotNull String originalFilePath, PsiFile file) throws IOException {
        String fileText = DevAssistUtils.getFileContent(file);

        if (fileText == null || fileText.isBlank()) {
            LOGGER.warn("No content found in file" + originalFilePath);
            return Optional.empty();
        }
        Path originalPath = Paths.get(originalFilePath);
        String fileName = originalPath.getFileName().toString();
        Path tempFilePath = Paths.get(tempSubFolder.toString(), fileName);
        Files.writeString(tempFilePath, fileText, StandardCharsets.UTF_8);
        return Optional.of(tempFilePath.toString());
    }


    /**
     * Copies a companion lock file (e.g., package-lock.json) into the temporary directory
     * when it exists alongside the scanned manifest.
     *
     * @param tempFolderPath   temp directory where the companion file should be written
     * @param originalFilePath original manifest path used to locate the companion file
     */
    private void saveCompanionFile(Path tempFolderPath, String originalFilePath) {
        if (originalFilePath.isEmpty() || Objects.isNull(tempFolderPath)) {
            return;
        }
        String parentFileName = getPath(originalFilePath).getFileName().toString();
        String companionFileName = getCompanionFileName(parentFileName);
        if (companionFileName.isEmpty()) {
            return;
        }
        Path parentPath = getPath(originalFilePath).getParent();
        Path companionOriginalPath = Paths.get(parentPath.toString(), companionFileName);
        if (!Files.exists(companionOriginalPath)) {
            return;
        }
        Path companionTempPath = Paths.get(tempFolderPath.toString(), companionFileName);
        try {
            Files.copy(companionOriginalPath, companionTempPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("Error occurred while saving companion file: " + e);
        }
    }


    /**
     * Convenience wrapper for {@link Paths#get(String, String...)} to support testing or overrides.
     *
     * @param file path string to convert
     * @return {@link Path} instance pointing to the supplied file
     */
    private Path getPath(@NotNull String file) {
        return Paths.get(file);
    }


    /**
     * Infers a companion lock file name based on the manifest file name.
     *
     * @param fileName name of the manifest file
     * @return companion file name or an empty string when no companion is defined
     */
    private String getCompanionFileName(String fileName) {
        if (fileName.equals("package.json")) {
            return "package-lock.json";
        }
        if (fileName.contains(".csproj")) {
            return "package.lock.json";
        }
        return "";
    }

    /**
     * Scans the given Psi file using OssScanner wrapper method.
     *
     * @param file - the file to scan
     * @param uri  - the file path
     * @return ScanResult of type OssRealtimeResults
     */
    public ScanResult<OssRealtimeResults> scan(@NotNull PsiFile file, @NotNull String uri) {
        if (!this.shouldScanFile(uri,file)) {
            return null;
        }
        Path tempSubFolder = this.getTempSubFolderPath(file);
        try {
            this.createTempFolder(tempSubFolder);
            Optional<String> mainTempPath = this.saveMainManifestFile(tempSubFolder, uri, file);
            if (mainTempPath.isEmpty()) {
                return null;
            }
            this.saveCompanionFile(tempSubFolder, uri);
            LOGGER.info("Start Realtime Scan On File: " + uri);
            IgnoreManager ignoreManager = IgnoreManager.getInstance(file.getProject());
            String ignoreFilePath = ignoreManager.getIgnoreTempFilePath();
            OssRealtimeResults scanResults = CxWrapperFactory.build().ossRealtimeScan(mainTempPath.get(), ignoreFilePath);
            return new OssScanResultAdaptor(scanResults);

        } catch (IOException | CxException | InterruptedException e) {
            LOGGER.warn(this.config.getErrorMessage(), e);
        } finally {
            LOGGER.info("Deleting temporary folder");
            deleteTempFolder(tempSubFolder);
        }
        return null;
    }

}
