package com.checkmarx.intellij.inspections;

import com.checkmarx.intellij.ASCA.AscaService;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class AscaInspection extends LocalInspectionTool {

    private final AscaService ascaService = new AscaService();

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new AscaVisitor(holder, ascaService);
    }

    @Override
    public boolean runForWholeFile() {
        return true;  // Indicate this inspection checks the entire file, not just specific elements
    }
}
