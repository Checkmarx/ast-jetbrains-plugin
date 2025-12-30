package com.checkmarx.intellij.inspections;

import com.checkmarx.ast.results.result.Node;
import com.checkmarx.ast.results.result.Result;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.project.ProjectResultsService;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Visitor that can mark each file element as a problem according to scan results.
 */
public class CxVisitor extends PsiElementVisitor {

    private static final Logger LOGGER = Utils.getLogger(CxVisitor.class);
    private final Set<Integer> registeredNodes = new HashSet<>();
    private static final String descriptionFormat = "%s - %s - %s";

    private final ProblemsHolder holder;

    public CxVisitor(ProblemsHolder holder) {
        super();
        this.holder = holder;
    }


    /**
     * Checks the scan results to decide whether to mark an element as a problem:
     * - The element start offset must match the line and column in the result;
     * - The problem must not have been previously registered.
     * Stores the result node in a set if the element is highlighted.
     *
     * @param element current file element
     */
    @Override
    public void visitElement(@NotNull PsiElement element) {
        super.visitElement(element);

        // end offset is exclusive, empty elements not allowed
        if (element.getTextRange().getStartOffset() == element.getTextRange().getEndOffset()) {
            return;
        }

        PsiFile file = element.getContainingFile();

        Document doc = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (doc == null) {
            return;
        }

        // lineNumber is 0-based, AST line number is 1-based
        int lineNumber = doc.getLineNumber(element.getTextOffset());

        try {
            List<Node> nodes = element.getProject().getService(ProjectResultsService.class)
                                      .getResultsForFileAndLine(element.getProject(),
                                                                file.getVirtualFile()
                                                                    .getName(), lineNumber + 1);

            for (Node node : nodes) {
                int startOffset = doc.getLineStartOffset(lineNumber) + getStartOffset(node);
                int endOffset = doc.getLineStartOffset(lineNumber) + getEndOffset(node);
                if (startOffset == element.getTextRange().getStartOffset()
                    && endOffset == element.getTextRange().getEndOffset()
                    && !alreadyRegistered(node)) {
                    registeredNodes.add(node.getNodeId());
                    holder.registerProblem(element,
                                           getDescriptionTemplate(element.getProject(), node),
                                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Exception occurred, Failed to get scan results for file: " + file.getVirtualFile().getPath(), e);
        }
    }

    /**
     * Checks if the result node was already registered.
     *
     * @param node result node
     * @return whether the node was already registered
     */
    private boolean alreadyRegistered(Node node) {
        return registeredNodes.contains(node.getNodeId());
    }

    /**
     * Translate a node to its start text offset by the line, column, length and definitions.
     *
     * @param node node to translate
     * @return start offset in the file
     */
    private static int getStartOffset(Node node) {
        String definitions = node.getDefinitions();
        if ("-1".equals(definitions)) {
            return node.getColumn() - 1 - node.getLength();
        }
        return node.getColumn() - 1;
    }

    /**
     * Translate a node to its end text offset by the line, column, length and definitions.
     *
     * @param node node to translate
     * @return end offset in the file
     */
    private static int getEndOffset(Node node) {
        String definitions = node.getDefinitions();
        if ("-1".equals(definitions)) {
            return node.getColumn() - 1;
        }
        return node.getColumn() - 1 + node.getLength();
    }

    /**
     * Provides a description for the highlighted problem.
     *
     * @param node corresponding result node
     * @return description
     */
    private static String getDescriptionTemplate(Project project, Node node) {
        Result result = project.getService(ProjectResultsService.class).getResultForNode(node);
        return String.format(descriptionFormat,
                             result.getData().getGroup(),
                             result.getData().getQueryName(),
                             node.getName());
    }
}
