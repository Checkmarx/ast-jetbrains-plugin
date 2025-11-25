package com.checkmarx.intellij.devassist.problems;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.CxIcons;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.model.Location;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.util.SeverityLevel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
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

    private static final Logger LOGGER = Utils.getLogger(ProblemDecorator.class);
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

            if (!Objects.equals(editor.getDocument(), PsiDocumentManager.getInstance(project).getDocument(file))) {
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
        LOGGER.info("The text range is -->"+textRange);
        TextAttributes textAttributes = createTextAttributes(scanIssue.getSeverity());

        RangeHighlighter highlighter = markupModel.addLineHighlighter(
                targetLine - 1, 0, null);

        if (isProblem) {
            highlighter = markupModel.addRangeHighlighter(
                    textRange.getStartOffset(),
                    textRange.getEndOffset(),
                    determineHighlighterLayer(scanIssue),
                    textAttributes,
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
    private TextAttributes createTextAttributes(String severity) {
        TextAttributes errorAttrs = EditorColorsManager.getInstance()
                .getGlobalScheme().getAttributes(getCodeInsightColors(severity));

        TextAttributes attr = new TextAttributes();
        attr.setEffectType(EffectType.WAVE_UNDERSCORE);
        attr.setEffectColor(errorAttrs.getEffectColor());
        attr.setForegroundColor(errorAttrs.getForegroundColor());
        attr.setBackgroundColor(null);
        return attr;
    }

    /**
     * Gets the CodeInsightColors key based on severity.
     *
     * @param severity the severity
     * @return the text attributes key for the given severity
     */
    private TextAttributesKey getCodeInsightColors(String severity) {
        if (severity.equalsIgnoreCase(SeverityLevel.MALICIOUS.getSeverity()) ||
                severity.equalsIgnoreCase(SeverityLevel.CRITICAL.getSeverity())
                || severity.equalsIgnoreCase(SeverityLevel.HIGH.getSeverity())) {
            return CodeInsightColors.ERRORS_ATTRIBUTES;
        } else if (severity.equalsIgnoreCase(SeverityLevel.MEDIUM.getSeverity())) {
            return CodeInsightColors.WARNINGS_ATTRIBUTES;
        } else {
            return CodeInsightColors.WEAK_WARNING_ATTRIBUTES;
        }
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
                return CxIcons.Small.MALICIOUS;
            case CRITICAL:
                return CxIcons.Small.CRITICAL;
            case HIGH:
                return CxIcons.Small.HIGH;
            case MEDIUM:
                return CxIcons.Small.MEDIUM;
            case LOW:
                return CxIcons.Small.LOW;
            case OK:
                return CxIcons.Small.OK;
            default:
                return CxIcons.Small.UNKNOWN;
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

    /**
     * Removes all existing gutter icons from the markup model in the given editor.
     *
     * @param project the file to remove the gutter icons from.
     */
    public static void removeAllGutterIcons(Project project) {
        try {
            ApplicationManager.getApplication().invokeLater(() -> {
                Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
                if (editor == null) return;

                MarkupModel markupModel = editor.getMarkupModel();
                if (Objects.nonNull(markupModel.getAllHighlighters())) {
                    markupModel.removeAllHighlighters();
                }
            });
        } catch (Exception e) {
            LOGGER.debug("RTS-Decorator: Exception occurred while removing gutter icons for: {} ",
                    e.getMessage());
        }
    }

    /**
     * Restores problems for the given file.
     *
     * @param project       the project
     * @param psiFile       the psi file
     * @param scanIssueList the scan issue list
     */
    public void restoreGutterIcons(Project project, PsiFile psiFile, List<ScanIssue> scanIssueList, Document document) {
        removeAllGutterIcons(project);
        for (ScanIssue scanIssue : scanIssueList) {
            try {
                boolean isProblem = DevAssistUtils.isProblem(scanIssue.getSeverity().toLowerCase());
                int problemLineNumber = scanIssue.getLocations().get(0).getLine();
                PsiElement elementAtLine = psiFile.findElementAt(document.getLineStartOffset(problemLineNumber));
                if (elementAtLine != null) {
                    highlightLineAddGutterIconForProblem(project, psiFile, scanIssue, isProblem, problemLineNumber);
                }
            } catch (Exception e) {
                LOGGER.debug("RTS-Decorator: Exception occurred while restoring gutter icons for: {} ",
                        psiFile.getName(), scanIssue.getTitle(), e.getMessage());
            }
        }

    }
}
