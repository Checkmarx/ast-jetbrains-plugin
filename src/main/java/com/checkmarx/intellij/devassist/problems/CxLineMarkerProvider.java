package com.checkmarx.intellij.devassist.problems;

import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

/**
 * Provides gutter icons for security issues identified by Checkmarx.
 */
public class CxLineMarkerProvider implements LineMarkerProvider, DumbAware{

    private static final ProblemDecorator DECORATOR = new ProblemDecorator();

    @Nullable
    @Override
    public LineMarkerInfo<PsiElement> getLineMarkerInfo(@NotNull PsiElement element) {
        // Only attach icons to leaf elements (like identifiers) to avoid overlapping/invisible icons
        if (!(element instanceof LeafPsiElement)) {
            return null;
        }

        ScanIssue scanIssue = getScanIssueForElement(element);
        if (scanIssue == null) {
            return null;
        }

        return new LineMarkerInfo<>(
                element,
                element.getTextRange(),
                DECORATOR.getGutterIconBasedOnStatus(scanIssue.getSeverity()),
                psiElement -> "Security Issue: " + scanIssue.getTitle(),
                (e, elt) -> {
                    // Navigate logic or detail view can be added here
                    System.out.println("Clicked: " + scanIssue.getTitle());
                },
                GutterIconRenderer.Alignment.RIGHT
        );
    }

    @Override
    public void collectSlowLineMarkers(@NotNull List<? extends PsiElement> elements,
                                       @NotNull Collection<? super LineMarkerInfo<?>> result) {
        // Optimization: not needed for this implementation
    }

    /**
     * Finds a ScanIssue that matches the line number of the specific PsiElement.
     */
    @Nullable
    private ScanIssue getScanIssueForElement(@NotNull PsiElement element) {
        try {
            PsiFile psiFile = element.getContainingFile();
            if (psiFile == null || psiFile.getVirtualFile() == null) {
                return null;
            }

            Project project = psiFile.getProject();
            ProblemHolderService service = ProblemHolderService.getInstance(project);
            if (service == null) {
                return null;
            }

            Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
            if (document == null) {
                return null;
            }

            // Get the line number of the element (0-indexed converted to 1-indexed)
            int elementLine = document.getLineNumber(element.getTextOffset()) + 1;

            List<ScanIssue> scanIssues = service.getScanIssueByFile(psiFile.getVirtualFile().getPath());
            if (scanIssues == null || scanIssues.isEmpty()) {
                return null;
            }

            // Return the first issue that matches this line
            return scanIssues.stream()
                    .filter(issue -> issue.getLocations() != null &&
                            issue.getLocations().stream().anyMatch(loc -> loc.getLine() == elementLine))
                    .findFirst()
                    .orElse(null);

        } catch (Exception e) {
            return null;
        }
    }
}