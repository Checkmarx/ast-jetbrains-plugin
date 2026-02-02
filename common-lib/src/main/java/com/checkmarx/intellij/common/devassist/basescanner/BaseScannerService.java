package com.checkmarx.intellij.common.devassist.basescanner;

import com.checkmarx.intellij.common.utils.Utils;
import com.checkmarx.intellij.common.devassist.common.ScanResult;
import com.checkmarx.intellij.common.devassist.configuration.ScannerConfig;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Base implementation of {@link ScannerService} that wires respective ScannerConig called
 * from different scannerServices
 * provides helpers for deciding when to scan files and scanners managing temporary  folders.
 * @param <T> is type of ScanResult produced by concrete scanner Scan method implementations
 */

@Getter
public class BaseScannerService<T> implements ScannerService<T> {
    public ScannerConfig config;
    private static final Logger LOGGER = Utils.getLogger(BaseScannerService.class);

    /**
     * Creates a new scanner service with the supplied configuration.
     *
     * @param config configuration values to be used by the scanner
     */
    public BaseScannerService(ScannerConfig config) {
        this.config = config;
    }


    /**
     * Determines whether the file at the given path should be scanned.
     * Files inside {@code /node_modules/} are skipped by default.
     *
     * @param filePath absolute or project-relative file path
     * @return {@code true} if the file should be scanned; {@code false} otherwise
     */
    public boolean shouldScanFile(String filePath, PsiFile psiFile) {
        return !filePath.contains("/node_modules/");
    }


    /**
     * Performs a scan of the supplied PSI file.
     * Subclasses are expected to override this method with concrete logic.
     *
     * @param psiFile IntelliJ PSI representation of the file to scan
     * @param uri     URI identifying the file/location
     * @return scan result for the file, or {@code null} if not implemented
     */
    public ScanResult<T> scan(PsiFile psiFile, String uri) {
        return null;
    }



    /**
     * Builds the path to a temporary sub-folder within the system temp directory.
     *
     * @param baseDir name of the sub-folder to create under {@code java.io.tmpdir}
     * @return absolute path string for the temporary sub-folder
     */
    protected String getTempSubFolderPath(String baseDir) {
        String tempOS = System.getProperty("java.io.tmpdir");
        Path tempDir = Paths.get(tempOS, baseDir);
        return tempDir.toString();
    }


    /**
     * Ensures that the specified temporary folder exists, creating any missing directories.
     *
     * @param folderPath target temporary folder path
     */
    protected void createTempFolder(@NotNull Path folderPath) {
        try {
            Files.createDirectories(folderPath);
        } catch (IOException e) {
            LOGGER.warn("Failed to create temporary folder:"+ folderPath, e);
        }
    }





    /**
     * Recursively deletes the provided temporary folder and files in it, if it has been created.
     *
     * @param tempFolder root path of the temporary folder to remove
     */
    protected void deleteTempFolder( @NotNull Path tempFolder) {
        if (Files.notExists(tempFolder)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(tempFolder)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception e) {
                            LOGGER.warn("Failed to delete file in temp folder:" + path);
                        }
                    });
        } catch (IOException e) {
            LOGGER.warn("Failed to delete temporary folder:" + tempFolder);
        }
    }
}
