package com.checkmarx.intellij.service;

import com.checkmarx.ast.asca.ScanResult;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.commands.ASCA;
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

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Service class for handling ASCA (Application Security Code Analysis) operations.
 */
public class AscaService {

    private static final String ASCA_DIR = "CxASCA";
    private static Logger LOGGER = Utils.getLogger(AscaService.class);

    /**
     * Default constructor for AscaService.
     */
    public AscaService() {
    }

    public AscaService(Logger logger) {
        LOGGER = logger;
    }

    /**
     * Runs the ASCA scan on the provided file and returns the ScanResult.
     *
     * @param file             the file to scan
     * @param project          the current project
     * @param ascLatestVersion whether to use the latest version of ASCA
     * @param agent            the agent name
     * @return the scan result, or null if an error occurs
     */
    @Nullable
    public ScanResult runAscaScan(PsiFile file, Project project, boolean ascLatestVersion, String agent) {
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
            ScanResult scanResult = ASCA.scanAsca(tempFilePath, ascLatestVersion, agent);
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
     * Gets the file content, either from in-memory document or from disk.
     *
     * @param file    the file to get content from
     * @param project the current project
     * @return the file content as a string, or null if an error occurs
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
     *
     * @param file       the file that was scanned
     * @param scanResult the result of the scan
     */
    private void handleScanResult(@NotNull PsiFile file, ScanResult scanResult) {
        if (scanResult == null || scanResult.getError() != null) {
            String errorDescription = scanResult != null ?
                    scanResult.getError().getDescription() : "Unknown error";
            LOGGER.warn(String.join(": ", "ASCA scan error", errorDescription));
            return;
        }

        String fileName = file.getName();
        int violationCount = (scanResult.getScanDetails() != null) ? scanResult.getScanDetails().size() : 0;
        if (violationCount == 0) {
            LOGGER.info(String.join(" ", "No security best practice violations found in", fileName));
        } else {
            String violationMessage = violationCount == 1 ?
                    Strings.join("1 security best practice violation found in ", fileName) :
                    violationCount + Strings.join(" security best practice violations found in" + fileName);
            LOGGER.info(String.join(" ", violationMessage, "in", fileName));
        }
    }

    /**
     * Saves content to a temporary file.
     *
     * @param fileName the name of the file
     * @param content  the content to save
     * @return the path to the temporary file, or null if an error occurs
     */
    @Nullable
    private String saveTempFile(String fileName, String content) {
        try {
            Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), ASCA_DIR);
            Files.createDirectories(tempDir);
            Path tempFilePath = tempDir.resolve(fileName);
            Files.write(tempFilePath, content.getBytes());
            LOGGER.debug("Temp file saved at: " + tempFilePath);
            return tempFilePath.toString();
        } catch (IOException e) {
            LOGGER.warn("Failed to save temporary file:", e);
            return null;
        }
    }

    /**
     * Deletes a file by the given file path.
     *
     * @param filePath the path to the file to delete
     */
    private void deleteFile(String filePath) {
        try {
            Path normalizedPath = Paths.get(filePath).toAbsolutePath().normalize();
            File file = normalizedPath.toFile();
            if (file.exists()) {
                if (file.delete()) {
                    LOGGER.debug(Strings.join("Temporary file ", filePath, " deleted."));
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to delete file", e);
        }
    }

    /**
     * Determines whether the file should be ignored.
     *
     * @param file the file to check
     * @return true if the file should be ignored, false otherwise
     */
    private boolean ignoreFiles(VirtualFile file) {
        return file == null || !file.isInLocalFileSystem();
    }

    /**
     * Installs the ASCA CLI if not already installed.
     *
     * @return a message indicating the result of the installation
     * @throws CxException                        if an error occurs during installation
     * @throws IOException                        if an I/O error occurs
     * @throws URISyntaxException                 if a URI syntax error occurs
     * @throws InterruptedException               if the installation is interrupted
     */
    public boolean installAsca() throws CxException, IOException, URISyntaxException, InterruptedException {
        ScanResult res = ASCA.installAsca();
        if (res.getError() != null) {
            LOGGER.warn(Strings.join("ASCA installation error: ", res.getError().getDescription()));
            return false;
        }
        return true;
    }
}