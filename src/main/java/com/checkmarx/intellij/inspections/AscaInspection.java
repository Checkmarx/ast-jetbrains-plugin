package com.checkmarx.intellij.inspections;

import com.checkmarx.ast.asca.ScanDetail;
import com.checkmarx.ast.asca.ScanResult;
import com.checkmarx.ast.oss.Vulnerability;
import com.checkmarx.intellij.realtimeScanners.dto.CxProblems;
import com.checkmarx.intellij.realtimeScanners.dto.Location;
import com.checkmarx.intellij.service.AscaService;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.inspections.quickfixes.AscaQuickFix;
import com.checkmarx.intellij.service.ProblemHolderService;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import lombok.Getter;
import lombok.Setter;
import com.checkmarx.ast.oss.Package;
import org.jetbrains.annotations.NotNull;


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Inspection tool for ASCA (AI Secure Coding Assistant).
 */
public class AscaInspection extends LocalInspectionTool {
    @Getter
    @Setter
    private AscaService ascaService = new AscaService();
    private final GlobalSettingsState settings = GlobalSettingsState.getInstance();
    private Map<String, ProblemHighlightType> severityToHighlightMap;
    public static String ASCA_INSPECTION_ID = "ASCA";
    private final Logger logger = Utils.getLogger(AscaInspection.class);

    private static final String CLI_DIRECTORY = "C:\\Users\\AniketS\\Downloads\\ast-cli_2.3.33-kerberos-auth-native2_windows_x64";
    private static final String SCAN_COMMAND = "cx scan oss-realtime --file-source \"C:\\Utils\\Dummy Project\\JavaVulnerableLab-master\\JavaVulnerableLab-master\\pom.xml\"";

