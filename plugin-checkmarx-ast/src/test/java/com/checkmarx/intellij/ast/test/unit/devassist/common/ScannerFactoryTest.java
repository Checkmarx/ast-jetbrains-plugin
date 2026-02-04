package com.checkmarx.intellij.ast.test.unit.devassist.common;

import com.checkmarx.intellij.devassist.basescanner.ScannerService;
import com.checkmarx.intellij.devassist.common.ScannerFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScannerFactoryTest {
    @Test
    @DisplayName("getAllSupportedScanners returns empty list if not supported")
    void testGetAllSupportedScanners_returnsTrueListIfSupported() {
        String path = "src/test/resources/build.gradle";
        PsiFile psiFile = mockPsiFile("build.gradle", null, path, true);
        ScannerFactory factory = new ScannerFactory(); // Use default constructor
        List<ScannerService<?>> result = factory.getAllSupportedScanners("src/test/resources/build.gradle",psiFile);
        assertFalse(result.isEmpty());
    }


    private PsiFile mockPsiFile(String name, String extension, String path, boolean exists) {
        PsiFile psiFile = mock(PsiFile.class);
        VirtualFile virtualFile = mock(VirtualFile.class);
        when(psiFile.getName()).thenReturn(name);
        when(psiFile.getVirtualFile()).thenReturn(virtualFile);
        when(virtualFile.getExtension()).thenReturn(extension);
        when(virtualFile.getPath()).thenReturn(path);
        when(virtualFile.exists()).thenReturn(exists);
        when(virtualFile.getName()).thenReturn(name);
        return psiFile;
    }
}
