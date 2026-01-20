package com.checkmarx.intellij.devassist.problems;

import com.checkmarx.intellij.devassist.basescanner.ScannerService;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Objects;

/**
 * Helper class for managing and creating problem descriptors.
 */
@AllArgsConstructor
@Getter
@Setter
@Builder
public class ProblemHelper {

    private final PsiFile file;
    private final Project project;
    private final String filePath;
    private final InspectionManager manager;
    private final boolean isOnTheFly;
    private final Document document;
    private final List<ScannerService<?>> supportedScanners;
    private final List<ScanIssue> scanIssueList;
    private final ProblemHolderService problemHolderService;
    private final ProblemDecorator problemDecorator;


    /**
     * Builder method enforcing mandatory fields: file, project
     */
    public static ProblemHelperBuilder builder(PsiFile file, Project project) {
        if (Objects.isNull(file) || Objects.isNull(project)) {
            throw new IllegalArgumentException("Provide all mandatory fields: file, project, and scanIssueList to build a ProblemHelper.");
        }
        return new ProblemHelperBuilder()
                .file(file)
                .project(project);
    }

    /**
     * Creates a new builder instance based on the provided problem helper.
     *
     * @param problemHelper - the problem helper to copy from
     * @return a new builder instance
     */
    public ProblemHelperBuilder toBuilder(ProblemHelper problemHelper) {
        return builder(problemHelper.getFile(), problemHelper.getProject())
                .scanIssueList(problemHelper.getScanIssueList())
                .problemHolderService(problemHelper.getProblemHolderService())
                .problemDecorator(problemHelper.getProblemDecorator())
                .supportedScanners(problemHelper.getSupportedScanners())
                .filePath(problemHelper.getFilePath())
                .manager(problemHelper.getManager())
                .isOnTheFly(problemHelper.isOnTheFly())
                .document(problemHelper.getDocument());
    }
}