    /**
     * Checks the file for ASCA issues.
     *
     * @param file the file to check
     * @param manager the inspection manager
     * @param isOnTheFly whether the inspection is on-the-fly
     * @return an array of problem descriptors
     */
    @Override
    public ProblemDescriptor @NotNull [] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
        try {
            if (!settings.isAsca()) {
                return ProblemDescriptor.EMPTY_ARRAY;
            }

            ScanResult scanResult = performAscaScan(file);
            if (isInvalidScan(scanResult)) {
                return ProblemDescriptor.EMPTY_ARRAY;
            }
            Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
            if (document == null) {
                return ProblemDescriptor.EMPTY_ARRAY;
            }

            return createProblemDescriptors(file, manager, scanResult.getScanDetails(), document, isOnTheFly);
        }
        catch (Exception e) {
            logger.warn("Failed to run ASCA scan", e);
            return ProblemDescriptor.EMPTY_ARRAY;
        }
    }

    /**
     * Creates problem descriptors for the given scan details.
     *
     * @param file the file to check
     * @param manager the inspection manager
     * @param scanDetails the scan details
     * @param document the document
     * @param isOnTheFly whether the inspection is on-the-fly
     * @return an array of problem descriptors
     */
    private ProblemDescriptor[] createProblemDescriptors(@NotNull PsiFile file, @NotNull InspectionManager manager, List<ScanDetail> scanDetails, Document document, boolean isOnTheFly) {
        List<ProblemDescriptor> problems = new ArrayList<>();

        List<CxProblems> problemsList = new ArrayList<>();

        for (ScanDetail detail : scanDetails) {
            int lineNumber = detail.getLine();
            if (isLineOutOfRange(lineNumber, document)) {
                continue;
            }
            PsiElement elementAtLine = file.findElementAt(document.getLineStartOffset(lineNumber - 1));
            if (elementAtLine != null) {
                ProblemDescriptor problem = createProblemDescriptor(file, manager, detail, document, lineNumber, isOnTheFly);
                problems.add(problem);
            }
        }

        try {
            List<Package> ossResultDetails = performOssScan(); // This perform scan and related methods are dummy here to just execute the command to temporarily get the results
            problemsList.addAll(fromPackagePojo(ossResultDetails)); // Oss results conversion - this can be later moved to oss scanner service class
            problemsList.addAll(buildCxProblems(scanDetails));

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Persist in project service
        ProblemHolderService.getInstance(file.getProject())
                .addProblems(file.getVirtualFile().getPath(), problemsList);

        return problems.toArray(ProblemDescriptor[]::new);
    }

    /**
     * Creates a problem descriptor for a specific scan detail.
     *
     * @param file the file to check
     * @param manager the inspection manager
     * @param detail the scan detail
     * @param document the document
     * @param lineNumber the line number
     * @param isOnTheFly whether the inspection is on-the-fly
     * @return a problem descriptor
     */
    private ProblemDescriptor createProblemDescriptor(@NotNull PsiFile file, @NotNull InspectionManager manager, ScanDetail detail, Document document, int lineNumber, boolean isOnTheFly) {
        TextRange problemRange = getTextRangeForLine(document, lineNumber);
        String description = formatDescription(detail.getRuleName(), detail.getRemediationAdvise());
        ProblemHighlightType highlightType = determineHighlightType(detail);

        return manager.createProblemDescriptor(
                file, problemRange, description, highlightType, isOnTheFly, new AscaQuickFix(detail));
    }

    public String formatDescription(String ruleName, String remediationAdvise) {
        return String.format(
                "<html><b>%s</b> - %s<br><font color='gray'>%s</font></html>",
                escapeHtml(ruleName), escapeHtml(remediationAdvise), escapeHtml(ASCA_INSPECTION_ID)
        );
    }

    // Helper method to escape HTML special characters for safety
    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Gets the text range for a specific line in the document.
     *
     * @param document the document
     * @param lineNumber the line number
     * @return the text range
     */
    private TextRange getTextRangeForLine(Document document, int lineNumber) {
        int startOffset = document.getLineStartOffset(lineNumber - 1);
        int endOffset = Math.min(document.getLineEndOffset(lineNumber - 1), document.getTextLength());

        String lineText = document.getText(new TextRange(startOffset, endOffset));
        int trimmedStartOffset = startOffset + (lineText.length() - lineText.stripLeading().length());

        return new TextRange(trimmedStartOffset, endOffset);
    }

    /**
     * Checks if the line number is out of range in the document.
     *
     * @param lineNumber the line number
     * @param document the document
     * @return true if the line number is out of range, false otherwise
     */
    private boolean isLineOutOfRange(int lineNumber, Document document) {
        return lineNumber <= 0 || lineNumber > document.getLineCount();
    }

    /**
     * Checks if the scan result is invalid.
     *
     * @param scanResult the scan result
     * @return true if the scan result is invalid, false otherwise
     */
    private boolean isInvalidScan(ScanResult scanResult) {
        return scanResult == null || scanResult.getScanDetails() == null;
    }

    /**
     * Determines the highlight type for a specific scan detail.
     *
     * @param detail the scan detail
     * @return the problem highlight type
     */
    private ProblemHighlightType determineHighlightType(ScanDetail detail) {
        return getSeverityToHighlightMap().getOrDefault(detail.getSeverity(), ProblemHighlightType.WEAK_WARNING);
    }

    /**
     * Gets the map of severity to highlight type.
     *
     * @return the map of severity to highlight type
     */
    private Map<String, ProblemHighlightType> getSeverityToHighlightMap() {
        if (severityToHighlightMap == null) {
            severityToHighlightMap = new HashMap<>();
            severityToHighlightMap.put(Constants.ASCA_CRITICAL_SEVERITY, ProblemHighlightType.GENERIC_ERROR);
            severityToHighlightMap.put(Constants.ASCA_HIGH_SEVERITY, ProblemHighlightType.GENERIC_ERROR);
            severityToHighlightMap.put(Constants.ASCA_MEDIUM_SEVERITY, ProblemHighlightType.WARNING);
            severityToHighlightMap.put(Constants.ASCA_LOW_SEVERITY, ProblemHighlightType.WEAK_WARNING);
        }
        return severityToHighlightMap;
    }

    /**
     * Performs an ASCA scan on the given file.
     *
     * @param file the file to scan
     * @return the scan result
     */
    private ScanResult performAscaScan(PsiFile file) {
        return ascaService.runAscaScan(file, file.getProject(), false, Constants.JET_BRAINS_AGENT_NAME);
    }

    /*
    code for checking the oss results
     */


    public List<Package> performOssScan() throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder();
        // Use 'cmd.exe /c' on Windows to run command via shell
        processBuilder.command("cmd.exe", "/c", SCAN_COMMAND);
        processBuilder.directory(new java.io.File(CLI_DIRECTORY));

        Process process = processBuilder.start();

        // Capture combined standard output from the command as JSON string
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
        }

        // Optional: capture standard error in case of errors
        StringBuilder errorOutput = new StringBuilder();
        try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String errLine;
            while ((errLine = errorReader.readLine()) != null) {
                errorOutput.append(errLine).append(System.lineSeparator());
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Process exited with code " + exitCode + ": " + errorOutput);
        }

        // Parse JSON to your wrapper class that contains List<Package>
        ObjectMapper mapper = new ObjectMapper();

        // Assuming CLI outputs JSON with root {"Packages": [...] }
        Wrapper wrapper = mapper.readValue(output.toString(), Wrapper.class);

        return wrapper.getPackages();
    }

    // Wrapper for the root JSON object
    public static class Wrapper {
        @JsonProperty("Packages")
        private List<Package> Packages;

        public List<Package> getPackages() {
            return Packages;
        }

        public void setPackages(List<Package> packages) {
            this.Packages = packages;
        }
    }

    private List<CxProblems> buildCxProblems(List<ScanDetail> scanDetails){
        List<CxProblems> problems = new ArrayList<>();
        for (ScanDetail detail : scanDetails) {
            CxProblems problem = new CxProblems();
            problem.setLine(detail.getLine());
            problem.setSeverity(detail.getSeverity());
            problem.setTitle(detail.getRuleName());
            problem.setDescription(detail.getDescription());
            problem.setRemediationAdvise(detail.getRemediationAdvise());
            problem.setRuleName(detail.getRuleName());
            problem.setScannerType(ASCA_INSPECTION_ID);
            problems.add(problem);
        }
        return problems;
    }


    // Move this method to a utility class once the ervice part gets completed
    public static List<CxProblems> fromPackagePojo(List<Package> pkgs) {
        return pkgs.stream()
                .filter(pkg -> !"OK".equals(pkg.getStatus()))
                .map(pkg -> {
                    CxProblems problem = new CxProblems();
                    // Set line number
                    if (pkg.getLocations() != null && !pkg.getLocations().isEmpty()) {
                        problem.setLine(pkg.getLocations().get(0).getLine());
                    } else {
                        problem.setLine(-1);
                    }

                    problem.setTitle(pkg.getPackageName());
                    problem.setPackageVersion(pkg.getPackageVersion());

                    List<Vulnerability> vulns = pkg.getVulnerabilities();
                    if (vulns != null && !vulns.isEmpty()) {
                        Vulnerability vuln = vulns.get(0);
                        problem.setSeverity(vuln.getSeverity());
                        problem.setDescription(vuln.getDescription());
                        problem.setCve(vuln.getCve());
                        problem.setRemediationAdvise("Check official documentation or security advisory.");
                        problem.setScannerType("OSS");
                    } else {
                        problem.setSeverity(pkg.getStatus());
                        problem.setDescription("No vulnerabilities, but issue detected.");
                        problem.setRemediationAdvise("No action required or further check needed.");
                        problem.setCve(null);
                        problem.setRuleName(null);
                    }

                    return problem;
                })
                .collect(Collectors.toList());
    }


}