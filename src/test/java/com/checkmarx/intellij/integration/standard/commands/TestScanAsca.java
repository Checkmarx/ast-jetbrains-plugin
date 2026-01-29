package com.checkmarx.intellij.integration.standard.commands;

import com.checkmarx.ast.asca.ScanResult;
import com.checkmarx.intellij.devassist.scanners.asca.AscaScannerService;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.integration.standard.BaseTest;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestScanAsca extends BaseTest {
    AscaScannerService ascaScannerService = new AscaScannerService();

    @Test
    public void testInstallAsca() {
        Assertions.assertDoesNotThrow(()->
        {
            boolean installed = ascaScannerService.installAsca();
            Assertions.assertTrue(installed);
        });
    }

    private PsiFile createPsiFileFromPath(String filePath) {
        // Retrieve the VirtualFile in a read action
        VirtualFile virtualFile = ApplicationManager.getApplication().runReadAction((Computable<VirtualFile>) () ->
                LocalFileSystem.getInstance().findFileByPath(filePath)
        );

        Assertions.assertNotNull(virtualFile, "The virtual file should not be null.");

        // Use the test fixture's project instead of default project
        // This ensures the project has a proper base path set up
        Project project = getProject();

        // Retrieve the PsiFile in a read action
        PsiFile psiFile = ApplicationManager.getApplication().runReadAction((Computable<PsiFile>) () ->
                PsiManager.getInstance(project).findFile(virtualFile)
        );

        Assertions.assertNotNull(psiFile, "The PsiFile should not be null.");
        return psiFile;
    }

    @Test
    public void testRunAscaScan_FileWithVulnerabilities_Success() {
        PsiFile psiFile = createPsiFileFromPath("src/test/java/com/checkmarx/intellij/integration/standard/data/python-vul-file.py");

        Assertions.assertDoesNotThrow(() -> {
            com.checkmarx.intellij.devassist.common.ScanResult<ScanResult> scanResult =
                ascaScannerService.scan(psiFile, psiFile.getVirtualFile().getPath());

            Assertions.assertNotNull(scanResult, "The scan result should not be null.");
            Assertions.assertNotNull(scanResult.getResults().getScanDetails(), "The scan details should not be null.");
            Assertions.assertFalse(scanResult.getResults().getScanDetails().isEmpty(), "The scan result should have at least one detail.");
        });
    }

    @Test
    public void testRunAscaScan_FileWithNoVulnerabilities_Success() {
        PsiFile psiFile = createPsiFileFromPath("src/test/java/com/checkmarx/intellij/integration/standard/data/csharp-no-vul.cs");

        Assertions.assertDoesNotThrow(() -> {
            com.checkmarx.intellij.devassist.common.ScanResult<ScanResult> scanResult =
                ascaScannerService.scan(psiFile, psiFile.getVirtualFile().getPath());

            if (scanResult != null && scanResult.getResults() != null) {
                // If scan completed, check that no vulnerabilities were found
                Assertions.assertTrue(scanResult.getResults().getScanDetails() == null ||
                    scanResult.getResults().getScanDetails().isEmpty(), "No vulnerabilities should be found.");
            }
            // Note: scanResult could be null if file is filtered out, which is also acceptable
        });
    }

    @Test
    public void testRunAscaScan_FileWithoutExtension_Fail() {
        PsiFile psiFile = createPsiFileFromPath("src/test/java/com/checkmarx/intellij/integration/standard/data/file");

        // File without extension should either be filtered out (null result) or produce empty results
        com.checkmarx.intellij.devassist.common.ScanResult<ScanResult> scanResult =
            ascaScannerService.scan(psiFile, psiFile.getVirtualFile().getPath());

        if (scanResult != null) {
            // If scan was attempted, check for error or empty results
            ScanResult ascaResult = scanResult.getResults();
            Assertions.assertTrue(ascaResult == null ||
                ascaResult.getScanDetails() == null ||
                ascaResult.getScanDetails().isEmpty() ||
                ascaResult.getError() != null,
                "File without extension should produce no results or an error.");
        }
        // Note: scanResult being null is also acceptable (file filtered out)
    }

}
