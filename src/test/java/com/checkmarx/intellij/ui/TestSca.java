package com.checkmarx.intellij.ui;

import com.automation.remarks.junit5.Video;
import com.intellij.remoterobot.fixtures.JTreeFixture;
import com.intellij.remoterobot.fixtures.dataExtractor.RemoteText;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

public class TestSca extends BaseUITest {

    @Test
    @Video
    public void testScaPanel() {
        getResults();
        waitForScanIdSelection();

        navigate("Scan", 2);
        navigate("sca", 3);

        List<RemoteText> prefixNodes = find(JTreeFixture.class, TREE).getData()
                .getAll()
                .stream()
                .filter(t -> t.getText().startsWith("HIGH"))
                .collect(Collectors.toList());
        if (prefixNodes.size() != 0) {
            navigate("HIGH", 4);
        }
        navigate("Pip", 5);

        JTreeFixture tree = find(JTreeFixture.class, TREE);
        int row = -1;
        for (int i = 0; i < tree.collectRows().size(); i++) {
            if (tree.getValueAtRow(i).startsWith("CVE")) {
                row = i;
                break;
            }
        }
        // open first node of the opened result
        final int resultRow = row;
        Assertions.assertTrue(resultRow > 1);
        waitFor(() -> {
            tree.clickRow(resultRow);
            return findAll(LINK_LABEL).size() > 0;
        });

        Assertions.assertTrue(hasAnyComponent("//div[@disabledicon='magicResolve.svg']"));

        testFileNavigation();
    }
}
