package com.checkmarx.intellij.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * Inspection to highlight code according to the scan results
 */
public class CxInspection extends LocalInspectionTool {

    private static final PsiElementVisitor dummyVisitor = new PsiElementVisitor() {
    };

    /**
     * This method is overridden to provide a custom visitor.
     * The visitor must not be recursive and must be thread-safe.
     *
     * @param holder     object for visitor to register problems found.
     * @param isOnTheFly true if inspection was run in non-batch mode
     * @return {@link CxVisitor} instance
     */
    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        System.out.println("------------buildVisitor getting called here");
        return Boolean.getBoolean("CxDev") && isOnTheFly ? dummyVisitor : new CxVisitor(holder);
    }
}
