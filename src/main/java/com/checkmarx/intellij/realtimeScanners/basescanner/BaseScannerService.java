package com.checkmarx.intellij.realtimeScanners.basescanner;

import com.checkmarx.intellij.realtimeScanners.configuration.ScannerConfig;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;

import java.util.concurrent.CompletableFuture;

public class BaseScannerService implements ScannerService{
  public ScannerConfig config;

    public BaseScannerService(ScannerConfig config){
     this.config=config;
    }

    @Override
    public boolean shouldScanFile(PsiFile file) {
        return false;
    }

    @Override
    public void scan(Document document) {
    }
}
