package com.checkmarx.intellij.devassist.inspection.problems;

import com.checkmarx.ast.ossrealtime.OssRealtimeScanPackage;
import com.checkmarx.ast.ossrealtime.OssRealtimeVulnerability;
import com.checkmarx.ast.realtime.RealtimeLocation;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.CxIcons;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.dto.CxProblems;
import com.checkmarx.intellij.inspections.AscaInspection;
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
import com.intellij.psi.PsiFile;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ProblemManager class responsible to provides utility methods for managing problem, highlighting and gutter icons.
 */
@Getter
public class ProblemManager {

    private final Map<String, ProblemHighlightType> severityHighlightTypeMap = new HashMap<>();
    private final Map<String, Integer> severityHighlighterLayerMap = new HashMap<>();

    public ProblemManager() {
        initSeverityToHighlightMap();
        initSeverityHighlighterLayerMap();
    }

    /**
     * Initializes the mapping from severity levels to problem highlight types.
     */
    private void initSeverityToHighlightMap() {
        severityHighlightTypeMap.put(Constants.MALICIOUS_SEVERITY, ProblemHighlightType.GENERIC_ERROR);
        severityHighlightTypeMap.put(Constants.CRITICAL_SEVERITY, ProblemHighlightType.GENERIC_ERROR);
        severityHighlightTypeMap.put(Constants.HIGH_SEVERITY, ProblemHighlightType.GENERIC_ERROR);
        severityHighlightTypeMap.put(Constants.MEDIUM_SEVERITY, ProblemHighlightType.WARNING);
        severityHighlightTypeMap.put(Constants.LOW_SEVERITY, ProblemHighlightType.WEAK_WARNING);
    }

    /**
     * Initializes the mapping from severity levels to highlighter layers.
     */
    private void initSeverityHighlighterLayerMap() {
        severityHighlighterLayerMap.put(Constants.MALICIOUS_SEVERITY, HighlighterLayer.ERROR);
        severityHighlighterLayerMap.put(Constants.CRITICAL_SEVERITY, HighlighterLayer.ERROR);
        severityHighlighterLayerMap.put(Constants.HIGH_SEVERITY, HighlighterLayer.ERROR);
        severityHighlighterLayerMap.put(Constants.MEDIUM_SEVERITY, HighlighterLayer.WARNING);
        severityHighlighterLayerMap.put(Constants.LOW_SEVERITY, HighlighterLayer.WEAK_WARNING);
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
    public void highlightLineAddGutterIconForProblem(@NotNull Project project, @NotNull PsiFile file,
                                                     OssRealtimeScanPackage scanPackage, boolean isProblem, int problemLineNumber) {
        ApplicationManager.getApplication().invokeLater(() -> {
            Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
            if (editor == null) return;

            if (!Objects.equals(editor.getDocument(),
                    com.intellij.psi.PsiDocumentManager.getInstance(project).getDocument(file))) {
                // Only decorate the active editor of this file
                return;
            }
            MarkupModel markupModel = editor.getMarkupModel();
            boolean isFirstLocation = true;

            boolean alreadyHasGutterIcon = isAlreadyHasGutterIcon(markupModel, editor, problemLineNumber);

            System.out.println("Already has gutter icon: " + alreadyHasGutterIcon + " targetLine : " + problemLineNumber);


            for (RealtimeLocation location : scanPackage.getLocations()) {
                int targetLine = location.getLine() + 1;
                highlightLocationInEditor(editor, markupModel, targetLine, scanPackage, isFirstLocation, isProblem, alreadyHasGutterIcon);
                isFirstLocation = false;
            }
        });
    }

    /**
     * Highlights a specific location in the editor and optionally adds a gutter icon.
     *
     * @param editor        the editor instance
     * @param markupModel   the markup model for highlighting
     * @param targetLine    the line number to highlight (1-based)
     * @param scanPackage   the scan package containing severity information
     * @param addGutterIcon whether to add a gutter icon for this location
     */
    private void highlightLocationInEditor(Editor editor, MarkupModel markupModel, int targetLine,
                                           OssRealtimeScanPackage scanPackage, boolean addGutterIcon, boolean isProblem, boolean alreadyHasGutterIcon) {
        TextRange textRange = getTextRangeForLine(editor.getDocument(), targetLine);
        TextAttributes attr = createTextAttributes();

        RangeHighlighter highlighter = markupModel.addLineHighlighter(
                targetLine - 1, 0, null);

        if (isProblem) {
            highlighter = markupModel.addRangeHighlighter(
                    textRange.getStartOffset(),
                    textRange.getEndOffset(),
                    determineHighlighterLayer(scanPackage),
                    attr,
                    HighlighterTargetArea.EXACT_RANGE
            );
        }

        if (addGutterIcon && !alreadyHasGutterIcon) {
            addGutterIcon(highlighter, scanPackage.getStatus());
        }
    }

    /**
     * Creates text attributes for error highlighting with wave underscore effect.
     *
     * @return the configured text attributes
     */
    private TextAttributes createTextAttributes() {
        TextAttributes errorAttrs = EditorColorsManager.getInstance()
                .getGlobalScheme().getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES);

        TextAttributes attr = new TextAttributes();
        attr.setEffectType(EffectType.WAVE_UNDERSCORE);
        attr.setEffectColor(errorAttrs.getEffectColor());
        attr.setForegroundColor(errorAttrs.getForegroundColor());
        attr.setBackgroundColor(null);

        return attr;
    }


