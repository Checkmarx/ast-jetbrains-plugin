package com.checkmarx.intellij.devassist.inspection;

import com.checkmarx.ast.ossrealtime.OssRealtimeResults;
import com.checkmarx.ast.ossrealtime.OssRealtimeScanPackage;
import com.checkmarx.ast.realtime.RealtimeLocation;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.basescanner.ScannerService;
import com.checkmarx.intellij.devassist.common.ScanResult;
import com.checkmarx.intellij.devassist.common.ScannerFactory;
import com.checkmarx.intellij.devassist.dto.CxProblems;
import com.checkmarx.intellij.devassist.inspection.remediation.CxOneAssistFix;
import com.checkmarx.intellij.devassist.inspection.remediation.IgnoreAllTypeFix;
import com.checkmarx.intellij.devassist.inspection.remediation.IgnoreVulnerabilityFix;
import com.checkmarx.intellij.devassist.inspection.remediation.ViewDetailsFix;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.checkmarx.intellij.devassist.problems.ProblemManager;
import com.checkmarx.intellij.devassist.utils.ScannerUtils;
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
import java.util.stream.Collectors;

/**
 * Dev Assist RealtimeInspection class that extends LocalInspectionTool to perform real-time code scan.
 */
public class RealtimeInspection extends LocalInspectionTool {

    private final Logger logger = Utils.getLogger(RealtimeInspection.class);

    private final Map<String, Long> fileTimeStamp = new ConcurrentHashMap<>();
    private final ScannerFactory scannerFactory = new ScannerFactory();
    private final ProblemManager problemManager = new ProblemManager();

    /**
     * Checks the given file for problems.
     *
     * @param file       to check.
     * @param manager    InspectionManager to ask for ProblemDescriptor's from.
     * @param isOnTheFly true if called during on the fly editor highlighting. Called from Inspect Code action otherwise.
     * @return an array of ProblemDescriptor's found in the file.
     */
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
        if (document == null) return ProblemDescriptor.EMPTY_ARRAY;

        ScanResult<?> scanResult = scanFile(file, path);
        if (Objects.isNull(scanResult)) return ProblemDescriptor.EMPTY_ARRAY;

        List<ProblemDescriptor> problems = createProblemDescriptors(file, manager, isOnTheFly, scanResult, document);
        problemHolderService.addProblemDescriptors(path, problems);

        ProblemHolderService.getInstance(file.getProject())
                .addProblems(file.getVirtualFile().getPath(),
                        buildCxProblems(scanResult.getPackages()));

        return problems.toArray(new ProblemDescriptor[0]);
    }

    /**
     * Scans the given file using the appropriate scanner service.
     *
     * @param file - the file to scan
     * @param path - the file path
     * @return the scan results
     */
    private ScanResult<?> scanFile(PsiFile file, String path) {
        System.out.println("** Called scan for file : " + file.getName());
        Optional<ScannerService<?>> scannerService = scannerFactory.findRealTimeScanner(path);
        if (scannerService.isEmpty()) {
            return null;
        }
        if (!ScannerUtils.isScannerActive(scannerService.get().getConfig().getEngineName())) {
            return null;
        }
        return scannerService.get().scan(file,path);
    }

    /**
     * Creates problem descriptors for the given scan details.
     *
     * @param file       the file to check
     * @param manager    the inspection manager
     * @param scanResult the scan details
     * @param document   the document
     * @param isOnTheFly whether the inspection is on-the-fly
     * @return a list of problem descriptors
     */
    private List<ProblemDescriptor> createProblemDescriptors(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly,
                                                             ScanResult<?> scanResult, Document document) {
        List<ProblemDescriptor> problems = new ArrayList<>();
        problemManager.removeAllGutterIcons(file);
        for (OssRealtimeScanPackage scanPackage : scanResult.getPackages()) {
            System.out.println("** Package name: " + scanPackage.getPackageName());
            List<RealtimeLocation> locations = scanPackage.getLocations();
            if (Objects.isNull(locations) || locations.isEmpty()) {
                continue;
            }
            // Example: Line number where a problem is found (1-based, e.g., line 18)
            int problemLineNumber = scanPackage.getLocations().get(0).getLine() + 1;
            System.out.println("** Package found lineNumber: " + problemLineNumber);
            if (problemManager.isLineOutOfRange(problemLineNumber, document)
                    || scanPackage.getStatus() == null || scanPackage.getStatus().isBlank())
                continue;
            try {
                boolean isProblem = problemManager.isProblem(scanPackage.getStatus().toLowerCase());
                if (isProblem) {
                    ProblemDescriptor problemDescriptor = createProblem(file, manager, scanPackage, document, problemLineNumber, isOnTheFly);
                    if (Objects.nonNull(problemDescriptor)) {
                        problems.add(problemDescriptor);
                    }
                }
                PsiElement elementAtLine = file.findElementAt(document.getLineStartOffset(problemLineNumber));
                if (Objects.isNull(elementAtLine)) continue;
                problemManager.highlightLineAddGutterIconForProblem(file.getProject(), file, scanPackage, isProblem, problemLineNumber);
            } catch (Exception e) {
                System.out.println("** EXCEPTION OCCURRED WHILE ITERATING SCAN RESULT: " + Arrays.toString(e.getStackTrace()));
            }
        }
        System.out.println("** problems  called:" + problems);
        return problems;
    }

    /**
     * Creates a ProblemDescriptor for the given scan package.
     *
     * @param file        @NotNull PsiFile
     * @param manager     @NotNull InspectionManager to create a problem
     * @param scanPackage OssRealtimeScanPackage scan result
     * @param document    Document of the file
     * @param lineNumber  int line number where a problem is found
     * @param isOnTheFly  boolean indicating if the inspection is on-the-fly
     * @return ProblemDescriptor for the problem found
     */
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
                    new CxOneAssistFix(), new ViewDetailsFix(), new IgnoreVulnerabilityFix(), new IgnoreAllTypeFix()
            );
        } catch (Exception e) {
            System.out.println("** EXCEPTION: ProblemDescriptor *** " + e.getMessage() + " " + Arrays.toString(e.getStackTrace()));
            return null;
        }
    }

    /**
     * After getting the entire scan result pass to this method to build the CxProblems for custom tool window
     *
     */
    public static List<CxProblems> buildCxProblems(List<OssRealtimeScanPackage> pkgs) {
        return pkgs.stream()
                .map(pkg -> {
                    CxProblems problem = new CxProblems();
                    if (pkg.getLocations() != null && !pkg.getLocations().isEmpty()) {
                        for (RealtimeLocation location : pkg.getLocations()) {
                            problem.addLocation(location.getLine() + 1, location.getStartIndex(), location.getEndIndex());
                        }
                    }
                    problem.setTitle(pkg.getPackageName());
                    problem.setPackageVersion(pkg.getPackageVersion());
                    problem.setScannerType(Constants.RealTimeConstants.OSS_REALTIME_SCANNER_ENGINE_NAME);
                    problem.setSeverity(pkg.getStatus());
                    // Optionally set other fields if available, e.g. description, cve, etc.
                    return problem;
                })
                .collect(Collectors.toList());
    }
}