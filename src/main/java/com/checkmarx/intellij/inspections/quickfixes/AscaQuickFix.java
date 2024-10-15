package com.checkmarx.intellij.inspections.quickfixes;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NotNull;

public class AscaQuickFix implements LocalQuickFix {

    String remediationAdvice;

    public AscaQuickFix(String remediationAdvice) {
        this.remediationAdvice = remediationAdvice;
    }

    @Override
    public @NotNull String getFamilyName() {
        return remediationAdvice;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        
    }


}
