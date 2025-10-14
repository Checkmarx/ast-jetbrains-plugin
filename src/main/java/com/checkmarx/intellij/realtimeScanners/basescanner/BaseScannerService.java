package com.checkmarx.intellij.realtimeScanners.basescanner;

import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.realtimeScanners.configuration.ScannerConfig;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class BaseScannerService implements ScannerService{
    public ScannerConfig config;
    private static final Logger LOGGER = Utils.getLogger(BaseScannerService.class);

    public BaseScannerService(ScannerConfig config){
        this.config=config;
    }

    public boolean shouldScanFile(String filePath) {
        // TODO:  check if its file
        return !filePath.contains("/node_modules/");
    }

     public void scan(Document document, String uri) {
     }

     protected String getTempSubFolderPath(String baseDir) {
        String tempOS= System.getProperty("java.io.tmpdir");
        Path tempDir= Paths.get(tempOS,baseDir);
        return tempDir.toString();
     }

     protected void createTempFolder(Path folderPath){
        try{
            Files.createDirectories(folderPath);
        } catch (IOException e){
            //TODO: improve the below logic and warning
            LOGGER.warn("Cannot create temp folder");
            e.printStackTrace();
        }
     }

     protected void deleteTempFolder(Path tempFolder){
         ApplicationManager.getApplication().runWriteAction(() -> {
             VirtualFile dir = LocalFileSystem.getInstance().findFileByPath(tempFolder.toString());
             try {
                 if (dir != null && dir.exists()) {
                     dir.delete(this);
                 }
             } catch (IOException e) {
                 LOGGER.warn("Cannot delete the folder: "+dir);
             }
         });
     }
}
