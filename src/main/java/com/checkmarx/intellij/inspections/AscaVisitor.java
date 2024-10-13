package com.checkmarx.intellij.inspections;

import com.checkmarx.intellij.ASCA.AscaService;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class AscaVisitor extends PsiElementVisitor {

    private final ProblemsHolder holder;
    private final AscaService ascaService;

    public AscaVisitor(ProblemsHolder holder, AscaService ascaService) {
        this.holder = holder;
        this.ascaService = ascaService;
    }

    @Override
    public void visitFile(@NotNull PsiFile file) {
        super.visitFile(file);

        Project project = file.getProject();
        VirtualFile virtualFile = file.getVirtualFile();
        String agent = "default-agent";  // Set the agent parameter for the scan

        // Call AscaService to scan the file
        ascaService.scanAsca(virtualFile, project, true, agent);
    }
}
