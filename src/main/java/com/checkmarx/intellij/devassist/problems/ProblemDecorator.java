package com.checkmarx.intellij.devassist.problems;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.CxIcons;
import com.checkmarx.intellij.devassist.model.Location;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.util.SeverityLevel;
import com.intellij.openapi.application.ApplicationManager;
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
 * ProblemDecorator class responsible to provides utility methods for managing problem, highlighting and gutter icons.
 */
@Getter
public class ProblemDecorator {

    private final Map<String, Integer> severityHighlighterLayerMap = new HashMap<>();

    public ProblemDecorator() {
        initSeverityHighlighterLayerMap();
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
            for (Location location : scanIssue.getLocations()) {
                int targetLine = location.getLine();
                highlightLocationInEditor(editor, markupModel, targetLine, scanIssue, isFirstLocation, isProblem, problemLineNumber);
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
                                           ScanIssue scanIssue, boolean addGutterIcon, boolean isProblem, int problemLineNumber) {
        TextRange textRange = DevAssistUtils.getTextRangeForLine(editor.getDocument(), targetLine);
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
        boolean alreadyHasGutterIcon = isAlreadyHasGutterIcon(markupModel, editor, problemLineNumber);
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
     * Determines the highlighter layer for a specific scan detail.
     *
     * @param scanIssue the scan detail
     * @return the highlighter layer
     */
    public Integer determineHighlighterLayer(ScanIssue scanIssue) {
        return severityHighlighterLayerMap.getOrDefault(scanIssue.getSeverity(), HighlighterLayer.WEAK_WARNING);
    }
}
