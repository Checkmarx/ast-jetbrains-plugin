package com.checkmarx.intellij.ASCA;

import com.checkmarx.ast.asca.ScanResult;
import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.commands.ASCA;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AscaService {

    private static final String ASCA_DIR = "CxASCA";
    private static final Logger LOGGER = Logger.getInstance(AscaService.class);

    public AscaService() {
    }

    /**
     * Runs the ASCA scan on the provided file and returns the ScanResult.
     */
    @Nullable
    public ScanResult runAscaScan(PsiFile file, Project project, boolean ascLatestVersion, String agent) {
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
     */
    @Nullable
    private String getFileContent(PsiFile file, Project project) {
        Document document = PsiDocumentManager.getInstance(project).getDocument(file);

        try {
            if (document != null) {
                return document.getText();
            } else {
                return new String(file.getVirtualFile().contentsToByteArray());
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to retrieve file content:", e);
            return null;
        }
    }

    /**
     * Handles the scan result, logs any errors or violations.
     */
    private void handleScanResult(PsiFile file, ScanResult scanResult) {
        if (scanResult == null || scanResult.getError() != null) {
            String errorDescription = scanResult != null ?
                    scanResult.getError().getDescription() : "Unknown error";
            LOGGER.warn(String.join(": ", "ASCA scan error", errorDescription));
            return;
        }

        String fileName = file.getVirtualFile().getName();
        int violationCount = (scanResult.getScanDetails() != null) ? scanResult.getScanDetails().size() : 0;
        if (violationCount == 0) {
            LOGGER.info(String.join(" ", "No security best practice violations found in", fileName));
        } else {
            String violationMessage = violationCount == 1 ?
                    Strings.join("1 security best practice violation found in ", file.getName()) :
                    violationCount + Strings.join(" security best practice violations found in" + fileName);
            LOGGER.info(String.join(" ", violationMessage, "in", file.getName()));
        }
    }


    /**
     * Saves content to a temporary file.
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
     */
    private boolean ignoreFiles(VirtualFile file) {
        return !file.isInLocalFileSystem();
    }

    /**
     * Installs the ASCA CLI if not already installed.
     */
    public String installAsca() throws CxException, CxConfig.InvalidCLIConfigException, IOException, URISyntaxException, InterruptedException {
        ScanResult res = ASCA.installAsca();
        if (res.getError() != null) {
            LOGGER.warn(Strings.join("ASCA installation error: ", res.getError().getDescription()));
            return res.getError().getDescription();
        }
        return "AI Secure Coding Assistant started.";
    }
}
