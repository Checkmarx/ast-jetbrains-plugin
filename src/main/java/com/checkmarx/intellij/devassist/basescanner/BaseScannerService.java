package com.checkmarx.intellij.devassist.basescanner;

import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.common.ScanResult;
import com.checkmarx.intellij.devassist.configuration.ScannerConfig;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

public class BaseScannerService<T> implements ScannerService<T> {
    public ScannerConfig config;
    private static final Logger LOGGER = Utils.getLogger(BaseScannerService.class);

    public BaseScannerService(ScannerConfig config) {
        this.config = config;
    }

    public ScannerConfig getConfig() {
        return this.config;
    }

    public boolean shouldScanFile(String filePath) {
        return !filePath.contains("/node_modules/");
    }

    public ScanResult<T> scan(PsiFile psiFile, String uri) {
        return null;
    }

    protected String getTempSubFolderPath(String baseDir) {
        String tempOS = System.getProperty("java.io.tmpdir");
        Path tempDir = Paths.get(tempOS, baseDir);
        return tempDir.toString();
    }

    protected void createTempFolder(Path folderPath) {
        try {
            Files.createDirectories(folderPath);
        } catch (IOException e) {
            LOGGER.warn("Cannot create temp folder", e);
        }
    }

    protected void deleteTempFolder(Path tempFolder) {
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
