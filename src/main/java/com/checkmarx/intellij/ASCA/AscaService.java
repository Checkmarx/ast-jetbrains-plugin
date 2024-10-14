package com.checkmarx.intellij.ASCA;

import com.checkmarx.ast.asca.ScanResult;
import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.commands.ASCA;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
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
     * @param project         the IntelliJ project context.
     * @param ascLatestVersion whether to use the latest version of the ASCA agent.
     * @param agent           the agent name to use.
     * @return the result of the ASCA scan, or null if the scan failed.
     */
    @Nullable
    public ScanResult runAscaScan(VirtualFile file, Project project, boolean ascLatestVersion, String agent) {
        if (ignoreFiles(file)) {
            return null;
        }

        try {
            // Save the file temporarily
            String filePath = saveTempFile(file.getName(), new String(file.contentsToByteArray()));

            // Run the ASCA scan
            LOGGER.info("Start ASCA scan on file: " + file.getPath());
            ScanResult scanResult = ASCA.scanAsca(filePath, ascLatestVersion, agent);

            // Delete the temporary file
            deleteFile(filePath);
            LOGGER.info("File " + filePath + " deleted.");

            // Handle errors if any
            if (scanResult.getError() != null) {
                LOGGER.warn("ASCA Warning: " + (scanResult.getError().getDescription() != null ?
                        scanResult.getError().getDescription() : scanResult.getError()));
                return null;
            }
            if (scanResult.getScanDetails() == null) {
                LOGGER.info("No security best practice violations found in " + file.getPath());
                return scanResult;
            } else LOGGER.info(scanResult.getScanDetails().size() + " security best practice violations found in " + file.getPath());
            return scanResult;

        } catch (IOException | CxConfig.InvalidCLIConfigException | URISyntaxException | CxException | InterruptedException e) {
            LOGGER.error("Error during ASCA scan.", e);
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
    public void installAsca() {
        try {
            ScanResult res = ASCA.installAsca();
            if (res.getError() != null) {
                String errorMessage = "ASCA Installation Error: " + res.getError().getDescription();
                LOGGER.error(errorMessage);
            }
        } catch (Exception e) {
            LOGGER.error("Error during ASCA installation.", e);
        }
    }
}
