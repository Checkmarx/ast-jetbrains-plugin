package com.checkmarx.intellij.realtimeScanners.basescanner;

import com.checkmarx.intellij.realtimeScanners.configuration.ScannerConfig;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.editor.Document;

import java.util.concurrent.CompletableFuture;

public interface ScannerService<T> {
    boolean shouldScanFile(String filePath);
    T scan(PsiFile psiFile, String uri);
    ScannerConfig getConfig();
}
