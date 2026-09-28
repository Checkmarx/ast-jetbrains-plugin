package com.checkmarx.intellij.ast.test.ui.PageMethods;

import com.intellij.remoterobot.fixtures.EditorFixture;
import com.intellij.remoterobot.utils.Keyboard;
import org.junit.jupiter.api.Assertions;

import java.awt.Point;
import java.awt.event.KeyEvent;

import static com.checkmarx.intellij.ast.test.ui.PageMethods.ScanResultsPannelPage.selectVulnerability;
import static com.checkmarx.intellij.ast.test.ui.utils.RemoteRobotUtils.*;
import static com.checkmarx.intellij.ast.test.ui.utils.UIHelper.*;
import static com.checkmarx.intellij.ast.test.ui.utils.Xpath.*;

/**
 * Page object for the vulnerability detail side panel's Attack Vector tab (SAST call-path nodes).
 */
public class AttackVectorPage {

    /**
     * Selects the vulnerability with the given name, opens its Attack Vector tab, and clicks the
     * node at the given 1-based position in the call-path node list.
     *
     * @param name      The name of the vulnerability to select.
     * @param nodeIndex The 1-based position of the node to click within the Attack Vector list.
     */
    public static void clickAttackVectorNode(String name, int nodeIndex) {
        selectVulnerability(name);

        String nodeLink = String.format(ATTACK_VECTOR_NODE_LINK, nodeIndex);
        waitFor(() -> {
            find(TAB_ATTACK_VECTOR).click();
            return hasAnyComponent(nodeLink);
        });

        find(nodeLink).click();
        log("Clicked Attack Vector node #" + nodeIndex + " for vulnerability: " + name);
    }

    /**
     * Verifies that the editor opened by the last {@link #clickAttackVectorNode} call shows the
     * given file with the caret positioned on the given line.
     *
     * @param file The expected file path (or suffix of it) the editor should be showing.
     * @param line The expected 1-based line number the caret should be positioned on.
     */
    public static void verifyEditorOpensFile(String file, int line) {
        waitFor(() -> hasAnyComponent(EDITOR));
        EditorFixture editor = find(EditorFixture.class, EDITOR);

        waitFor(() -> normalizedPath(getOpenFilePath(editor)).endsWith(normalizedPath(file)));

        String openFile = getOpenFilePath(editor);
        Assertions.assertTrue(normalizedPath(openFile).endsWith(normalizedPath(file)),
                "Editor should have opened file ending with '" + file + "' but was: " + openFile);

        int actualLine = getCaretLine(editor);
        Assertions.assertEquals(line, actualLine,
                "Editor caret should be positioned on line " + line + " but was on line " + actualLine);

        log("Verified editor opened '" + file + "' at line " + line);
    }

    /**
     * Selects the vulnerability with the given name, opens its Attack Vector tab, clicks the node at
     * the given 1-based position (which opens the editor on the vulnerable line, see
     * {@link #clickAttackVectorNode}), then hovers the mouse over the marker on that line and verifies
     * a tooltip appears containing the given text.
     *
     * @param name                The name of the vulnerability to select.
     * @param nodeIndex           The 1-based position of the node to click within the Attack Vector list.
     * @param expectedTooltipText Text expected to appear in the tooltip shown for the marker.
     */
    public static void hoverOverMarkerAndVerifyTooltip(String name, int nodeIndex, String expectedTooltipText) {
        clickAttackVectorNode(name, nodeIndex);

        waitFor(() -> hasAnyComponent(EDITOR));
        EditorFixture editor = find(EditorFixture.class, EDITOR);

        Point markerPoint = getCaretScreenPoint(editor);
        try {
            editor.moveMouse(markerPoint);

            waitFor(() -> hasAnyComponent(EDITOR_TOOLTIP_CONTENT));

            String tooltipText = getText(EDITOR_TOOLTIP_CONTENT);
            Assertions.assertTrue(tooltipText != null && tooltipText.contains(expectedTooltipText),
                    "Tooltip for vulnerability marker should contain '" + expectedTooltipText + "' but was: " + tooltipText);

            log("Verified tooltip shown for vulnerability marker: " + name);
        } finally {
            // Dismiss the hover tooltip and move the real cursor off the marker so a failed
            // assertion above doesn't leave it stuck over the editor, blocking subsequent tests.
            new Keyboard(remoteRobot).key(KeyEvent.VK_ESCAPE);
            editor.moveMouse(new Point(0, 0));
        }
    }

    /**
     * Resolves the on-screen point (relative to the editor component) of the current caret position,
     * so it can be passed to {@link EditorFixture#moveMouse(Point)} to hover over that location.
     */
    private static Point getCaretScreenPoint(EditorFixture editor) {
        Object result = editor.callJs(
                "(function() {"
                        + "  var e = component.getEditor();"
                        + "  var pos = e.getCaretModel().getLogicalPosition();"
                        + "  var p = e.logicalPositionToXY(pos);"
                        + "  return p.x + ',' + p.y;"
                        + "})()"
        );
        String[] parts = result.toString().split(",");
        return new Point(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
    }

    private static String getOpenFilePath(EditorFixture editor) {
        Object result = editor.callJs(
                "component.getEditor().getVirtualFile() ? component.getEditor().getVirtualFile().getPath() : null");
        return result != null ? result.toString() : "";
    }

    private static int getCaretLine(EditorFixture editor) {
        Object result = editor.callJs("component.getEditor().getCaretModel().getLogicalPosition().line + 1");
        return result != null ? Integer.parseInt(result.toString()) : -1;
    }

    private static String normalizedPath(String path) {
        return path == null ? "" : path.replace('\\', '/');
    }

    /**
     * Selects the vulnerability with the given name, opens its Attack Vector tab, switches to the
     * nested Remediation Examples tab, and verifies both a vulnerable code sample and a fixed code
     * sample are shown with non-blank code content.
     * <p>
     * {@link com.checkmarx.intellij.ast.window.results.tree.nodes.ResultNode#generateCodeSamples}
     * renders each sample as a title pane followed by a code pane, so with the two samples
     * (vulnerable, fixed) returned for a query, the code panes land at positions 2 and 4.
     *
     * @param name The name of the vulnerability to select.
     */
    public static void selectVulnerabilityAndVerifyRemediationExamplesTab(String name) {
        selectVulnerability(name);

        waitFor(() -> {
            find(TAB_ATTACK_VECTOR).click();
            return hasAnyComponent(TAB_RECOMMENDATIONS_EXAMPLES);
        });

        String vulnerableCodeBlock = String.format(TAB_RECOMMENDATIONS_EXAMPLES_CODE_BLOCK, 2);
        String fixedCodeBlock = String.format(TAB_RECOMMENDATIONS_EXAMPLES_CODE_BLOCK, 4);

        waitFor(() -> {
            find(TAB_RECOMMENDATIONS_EXAMPLES).click();
            return hasAnyComponent(vulnerableCodeBlock) && hasAnyComponent(fixedCodeBlock);
        });

        String vulnerableCode = getText(vulnerableCodeBlock);
        Assertions.assertTrue(vulnerableCode != null && !vulnerableCode.trim().isEmpty(),
                "Remediation Examples tab should show a non-blank vulnerable code sample for: " + name);

        String fixedCode = getText(fixedCodeBlock);
        Assertions.assertTrue(fixedCode != null && !fixedCode.trim().isEmpty(),
                "Remediation Examples tab should show a non-blank fixed code sample for: " + name);

        log("Verified Remediation Examples tab shows vulnerable and fixed code samples for vulnerability: " + name);
    }
}
