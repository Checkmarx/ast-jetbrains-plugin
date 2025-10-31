package com.checkmarx.intellij.realtimeScanners.basescanner;

import com.intellij.psi.PsiFile;
import com.intellij.openapi.editor.Document;

import java.util.concurrent.CompletableFuture;

public interface ScannerService {

    boolean shouldScanFile(String filePath);
    void scan(PsiFile psiFile, String uri);

}
