package com.checkmarx.intellij.ast.test.ui.PageMethods;

import com.intellij.remoterobot.fixtures.JTreeFixture;
import com.intellij.remoterobot.fixtures.dataExtractor.RemoteText;

import java.time.Duration;
import java.util.List;

import static com.checkmarx.intellij.ast.test.ui.utils.RemoteRobotUtils.findAll;
import static com.checkmarx.intellij.ast.test.ui.utils.UIHelper.waitFor;
import static com.checkmarx.intellij.ast.test.ui.utils.Xpath.FINDINGS_TREE_XPATH;


public class CxOneAssistFindingsTabPage {

    public static void isVulnerableFilePresentInCxAssistTree(String fileName) {
        waitFor(() -> findFileInTree(fileName));
    }

    public static void isVulnerableFilePresentInCxAssistTree(String fileName, Duration timeout) {
        waitFor(() -> findFileInTree(fileName), timeout);
    }

    /**
     * Checks if a vulnerable file is present in the CxOne Assist Findings Tree without waiting.
     * @param fileName The filename to check
     * @return true if the file is found, false otherwise
     */
    public static boolean checkIfVulnerableFileExists(String fileName) {
        try {
            return findFileInTree(fileName);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean findFileInTree(String fileName) {
        List<JTreeFixture> trees = findAll(JTreeFixture.class, FINDINGS_TREE_XPATH);
        if (trees.isEmpty()) return false;
        return trees.get(0).findAllText().stream()
                .map(RemoteText::getText)
                .anyMatch(token -> token.equals(fileName));
    }
}
