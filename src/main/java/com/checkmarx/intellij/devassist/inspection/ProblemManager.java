package com.checkmarx.intellij.devassist.inspection;

import com.checkmarx.ast.ossrealtime.OssRealtimeScanPackage;
import com.checkmarx.ast.ossrealtime.OssRealtimeVulnerability;
import com.checkmarx.ast.realtime.RealtimeLocation;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.CxIcons;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.dto.CxProblems;
import com.checkmarx.intellij.inspections.AscaInspection;
import com.checkmarx.intellij.service.ProblemHolderService;
import com.checkmarx.intellij.util.Status;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

@Getter
public class ProblemManager {

    private final Map<String, ProblemHighlightType> severityToHighlightMap = new HashMap<>();

    public ProblemManager() {
        initSeverityToHighlightMap();
    }

    /**
     * Initializes the mapping from severity levels to problem highlight types.
     */
    private void initSeverityToHighlightMap() {
        severityToHighlightMap.put(Constants.MALICIOUS_SEVERITY, ProblemHighlightType.GENERIC_ERROR);
        severityToHighlightMap.put(Constants.CRITICAL_SEVERITY, ProblemHighlightType.GENERIC_ERROR);
        severityToHighlightMap.put(Constants.HIGH_SEVERITY, ProblemHighlightType.GENERIC_ERROR);
        severityToHighlightMap.put(Constants.MEDIUM_SEVERITY, ProblemHighlightType.WARNING);
        severityToHighlightMap.put(Constants.LOW_SEVERITY, ProblemHighlightType.WEAK_WARNING);
    }

    /**
     * Checks if the scan package is a problem.
     *
     * @param status - the status of the scan package e.g. "high", "medium", "low", etc.
     * @return true if the scan package is a problem, false otherwise
     */
    public boolean isProblem(String status) {
        if (status.equalsIgnoreCase(Status.OK.getStatus())) {
            return false;
        } else return !status.equalsIgnoreCase(Status.UNKNOWN.getStatus());
    }

    /**
     * Checks if the line number is out of range in the document.
     *
     * @param lineNumber the line number
     * @param document   the document
     * @return true if the line number is out of range, false otherwise
     */
    public boolean isLineOutOfRange(int lineNumber, Document document) {
        return lineNumber <= 0 || lineNumber > document.getLineCount();
    }

    /**
     * Gets the text range for the specified line number in the document,
     * trimming leading and trailing whitespace.
     *
     * @param document          the document
     * @param problemLineNumber the line number (1-based)
     * @return the text range for the line, or null if the line number is invalid
     */
    public TextRange getTextRangeForLine(Document document, int problemLineNumber) {
        // Convert to 0-based index for document API
        int lineIndex = problemLineNumber - 1;

        // Get the exact offsets for this line
        int lineStartOffset = document.getLineStartOffset(lineIndex);
        int lineEndOffset = document.getLineEndOffset(lineIndex);

        // Get the line text and trim whitespace for highlighting
        // Get the line text and trim whitespace for highlighting
        int leadingSpaces = 0;
        int trailingSpaces = 0;

        // Calculate leading spaces
        for (int i = lineStartOffset; i < lineEndOffset; i++) {
            char c = document.getCharsSequence().charAt(i);
            if (!Character.isWhitespace(c)) break;
            leadingSpaces++;
        }

        // Calculate trailing spaces
        for (int i = lineEndOffset - 1; i >= lineStartOffset; i--) {
            char c = document.getCharsSequence().charAt(i);
            if (!Character.isWhitespace(c)) break;
            trailingSpaces++;
        }

        int trimmedStartOffset = lineStartOffset + leadingSpaces;
        int trimmedEndOffset = lineEndOffset - trailingSpaces;

        // Ensure valid range
        if (trimmedStartOffset >= trimmedEndOffset) {
            trimmedStartOffset = lineStartOffset;
            trimmedEndOffset = lineEndOffset;
        }
        return new TextRange(trimmedStartOffset, trimmedEndOffset);
    }