    /**
     * Adds a gutter icon to the highlighter.
     *
     * @param highlighter the highlighter
     * @param severity    the severity
     */
    private void addGutterIcon(RangeHighlighter highlighter, String severity) {
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

    /**
     * Removes all existing gutter icons from the markup model in the given editor.
     *
     * @param file the file to remove the gutter icons from.
     */
    public void removeAllGutterIcons(PsiFile file) {
        ApplicationManager.getApplication().invokeLater(() -> {
            Editor editor = FileEditorManager.getInstance(file.getProject()).getSelectedTextEditor();
            if (editor == null) return;
            MarkupModel markupModel = editor.getMarkupModel();
            Arrays.stream(markupModel.getAllHighlighters())
                    .filter(highlighter -> highlighter.getGutterIconRenderer() != null)
                    .forEach(markupModel::removeHighlighter);
        });
    }

    /**
     * Checks if the highlighter already has a gutter icon for the given line.
     *
     * @param markupModel the markup model
     * @param editor      the editor
     * @param line        the line
     * @return true if the highlighter already has a gutter icon for the given line, false otherwise
     * @apiNote this method is particularly used to avoid adding duplicate gutter icons in the file for duplicate dependencies.
     */
    private boolean isAlreadyHasGutterIcon(MarkupModel markupModel, Editor editor, int line) {
        return Arrays.stream(markupModel.getAllHighlighters())
                .anyMatch(highlighter -> {
                    GutterIconRenderer renderer = highlighter.getGutterIconRenderer();
                    if (renderer == null) return false;
                    int existingLine = editor.getDocument().getLineNumber(highlighter.getStartOffset()) + 1;
                    // Match if highlighter covers the same PSI element region
                    return existingLine == line;
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
        return severityHighlightTypeMap.getOrDefault(detail.getStatus(), ProblemHighlightType.WEAK_WARNING);
    }

    /**
     * Determines the highlighter layer for a specific scan detail.
     *
     * @param detail the scan detail
     * @return the highlighter layer
     */
    public Integer determineHighlighterLayer(OssRealtimeScanPackage detail) {
        return severityHighlighterLayerMap.getOrDefault(detail.getStatus(), HighlighterLayer.WEAK_WARNING);
    }

    public void addToCxOneProblems(PsiFile file, List<CxProblems> problemsList) {
        ProblemHolderService.getInstance(file.getProject())
                .addProblems(file.getVirtualFile().getPath(), problemsList);
    }
}
