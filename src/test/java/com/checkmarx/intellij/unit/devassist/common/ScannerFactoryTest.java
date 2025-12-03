package com.checkmarx.intellij.unit.devassist.common;

import com.checkmarx.intellij.devassist.basescanner.ScannerService;
import com.checkmarx.intellij.devassist.common.ScannerFactory;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ScannerFactoryTest {
    @Test
    @DisplayName("getAllSupportedScanners returns empty list if not supported")
    void testGetAllSupportedScanners_returnsEmptyListIfNotSupported() {
        PsiFile file = mock(PsiFile.class);
        ScannerFactory factory = new ScannerFactory(); // Use default constructor
        List<ScannerService<?>> result = factory.getAllSupportedScanners("unsupported.js",file);
        assertTrue(result.isEmpty());
    }
}
