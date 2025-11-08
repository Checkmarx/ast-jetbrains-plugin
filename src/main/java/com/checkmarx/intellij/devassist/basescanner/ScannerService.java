package com.checkmarx.intellij.devassist.basescanner;

import com.checkmarx.intellij.devassist.configuration.ScannerConfig;
import com.intellij.psi.PsiFile;

public interface ScannerService<T> {
    boolean shouldScanFile(String filePath);
    T scan(PsiFile psiFile, String uri);
    ScannerConfig getConfig();
}
