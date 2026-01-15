package com.checkmarx.intellij.ui.PageMethods;

import com.intellij.remoterobot.fixtures.JTreeFixture;
import com.intellij.remoterobot.fixtures.dataExtractor.RemoteText;

import java.util.List;

import static com.checkmarx.intellij.ui.utils.RemoteRobotUtils.findAll;
import static com.checkmarx.intellij.ui.utils.UIHelper.*;
import static com.checkmarx.intellij.ui.utils.Xpath.*;

public class CxOneAssistFindingsTabPage {

    public static void isVulnerableFilePresentInCxAssistTree(String fileName) {
        //Implementation to verify if a specific vulnerable file is present in CxOne Assist Findings Tree
        waitFor(() -> {
            List<JTreeFixture> trees = findAll(JTreeFixture.class, FINDINGS_TREE_XPATH);

            if (trees.isEmpty()) return false;

            // Check if exact filename exists as a node
            return trees.get(0).findAllText().stream().map(RemoteText::getText).anyMatch(token -> token.equals(fileName));
        });
    }
}
