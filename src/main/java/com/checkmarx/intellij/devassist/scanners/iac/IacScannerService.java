package com.checkmarx.intellij.devassist.scanners.iac;

import com.checkmarx.ast.iacrealtime.IacRealtimeResults;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.basescanner.BaseScannerService;
import com.checkmarx.intellij.devassist.common.ScanResult;
import com.checkmarx.intellij.devassist.configuration.ScannerConfig;
import com.checkmarx.intellij.devassist.telemetry.TelemetryService;
import com.checkmarx.intellij.devassist.utils.DevAssistConstants;
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
    private String fileType;

    public IacScannerService() {
        super(IacScannerService.createConfig());
    }

    public static ScannerConfig createConfig() {
        return ScannerConfig.builder()
                .engineName(ScanEngine.IAC.name())
                .configSection(DevAssistConstants.IAC_REALTIME_SCANNER)
                .activateKey(DevAssistConstants.ACTIVATE_IAC_REALTIME_SCANNER)
                .errorMessage(DevAssistConstants.ERROR_IAC_REALTIME_SCANNER)
                .disabledMessage(DevAssistConstants.IAC_REALTIME_SCANNER_DISABLED)
                .enabledMessage(DevAssistConstants.IAC_REALTIME_SCANNER_START)
                .build();
    }


    /**
     * Determines whether the given file should be scanned based on specific conditions.
     * The method first checks the result of the parent class's {@code shouldScanFile} method.
     * If that result is {@code false}, the file should not be scanned.
     * Otherwise, it evaluates whether the file matches specific IAC (Infrastructure as Code) file patterns.
     *
     * @param filePath the absolute or project-relative path of the file to be considered for scanning.
     * @param psiFile  the PSI (Program Structure Interface) representation of the file.
     * @return {@code true} if the file should be scanned; {@code false} otherwise.
     */
    @Override
    public boolean shouldScanFile(String filePath, PsiFile psiFile) {
        if (!super.shouldScanFile(filePath, psiFile)) {
            return false;
        }
        return this.isIacFilePatternMatching(filePath, psiFile);
    }

    /**
     * Checks if the provided file path and PsiFile correspond to a supported
     * Infrastructure as Code (IAC) file based on predefined patterns and extensions.
     *
     * @param filePath the path of the file to check for pattern matching, given as a string.
     * @param psiFile  an instance of {@link PsiFile}, representing the file to be checked.
     * @return {@code true} if the file matches the defined IAC patterns or supported extensions.
     * Returns {@code false} otherwise.
     */
    private boolean isIacFilePatternMatching(String filePath, PsiFile psiFile) {
        List<PathMatcher> pathMatchers = DevAssistConstants.IAC_SUPPORTED_PATTERNS.stream()
                .map(p -> FileSystems.getDefault().getPathMatcher("glob:" + p))
                .collect(Collectors.toList());
        for (PathMatcher pathMatcher : pathMatchers) {
            if (pathMatcher.matches(Paths.get(filePath.toLowerCase()))) {
                fileType = DevAssistUtils.isDockerFile(filePath.toLowerCase()) ? DevAssistConstants.DOCKERFILE : psiFile.getVirtualFile().getExtension();
                return true;
            }
        }
        String extension = DevAssistUtils.getFileExtension(psiFile);
        if (extension == null) return false;
        fileType = extension.toLowerCase();
        return DevAssistConstants.IAC_FILE_EXTENSIONS.contains(extension);
    }

    /**
     * Creates a subfolder under the specified temporary folder, saves the content of the file
     * into it, and returns the paths for both the newly created file and its parent folder.
     * If the file content is empty or null, a warning is logged and the method returns null.
     *
     * @param tempSubFolder the path to the base temporary folder where the subfolder will be created
     * @param relativePath  the relative path for the new file under the temporary subfolder
     * @param psiFile       the file whose content is to be saved in the created subfolder
     * @return a pair containing the full path of the newly saved file and the path of the subfolder;
     * returns null if the file content is empty or null
     * @throws IOException if an error occurs while creating the folder or writing the file
     */
    private Pair<Path, Path> createSubFolderAndSaveFile(Path tempSubFolder, String relativePath, PsiFile psiFile) throws IOException {
        String fileText = DevAssistUtils.getFileContent(psiFile);
        if (fileText == null || fileText.isBlank()) {
            LOGGER.warn("No content found in file: " + psiFile.getVirtualFile().getPath());
            return null;
        }
        this.createTempFolder(tempSubFolder);
        Path fullTargetPath = Paths.get(tempSubFolder.toString(), relativePath);
        Files.writeString(fullTargetPath, fileText, StandardCharsets.UTF_8);
        return Pair.of(fullTargetPath, tempSubFolder);
    }

    /**
     * Generates a hash for the specified file based on its relative path and the current time.
     * The resulting hash can be used to uniquely identify the file in a temporary context.
     * In case of an exception during hash generation, an alternative fallback approach is used.
     *
     * @param relativePath the relative path of the file used to generate the hash; must not be null
     * @return a 16-character hexadecimal string representing the generated hash
     */
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

    /**
     * Saves the content of a PSI file into a newly created temporary subfolder under the specified
     * temporary folder. The method derives a subfolder name based on the PSI file's name and a
     * generated hash.
     *
     * @param tempFolder the base temporary folder where the subfolder will be created
     * @param psiFile    the PSI (Program Structure Interface) representation of the file to be saved
     * @return a pair containing the path to the newly created subfolder and the full path of the saved file;
     * returns null if the file content is empty or null
     * @throws IOException if an error occurs while creating the folder or saving the file
     */
    private Pair<Path, Path> saveTempFiles(Path tempFolder, PsiFile psiFile) throws IOException {
        String relativePath = psiFile.getName();
        Path tempSubFolder = Paths.get(tempFolder.toString(), psiFile.getName() + "-" + this.generateFileHash(relativePath));
        return this.createSubFolderAndSaveFile(tempSubFolder, relativePath, psiFile);
    }

    /**
     * Performs an IAC (Infrastructure as Code) real-time scan on the given file.
     * This method evaluates whether the file is eligible for scanning, creates a temporary
     * folder to store the file content, executes the scan using a scan engine, and then cleans up
     * the temporary resources upon completion.
     *
     * @param psiFile the PSI (Program Structure Interface) representation of the file to be scanned.
     *                Must not be null.
     * @param uri     the absolute or project-relative URI of the file to be scanned. Must not be null.
     * @return an instance of {@link ScanResult} containing the scan results as {@link IacRealtimeResults},
     * or {@code null} if the
     */
    @Override
    public ScanResult<IacRealtimeResults> scan(@NotNull PsiFile psiFile, @NotNull String uri) {
        if (!this.shouldScanFile(uri, psiFile)) {
            return null;
        }
        String tempFolder = super.getTempSubFolderPath(DevAssistConstants.IAC_REALTIME_SCANNER_DIRECTORY);
        Pair<Path, Path> saveResult = null;
        try {
            Path tempFolderPath = Paths.get(tempFolder);
            this.createTempFolder(tempFolderPath);
            VirtualFile vFile = psiFile.getVirtualFile();
            if (!vFile.exists()) {
                return null;
            }
            String tempFilePath;

            saveResult = this.saveTempFiles(tempFolderPath, psiFile);
            if (Objects.nonNull(saveResult)) {
                tempFilePath = saveResult.getLeft().toString();
                LOGGER.info("Start IAC Realtime Scan On File: " + uri);
                IacRealtimeResults scanResults = CxWrapperFactory.build().iacRealtimeScan(tempFilePath, DevAssistUtils.getContainerTool(), "");
                IacScanResultAdaptor scanResultAdaptor = new IacScanResultAdaptor(scanResults,fileType);
                TelemetryService.logScanResults(scanResultAdaptor, ScanEngine.IAC);
                return scanResultAdaptor;
            }
        } catch (IOException | CxException | InterruptedException e) {
            LOGGER.warn(this.config.getErrorMessage(), e);
        } finally {
            LOGGER.debug("Deleting temporary folder");
            if (Objects.nonNull(saveResult)) {
                deleteTempFolder(saveResult.getRight());
            }
        }
        return null;
    }


}
