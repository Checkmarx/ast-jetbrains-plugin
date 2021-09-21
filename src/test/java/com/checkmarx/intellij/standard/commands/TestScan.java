package com.checkmarx.intellij.standard.commands;

import com.checkmarx.intellij.commands.Scan;
import com.checkmarx.intellij.standard.BaseTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

public class TestScan extends BaseTest {

    @Test
    public void testGetLatest() {
        String scanId = Assertions.assertDoesNotThrow(Scan::getLatestScanId);
        Assertions.assertDoesNotThrow(() -> UUID.fromString(scanId));
    }
}
