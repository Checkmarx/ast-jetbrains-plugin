package com.checkmarx.intellij.standard.commands;

import com.checkmarx.ast.asca.ScanResult;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.ASCA.AscaService;
import com.checkmarx.intellij.standard.BaseTest;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

public class TestScanAsca extends BaseTest {
    AscaService ascaService = new AscaService();

    @Test
    public void testInstallAsca() {
        Assertions.assertDoesNotThrow(()->
        {
            String ascaMsg = ascaService.installAsca();
            Assertions.assertEquals(AscaService.ASCA_STARTED_MSG, ascaMsg);
        });
    }

    private PsiFile createPsiFileFromPath(String filePath) {
        // Retrieve the VirtualFile in a read action
        VirtualFile virtualFile = ApplicationManager.getApplication().runReadAction((Computable<VirtualFile>) () ->
                LocalFileSystem.getInstance().findFileByPath(filePath)
        );

        Assertions.assertNotNull(virtualFile, "The virtual file should not be null.");
        Project project = ProjectManager.getInstance().getDefaultProject();

        // Retrieve the PsiFile in a read action
        PsiFile psiFile = ApplicationManager.getApplication().runReadAction((Computable<PsiFile>) () ->
                PsiManager.getInstance(project).findFile(virtualFile)
        );

        Assertions.assertNotNull(psiFile, "The PsiFile should not be null.");
        return psiFile;
    }

    @Test
    public void testRunAscaScan_FileWithVulnerabilities_Success() {
        PsiFile psiFile = createPsiFileFromPath("src/test/java/com/checkmarx/intellij/standard/data/python-vul-file.py");
        Project project = ProjectManager.getInstance().getDefaultProject();

        Assertions.assertDoesNotThrow(() -> {
            ScanResult ascaMsg = ascaService.runAscaScan(psiFile, project, true, "Jetbrains");
            assert ascaMsg != null;
            Assertions.assertNotNull(ascaMsg.getScanDetails(), "The scan result should not be null.");
            Assertions.assertFalse(ascaMsg.getScanDetails().isEmpty(), "The scan result should have at least one detail.");
        });
    }

    @Test
    public void testRunAscaScan_FileWithNoVulnerabilities_Success() {
        PsiFile psiFile = createPsiFileFromPath("src/test/java/com/checkmarx/intellij/standard/data/csharp-no-vul.cs");
        Project project = ProjectManager.getInstance().getDefaultProject();

        Assertions.assertDoesNotThrow(() -> {
            ScanResult ascaMsg = ascaService.runAscaScan(psiFile, project, true, "Jetbrains");
            assert ascaMsg != null;
            Assertions.assertNull(ascaMsg.getScanDetails(), "The scan result should be null.");
        });
    }

    @Test
    public void testRunAscaScan_FileWithoutExtension_Fail() {
        PsiFile psiFile = createPsiFileFromPath("src/test/java/com/checkmarx/intellij/standard/data/file");
        Project project = ProjectManager.getInstance().getDefaultProject();
        ScanResult ascaResult = ascaService.runAscaScan(psiFile, project, true, "Jetbrains");

        assert ascaResult != null;
        Assertions.assertNull(ascaResult.getScanDetails());
        Assertions.assertNotNull(ascaResult.getError());
    }

}
