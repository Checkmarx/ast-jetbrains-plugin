package com.checkmarx.intellij.common.devassist.basescanner;

import com.checkmarx.intellij.common.devassist.common.ScanResult;
import com.checkmarx.intellij.common.devassist.configuration.ScannerConfig;
import com.intellij.psi.PsiFile;

public interface ScannerService<T> {
    boolean shouldScanFile(String filePath, PsiFile psiFile);

    ScanResult<T> scan(PsiFile psiFile, String uri);

    ScannerConfig getConfig();
}
