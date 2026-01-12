package com.checkmarx.intellij.unit.devassist.scanners.asca;

import com.checkmarx.ast.asca.ScanResult;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.intellij.devassist.scanners.asca.AscaScannerService;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AscaScannerServiceTest {

    private AscaScannerService service;
    private PsiFile psiFile;
    private VirtualFile virtualFile;

    @BeforeEach
    void setUp() {
        service = new AscaScannerService();
        psiFile = mock(PsiFile.class);
        virtualFile = mock(VirtualFile.class);
        when(psiFile.getVirtualFile()).thenReturn(virtualFile);
        when(virtualFile.getExtension()).thenReturn("java");
    }

    @Test
    @DisplayName("createConfig builds ASCA engine configuration")
    void testCreateConfig() {
        var config = AscaScannerService.createConfig();
        assertEquals("ASCA", config.getEngineName());
        assertNotNull(config.getConfigSection());
        assertNotNull(config.getActivateKey());
    }

    @Test
    @DisplayName("shouldScanFile rejects paths under node_modules")
    void shouldScanFileRejectsNodeModules() {
        assertFalse(service.shouldScanFile("/project/node_modules/Main.java", psiFile));
    }

    @Test
    @DisplayName("shouldScanFile rejects unsupported extensions")
    void shouldScanFileRejectsUnsupportedExtension() {
        when(virtualFile.getExtension()).thenReturn("txt");
        assertFalse(service.shouldScanFile("/project/Main.txt", psiFile));
    }

    @Test
    @DisplayName("shouldScanFile accepts supported extensions")
    void shouldScanFileAcceptsSupportedExtension() {
        assertTrue(service.shouldScanFile("/project/Main.java", psiFile));
    }

    @Test
    @DisplayName("shouldScanFile returns false when virtual file is missing")
    void shouldScanFileRejectsWhenVirtualFileNull() {
        PsiFile psiWithoutVirtual = mock(PsiFile.class);
        when(psiWithoutVirtual.getVirtualFile()).thenReturn(null);
        assertFalse(service.shouldScanFile("/project/Main.java", psiWithoutVirtual));
    }

    @Test
    @DisplayName("scan returns null when file is not eligible")
    void scanReturnsNullWhenShouldScanFileFalse() {
        AscaScannerService spyService = spy(new AscaScannerService());
        doReturn(false).when(spyService).shouldScanFile(anyString(), eq(psiFile));
        assertNull(spyService.scan(psiFile, "/project/Main.java"));
    }

    @Test
    @DisplayName("installAsca returns true when wrapper reports success")
    void installAscaReturnsTrueOnSuccess() throws Exception {
        try (MockedStatic<com.checkmarx.intellij.settings.global.CxWrapperFactory> factory =
                     mockStatic(com.checkmarx.intellij.settings.global.CxWrapperFactory.class)) {
            CxWrapper wrapper = mock(CxWrapper.class);
            ScanResult scanResult = mock(ScanResult.class);
            when(scanResult.getError()).thenReturn(null);
            when(wrapper.ScanAsca(anyString(), eq(true), anyString())).thenReturn(scanResult);

            factory.when(com.checkmarx.intellij.settings.global.CxWrapperFactory::build).thenReturn(wrapper);

            assertTrue(service.installAsca());
        }
    }

    @Test
    @DisplayName("installAsca returns false when wrapper throws exception")
    void installAscaReturnsFalseOnException() throws Exception {
        try (MockedStatic<com.checkmarx.intellij.settings.global.CxWrapperFactory> factory =
                     mockStatic(com.checkmarx.intellij.settings.global.CxWrapperFactory.class)) {
            factory.when(com.checkmarx.intellij.settings.global.CxWrapperFactory::build)
                    .thenThrow(new RuntimeException("boom"));
            assertFalse(service.installAsca());
        }
    }

    @Test
    @DisplayName("sanitizeFileName strips dangerous characters")
    void sanitizeFileNameRemovesDangerousCharacters() throws Exception {
        Method sanitize = AscaScannerService.class.getDeclaredMethod("sanitizeFileName", String.class);
        sanitize.setAccessible(true);
        String sanitized = (String) sanitize.invoke(service, "../..\\evil:name?.java");
        assertFalse(sanitized.contains(".."));
        assertFalse(sanitized.contains("/"));
        assertFalse(sanitized.contains("\\"));
        assertFalse(sanitized.contains(":"));
        assertTrue(sanitized.endsWith(".java"));
    }
}


