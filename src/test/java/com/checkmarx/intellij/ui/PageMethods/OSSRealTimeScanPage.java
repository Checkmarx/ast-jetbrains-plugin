package com.checkmarx.intellij.ui.PageMethods;

import com.intellij.remoterobot.fixtures.JTreeFixture;
import com.intellij.remoterobot.fixtures.dataExtractor.RemoteText;
import org.junit.jupiter.api.Assertions;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static com.checkmarx.intellij.ui.utils.RemoteRobotUtils.*;
import static com.checkmarx.intellij.ui.utils.Xpath.*;
import static com.checkmarx.intellij.ui.utils.UIHelper.*;

public class OSSRealTimeScanPage {

    public static void verifyOssFindingsTreeNotEmpty() {
        log("Verifying OSS Findings tree is not empty");
        //Check that findings tree is not empty and contains OSS-related files
        final AtomicBoolean findingsPresent = new AtomicBoolean(false);
        //wait for findings to appear in the tree
        waitFor(() -> {
            List<JTreeFixture> trees = findAll(JTreeFixture.class, FINDINGS_TREE_XPATH);
            if (trees.isEmpty())
                return false;
            //Check if any of the findings relate to OSS files (.xml/.json)
            boolean found = trees.get(0)
                    .findAllText()
                    .stream()
                    .map(RemoteText::getText)
                    .anyMatch(text ->
                            text.endsWith(".xml") ||
                                    text.endsWith(".json")
                    );

            findingsPresent.set(found);
            return found;
        });

        Assertions.assertTrue(
                findingsPresent.get(),
                "OSS findings tree is empty or no OSS-related files (.xml/.json) were found"
        );
    }
}
