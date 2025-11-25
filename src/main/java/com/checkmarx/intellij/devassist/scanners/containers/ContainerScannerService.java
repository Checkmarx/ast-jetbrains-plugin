package com.checkmarx.intellij.devassist.scanners.containers;

import com.checkmarx.ast.containersrealtime.ContainersRealtimeResults;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.basescanner.BaseScannerService;
import com.checkmarx.intellij.devassist.basescanner.ScannerService;
import com.checkmarx.intellij.devassist.common.ScanResult;
import com.checkmarx.intellij.devassist.configuration.ScannerConfig;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.checkmarx.intellij.settings.global.CxWrapperFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
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
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class ContainerScannerService extends BaseScannerService<ContainersRealtimeResults> {

    private static final Logger LOGGER = Utils.getLogger(ContainerScannerService.class);

    /**
     * Creates a new scanner service with the  configuration.
     */


    public ContainerScannerService() {
        super(createConfig());
    }

    public static ScannerConfig createConfig() {
        return ScannerConfig.builder()
                .engineName(ScanEngine.CONTAINERS.name())
                .configSection(Constants.RealTimeConstants.CONTAINER_REALTIME_SCANNER)
                .activateKey(Constants.RealTimeConstants.ACTIVATE_CONTAINER_REALTIME_SCANNER)
                .enabledMessage(Constants.RealTimeConstants.CONTAINER_REALTIME_SCANNER_START)
                .disabledMessage(Constants.RealTimeConstants.CONTAINER_REALTIME_SCANNER_DISABLED)
                .errorMessage(Constants.RealTimeConstants.ERROR_CONTAINER_REALTIME_SCANNER).build();
    }


    public boolean shouldScanFile(String filePath, PsiFile psiFile) {
        if (!super.shouldScanFile(filePath, psiFile)) {
            return false;
        }
        return isContainersFilePatternMatching(filePath) || isHelmFilePatternMatching(filePath, psiFile);
    }

    private boolean isHelmFilePatternMatching(String filePath, PsiFile psiFile) {
        VirtualFile vFile = psiFile.getVirtualFile();
        if (!vFile.exists()) {
            return false;
        }
        String fileExtension = vFile.getExtension();
        boolean isYamlFile = Objects.nonNull(fileExtension) && Constants.RealTimeConstants.CONTAINER_HELM_EXTENSION.contains(fileExtension.toLowerCase());
        if (isYamlFile) {
            LOGGER.info("Its yaml");
            if (Constants.RealTimeConstants.CONTAINER_HELM_EXCLUDED_FILES.contains(psiFile.getName())) {
                return false;
            }
            return filePath.toLowerCase().contains("/helm/");
        }
        return false;
    }

    private boolean isContainersFilePatternMatching(String filePath) {
        List<PathMatcher> pathMatchers = Constants.RealTimeConstants.CONTAINERS_FILE_PATTERNS.stream().map(f -> FileSystems.getDefault().getPathMatcher("glob:" + f)).collect(Collectors.toList());
        for (PathMatcher pathMatcher : pathMatchers) {
            if (pathMatcher.matches(Paths.get(filePath))) {
                return true;
            }
        }
        return false;
    }

    private boolean isDockerComposeFile(String filePath) {
        return Paths.get(filePath).getFileName().toString().toLowerCase().contains("docker-compose");
    }

    @Override
    public ScannerConfig getConfig() {
        return createConfig();
    }

    private String generateFileHash(@NotNull String relativePath) {
        try {
            LocalTime time = LocalTime.now();
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
            LOGGER.debug("Using alternative method of generating hashCode for temporary file");
            return Integer.toHexString((relativePath + System.currentTimeMillis()).hashCode());
        }

    }

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

    @Override
    public ScanResult<ContainersRealtimeResults> scan(PsiFile psiFile, String uri) {

        if (!this.shouldScanFile(uri, psiFile)) {
            LOGGER.info("Returning from here");
            return null;
        }

        String tempFolder = super.getTempSubFolderPath(Constants.RealTimeConstants.CONTAINER_REALTIME_SCANNER_DIRECTORY);
        try {
            Path tempFolderPath = Paths.get(tempFolder);
            this.createTempFolder(tempFolderPath);
            VirtualFile vFile = psiFile.getVirtualFile();
            if (!vFile.exists()) {
                return null;
            }
            String fileExtension = vFile.getExtension();

            String tempFilePath;
            Pair<Path, Path> saveResult;
            boolean isYamlFile = Objects.nonNull(fileExtension) && Constants.RealTimeConstants.CONTAINER_HELM_EXTENSION.contains(fileExtension.toLowerCase());
            if (isYamlFile && !isDockerComposeFile(uri)) {
                saveResult = this.saveHelmFile(tempFolderPath, psiFile);
            } else {
                saveResult = this.saveOtherFiles(tempFolderPath, psiFile);
            }
            if (Objects.nonNull(saveResult)) {
                tempFilePath = saveResult.getLeft().toString();
                LOGGER.info("Start Container Realtime Scan On File: " + uri);
                ContainersRealtimeResults scanResults = CxWrapperFactory.build().containersRealtimeScan(tempFilePath, "");
                return new ContainerScanResultAdaptor(scanResults);
            }

        } catch (IOException | CxException | InterruptedException e) {
            LOGGER.warn(this.config.getErrorMessage(), e);
        }
        return null;

    }


}
