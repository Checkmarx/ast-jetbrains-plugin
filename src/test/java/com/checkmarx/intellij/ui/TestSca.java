package com.checkmarx.intellij.ui;

import com.automation.remarks.junit5.Video;
import com.intellij.remoterobot.fixtures.JTreeFixture;
import com.intellij.remoterobot.fixtures.dataExtractor.RemoteText;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.checkmarx.intellij.ui.utils.Xpath.*;
import static com.checkmarx.intellij.ui.utils.RemoteRobotUtils.*;

public class TestSca extends BaseUITest {
    @Test
    @Video
    public void testScaPanel() {
        getResults();
        waitForScanIdSelection();

        navigate("Scan", 2);
        navigate("sca", 3);
        navigate("Vulnerability", 4);

        JTreeFixture tree = find(JTreeFixture.class, TREE);

        List<RemoteText> scaHighNodes = tree.getData().getAll().stream().filter(t -> t.getText().startsWith("HIGH")).collect(Collectors.toList());

        if (scaHighNodes.size() != 0) {
            navigate("HIGH", 4);
        }

        navigate("Pip", 5);

        Optional<String> cveRow = tree.collectRows().stream().filter(treeRow -> treeRow.startsWith("CVE")).findFirst();
        int dsvwRowIdx = cveRow.map(s -> tree.collectRows().indexOf(s)).orElse(-1);

        Assertions.assertTrue(dsvwRowIdx > 1);
        waitFor(() -> {
            tree.clickRow(dsvwRowIdx);
            return !findAll(LINK_LABEL).isEmpty();
        });

        Assertions.assertTrue(hasAnyComponent(MAGIC_RESOLVE));

        testFileNavigation();
    }
}
