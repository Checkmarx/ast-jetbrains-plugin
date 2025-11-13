package com.checkmarx.intellij.devassist.scanners.oss;

import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.basescanner.BaseScannerService;
import com.checkmarx.intellij.devassist.common.ScanResult;
import com.checkmarx.intellij.devassist.configuration.ScannerConfig;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.checkmarx.intellij.settings.global.CxWrapperFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import com.checkmarx.ast.ossrealtime.OssRealtimeResults;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;


public class OssScannerService extends BaseScannerService<OssRealtimeResults> {
    private static final Logger LOGGER = Utils.getLogger(OssScannerService.class);

    public OssScannerService() {
        super(createConfig());
    }

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


    public boolean shouldScanFile(String filePath) {
        if (!super.shouldScanFile(filePath)) {
            return false;
        }
        return this.isManifestFilePatternMatching(filePath);
    }

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

    private String toSafeTempFileName(String relativePath) {
        String baseName = Paths.get(relativePath).getFileName().toString();
        String hash = this.generateFileHash(relativePath);
        return baseName + "-" + hash + ".tmp";
    }

    private String generateFileHash(String relativePath) {
        try {
            LocalTime time = LocalTime.now();
            // MMSS string format for the suffix
            String timeSuffix = String.format("%02d%02d", time.getMinute(), time.getSecond());
            String combined = relativePath + timeSuffix;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            // TODO : add the logger that you are using diff way
            return Integer.toHexString((relativePath + System.currentTimeMillis()).hashCode());
        }
    }

    protected Path getTempSubFolderPath(PsiFile file) {
        String baseTempPath = super.getTempSubFolderPath(Constants.RealTimeConstants.OSS_REALTIME_SCANNER_DIRECTORY);
        String relativePath = file.getName();
        return Paths.get(baseTempPath, toSafeTempFileName(relativePath));
    }

    private String saveMainManifestFile(Path tempSubFolder, String originalFilePath, String content) throws IOException {
        Path originalPath = Paths.get(originalFilePath);
        String fileName = originalPath.getFileName().toString();
        Path tempFilePath = Paths.get(tempSubFolder.toString(), fileName);
        Files.writeString(tempFilePath, content, StandardCharsets.UTF_8);
        return tempFilePath.toString();
    }

    private void saveCompanionFile(Path tempFolderPath, String originalFilePath) {
        String companionFileName = getCompanionFileName(getPath(originalFilePath).getFileName().toString());
        if (companionFileName.isEmpty()) {
            return;
        }
        Path companionOriginalPath = Paths.get(getPath(originalFilePath).getParent().toString(), companionFileName);
        if (!Files.exists(companionOriginalPath)) {
            return;
        }
        Path companionTempPath = Paths.get(tempFolderPath.toString(), companionFileName);
        try {
            Files.copy(companionOriginalPath, companionTempPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            //TODO improve the logger
            LOGGER.warn("Error occurred during OSS realTime scan", e);
        }
    }

    private Path getPath(String file) {
        return Paths.get(file);
    }

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
     * @param uri - the file path
     * @return ScanResult of type OssRealtimeResults
     */
    public ScanResult<OssRealtimeResults> scan(PsiFile file, String uri) {
        OssRealtimeResults scanResults;
        if (!this.shouldScanFile(uri)) {
            return null;
        }
        Path tempSubFolder = this.getTempSubFolderPath(file);
        try {
            this.createTempFolder(tempSubFolder);
            String mainTempPath = this.saveMainManifestFile(tempSubFolder, uri, file.getText());
            this.saveCompanionFile(tempSubFolder, uri);
            LOGGER.info("Start Realtime scan On File: " + uri);
            scanResults = CxWrapperFactory.build().ossRealtimeScan(mainTempPath, "");
            return new OssScanResultAdaptor(scanResults);

        } catch (IOException | CxException | InterruptedException e) {
            LOGGER.warn("Error occurred during OSS realTime scan", e);
        } finally {
            LOGGER.info("Deleting temporary folder");
            deleteTempFolder(tempSubFolder);
        }
        return null;
    }

}
