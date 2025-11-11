package com.checkmarx.intellij.devassist.inspection.remediation;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import org.jetbrains.annotations.NotNull;

public class CxOneAssistFix implements LocalQuickFix {
/*
    @SafeFieldForPreview
    private ScanResult<?> scanResult;

    public CxOneAssistFix(ScanResult<?> scanResult) {
        super();
        this.scanResult = scanResult;
    }*/

    @NotNull
    @Override
    public String getFamilyName() {
        return "Fix with CxOne Assist";
    }

    /**
     * Called to apply the fix.
     * <p>
     * Please call {@link ProjectInspectionProfileManager#fireProfileChanged()} if inspection profile is changed as result of fix.
     *
     * @param project    {@link Project}
     * @param descriptor problem reported by the tool which provided this quick fix action
     */
    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        System.out.println("applyFix called..");
    }
}
