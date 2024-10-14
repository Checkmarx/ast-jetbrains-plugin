package com.checkmarx.intellij.ASCA;

import com.checkmarx.ast.asca.ScanResult;
import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.commands.ASCA;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
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
        // Default constructor
    }

    /**
     * Runs the ASCA scan on the provided file and returns the ScanResult.
     *
     * @param file            the file to scan.
     * @param ascLatestVersion whether to use the latest version of the ASCA agent.
     * @param agent           the agent name to use.
     * @return the result of the ASCA scan, or null if the scan failed.
     */
    @Nullable
    public ScanResult runAscaScan(PsiFile file, Project project, boolean ascLatestVersion, String agent) {
        // Check if the file should be ignored
        if (ignoreFiles(file.getVirtualFile())) {
            return null;
        }

        // Get the document (in-memory representation of the file) to capture unsaved changes
        Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        String fileContent;

        try {
            // If document exists, use the in-memory content (unsaved changes)
            if (document != null) {
                fileContent = document.getText();
            } else {
                // If document does not exist, use the saved content from the file on disk
                fileContent = new String(file.getVirtualFile().contentsToByteArray());
            }

            // Save the content to a temporary file
            String tempFilePath = saveTempFile(file.getName(), fileContent);
            if (tempFilePath == null) {
                LOGGER.error("Failed to create temporary file for ASCA scan.");
                return null;
            }

            // Run the ASCA scan on the temporary file
            LOGGER.info("Start ASCA scan on file: " + file.getVirtualFile().getPath());
            ScanResult scanResult = ASCA.scanAsca(tempFilePath, ascLatestVersion, agent);

            // Clean up the temporary file
            deleteFile(tempFilePath);
            LOGGER.info("Temporary file " + tempFilePath + " deleted.");

            // Handle scan errors if any
            if (scanResult.getError() != null) {
                LOGGER.warn("ASCA Warning: " + (scanResult.getError().getDescription() != null ?
                        scanResult.getError().getDescription() : scanResult.getError()));
                return null;
            }

            // Log the scan details or absence of violations
            if (scanResult.getScanDetails() == null) {
                LOGGER.info("No security best practice violations found in " + file.getVirtualFile().getPath());
            } else {
                LOGGER.info(scanResult.getScanDetails().size() + " security best practice violations found in " + file.getVirtualFile().getPath());
            }

            return scanResult;

        } catch (IOException | CxConfig.InvalidCLIConfigException | URISyntaxException | CxException | InterruptedException e) {
            LOGGER.warn("Error during ASCA scan:", e);
            return null;
        }
    }

    private boolean ignoreFiles(VirtualFile file) {
        // Ignore non-local files
        return !file.isInLocalFileSystem();
    }

    private String saveTempFile(String fileName, String content) {
        try {
            Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), ASCA_DIR);
            Files.createDirectories(tempDir);
            Path tempFilePath = tempDir.resolve(fileName);
            Files.write(tempFilePath, content.getBytes());
            LOGGER.info("Temp file was saved in: " + tempFilePath);
            return tempFilePath.toString();
        } catch (IOException e) {
            LOGGER.error("Failed to save temporary file:", e);
            return null;
        }
    }

    private void deleteFile(String filePath) {
        try {
            File file = new File(filePath);
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to delete file", e);
        }
    }

    /**
     * Installs the ASCA CLI if not already installed.
     */
    public String installAsca() throws CxException, CxConfig.InvalidCLIConfigException, IOException, URISyntaxException, InterruptedException {
            ScanResult res = ASCA.installAsca();
            if (res.getError() != null) {
                LOGGER.error("ASCA Installation Error: " + res.getError().getDescription());
                return res.getError().getDescription();
            }
            return "AI Secure Coding Assistant started.";
    }
}
