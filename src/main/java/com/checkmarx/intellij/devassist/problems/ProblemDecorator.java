package com.checkmarx.intellij.devassist.problems;

import com.checkmarx.intellij.CxIcons;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.model.Location;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.util.SeverityLevel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * ProblemDecorator class responsible to provides utility methods for managing problem, highlighting and gutter icons.
 */
public class ProblemDecorator {

    private static final Logger LOGGER = Utils.getLogger(ProblemDecorator.class);

    /**
     * Adds a gutter icon at the line of the given PsiElement.
     */
    public void highlightLineAddGutterIconForProblem(ProblemHelper problemHelper, ScanIssue scanIssue, boolean isProblem, int problemLineNumber) {
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                Editor editor = FileEditorManager.getInstance(problemHelper.getProject()).getSelectedTextEditor();
                if (editor == null) return;

                if (!Objects.equals(editor.getDocument(), PsiDocumentManager.getInstance(problemHelper.getProject())
                        .getDocument(problemHelper.getFile()))) {
                    // Only decorate the active editor of this file
                    return;
                }
                MarkupModel markupModel = editor.getMarkupModel();
                // Create a new instance of ProblemDecoratorHelper
                ProblemDecoratorHelper decoratorHelper = new ProblemDecoratorHelper(scanIssue);
                decoratorHelper.setMarkupModel(markupModel);
                decoratorHelper.setEditor(editor);
                decoratorHelper.setDocument(editor.getDocument());
                decoratorHelper.setAddGutterIcon(true);
                decoratorHelper.setProblemLineNumber(problemLineNumber);
                decoratorHelper.setProblem(isProblem);

                for (Location location : scanIssue.getLocations()) {
                    decoratorHelper.setHighlightLineNumber(location.getLine());
                    highlightLocationInEditor(problemHelper, decoratorHelper);
                    decoratorHelper.setAddGutterIcon(false);
                }
            } catch (Exception exception) {
                LOGGER.warn(format("RTS-Decorator: Exception occurred while highlighting or adding gutter icon for line: %s , Exception: {} ",
                        problemLineNumber), exception);
            }
        },ModalityState.NON_MODAL);
    }

    /**
     * Highlights a specific location in the editor and optionally adds a gutter icon.
     *
     * @param problemHelper   the problem helper containing relevant scan issue information
     * @param decoratorHelper the decorator helper containing editor and markup model information
     */
    private void highlightLocationInEditor(ProblemHelper problemHelper, ProblemDecoratorHelper decoratorHelper) {
        try {
            TextRange textRange = DevAssistUtils.getTextRangeForLine(decoratorHelper.getDocument(), decoratorHelper.getHighlightLineNumber());
            TextAttributes textAttributes = createTextAttributes();

            RangeHighlighter highlighter = decoratorHelper.getMarkupModel().addLineHighlighter(
                    decoratorHelper.getHighlightLineNumber() - 1, 0, null);

            if (decoratorHelper.isProblem()) {
                highlighter = decoratorHelper.getMarkupModel().addRangeHighlighter(
                        textRange.getStartOffset(),
                        textRange.getEndOffset(),
                        HighlighterLayer.ERROR,
                        textAttributes,
                        HighlighterTargetArea.EXACT_RANGE
                );
            }
            boolean alreadyHasGutterIcon = isAlreadyHasGutterIconOnLine(decoratorHelper.getMarkupModel(), decoratorHelper.getEditor(),
                    decoratorHelper.getProblemLineNumber());

            if (decoratorHelper.isAddGutterIcon() && !alreadyHasGutterIcon) {
                String severity = getMostSeverity(problemHelper.getScanIssueList(), decoratorHelper.getScanIssue(),
                        decoratorHelper.getProblemLineNumber());
                addGutterIcon(highlighter, severity);
            }
        } catch (Exception e) {
            LOGGER.debug("RTS-Decorator: Exception occurred while highlighting line: {} , Exception: {} ",
                    decoratorHelper.getHighlightLineNumber(), e.getMessage());
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
     * Gets the most severe severity among scan issues located on the same line.
     *
     * @param allScanIssueList  the list of all scan issues for the file including all scan engines
     * @param scanIssue         the scan issue containing severity and other details
     * @param problemLineNumber the line number in the editor where the scan issue is located
     */
    private String getMostSeverity(List<ScanIssue> allScanIssueList, ScanIssue scanIssue, int problemLineNumber) {
        try {
            List<ScanIssue> sameLineIssueList = allScanIssueList.stream()
                    .filter(issue -> issue.getLocations().get(0).getLine() == problemLineNumber
                            && !Objects.equals(issue.getScanIssueId(), scanIssue.getScanIssueId())
                            && !Objects.equals(issue.getSeverity(), scanIssue.getSeverity()))
                    .collect(Collectors.toList());

            return !sameLineIssueList.isEmpty()
                    ? DevAssistUtils.getSeverityBasedOnPrecedence(sameLineIssueList, scanIssue.getSeverity())
                    : scanIssue.getSeverity();
        } catch (Exception exception) {
            LOGGER.debug("RTS: Exception occurred while retrieving most severity to update gutter icon.", exception);
            return scanIssue.getSeverity();
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
    private boolean isAlreadyHasGutterIconOnLine(MarkupModel markupModel, Editor editor, int line) {
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
     * Removes all existing gutter icons from the markup model in the given editor.
     *
     * @param project the file to remove the gutter icons from.
     */
    public static void removeAllHighlighters(Project project) {
        try {
            ApplicationManager.getApplication().invokeLater(() -> {
                Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
                if (editor == null) return;

                MarkupModel markupModel = editor.getMarkupModel();
                if (markupModel.getAllHighlighters().length > 0) {
                    markupModel.removeAllHighlighters();
                }
            },ModalityState.NON_MODAL);
        } catch (Exception e) {
            LOGGER.debug("RTS-Decorator: Exception occurred while removing highlighter with gutter icons for: {} ",
                    e.getMessage());
        }
    }

    /**
     * Decorating the UI for the given file.
     *
     * @param project       the project
     * @param psiFile       the psi file
     * @param scanIssueList the scan issue list
     */
    public void decorateUI(Project project, PsiFile psiFile, List<ScanIssue> scanIssueList, Document document) {
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                // Update UI, highlight, or trigger inspection
                removeAllHighlighters(project);
                ProblemHelper problemHelper = ProblemHelper.builder(psiFile, project)
                        .scanIssueList(scanIssueList)
                        .document(document)
                        .build();
                for (ScanIssue scanIssue : scanIssueList) {
                    try {
                        int problemLineNumber = scanIssue.getLocations().get(0).getLine();
                        PsiElement elementAtLine = DevAssistUtils.getPsiElement(psiFile, document, problemLineNumber);
                        if (Objects.isNull(elementAtLine)) {
                            LOGGER.warn(format("RTS-Decorator: Skipping to add gutter icon, Failed to find PSI element for line : %s , Issue: %s",
                                    problemLineNumber, scanIssue.getTitle()));
                            continue;
                        }
                        boolean isProblem = DevAssistUtils.isProblem(scanIssue.getSeverity().toLowerCase());
                        highlightLineAddGutterIconForProblem(problemHelper, scanIssue, isProblem, problemLineNumber);
                    } catch (Exception e) {
                        LOGGER.debug("RTS-Decorator: Exception occurred while restoring gutter icons for: {} ",
                                psiFile.getName(), scanIssue.getTitle(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                LOGGER.warn(format("RTS-Decorator: Exception occurred while removing all highlighters for file: %s", psiFile.getName()), e);
            }
        }, ModalityState.NON_MODAL);
    }

    /**
     * Helper class to hold relevant information for problem decoration.
     */
    @Setter
    @Getter
    public static class ProblemDecoratorHelper {

        ProblemDecoratorHelper(ScanIssue scanIssue) {
            this.scanIssue = scanIssue;
        }

        private ScanIssue scanIssue;
        private MarkupModel markupModel;
        private Editor editor;
        private Document document;
        private boolean addGutterIcon;
        private int problemLineNumber;
        private boolean isProblem;
        private int highlightLineNumber;
    }
}
