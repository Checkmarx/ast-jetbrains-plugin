package com.checkmarx.intellij.devassist.scanners.asca;

import com.checkmarx.ast.asca.ScanResult;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.basescanner.BaseScannerService;
import com.checkmarx.intellij.devassist.configuration.ScannerConfig;
import com.checkmarx.intellij.devassist.telemetry.TelemetryService;
import com.checkmarx.intellij.devassist.utils.DevAssistConstants;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.checkmarx.intellij.settings.global.CxWrapperFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Realtime ASCA scanner service that integrates with the realtime scanner system.
 * This service includes all ASCA scanning logic consolidated from the legacy AscaService.
 */
public class AscaScannerService extends BaseScannerService<ScanResult> {
    private static final Logger LOGGER = Utils.getLogger(AscaScannerService.class);
    private static final String ASCA_DIR = "CxASCA";

    /**
     * Creates an ASCA scanner service with the default ASCA realtime configuration.
     */
    public AscaScannerService() {
        super(createConfig());
    }

    /**
     * Builds the default scanner configuration used for ASCA realtime scanning.
     *
     * @return fully populated {@link ScannerConfig} instance for the ASCA engine
     */
    public static ScannerConfig createConfig() {
        return ScannerConfig.builder()
                .engineName(ScanEngine.ASCA.name())
                .configSection(DevAssistConstants.ASCA_REALTIME_SCANNER)
                .activateKey(DevAssistConstants.ACTIVATE_ASCA_REALTIME_SCANNER)
                .errorMessage(DevAssistConstants.ERROR_ASCA_REALTIME_SCANNER)
                .disabledMessage(DevAssistConstants.ASCA_REALTIME_SCANNER_DISABLED)
                .enabledMessage(DevAssistConstants.ASCA_REALTIME_SCANNER_START)
                .build();
    }

    /**
     * Determines whether a file should be scanned by ASCA.
     * ASCA only scans files with supported programming language extensions.
     *
     * @param filePath absolute path to the file
     * @param psiFile the PSI file object
     * @return {@code true} if the file should be scanned; {@code false} otherwise
     */
    @Override
    public boolean shouldScanFile(String filePath, PsiFile psiFile) {
        if (!super.shouldScanFile(filePath, psiFile)) {
            LOGGER.debug("ASCA scanner: file not eligible (base filter) - " + filePath);
            return false;
        }

        // ASCA only scans files with supported extensions (inclusion-based filtering)
        if (!hasSupportedExtension(filePath, psiFile)) {
            LOGGER.debug("ASCA scanner: unsupported file extension - " + filePath);
            return false;
        }

        LOGGER.debug("ASCA scanner: file eligible - " + filePath);
        return true;
    }

    /**
     * Checks if the file has a supported extension for ASCA scanning.
     * Based on VSCode implementation: only scan .java, .cs, .go, .py, .js, .jsx files.
     *
     * @param filePath path to the file
     * @param psiFile the PSI file object
     * @return {@code true} if the file has a supported extension; {@code false} otherwise
     */
    private boolean hasSupportedExtension(String filePath, PsiFile psiFile) {
        // Get file extension from virtual file (more reliable than path parsing)
        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile == null) {
            LOGGER.debug("ASCA scanner: virtual file is null for " + filePath);
            return false;
        }

        String extension = virtualFile.getExtension();
        if (extension == null) {
            LOGGER.debug("ASCA scanner: no file extension found for " + filePath);
            return false;
        }

        boolean isSupported = DevAssistConstants.ASCA_SUPPORTED_EXTENSIONS.contains(extension.toLowerCase());

        if (!isSupported) {
            LOGGER.debug("ASCA scanner: extension '" + extension + "' not in supported list: " +
                DevAssistConstants.ASCA_SUPPORTED_EXTENSIONS);
        }

        return isSupported;
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
            ScanResult ascaResult = runAscaScan(psiFile, psiFile.getProject(), true, "JetBrains");

            if (ascaResult == null) {
                LOGGER.debug("ASCA scanner: no results returned - " + uri);
                return null;
            }

            int issueCount = ascaResult.getScanDetails() != null ? ascaResult.getScanDetails().size() : 0;
            LOGGER.debug("ASCA scanner: scan completed - " + uri + " (" + issueCount + " issues found)");

