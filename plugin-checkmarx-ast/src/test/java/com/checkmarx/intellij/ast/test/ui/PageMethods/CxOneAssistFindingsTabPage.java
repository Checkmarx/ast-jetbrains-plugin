package com.checkmarx.intellij.ast.test.ui.PageMethods;

import com.intellij.remoterobot.fixtures.JTreeFixture;
import com.intellij.remoterobot.fixtures.dataExtractor.RemoteText;

import java.util.List;

import static com.checkmarx.intellij.ast.test.ui.utils.RemoteRobotUtils.findAll;
import static com.checkmarx.intellij.ast.test.ui.utils.UIHelper.waitFor;
import static com.checkmarx.intellij.ast.test.ui.utils.Xpath.FINDINGS_TREE_XPATH;


public class CxOneAssistFindingsTabPage {

    public static void isVulnerableFilePresentInCxAssistTree(String fileName) {
        //Implementation to verify if a specific vulnerable file is present in CxOne Assist Findings Tree
        waitFor(() -> {
            List<JTreeFixture> trees = findAll(JTreeFixture.class, FINDINGS_TREE_XPATH);

            if (trees.isEmpty()) return false;

            // Check if the exact filename exists as a node
            return trees.get(0).findAllText().stream().map(RemoteText::getText).anyMatch(token -> token.equals(fileName));
        });
    }
}
