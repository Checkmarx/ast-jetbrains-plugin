package com.checkmarx.intellij.devassist.problems;

import com.checkmarx.intellij.devassist.basescanner.ScannerService;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Helper class for managing and creating problem descriptors.
 */
@AllArgsConstructor
@Getter
@Setter
@Builder
public class ProblemHelper {

    private final PsiFile file;
    private final String filePath;
    private final InspectionManager manager;
    private final boolean isOnTheFly;
    private final Document document;
    private final List<ScannerService<?>> supportedScanners;
    private final List<ScanIssue> scanIssueList;
    private final ProblemHolderService problemHolderService;

}
