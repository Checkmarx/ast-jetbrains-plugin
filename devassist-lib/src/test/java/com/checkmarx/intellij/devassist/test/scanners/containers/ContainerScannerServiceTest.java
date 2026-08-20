package com.checkmarx.intellij.devassist.test.scanners.containers;


import com.checkmarx.intellij.devassist.common.ScanResult;
import com.checkmarx.intellij.devassist.configuration.ScannerConfig;
import com.checkmarx.intellij.devassist.scanners.containers.ContainerScannerService;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


public class ContainerScannerServiceTest {
   private ContainerScannerService service;

    @TempDir
    Path tempDir;

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

    @Test
    void shouldScanFileReturnsTrueForUppercaseDockerfile() {
        String path = "/workspace/DOCKERFILE";
        PsiFile psiFile = mockPsiFile("DOCKERFILE", null, path, true);
        assertTrue(service.shouldScanFile(path, psiFile), "Uppercase DOCKERFILE should be scanned");
    }

    @Test
    void shouldScanFileReturnsFalseForUnrelatedExtension() {
        String path = "/workspace/file.txt";
        PsiFile psiFile = mockPsiFile("file.txt", "txt", path, true);
        assertFalse(service.shouldScanFile(path, psiFile), "Unrelated extension should not be scanned");
    }

    @Test
    void shouldScanFileReturnsTrueForValidComposeFile() {
        String path = "/workspace/docker-compose.yaml";
        PsiFile psiFile = mockPsiFile("docker-compose.yaml", "yaml", path, true);
        assertTrue(service.shouldScanFile(path, psiFile), "docker-compose.yaml should be scanned");
    }

    @Test
    void shouldScanFileReturnsFalseForComposeOverrideFile() {
        String path = "/workspace/docker-compose.override.yaml";
        PsiFile psiFile = mockPsiFile("docker-compose.override.yaml", "yaml", path, true);
        assertFalse(service.shouldScanFile(path, psiFile), "docker-compose.override.yaml should not be scanned");
    }

    @Test
    void shouldScanFileReturnsTrueForDockerfileWithExtension() {
        String path = "/workspace/Dockerfile.dev";
        PsiFile psiFile = mockPsiFile("Dockerfile.dev", "dev", path, true);
        assertTrue(service.shouldScanFile(path, psiFile), "Dockerfile with extension should be scanned");
    }

    @Test
    void shouldScanFileReturnsTrueForComposeFileInSubdirectory() {
        String path = "/workspace/sub/docker-compose.yaml";
        PsiFile psiFile = mockPsiFile("docker-compose.yaml", "yaml", path, true);
        assertTrue(service.shouldScanFile(path, psiFile), "docker-compose.yaml in subdirectory should be scanned");
    }


    @Test
    void shouldScanFileReturnsTrueForComposeFileWithUppercaseExtension() {
        String path = "/workspace/docker-compose.YAML";
        PsiFile psiFile = mockPsiFile("docker-compose.YAML", "YAML", path, true);
        assertTrue(service.shouldScanFile(path, psiFile), "docker-compose.YAML should be scanned");
    }

    @Test
    void shouldScanFileReturnsFalseForEmptyName() {
        String path = "/workspace/";
        PsiFile psiFile = mockPsiFile("", null, path, true);
        assertFalse(service.shouldScanFile(path, psiFile), "Empty file name should not be scanned");
    }

    @Test
    @DisplayName("createConfig_buildsContainersEngineConfig")
    void createConfig_buildsContainersEngineConfig() {
        ScannerConfig config = ContainerScannerService.createConfig();
        assertEquals(ScanEngine.CONTAINERS.name(), config.getEngineName());
        assertNotNull(config.getConfigSection());
        assertNotNull(config.getActivateKey());
    }

