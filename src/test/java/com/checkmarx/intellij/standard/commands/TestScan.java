package com.checkmarx.intellij.standard.commands;

import com.checkmarx.ast.project.Project;
import com.checkmarx.intellij.Environment;
import com.checkmarx.intellij.commands.Scan;
import com.checkmarx.intellij.standard.BaseTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
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
                = Assertions.assertDoesNotThrow(() -> Scan.getList(project.getId(), Environment.BRANCH_NAME));
        String msg = String.format("project: %s branch: %s scans: %d", project.getId(), Environment.BRANCH_NAME, scans.size());
        Assertions.assertTrue(scans.size() > 0, msg);
        Assertions.assertTrue(scans.size() <= 10000, msg);
        int i = 0;
        for (com.checkmarx.ast.scan.Scan scan : scans) {
            if (i++ > 10) {
                break;
            }
            Assertions.assertEquals("Completed", scan.getStatus());
            Assertions.assertEquals(Environment.BRANCH_NAME, scan.getBranch());
            Assertions.assertEquals(getEnvProject().getId(), scan.getProjectId());
        }
    }

    @Test
    public void testScanShow() {
        com.checkmarx.ast.scan.Scan scan = Assertions.assertDoesNotThrow(() -> Scan.scanShow(Environment.SCAN_ID));
        Project project = getEnvProject();
        Assertions.assertEquals(scan.getProjectId(), project.getId());
    }

    @Test
    public void testScanCreate() {
        com.checkmarx.ast.scan.Scan scan = Assertions.assertDoesNotThrow(() -> Scan.scanCreate(System.getProperty("user.dir"), Environment.PROJECT_NAME, Environment.BRANCH_NAME));
        Project project = getEnvProject();
        Assertions.assertNotEquals("completed", scan.getStatus().toLowerCase(Locale.ROOT));
        Assertions.assertEquals(scan.getProjectId(), project.getId());
    }

    @Test
    public void testScanCancel() {
        com.checkmarx.ast.scan.Scan scan = Assertions.assertDoesNotThrow(() -> Scan.scanCreate(System.getProperty("user.dir"), Environment.PROJECT_NAME, Environment.BRANCH_NAME));
        Project project = getEnvProject();
        Assertions.assertNotEquals("completed", scan.getStatus().toLowerCase(Locale.ROOT));
        Assertions.assertEquals(scan.getProjectId(), project.getId());
        Assertions.assertDoesNotThrow(() -> Scan.scanCancel(scan.getId()));
    }
}
