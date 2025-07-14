package com.checkmarx.intellij.integration.standard.commands;

import com.checkmarx.ast.project.Project;
import com.checkmarx.intellij.integration.Environment;
import com.checkmarx.intellij.commands.Scan;
import com.checkmarx.intellij.integration.standard.BaseTest;
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

        // Check that all scans are for the correct project and branch and have completed
        Assertions.assertTrue(scans.stream().allMatch(scan -> scan.getProjectId().equals(project.getId()) && scan.getBranch().equals(Environment.BRANCH_NAME) && scan.getStatus().equals("Completed")));
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
