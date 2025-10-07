package com.checkmarx.intellij.realtimeScanners.basescanner;

import com.checkmarx.intellij.realtimeScanners.configuration.ScannerConfig;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;

import java.util.concurrent.CompletableFuture;

public class BaseScannerService implements ScannerService{
  public ScannerConfig config;

    public BaseScannerService(){

    }

    @Override
    public boolean shouldScanFile(PsiFile file) {
      // logic to be added
        return false;
    }

    @Override
    public CompletableFuture<Void> scan(Document document) {
        return null;
    }
}
