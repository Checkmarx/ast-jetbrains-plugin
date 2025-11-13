package com.checkmarx.intellij.devassist.problems;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.CxIcons;
import com.checkmarx.intellij.devassist.model.Location;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.util.SeverityLevel;
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
import java.util.*;

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
        if (status.equalsIgnoreCase(SeverityLevel.OK.getSeverity())) {
            return false;
        } else return !status.equalsIgnoreCase(SeverityLevel.UNKNOWN.getSeverity());
    }

    /**
     * Checks if the given line number is out of range for the document.
     *
     * @param lineNumber the line number to check (1-based)
     * @param document   the document
     * @return true if the line number is out of range, false otherwise
     */
    public boolean isLineOutOfRange(int lineNumber, Document document) {
        return lineNumber <= 0 || lineNumber > document.getLineCount();
    }

    /**
     * Retrieves the text range for the specified line in the given document, trimming leading and trailing whitespace.
     *
     * @param document          the document from which the specified line's text range is to be retrieved
     * @param problemLineNumber the 1-based line number for which the text range is needed
     * @return a TextRange representing the trimmed start and end offsets of the specified line
     */
    public TextRange getTextRangeForLine(Document document, int problemLineNumber) {
        // Convert to 0-based index for document API
        int lineIndex = problemLineNumber - 1;

        // Get the exact offsets for this line
        int lineStartOffset = document.getLineStartOffset(lineIndex);
        int lineEndOffset = document.getLineEndOffset(lineIndex);

        // Get the line text and trim whitespace for highlighting
        CharSequence chars = document.getCharsSequence();

        // Calculate leading spaces
        int trimmedStartOffset = lineStartOffset;
        while (trimmedStartOffset < lineEndOffset && Character.isWhitespace(chars.charAt(trimmedStartOffset))) {
            trimmedStartOffset++;
        }
        // Calculate trailing spaces
        int trimmedEndOffset = lineEndOffset;
        while (trimmedEndOffset > trimmedStartOffset && Character.isWhitespace(chars.charAt(trimmedEndOffset - 1))) {
            trimmedEndOffset--;
        }
        // Ensure a valid range (fallback to original if the line is all whitespace)
        if (trimmedStartOffset >= trimmedEndOffset) {
            return new TextRange(lineStartOffset, lineEndOffset);
        }
        return new TextRange(trimmedStartOffset, trimmedEndOffset);
    }


    /**
     * Adds a gutter icon at the line of the given PsiElement.
     */
    public void highlightLineAddGutterIconForProblem(@NotNull Project project, @NotNull PsiFile file,
                                                     ScanIssue scanIssue, boolean isProblem, int problemLineNumber) {
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


            for (Location location : scanIssue.getLocations()) {
                int targetLine = location.getLine();
                highlightLocationInEditor(editor, markupModel, targetLine, scanIssue, isFirstLocation, isProblem, alreadyHasGutterIcon);
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
     * @param scanIssue     the scan package containing severity information
     * @param addGutterIcon whether to add a gutter icon for this location
     */
    private void highlightLocationInEditor(Editor editor, MarkupModel markupModel, int targetLine,
                                           ScanIssue scanIssue, boolean addGutterIcon, boolean isProblem, boolean alreadyHasGutterIcon) {
        TextRange textRange = getTextRangeForLine(editor.getDocument(), targetLine);
        TextAttributes attr = createTextAttributes();

        RangeHighlighter highlighter = markupModel.addLineHighlighter(
                targetLine - 1, 0, null);

        if (isProblem) {
            highlighter = markupModel.addRangeHighlighter(
                    textRange.getStartOffset(),
                    textRange.getEndOffset(),
                    determineHighlighterLayer(scanIssue),
                    attr,
                    HighlighterTargetArea.EXACT_RANGE
            );
        }

        if (addGutterIcon && !alreadyHasGutterIcon) {
            addGutterIcon(highlighter, scanIssue.getSeverity());
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
        switch (SeverityLevel.fromValue(severity)) {
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
     * @param scanIssue the scan detail
     * @return the problem highlight type
     */
    public ProblemHighlightType determineHighlightType(ScanIssue scanIssue) {
        return severityHighlightTypeMap.getOrDefault(scanIssue.getSeverity(), ProblemHighlightType.WEAK_WARNING);
    }

    /**
     * Determines the highlighter layer for a specific scan detail.
     *
     * @param scanIssue the scan detail
     * @return the highlighter layer
     */
    public Integer determineHighlighterLayer(ScanIssue scanIssue) {
        return severityHighlighterLayerMap.getOrDefault(scanIssue.getSeverity(), HighlighterLayer.WEAK_WARNING);
    }

    public void addToCxOneFindings(PsiFile file, List<ScanIssue> problemsList) {
        ProblemHolderService.getInstance(file.getProject()).addProblems(file.getVirtualFile().getPath(), problemsList);
    }
}
