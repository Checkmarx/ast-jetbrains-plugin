package com.checkmarx.intellij.devassist.scanners.containers;

import com.checkmarx.ast.containersrealtime.ContainersRealtimeResults;
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;


/**
 * Realtime ContainerScannerService  scanner Class that does temporary file handling,
 * and invocation of the Checkmarx container realtime scanning engine.
 */
public class ContainerScannerService extends BaseScannerService<ContainersRealtimeResults> {

    private static final Logger LOGGER = Utils.getLogger(ContainerScannerService.class);
    private String fileType;

    /**
     * Creates a new scanner service with the  configuration.
     */

    public ContainerScannerService() {
        super(createConfig());
    }

    /**
     * Builds the default scanner configuration used for container realtime scanning.
     *
     * @return fully populated {@link ScannerConfig} instance for the container engine
     */

    public static ScannerConfig createConfig() {
        return ScannerConfig.builder()
                .engineName(ScanEngine.CONTAINERS.name())
                .configSection(Constants.RealTimeConstants.CONTAINER_REALTIME_SCANNER)
                .activateKey(Constants.RealTimeConstants.ACTIVATE_CONTAINER_REALTIME_SCANNER)
                .enabledMessage(Constants.RealTimeConstants.CONTAINER_REALTIME_SCANNER_START)
                .disabledMessage(Constants.RealTimeConstants.CONTAINER_REALTIME_SCANNER_DISABLED)
                .errorMessage(Constants.RealTimeConstants.ERROR_CONTAINER_REALTIME_SCANNER).build();
    }

    /**
     * Determines whether a file should be scanned by validating the base checks and
     * ensuring it matches a supported container pattern.
     *
     * @param filePath absolute path to the file
     * @return {@code true} if the file should be scanned; {@code false} otherwise
     */

    public boolean shouldScanFile(String filePath, PsiFile psiFile) {
        if (!super.shouldScanFile(filePath, psiFile)) {
            return false;
        }
        return isContainersFilePatternMatching(filePath) || isHelmFile(psiFile, filePath);
    }


    /**
     * Checks whether the supplied file path matches any of the container file patterns .
     *
     * @param filePath path to evaluate
     * @return {@code true} if a containers pattern matches; {@code false} otherwise
     */

    private boolean isContainersFilePatternMatching(String filePath) {
        List<PathMatcher> pathMatchers = Constants.RealTimeConstants.CONTAINERS_FILE_PATTERNS.stream().map(f -> FileSystems.getDefault().getPathMatcher("glob:" + f)).collect(Collectors.toList());
        for (PathMatcher pathMatcher : pathMatchers) {
            if (pathMatcher.matches(Paths.get(filePath.toLowerCase()))) {
                if(DevAssistUtils.isDockerComposeFile(filePath.toLowerCase())){
                    fileType=Constants.RealTimeConstants.DOCKER_COMPOSE;
                }
                else if(DevAssistUtils.isDockerFile(filePath.toLowerCase())){
                    fileType=Constants.RealTimeConstants.DOCKERFILE;
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public ScannerConfig getConfig() {
        return createConfig();
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
            String combined = relativePath + timeSuffix + UUID.randomUUID().toString().substring(0, 5);
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
     * Persists the container file into the temporary directory for scanning.
     *
     * @param tempSubFolder    destination temp directory
     * @param psiFile             PSI file containing the manifest contents
     * @return pair of full file path and temp directory path
     * @throws IOException if writing the file fails
     */
    private Pair<Path, Path> createSubFolderAndSaveFile(Path tempSubFolder, String relativePath, PsiFile psiFile) throws IOException {
        String fileText = DevAssistUtils.getFileContent(psiFile);
        if (fileText == null || fileText.isBlank()) {
            LOGGER.warn("No content found in file");
            return null;
        }
        this.createTempFolder(tempSubFolder);
        Path fullTargetPath = Paths.get(tempSubFolder.toString(), relativePath);
        Files.writeString(fullTargetPath, fileText, StandardCharsets.UTF_8);
        return Pair.of(fullTargetPath, tempSubFolder);
    }

    /**
     * Resolves the temporary sub-folder path allocated to the supplied PSI file.
     *
     * @param psiFile manifest PSI file being scanned
     * @return path pointing to a unique temp directory and fullPath of file
     */

    private Pair<Path, Path> saveOtherFiles(Path tempFolder, PsiFile psiFile) throws IOException {
        String relativePath = psiFile.getName();
        Path tempSubFolder = Paths.get(tempFolder.toString(), psiFile.getName() + "-" + this.generateFileHash(relativePath));
        return createSubFolderAndSaveFile(tempSubFolder, relativePath, psiFile);
    }

    private Pair<Path, Path> saveHelmFile(Path tempfolderPath, PsiFile file) throws IOException {
        String helmRelativePath = file.getName();
        Path helmSubFolderPath = (Paths.get(tempfolderPath.toString(), "helm-" + this.generateFileHash(helmRelativePath)));
        return createSubFolderAndSaveFile(helmSubFolderPath, helmRelativePath, file);
    }

    /**
     * Checks whether the supplied file path matches any of the helm extension .
     *
     * @param filePath path to evaluate
     * @param psiFile PsiFile to evaluate
     * @return {@code true} if a helm pattern matches; {@code false} otherwise
     */

    public  boolean isHelmFile(@NotNull PsiFile psiFile,@NotNull String filePath) {
        if (DevAssistUtils.isYamlFile(psiFile)) {
            if (Constants.RealTimeConstants.CONTAINER_HELM_EXCLUDED_FILES.contains(psiFile.getName().toLowerCase())) {
                return false;
            }
            if(filePath.toLowerCase().contains("/helm/")){
                fileType=Constants.RealTimeConstants.HELM;
                return true;
            };
        }
        return false;
    }

    /**
     * Scans the given Psi file using OssScanner wrapper method.
     *
     * @param psiFile - the file to scan
     * @param uri  - the file path
     * @return ScanResult of type OssRealtimeResults
     */
    @Override
    public ScanResult<ContainersRealtimeResults> scan(PsiFile psiFile, String uri) {
        if (!this.shouldScanFile(uri, psiFile)) {
            return null;
        }
        String tempFolder = super.getTempSubFolderPath(Constants.RealTimeConstants.CONTAINER_REALTIME_SCANNER_DIRECTORY);
        Pair<Path, Path> saveResult = null;
        try {
            Path tempFolderPath = Paths.get(tempFolder);
            this.createTempFolder(tempFolderPath);
            String tempFilePath;
            if (isHelmFile(psiFile, uri)) {
                saveResult = this.saveHelmFile(tempFolderPath, psiFile);
            } else {
                saveResult = this.saveOtherFiles(tempFolderPath, psiFile);
            }
            if (Objects.nonNull(saveResult)) {
                tempFilePath = saveResult.getLeft().toString();
                LOGGER.info("Start Container Realtime Scan On File: " + uri);
                IgnoreManager ignoreManager = IgnoreManager.getInstance(psiFile.getProject());
                String ignoreFilePath = ignoreManager.getIgnoreTempFilePath();
                ContainersRealtimeResults scanResults = CxWrapperFactory.build().containersRealtimeScan(tempFilePath, ignoreFilePath);
                return new ContainerScanResultAdaptor(scanResults, fileType);
            }

        } catch (IOException | CxException | InterruptedException e) {
            LOGGER.warn(this.config.getErrorMessage(), e);
        } finally {
            LOGGER.info("Deleting temporary folder");
            if (Objects.nonNull(saveResult)) {
                deleteTempFolder(saveResult.getRight());
            }
        }
        return null;
    }

}