            AscaScanResultAdaptor scanResultAdaptor = new AscaScanResultAdaptor(ascaResult, uri);
            TelemetryService.logScanResults(scanResultAdaptor, ScanEngine.ASCA);
            return scanResultAdaptor;

        } catch (Exception e) {
            LOGGER.warn("ASCA scanner: scan error for file: " + uri, e);
            return null;
        }
    }

    /**
     * Runs the ASCA scan on the provided file and returns the ScanResult.
     * Consolidated from legacy AscaService.
     *
     * @param file             the file to scan
     * @param project          the current project
     * @param ascLatestVersion whether to use the latest version of ASCA
     * @param agent            the agent name
     * @return the scan result, or null if an error occurs
     */
    @Nullable
    private ScanResult runAscaScan(PsiFile file, Project project, boolean ascLatestVersion, String agent) {
        if (file == null) {
            return null;
        }

        VirtualFile virtualFile = file.getVirtualFile();

        if (ignoreFiles(virtualFile)) {
            return null;
        }

        String fileContent = getFileContent(file, project);
        if (fileContent == null) {
            return null;
        }

        String tempFilePath = saveTempFile(file.getName(), fileContent);
        if (tempFilePath == null) {
            LOGGER.warn("Failed to create temporary file for ASCA scan.");
            return null;
        }

        try {
            LOGGER.info(Strings.join("Starting ASCA scan on file: ", virtualFile.getPath()));
            ScanResult scanResult = scanAscaFile(tempFilePath, ascLatestVersion, agent, DevAssistUtils.getIgnoreFilePath(project));
            handleScanResult(file, scanResult);
            return scanResult;
        } catch (Exception e) {
            LOGGER.warn("Error during ASCA scan:", e);
            return null;
        } finally {
            deleteFile(tempFilePath);
        }
    }

    /**
     * Calls the ASCA CLI to scan a file.
     * Consolidated from ASCA command class.
     */
    private ScanResult scanAscaFile(String path, boolean ascaLatestVersion, String agent, String ignoreFilePath)
            throws IOException, CxException, InterruptedException {
        return CxWrapperFactory.build().ScanAsca(path, ascaLatestVersion, agent,null);
    }

    /**
     * Gets the file content, either from in-memory document or from disk.
     */
    private String getFileContent(PsiFile file, Project project) {
        return ApplicationManager.getApplication().runReadAction((Computable<String>) () -> {
            Document document = PsiDocumentManager.getInstance(project).getDocument(file);
            if (document != null) {
                return document.getText();
            }

            VirtualFile virtualFile = file.getVirtualFile();
            if (virtualFile == null) {
                LOGGER.warn("Virtual file is null for the given PsiFile.");
                return null;
            }

            try {
                return new String(virtualFile.contentsToByteArray());
            } catch (IOException e) {
                LOGGER.warn("Failed to retrieve file content from virtual file:", e);
                return null;
            }
        });
    }

    /**
     * Handles the scan result, logs any errors or violations.
     */
    private void handleScanResult(@NotNull PsiFile file, ScanResult scanResult) {
        if (scanResult == null || scanResult.getError() != null) {
            String errorDescription = scanResult != null ?
                    scanResult.getError().getDescription() : "Unknown error";
            LOGGER.warn(String.join(": ", "ASCA scan error", errorDescription));
            return;
        }

        if (scanResult.getScanDetails() != null && !scanResult.getScanDetails().isEmpty()) {
            LOGGER.info("ASCA scan completed successfully with " + scanResult.getScanDetails().size() + " findings.");
        } else {
            LOGGER.info("ASCA scan completed - no issues found.");
        }
    }

    /**
     * Gets a secure temporary directory path for ASCA operations.
     * Uses normalized and validated paths to prevent directory traversal attacks.
     *
     * @return secure temp directory path
     * @throws SecurityException if temp directory cannot be validated
     */
    private Path getSecureTempDirectory() throws SecurityException {
        try {
            // Get system temp directory with validation
            String tempOSPath = System.getProperty("java.io.tmpdir");
            if (tempOSPath == null || tempOSPath.trim().isEmpty()) {
                throw new SecurityException("System temp directory is not available");
            }

            // Create and validate the base temp directory
            Path baseTempDir = Paths.get(tempOSPath).toAbsolutePath().normalize();

            // Ensure it's a real directory that exists
            if (!Files.exists(baseTempDir) || !Files.isDirectory(baseTempDir)) {
                throw new SecurityException("System temp directory is not valid: " + baseTempDir);
            }

            // Create ASCA subdirectory with safe name
            Path ascaTempDir = baseTempDir.resolve(ASCA_DIR).normalize();

            // Security check: ensure the ASCA dir is still within system temp
            if (!ascaTempDir.startsWith(baseTempDir)) {
                throw new SecurityException("ASCA temp directory would be outside system temp");
            }

            return ascaTempDir;

        } catch (Exception e) {
            throw new SecurityException("Failed to create secure temp directory", e);
        }
    }

    /**
     * Saves the file content to a temporary file for ASCA scanning.
     */
    private String saveTempFile(String fileName, String fileContent) {
        try {
            // Get secure temp directory
            Path tempDir = getSecureTempDirectory();
            createTempFolder(tempDir);

            // Sanitize fileName to prevent directory traversal attacks
            String sanitizedFileName = sanitizeFileName(fileName);

            // Create secure path with normalization
            Path tempFilePath = tempDir.resolve(sanitizedFileName).normalize();

            // Security check: ensure the resolved path is still within the temp directory
            if (!tempFilePath.startsWith(tempDir)) {
                LOGGER.warn("Security violation: Attempt to write file outside temp directory: " + fileName);
                return null;
            }

            Files.write(tempFilePath, fileContent.getBytes());

            LOGGER.debug("Temporary file created: " + tempFilePath.toAbsolutePath());
            return tempFilePath.toAbsolutePath().toString();
        } catch (SecurityException e) {
            LOGGER.error("Security error creating temp file: " + e.getMessage(), e);
            return null;
        } catch (IOException e) {
            LOGGER.warn("Failed to save temporary file:", e);
            return null;
        }
    }

    /**
     * Sanitizes the file name to prevent directory traversal attacks.
     * Removes or replaces dangerous characters and path separators.
     *
     * @param fileName the original file name
     * @return sanitized file name safe for file system operations
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return "temp_file.tmp";
        }

        // Remove path separators and dangerous characters
        String sanitized = fileName.replaceAll("[/\\\\:*?\"<>|]", "_")
                                  .replaceAll("\\.\\.+", ".") // Replace multiple dots with single dot
                                  .trim();

        // Ensure file name is not empty after sanitization
        if (sanitized.isEmpty() || sanitized.equals(".") || sanitized.equals("..")) {
            sanitized = "temp_file.tmp";
        }

        // Limit length to prevent filesystem issues
        if (sanitized.length() > 255) {
            String extension = "";
            int lastDot = sanitized.lastIndexOf('.');
            if (lastDot > 0) {
                extension = sanitized.substring(lastDot);
                sanitized = sanitized.substring(0, Math.min(255 - extension.length(), lastDot));
            } else {
                sanitized = sanitized.substring(0, 255);
            }
            sanitized = sanitized + extension;
        }

        LOGGER.debug("Sanitized file name: '" + fileName + "' -> '" + sanitized + "'");
        return sanitized;
    }

    /**
     * Deletes the temporary file after scanning.
     */
    private void deleteFile(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return;
        }

        try {
            Path path = Paths.get(filePath).toAbsolutePath().normalize();

            // Security check: ensure we're only deleting files in temp directory
            Path tempDir = getSecureTempDirectory();

            if (!path.startsWith(tempDir)) {
                LOGGER.warn("Security violation: Attempt to delete file outside temp directory: " + filePath);
                return;
            }

            Files.deleteIfExists(path);
            LOGGER.debug("Temporary file deleted: " + path);
        } catch (SecurityException e) {
            LOGGER.error("Security error deleting temp file: " + e.getMessage(), e);
        } catch (IOException e) {
            LOGGER.warn("Failed to delete temporary file: " + filePath, e);
        } catch (Exception e) {
            LOGGER.warn("Unexpected error deleting temporary file: " + filePath, e);
        }
    }

    /**
     * Checks if files should be ignored for ASCA scanning.
     */
    private boolean ignoreFiles(VirtualFile file) {
        return file == null || !file.isInLocalFileSystem();
    }

    /**
     * Installs the ASCA CLI if not already installed.
     * Consolidated from legacy AscaService.
     *
     * @return true if installation successful, false otherwise
     */
    public boolean installAsca() {
        try {
            ScanResult res = CxWrapperFactory.build().ScanAsca("", true, Constants.JET_BRAINS_AGENT_NAME,null);
            if (res.getError() != null) {
                LOGGER.warn(Strings.join("ASCA installation error: ", res.getError().getDescription()));
                return false;
            }
            return true;
        } catch (Exception e) {
            LOGGER.warn("ASCA installation failed", e);
            return false;
        }
    }
}
