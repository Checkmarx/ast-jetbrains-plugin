package com.checkmarx.intellij.standard.commands;

import com.checkmarx.ast.project.Project;
import com.checkmarx.intellij.Environment;
import com.checkmarx.intellij.commands.Scan;
import com.checkmarx.intellij.standard.BaseTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

public class TestScan extends BaseTest {

    @Test
    public void testGetLatest() {
        String scanId = Assertions.assertDoesNotThrow(Scan::getLatestScanId);
        Assertions.assertDoesNotThrow(() -> UUID.fromString(scanId));
    }

    @Test
    public void testGetList() {
        Project project = getEnvProject();
        List<com.checkmarx.ast.scan.Scan> scans
                = Assertions.assertDoesNotThrow(() -> Scan.getList(project.getID(), Environment.BRANCH_NAME));
        Assertions.assertTrue(scans.size() > 0);
    }

    @Test
    public void testScanShow() {
        com.checkmarx.ast.scan.Scan scan = Assertions.assertDoesNotThrow(() -> Scan.scanShow(Environment.SCAN_ID));
        Project project = getEnvProject();
        Assertions.assertEquals(scan.getProjectID(), project.getID());
    }
}