    /**
     * Formats the description for the given scan package.
     *
     * @param scanPackage the scan package
     * @return the formatted description
     */
    public String formatDescription(OssRealtimeScanPackage scanPackage) {
        StringBuilder descBuilder = new StringBuilder();
        descBuilder.append("<html><body><div style='display:flex;align-items:center;gap:4px;'>");
        descBuilder.append("<div><img src='").append(getIconPath(Constants.ImagePaths.DEV_ASSIST_PNG)).append("'/></div><br>");

        if (scanPackage.getStatus().equalsIgnoreCase(Status.MALICIOUS.getStatus())) {
            buildMaliciousPackageMessage(descBuilder, scanPackage);
            return descBuilder.toString();
        }
        descBuilder.append("<div'>").append(scanPackage.getStatus()).append("-risk package:  ").append(scanPackage.getPackageName())
                .append("@").append(scanPackage.getPackageVersion()).append("</div><br>");

        descBuilder.append("<div'>").append(getImage(getIconPath(Constants.ImagePaths.PACKAGE_PNG)))
                .append("<b>").append(scanPackage.getPackageName()).append("@").append(scanPackage.getPackageVersion()).append("</b>")
                .append(" - ").append(scanPackage.getStatus()).append(" Severity Package").append("</div><br><br>");

        List<OssRealtimeVulnerability> vulnerabilityList = scanPackage.getVulnerabilities();
        if (!Objects.isNull(vulnerabilityList) && !vulnerabilityList.isEmpty()) {
            descBuilder.append("<div>");
            buildVulnerabilityCountMessage(descBuilder, vulnerabilityList);
            descBuilder.append("</div><br>");
            descBuilder.append("<div>");

            findVulnerabilityBySeverity(vulnerabilityList, scanPackage.getStatus())
                    .ifPresent(vulnerability ->
                            descBuilder.append(Utils.escapeHtml(vulnerability.getDescription())).append("<br>")
                    );
            descBuilder.append("</div><br>");
        }
        descBuilder.append("</div></body></html>");
        return descBuilder.toString();
    }

    /**
     * Finds a vulnerability matching the specified severity level.
     *
     * @param vulnerabilityList the list of vulnerabilities to search
     * @param severity          the severity level to match
     * @return an Optional containing the matching vulnerability, or empty if not found
     */
    private Optional<OssRealtimeVulnerability> findVulnerabilityBySeverity(List<OssRealtimeVulnerability> vulnerabilityList, String severity) {
        return vulnerabilityList.stream()
                .filter(vulnerability -> vulnerability.getSeverity().equalsIgnoreCase(severity))
                .findAny();
    }


    private void buildMaliciousPackageMessage(StringBuilder descBuilder, OssRealtimeScanPackage scanPackage) {
        descBuilder.append("<div'>").append(scanPackage.getStatus()).append(" package detected:  ").append(scanPackage.getPackageName())
                .append("@").append(scanPackage.getPackageVersion()).append("</div><br>");
        descBuilder.append("<div'>").append(getImage(getIconPath(Constants.ImagePaths.MALICIOUS_SEVERITY_PNG)))
                .append("<b>").append(scanPackage.getPackageName()).append("@").append(scanPackage.getPackageVersion()).append("</b>")
                .append(" - ").append(scanPackage.getStatus()).append(" Package").append("</div><br>");
        descBuilder.append("</div><br>").append("</div></body></html>");
    }

    private Map<String, Long> getVulnerabilityCount(List<OssRealtimeVulnerability> vulnerabilityList) {
        return vulnerabilityList.stream()
                .map(OssRealtimeVulnerability::getSeverity)
                .collect(Collectors.groupingBy(severity -> severity, Collectors.counting()));
    }

    private void buildVulnerabilityCountMessage(StringBuilder descBuilder, List<OssRealtimeVulnerability> vulnerabilityList) {

        Map<String, Long> vulnerabilityCount = getVulnerabilityCount(vulnerabilityList);

        if (Objects.isNull(vulnerabilityCount) || vulnerabilityList.isEmpty()) {
            return;
        }

        if (vulnerabilityCount.containsKey(Status.CRITICAL.getStatus())) {
            descBuilder.append(getImage(getIconPath(Constants.ImagePaths.CRITICAL_SEVERITY_PNG))).append(vulnerabilityCount.get(Status.CRITICAL.getStatus())).append("&nbsp;&nbsp;&nbsp;&nbsp;");
        }
        if (vulnerabilityCount.containsKey("High")) {
            descBuilder.append(getImage(getIconPath(Constants.ImagePaths.HIGH_SEVERITY_PNG))).append(vulnerabilityCount.get("High")).append("&nbsp;&nbsp;&nbsp;&nbsp;");
        }
        if (!vulnerabilityCount.isEmpty() && vulnerabilityCount.containsKey("Medium") && vulnerabilityCount.get("Medium") > 0) {
            descBuilder.append(getImage(getIconPath(Constants.ImagePaths.MEDIUM_SEVERITY_PNG))).append(vulnerabilityCount.get("Medium")).append("&nbsp;&nbsp;&nbsp;&nbsp;");
        }
        if (!vulnerabilityCount.isEmpty() && vulnerabilityCount.containsKey("Low") && vulnerabilityCount.get("Low") > 0) {
            descBuilder.append(getImage(getIconPath(Constants.ImagePaths.LOW_SEVERITY_PNG))).append(vulnerabilityCount.get("Low")).append("&nbsp;&nbsp;&nbsp;&nbsp;");
        }
    }

    private String getImage(String iconPath) {
        return "<img src='" + iconPath + "'/>&nbsp;&nbsp;";
    }

