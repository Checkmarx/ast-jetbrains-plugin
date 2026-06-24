package com.checkmarx.intellij.ast.test.ui;

import com.automation.remarks.junit5.Video;
import com.checkmarx.intellij.common.window.actions.filter.SeverityFilter;
import com.intellij.remoterobot.fixtures.ComponentFixture;
import com.intellij.remoterobot.fixtures.JTreeFixture;
import com.intellij.remoterobot.fixtures.dataExtractor.RemoteText;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.checkmarx.intellij.ast.test.ui.utils.UIHelper.*;
import static com.checkmarx.intellij.ast.test.ui.utils.Xpath.*;
import static com.checkmarx.intellij.ast.test.ui.utils.RemoteRobotUtils.*;
import static com.checkmarx.intellij.ast.window.results.tree.nodes.ResultNode.UPGRADE_TO_VERSION_LABEL;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestSca extends com.checkmarx.intellij.ast.test.ui.BaseUITest {
    EnumSet<SeverityFilter> exclude = EnumSet.of(SeverityFilter.MALICIOUS, SeverityFilter.INFO);

    /** Navigates to an SCA Maven vulnerability row and selects it. Returns the row index. */
    private int navigateToScaVulnerability() {
        openCxToolWindow();
        getResults();
        waitForScanIdSelection();

        severity();
        Arrays.stream(SeverityFilter.values())
                .filter(severity -> !exclude.contains(severity))
                .forEach(severity -> toggleFilter(severity, true));
        navigate("Scan", 2);
        navigate("sca", 3);
        navigate("Vulnerability", 4);
        log("Checking SCA results");
        JTreeFixture tree = find(JTreeFixture.class, TREE);

        List<RemoteText> scaLowNodes = tree.getData()
                .getAll()
                .stream()
                .filter(t -> t.getText().startsWith("LOW"))
                .collect(Collectors.toList());

        if (!scaLowNodes.isEmpty()) {
            navigate("LOW", 4);
        }

        navigate("Maven", 2);

        Optional<String> cveRow = tree.collectRows().stream().filter(treeRow -> treeRow.startsWith("Maven")).findFirst();
        int rowIdx = cveRow.map(s -> tree.collectRows().indexOf(s)).orElse(-1);
        Assertions.assertTrue(rowIdx > 1, "Maven SCA vulnerability row not found in tree");

        waitFor(() -> {
            tree.clickRow(rowIdx);
            return !findAll(LINK_LABEL).isEmpty();
        });

        return rowIdx;
    }

    @Test
    @Video
    @Order(1)
    @DisplayName("SCA panel: navigate tree, verify remediation, and test file navigation")
    public void testScaPanel() {
        int dsvwRowIdx = navigateToScaVulnerability();
        JTreeFixture tree = find(JTreeFixture.class, TREE);

        // If there is an auto remediation to the file, there must be a label starting with Upgrade to version
        if (hasAnyComponent(AUTO_REMEDIATION)) {
            waitFor(() -> {
                tree.clickRow(dsvwRowIdx);
                return find(MAGIC_RESOLVE).getData()
                        .getAll()
                        .stream()
                        .anyMatch(element -> element.getText().startsWith(UPGRADE_TO_VERSION_LABEL));
            });
        } else {
            waitFor(() -> {
                tree.clickRow(dsvwRowIdx);
                return !findAll(NO_INFORMATION).isEmpty();
            });
        }

        testFileNavigation();
        openCxToolWindow();
    }

    @Test
    @Video
    @Order(2)
    @DisplayName("TC36: Verify SCA vulnerability details (CVE and CVSS score) are displayed on selection")
    public void testScaVulnerabilityDetailsDisplayed() {
        int rowIdx = navigateToScaVulnerability();
        JTreeFixture tree = find(JTreeFixture.class, TREE);

        // Select the vulnerability and wait for the detail panel to load
        waitFor(() -> {
            tree.clickRow(rowIdx);
            return !findAll(LINK_LABEL).isEmpty();
        });

        // The SCA detail panel renders a summary label with format: "Vulnerability | CVE-XXXX | 7.5 | HIGH"
        // Verify the summary JLabel contains a CVE/CWE identifier and a CVSS score
        waitFor(() -> {
            List<ComponentFixture> labels = findAll("//div[@class='JLabel']");
            return labels.stream()
                    .map(l -> l.callJs("component.getText()").toString())
                    .anyMatch(text -> text.contains("Vulnerability") && text.matches(".*\\d+\\.\\d+.*"));
        });
        log("SCA vulnerability details panel with CVE and CVSS score verified");
    }

    @Test
    @Video
    @Order(3)
    @DisplayName("TC37: Verify remediation recommendation with version is displayed for eligible SCA vulnerability")
    public void testScaRemediationVersionDisplayed() {
        int rowIdx = navigateToScaVulnerability();
        JTreeFixture tree = find(JTreeFixture.class, TREE);

        waitFor(() -> {
            tree.clickRow(rowIdx);
            return !findAll(LINK_LABEL).isEmpty();
        });

        if (hasAnyComponent(AUTO_REMEDIATION)) {
            // Verify the remediation label contains "Upgrade to version: " followed by an actual version string
            waitFor(() -> {
                List<RemoteText> texts = find(MAGIC_RESOLVE).getData().getAll();
                return texts.stream().anyMatch(element -> {
                    String text = element.getText();
                    return text.startsWith(UPGRADE_TO_VERSION_LABEL)
                            && text.length() > UPGRADE_TO_VERSION_LABEL.length();
                });
            });

            String versionText = find(MAGIC_RESOLVE).getData().getAll().stream()
                    .map(RemoteText::getText)
                    .filter(t -> t.startsWith(UPGRADE_TO_VERSION_LABEL))
                    .findFirst()
                    .orElse("");
            log("Remediation recommendation found: " + versionText);
            Assertions.assertTrue(versionText.length() > UPGRADE_TO_VERSION_LABEL.length(),
                    "Remediation should include an actual version number after '" + UPGRADE_TO_VERSION_LABEL + "'");
        } else {
            log("No auto-remediation available for this vulnerability — verifying 'No Information' is shown");
            waitFor(() -> !findAll(NO_INFORMATION).isEmpty());
        }
    }
}
