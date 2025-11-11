package com.checkmarx.intellij.devassist.basescanner;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;

public interface ScannerCommand extends Disposable {
    void register(Project project);
    void dispose();
    void deregister(Project project);
}
