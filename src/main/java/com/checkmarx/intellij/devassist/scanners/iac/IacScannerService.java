package com.checkmarx.intellij.devassist.scanners.iac;

import com.checkmarx.ast.containersrealtime.ContainersRealtimeResults;
import com.checkmarx.ast.iacrealtime.IacRealtimeResults;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.basescanner.BaseScannerService;
import com.checkmarx.intellij.devassist.common.ScanResult;
import com.checkmarx.intellij.devassist.configuration.ScannerConfig;
import com.checkmarx.intellij.devassist.scanners.containers.ContainerScanResultAdaptor;
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
import java.util.stream.Collectors;

public class IacScannerService extends BaseScannerService<IacRealtimeResults> {

    private static final Logger LOGGER = Utils.getLogger(IacScannerService.class);


    public  IacScannerService(){
        super(IacScannerService.createConfig());
    }

    public static ScannerConfig createConfig(){
        return ScannerConfig.builder()
                .engineName(ScanEngine.IAC.name())
                .configSection(Constants.RealTimeConstants.IAC_REALTIME_SCANNER)
                .activateKey(Constants.RealTimeConstants.ACTIVATE_IAC_REALTIME_SCANNER)
                .errorMessage(Constants.RealTimeConstants.ERROR_IAC_REALTIME_SCANNER)
                .disabledMessage(Constants.RealTimeConstants.IAC_REALTIME_SCANNER_DISABLED)
                .enabledMessage(Constants.RealTimeConstants.IAC_REALTIME_SCANNER_START)
                .build();
    }


    public boolean shouldScanFile(String filePath, PsiFile psiFile) {
        if (!super.shouldScanFile(filePath,psiFile)) {
            return false;
        }
        return this.isIacFilePatternMatching(filePath,psiFile);
    }

    private boolean isIacFilePatternMatching(String filePath, PsiFile psiFile) {
        List<PathMatcher> pathMatchers = Constants.RealTimeConstants.IAC_SUPPORTED_PATTERNS.stream()
                .map(p -> FileSystems.getDefault().getPathMatcher("glob:" + p))
                .collect(Collectors.toList());

        for (PathMatcher pathMatcher : pathMatchers) {
            if (pathMatcher.matches(Paths.get(filePath.toLowerCase()))) {
                return true;
            }
        }
        VirtualFile vFile = psiFile.getVirtualFile();
        if (!vFile.exists()) {
            return false;
        }
        String fileExtension = vFile.getExtension();
        return Objects.nonNull(fileExtension) && Constants.RealTimeConstants.IAC_FILE_EXTENSIONS.contains(fileExtension.toLowerCase());
    }

    private Pair<Path, Path> createSubFolderAndSaveFile(Path tempSubFolder, String relativePath, PsiFile psiFile) throws IOException {
        String fileText = DevAssistUtils.getFileContent(psiFile);
        if (fileText == null || fileText.isBlank()) {
            LOGGER.warn("No content found in file: "+psiFile.getVirtualFile().getPath());
            return null;
        }
        this.createTempFolder(tempSubFolder);
        Path fullTargetPath = Paths.get(tempSubFolder.toString(), relativePath);
        Files.writeString(fullTargetPath, fileText, StandardCharsets.UTF_8);
        return Pair.of(fullTargetPath, tempSubFolder);
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

    private Pair<Path,Path>saveTempFiles(Path tempFolder, PsiFile psiFile) throws  IOException{
        String relativePath = psiFile.getName();
        Path tempSubFolder = Paths.get(tempFolder.toString(), psiFile.getName() + "-" + this.generateFileHash(relativePath));
        return this.createSubFolderAndSaveFile(tempSubFolder, relativePath, psiFile);
    }

    @Override
    public ScanResult<IacRealtimeResults> scan(@NotNull PsiFile psiFile, @NotNull String uri){
        if (!this.shouldScanFile(uri, psiFile)) {
            return null;
        }
        String tempFolder = super.getTempSubFolderPath(Constants.RealTimeConstants.IAC_REALTIME_SCANNER_DIRECTORY);
        Pair<Path, Path> saveResult = null;
        try{
            Path tempFolderPath= Paths.get(tempFolder);
            this.createTempFolder(tempFolderPath);
            VirtualFile vFile = psiFile.getVirtualFile();
            if (!vFile.exists()) {
                return null;
            } String tempFilePath;

            saveResult = this.saveTempFiles(tempFolderPath, psiFile);
            if(Objects.nonNull(saveResult)){
                tempFilePath = saveResult.getLeft().toString();
                LOGGER.info("Start IAC Realtime Scan On File: " + uri);
                IacRealtimeResults scanResults = CxWrapperFactory.build().iacRealtimeScan(tempFilePath, "podman","");
                LOGGER.info("ScanResults:"+scanResults);
                return null;
            }
        }
        catch (IOException | CxException | InterruptedException e) {
            LOGGER.warn(this.config.getErrorMessage(), e);
        }
        finally {
            LOGGER.info("Deleting temporary folder");
            if (Objects.nonNull(saveResult)) {
                deleteTempFolder(saveResult.getRight());
            }
        }
        return null;
    }


}