    @Test
    @DisplayName("shouldScanFile_nullVirtualFile_dockerfilePath_returnsTrue")
    void shouldScanFile_nullVirtualFile_dockerfilePath_returnsTrue() {
        PsiFile psiFile = mock(PsiFile.class);
        when(psiFile.getVirtualFile()).thenReturn(null);
        // Container pattern matches before isHelmFile is reached
        assertTrue(service.shouldScanFile("/workspace/dockerfile", psiFile));
    }

    @Test
    @DisplayName("scan_whenShouldScanFileIsFalse_returnsNull")
    void scan_whenShouldScanFileIsFalse_returnsNull() {
        PsiFile psiFile = mockPsiFile("Main.java", "java", "/project/Main.java", true);
        ContainerScannerService spyService = spy(new ContainerScannerService());
        doReturn(false).when(spyService).shouldScanFile(anyString(), eq(psiFile));
        ScanResult<?> result = spyService.scan(psiFile, "/project/Main.java");
        assertNull(result);
    }

    // ===== createConfig completeness =====

    @Test
    @DisplayName("createConfig sets enabledMessage and disabledMessage")
    void createConfig_enabledAndDisabledMessages_areSet() {
        ScannerConfig config = ContainerScannerService.createConfig();
        assertNotNull(config.getEnabledMessage());
        assertNotNull(config.getDisabledMessage());
    }

    // ===== getConfig =====

    @Test
    @DisplayName("getConfig returns a config with CONTAINERS engine name")
    void getConfig_returnsContainersConfig() {
        ScannerConfig config = service.getConfig();
        assertEquals(ScanEngine.CONTAINERS.name(), config.getEngineName());
    }

    // ===== isHelmFile =====

    @Test
    @DisplayName("isHelmFile returns false for non-yaml file")
    void isHelmFile_nonYamlFile_returnsFalse() {
        PsiFile psiFile = mockPsiFile("Main.java", "java", "/workspace/helm/Main.java", true);
        assertFalse(service.isHelmFile(psiFile, "/workspace/helm/Main.java"));
    }

    @Test
    @DisplayName("isHelmFile returns false for yaml file not under /helm/")
    void isHelmFile_yamlNotInHelmFolder_returnsFalse() {
        PsiFile psiFile = mockPsiFile("values.yaml", "yaml", "/workspace/config/values.yaml", true);
        assertFalse(service.isHelmFile(psiFile, "/workspace/config/values.yaml"));
    }

    @Test
    @DisplayName("isHelmFile returns false for chart.yaml in helm folder (excluded)")
    void isHelmFile_chartYamlInHelmFolder_returnsFalse() {
        PsiFile psiFile = mockPsiFile("chart.yaml", "yaml", "/workspace/helm/chart.yaml", true);
        assertFalse(service.isHelmFile(psiFile, "/workspace/helm/chart.yaml"));
    }

    @Test
    @DisplayName("isHelmFile returns true for yml file under /helm/")
    void isHelmFile_ymlFileInHelmFolder_returnsTrue() {
        PsiFile psiFile = mockPsiFile("deployment.yml", "yml", "/workspace/helm/templates/deployment.yml", true);
        assertTrue(service.isHelmFile(psiFile, "/workspace/helm/templates/deployment.yml"));
    }

    // ===== shouldScanFile — docker-compose.yml variant =====

    @Test
    @DisplayName("shouldScanFile returns true for docker-compose.yml")
    void shouldScanFileReturnsTrueForDockerComposeYml() {
        String path = "/workspace/docker-compose.yml";
        PsiFile psiFile = mockPsiFile("docker-compose.yml", "yml", path, true);
        assertTrue(service.shouldScanFile(path, psiFile));
    }

    @Test
    @DisplayName("shouldScanFile returns true for docker-compose-prod.yaml")
    void shouldScanFileReturnsTrueForDockerComposeProdYaml() {
        String path = "/workspace/docker-compose-prod.yaml";
        PsiFile psiFile = mockPsiFile("docker-compose-prod.yaml", "yaml", path, true);
        assertTrue(service.shouldScanFile(path, psiFile));
    }

    // ===== scan — null fileContent path returns null =====

