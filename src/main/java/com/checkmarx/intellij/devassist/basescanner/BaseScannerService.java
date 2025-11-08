package com.checkmarx.intellij.devassist.basescanner;

import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.configuration.ScannerConfig;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

public class BaseScannerService<T> implements ScannerService<T>{
    public ScannerConfig config;
    private static final Logger LOGGER = Utils.getLogger(BaseScannerService.class);

    public BaseScannerService(ScannerConfig config){
        this.config=config;
    }

    public  ScannerConfig getConfig(){
      return this.config;
    }

    public boolean shouldScanFile(String filePath) {
        return !filePath.contains("/node_modules/");
    }

     public  T scan(PsiFile psiFile, String uri) {
         return null;
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
            LOGGER.warn("Cannot create temp folder",e);

        }
     }

     protected void deleteTempFolder(Path tempFolder){
         VirtualFile tempFileDir = LocalFileSystem.getInstance().findFileByPath(tempFolder.toString());
         CompletableFuture.runAsync(() -> {
             ApplicationManager.getApplication().invokeAndWait(() -> {
                 WriteAction.run(() -> {
                     try {
                         if (tempFileDir != null && tempFileDir.exists()) {
                             tempFileDir.delete(this);
                         }
                     } catch (IOException e) {
                         LOGGER.warn("Cannot delete the folder: " + tempFileDir);
                     }
                 });
             });
         }).thenRun(() -> LOGGER.info("Temp folder deleted"));
     }
}
