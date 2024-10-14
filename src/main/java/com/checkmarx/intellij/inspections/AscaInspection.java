package com.checkmarx.intellij.inspections;

import com.checkmarx.ast.asca.ScanDetail;
import com.checkmarx.ast.asca.ScanResult;
import com.checkmarx.intellij.ASCA.AscaService;
import com.checkmarx.intellij.Constants;
import com.intellij.codeInspection.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class AscaInspection extends LocalInspectionTool {

    @Override
    public ProblemDescriptor @NotNull [] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
        // Perform the ASCA scan and retrieve the results
        ScanResult scanResult = performAscaScan(file);

        if (scanResult != null && scanResult.getScanDetails()!=null) {
            List<ProblemDescriptor> problems = new ArrayList<>();
            Project project = file.getProject();
            Document document = PsiDocumentManager.getInstance(project).getDocument(file);

            if (document == null) {
                return ProblemDescriptor.EMPTY_ARRAY;
            }

            for (ScanDetail detail : scanResult.getScanDetails()) {
                int lineNumber = detail.getLine();  // Assuming getLineNumber() exists

                if (lineNumber > 0 && lineNumber <= document.getLineCount()) {
                    int startOffset = document.getLineStartOffset(lineNumber - 1);  // Convert line number to start offset
                    int endOffset = document.getLineEndOffset(lineNumber - 1);      // Calculate end offset as the end of the line

                    // Find the PsiElement at the start offset
                    PsiElement elementAtLine = file.findElementAt(startOffset);

                    if (elementAtLine != null) {
                        // You can further refine the end offset based on the element or the problem details
                        endOffset = Math.min(endOffset, document.getTextLength());  // Ensure endOffset is within the document length

                        // Create a custom TextRange for highlighting a specific portion of the document
                        TextRange problemRange = new TextRange(startOffset, endOffset);

                        String description = detail.getRuleName() + " - " + detail.getRemediationAdvise();
                        ProblemHighlightType highlightType = determineHighlightType(detail);


                        ProblemDescriptor problem = manager.createProblemDescriptor(
                                file,                // The file where the problem occurs
                                problemRange,        // The custom range of the problem
                                description,         // The issue description
                                highlightType,  // Highlight type
                                isOnTheFly,          // Whether it is on-the-fly inspection
                                (LocalQuickFix) null // Optional quick fix
                        );
                        problems.add(problem);
                    }
                }
            }

            return problems.toArray(ProblemDescriptor[]::new);
        }

        return ProblemDescriptor.EMPTY_ARRAY;
    }

    private ProblemHighlightType determineHighlightType(ScanDetail detail) {
        // Example logic: adjust based on severity level in ScanDetail
        String severity = detail.getSeverity();

        switch (severity) {
            case "Critical":
                return ProblemHighlightType.GENERIC_ERROR;
            case "High":
                return ProblemHighlightType.GENERIC_ERROR;  // Error for high-severity issues
            case "Medium":
                return ProblemHighlightType.WARNING;  // Warning for medium severity
            case "Low":
            default:
                return ProblemHighlightType.INFORMATION;  // Weak warning for low severity or unknown severity
        }
    }

    private ScanResult performAscaScan(PsiFile file) {
        // Perform the ASCA scan here using the AscaService
        return new AscaService().runAscaScan(file.getVirtualFile(), file.getProject(), false, Constants.JET_BRAINS_AGENT_NAME);
    }
}