    private String getIconPath(String iconPath) {
        URL res = AscaInspection.class.getResource(iconPath);
        return (res != null) ? res.toExternalForm() : "";
    }

    /**
     * Adds a gutter icon at the line of the given PsiElement.
     */
    public void addGutterIconForProblem(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement problemElement, OssRealtimeScanPackage scanPackage) {

        String severity = scanPackage.getStatus();

        ApplicationManager.getApplication().invokeLater(() -> {
            Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
            if (editor == null) return;
            if (!Objects.equals(editor.getDocument(),
                    com.intellij.psi.PsiDocumentManager.getInstance(project).getDocument(file))) {
                // Only decorate the active editor of this file
                return;
            }
            int line = editor.getDocument().getLineNumber(problemElement.getTextOffset());
            System.out.println("gutter line:" + line);
            MarkupModel markupModel = editor.getMarkupModel();

            int gutterIndex = 0;
            for(RealtimeLocation location: scanPackage.getLocations()){
                int problemLine = location.getLine() + 1;
                System.out.println("gutter problemLine line:" + problemLine);
                TextRange textRange = getTextRangeForLine(editor.getDocument(), problemLine);
                // Normal range highlighting for other severities
                TextAttributes errorAttrs = EditorColorsManager.getInstance()
                        .getGlobalScheme().getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES);
                TextAttributes attr = new TextAttributes();
                attr.setEffectType(EffectType.WAVE_UNDERSCORE);
                attr.setEffectColor(errorAttrs.getEffectColor());
                attr.setForegroundColor(errorAttrs.getForegroundColor());
                attr.setBackgroundColor(null);

                RangeHighlighter highlighter = markupModel.addRangeHighlighter(
                        textRange.getStartOffset(),
                        textRange.getEndOffset(),
                        HighlighterLayer.ERROR,
                        attr,
                        HighlighterTargetArea.EXACT_RANGE
                );

                if (gutterIndex  != 0) {
                    continue;
                }
                gutterIndex++;
                // Check if there’s already a gutter icon highlighter at this element’s position
                boolean alreadyHasGutterIcon = isAlreadyHasGutterIcon(markupModel, editor, problemLine);

                System.out.println("alreadyHasGutterIcon:" + alreadyHasGutterIcon);

                highlighter.setGutterIconRenderer(new GutterIconRenderer() {

                    @Override
                    public @NotNull Icon getIcon() {
                        return getGutterIconBasedOnStatus(severity);
                    }

                    @Override
                    public @NotNull Alignment getAlignment() {
                        return Alignment.LEFT;
                    }

                    @Override
                    public String getTooltipText() {
                        return severity;
                    }

                    @Override
                    public boolean equals(Object obj) {
                        return obj == this;
                    }

                    @Override
                    public int hashCode() {
                        return System.identityHashCode(this);
                    }
                });

            }


        });


    }

    private boolean isAlreadyHasGutterIcon(MarkupModel markupModel, Editor editor, int line) {
        return Arrays.stream(markupModel.getAllHighlighters())
                .anyMatch(highlighter -> {
                    GutterIconRenderer renderer = highlighter.getGutterIconRenderer();
                    if (renderer == null) return false;
                    int existingLine = editor.getDocument().getLineNumber(highlighter.getStartOffset());
                    // Match if highlighter covers the same PSI element region
                    if (existingLine == line) {
                        // Remove if overlaps the same element
                        markupModel.removeHighlighter(highlighter);
                        return true;
                    }
                    return false;
                });
    }

    /**
     * Gets the gutter icon for the given severity.
     *
     * @param severity the severity
     * @return the severity icon
     */
    public Icon getGutterIconBasedOnStatus(String severity) {
        switch (Status.fromValue(severity)) {
            case MALICIOUS:
                return CxIcons.GUTTER_MALICIOUS;
            case CRITICAL:
                return CxIcons.GUTTER_CRITICAL;
            case HIGH:
                return CxIcons.GUTTER_HIGH;
            case MEDIUM:
                return CxIcons.GUTTER_MEDIUM;
            case LOW:
                return CxIcons.GUTTER_LOW;
            case OK:
                return CxIcons.GUTTER_GREEN_SHIELD_CHECK;
            default:
                return CxIcons.GUTTER_SHIELD_QUESTION;
        }
    }

    /**
     * Determines the highlight type for a specific scan detail.
     *
     * @param detail the scan detail
     * @return the problem highlight type
     */
    public ProblemHighlightType determineHighlightType(OssRealtimeScanPackage detail) {
        return severityToHighlightMap.getOrDefault(detail.getStatus(), ProblemHighlightType.WEAK_WARNING);
    }

    public void addToCxOneProblems(PsiFile file, List<CxProblems> problemsList) {
        ProblemHolderService.getInstance(file.getProject())
                .addProblems(file.getVirtualFile().getPath(), problemsList);
    }
}