    @Test
    @DisplayName("scan returns null when file content is null (no issues to create)")
    void scan_nullFileContent_returnsNull() {
        PsiFile psiFile = mockPsiFile("dockerfile", null, "/project/dockerfile", true);
        ContainerScannerService spyService = spy(new ContainerScannerService());
        doReturn(true).when(spyService).shouldScanFile(anyString(), eq(psiFile));

        try (MockedStatic<ApplicationManager> appMgrMock = mockStatic(ApplicationManager.class, CALLS_REAL_METHODS)) {
            Application mockApp = mock(Application.class, RETURNS_DEEP_STUBS);
            appMgrMock.when(ApplicationManager::getApplication).thenReturn(mockApp);
            when(mockApp.runReadAction(any(Computable.class))).thenReturn(null);

            assertNull(spyService.scan(psiFile, "/project/dockerfile"));
        }
    }

    // ===== createSubFolderAndSaveFile — null content returns null =====

    @Test
    @DisplayName("createSubFolderAndSaveFile: null content returns null")
    void createSubFolderAndSaveFile_nullContent_returnsNull() throws Exception {
        PsiFile psiFile = mockPsiFile("Dockerfile", null, "/project/Dockerfile", true);

        try (MockedStatic<DevAssistUtils> devUtilsMock = mockStatic(DevAssistUtils.class)) {
            devUtilsMock.when(() -> DevAssistUtils.getFileContent(psiFile)).thenReturn(null);

            Method method = ContainerScannerService.class.getDeclaredMethod(
                    "createSubFolderAndSaveFile", Path.class, String.class, PsiFile.class);
            method.setAccessible(true);

            Object result = method.invoke(service, tempDir, "Dockerfile", psiFile);
            assertNull(result);
        }
    }

    @Test
    @DisplayName("createSubFolderAndSaveFile: blank content returns null")
    void createSubFolderAndSaveFile_blankContent_returnsNull() throws Exception {
        PsiFile psiFile = mockPsiFile("Dockerfile", null, "/project/Dockerfile", true);

        try (MockedStatic<DevAssistUtils> devUtilsMock = mockStatic(DevAssistUtils.class)) {
            devUtilsMock.when(() -> DevAssistUtils.getFileContent(psiFile)).thenReturn("   ");

            Method method = ContainerScannerService.class.getDeclaredMethod(
                    "createSubFolderAndSaveFile", Path.class, String.class, PsiFile.class);
            method.setAccessible(true);

            Object result = method.invoke(service, tempDir, "Dockerfile", psiFile);
            assertNull(result);
        }
    }

    @Test
    @DisplayName("createSubFolderAndSaveFile: valid content writes file and returns pair")
    void createSubFolderAndSaveFile_validContent_writesFileAndReturnsPair() throws Exception {
        PsiFile psiFile = mockPsiFile("Dockerfile", null, "/project/Dockerfile", true);
        Path subFolder = tempDir.resolve("subfolder");

        try (MockedStatic<DevAssistUtils> devUtilsMock = mockStatic(DevAssistUtils.class)) {
            devUtilsMock.when(() -> DevAssistUtils.getFileContent(psiFile)).thenReturn("FROM ubuntu:20.04");

            Method method = ContainerScannerService.class.getDeclaredMethod(
                    "createSubFolderAndSaveFile", Path.class, String.class, PsiFile.class);
            method.setAccessible(true);

            Object result = method.invoke(service, subFolder, "Dockerfile", psiFile);
            assertNotNull(result);
        }
    }

    // ===== generateFileHash =====

    @Test
    @DisplayName("generateFileHash returns non-null hex string of length 16")
    void generateFileHash_returnsHex16Chars() throws Exception {
        Method method = ContainerScannerService.class.getDeclaredMethod("generateFileHash", String.class);
        method.setAccessible(true);
        String hash = (String) method.invoke(service, "src/Dockerfile");
        assertNotNull(hash);
        assertEquals(16, hash.length());
        assertTrue(hash.matches("[0-9a-f]+"), "Hash should be hexadecimal");
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
