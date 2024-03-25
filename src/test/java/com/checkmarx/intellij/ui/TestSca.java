package com.checkmarx.intellij.ui;

import com.automation.remarks.junit5.Video;
import com.intellij.remoterobot.fixtures.JTreeFixture;
import com.intellij.remoterobot.fixtures.dataExtractor.RemoteText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static com.checkmarx.intellij.ui.utils.Xpath.*;
import static com.checkmarx.intellij.ui.utils.RemoteRobotUtils.*;

class TestSca extends BaseUITest {
    @Test
    @Video
    void testScaPanel() {
        getResults();
        waitForScanIdSelection();

        navigate("Scan", 2);
        navigate("sca", 3);
        navigate("Vulnerability", 4);
        log("Checking SCA results");
        JTreeFixture tree = find(JTreeFixture.class, TREE);

        List<RemoteText> scaHighNodes = tree.getData()
                                            .getAll()
                                            .stream()
                                            .filter(t -> t.getText().startsWith("HIGH"))
                                            .collect(Collectors.toList());

        if (!scaHighNodes.isEmpty()) {
            navigate("HIGH", 4);
        }
        //TODO: Fix the bug here
        //navigate("Npm", 5);
//        Optional<String> cveRow = tree.collectRows().stream().filter(treeRow -> treeRow.startsWith("CVE")).findFirst();
//        int dsvwRowIdx = cveRow.map(s -> tree.collectRows().indexOf(s)).orElse(-1);
//
//        Assertions.assertTrue(dsvwRowIdx > 1);
//        waitFor(() -> {
//            tree.clickRow(dsvwRowIdx);
//            if (hasAnyComponent(LINK_LABEL)) {
//                return !findAll(LINK_LABEL).isEmpty();
//            }
//            return true;
//        });
//
//        // If there is an auto remediation to the file, there must be a label starting with Upgrade to version. Otherwise, no information must be displayed
//        if (hasAnyComponent(AUTO_REMEDIATION)) {
//            waitFor(() -> {
//                tree.clickRow(dsvwRowIdx);
//                return find(MAGIC_RESOLVE).getData()
//                                          .getAll()
//                                          .stream()
//                                          .anyMatch(element -> element.getText().startsWith(UPGRADE_TO_VERSION_LABEL));
//            });
//        } else {
//            waitFor(() -> {
//                tree.clickRow(dsvwRowIdx);
//                return !findAll(NO_INFORMATION).isEmpty();
//            });
//        }

        testFileNavigation();
    }
}
