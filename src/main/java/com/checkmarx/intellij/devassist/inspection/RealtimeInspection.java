package com.checkmarx.intellij.devassist.inspection;

import com.checkmarx.ast.ossrealtime.OssRealtimeResults;
import com.checkmarx.ast.ossrealtime.OssRealtimeScanPackage;
import com.checkmarx.ast.realtime.RealtimeLocation;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.basescanner.ScannerService;
import com.checkmarx.intellij.devassist.common.ScannerFactory;
import com.checkmarx.intellij.devassist.configuration.RealtimeScannerManager;
import com.checkmarx.intellij.devassist.inspection.remediation.CxOneAssistFix;
import com.checkmarx.intellij.service.ProblemHolderService;
import com.checkmarx.intellij.util.Status;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RealtimeInspection extends LocalInspectionTool {

    private final Logger logger = Utils.getLogger(RealtimeInspection.class);

    private final Map<String, Long> fileTimeStamp = new ConcurrentHashMap<>();
    private final ScannerFactory scannerFactory = new ScannerFactory();
    private final ProblemManager problemManager = new ProblemManager();

    @Override
    public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
        System.out.println("** RealtimeInspection called for file : " + file.getName());
        String path = file.getVirtualFile().getPath();

        ProblemHolderService problemHolderService = ProblemHolderService.getInstance(file.getProject());
        long currentModificationTime = file.getModificationStamp();

        if (fileTimeStamp.containsKey(path) && fileTimeStamp.get(path) == (currentModificationTime)) {
            return problemHolderService.getProblemDescriptors(path).toArray(new ProblemDescriptor[0]);
        }
        fileTimeStamp.put(path, currentModificationTime);
        System.out.println("** File modified : " + file.getName());

        Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (document == null) {
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        OssRealtimeResults scanResult = scanFile(file, file.getVirtualFile().getPath());
        if (Objects.isNull(scanResult)) {
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        List<ProblemDescriptor> problems = createProblemDescriptors(file, manager, isOnTheFly, scanResult, document);
        problemHolderService.addProblemDescriptors(path, problems);
        return problems.toArray(new ProblemDescriptor[0]);
    }


    private OssRealtimeResults scanFile(PsiFile file, String path) {
        System.out.println("** Called scan for file : " + file.getName());
        Optional<ScannerService<?>> scannerService = scannerFactory.findApplicationScanner(path);
        if (scannerService.isEmpty()) {
            return null;
        }
        RealtimeScannerManager scannerManager = file.getProject().getService(RealtimeScannerManager.class);
        if (scannerManager == null || !scannerManager.isScannerActive(scannerService.get().getConfig().getEngineName())) {
            return null;
        }
        return (OssRealtimeResults) scannerService.get().scan(file, path);
    }

    /**
     * Creates problem descriptors for the given scan details.
     *
     * @param file       the file to check
     * @param manager    the inspection manager
     * @param scanResult   the scan details
     * @param document   the document
     * @param isOnTheFly whether the inspection is on-the-fly
     * @return an array of problem descriptors
     */
    private List<ProblemDescriptor> createProblemDescriptors(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly, OssRealtimeResults scanResult, Document document) {
        List<ProblemDescriptor> problems = new ArrayList<>();
        for (OssRealtimeScanPackage scanPackage : scanResult.getPackages()) {
            System.out.println("** Package name: " + scanPackage.getPackageName());
            List<RealtimeLocation> locations = scanPackage.getLocations();
            if (Objects.isNull(locations) || locations.isEmpty()) {
                continue;
            }
            // Example: Line number where problem is found (1-based, e.g., line 18)
            int problemLineNumber = scanPackage.getLocations().get(0).getLine() + 1;
            System.out.println("** Package found lineNumber: " + problemLineNumber);
            if (problemManager.isLineOutOfRange(problemLineNumber, document)
                    || scanPackage.getStatus() == null || scanPackage.getStatus().isBlank())
                continue;
            try {
                if (isProblem(scanPackage.getStatus().toLowerCase())) {
                    ProblemDescriptor problemDescriptor = createProblem(file, manager, scanPackage, document, problemLineNumber, isOnTheFly);
                    if (Objects.nonNull(problemDescriptor)) {
                        problems.add(problemDescriptor);
                    }
                }
                PsiElement elementAtLine = file.findElementAt(document.getLineStartOffset(problemLineNumber));
                if (Objects.isNull(elementAtLine)) continue;

                problemManager.addGutterIconForProblem(file.getProject(), file, elementAtLine, scanPackage.getStatus());
            } catch (Exception e) {
                System.out.println("** EXCEPTION OCCURRED WHILE ITERATING SCAN RESULT: " + Arrays.toString(e.getStackTrace()));
            }
        }
        System.out.println("** problems  called:" + problems);
        return problems;
    }


    private ProblemDescriptor createProblem(@NotNull PsiFile file, @NotNull InspectionManager manager, OssRealtimeScanPackage scanPackage, Document document, int lineNumber, boolean isOnTheFly) {
        try {
            System.out.println("** Creating problem using inspection called **");
            TextRange problemRange = problemManager.getTextRangeForLine(document, lineNumber);
            String description = problemManager.formatDescription(scanPackage);
            ProblemHighlightType problemHighlightType = problemManager.determineHighlightType(scanPackage);

            return manager.createProblemDescriptor(
                    file,
                    problemRange,
                    description,
                    problemHighlightType,
                    isOnTheFly,
                    new CxOneAssistFix()
            );

        } catch (Exception e) {
            System.out.println("** EXCEPTION: ProblemDescriptor *** " + e.getMessage() + " " + Arrays.toString(e.getStackTrace()));
            return null;
        }
    }

    /**
     * Checks if the scan package is a problem.
     *
     * @param status - the status of the scan package e.g. "high", "medium", "low", etc.
     * @return true if the scan package is a problem, false otherwise
     */
    private boolean isProblem(String status) {
        if (status.equals(Status.OK.getStatus())) {
            return false;
        } else return !status.equals(Status.UNKNOWN.getStatus());
    }



    /*private ProblemDescriptor createProblemDescriptor(@NotNull PsiFile file, @NotNull InspectionManager manager, OssRealtimeScanPackage detail, Document document, int lineNumber, boolean isOnTheFly) {

        List<ProblemDescriptor> problems = new ArrayList<>();
        System.out.println("** createProblemDescriptors called **");

        for (OssRealtimeScanPackage scanPackage : packages) {
            System.out.println("** OssRealtimeScanPackage name:" + scanPackage.getPackageName());

          *//*  if (Objects.isNull(scanPackage.getVulnerabilities()) || scanPackage.getVulnerabilities().isEmpty()) {
                System.out.println("** Vulnerabilities not found:" + scanPackage.getPackageName());
                continue;
            }*//*
            List<RealtimeLocation> locations = scanPackage.getLocations();
            if (Objects.isNull(locations) || locations.isEmpty()) {
                System.out.println("** Locations not found:" + scanPackage.getPackageName());
                continue;
            }

             String description = problemManager.formatDescription(scanPackage);
            ProblemHighlightType highlightType = problemManager.determineHighlightType(scanPackage);

           for (RealtimeLocation loc : locations) {

                if (problemManager.isLineOutOfRange(loc.getLine(), document))
                    continue;

                int lineIndex = loc.getLine()-1;

                if (lineIndex < 1 || lineIndex > document.getLineCount()) continue;

                int startOffset = document.getLineStartOffset(lineIndex) + loc.getStartIndex();
                int endOffset = Math.min(document.getLineEndOffset(lineIndex),
                        document.getLineStartOffset(lineIndex) + loc.getEndIndex());

                // Get the actual line text
                String lineText = document.getText(new TextRange(document.getLineStartOffset(lineIndex), document.getLineEndOffset(lineIndex)));
                int trimmedLineStart = document.getLineStartOffset(lineIndex) + (lineText.length() - lineText.stripLeading().length());
                int trimmedLineEnd = document.getLineEndOffset(lineIndex) - (lineText.length() - lineText.stripTrailing().length());

                // Calculate highlight range: restrict to code, not spaces
                int highlightStart = Math.max(startOffset, trimmedLineStart);
                int highlightEnd = Math.min(endOffset, trimmedLineEnd);

                // If location is for the whole line, just highlight non-whitespace
                if (loc.getStartIndex() == 0 && (loc.getEndIndex() >= lineText.length() || loc.getEndIndex() == 1000)) {
                    highlightStart = trimmedLineStart;
                    highlightEnd = trimmedLineEnd;
                }
                if (highlightStart >= highlightEnd) continue; // Skip empty ranges

                boolean skipHighlight = "ok".equalsIgnoreCase(scanPackage.getStatus())
                        || "unknown".equalsIgnoreCase(scanPackage.getStatus());


                TextRange relativeRange = new TextRange(highlightStart, highlightEnd);

                PsiElement element = file.findElementAt(highlightStart);

                System.out.println("** element: "+ element);

            // int startOffset = problemManager.getOffset(document, loc.getLine(), loc.getStartIndex());
            //int endOffset = problemManager.getOffset(document, loc.getLine(), loc.getEndIndex());
            // Math.min(document.getLineEndOffset(loc.getLine()-1), problemManager.getOffset(document, loc.getLine(), loc.getEndIndex()));

                int lineStart = document.getLineStartOffset(loc.getLine() - 1);
                int lineEnd = document.getLineEndOffset(loc.getLine() - 1);

                int startOffset = lineStart + Math.max(0, loc.getStartIndex());
                int endOffset = Math.min(lineEnd, lineStart + loc.getEndIndex());

                System.out.println("** startOffset: "+ startOffset+", endOffset: "+ endOffset);


                if (startOffset >= endOffset || endOffset > document.getTextLength()) return null;

                TextRange safeRange = new TextRange(startOffset, endOffset);

                PsiElement element = ReadAction.compute(() ->
                        AstLoadingFilter.forceAllowTreeLoading(file, () -> file.findElementAt(startOffset))
                );

                if (element == null) return null;

                TextRange elementRange = element.getTextRange();
                if (elementRange == null) return null;

                TextRange relativeRange = new TextRange(
                        Math.max(0, safeRange.getStartOffset() - elementRange.getStartOffset()),
                        Math.min(element.getTextLength(), safeRange.getEndOffset() - elementRange.getStartOffset())
                );

                ProblemDescriptor descriptor = manager.createProblemDescriptor(
                        file,
                        relativeRange,
                        description,
                        highlightType,
                        isOnTheFly,
                        new CxOneAssistFix()
                );
                problems.add(descriptor);
            }


        }
        System.out.println("** problems  called:" + problems);
        return problems.toArray(ProblemDescriptor[]::new);
    }*/

}

