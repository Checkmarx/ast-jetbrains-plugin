package com.checkmarx.intellij.unit.devassist.scanners.containers;


import com.checkmarx.intellij.devassist.scanners.containers.ContainerScannerService;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class ContainerScannerServiceTest {
   private ContainerScannerService service;

    @BeforeEach
    void setUp() {
        service = new ContainerScannerService();
    }

    @Test
    void shouldScanFileReturnsTrueForDockerfilePattern() {
        String path = "/workspace/dockerfile";
        PsiFile psiFile = mockPsiFile("dockerfile", null, path, true);

        assertTrue(service.shouldScanFile(path, psiFile), "dockerfile should match CONTAINERS_FILE_PATTERNS");
    }

    @Test
    void shouldScanFileReturnsFalseInsideNodeModules() {
        String path = "/workspace/node_modules/dockerfile";
        PsiFile psiFile = mockPsiFile("dockerfile", null, path, true);

        assertFalse(service.shouldScanFile(path, psiFile), "Files under node_modules must be ignored");
    }

    @Test
    void shouldScanFileReturnsTrueForHelmYamlInHelmFolder() {
        String path = "/workspace/charts/helm/templates/values.yaml";
        PsiFile psiFile = mockPsiFile("values.yaml", "yaml", path, true);

        assertTrue(service.shouldScanFile(path, psiFile), "Helm yaml files under /helm/ should be scanned");
    }

    @Test
    void shouldScanFileReturnsFalseForExcludedHelmFiles() {
        String path = "/workspace/charts/helm/chart.yaml";
        PsiFile psiFile = mockPsiFile("chart.yaml", "yaml", path, true);

        assertFalse(service.shouldScanFile(path, psiFile), "chart.yaml should be excluded from helm scanning");
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
