package com.checkmarx.intellij.ASCA;

import com.checkmarx.ast.asca.ScanDetail;
import com.checkmarx.ast.asca.ScanResult;
import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.commands.ASCA;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AscaService {

    private static final String ASCA_DIR = "CxASCA";
    private static final Logger LOGGER = Logger.getInstance(AscaService.class);

    public AscaService() {
        // Default constructor
    }

    public void scanAsca(VirtualFile file, Project project, boolean ascLatestVersion, String agent) {
        if (ignoreFiles(file)) {
            return;
        }

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                // Save the file temporarily
                String filePath = saveTempFile(file.getName(), new String(file.contentsToByteArray()));

                // Run the ASCA scan
                LOGGER.info("Start ASCA scan on file: " + file.getPath());
                ScanResult scanAscaResult = ASCA.scanAsca(filePath, ascLatestVersion, agent);

                // Delete the temporary file
                deleteFile(filePath);
                LOGGER.info("File " + filePath + " deleted.");

                // Handle errors if any
                if (scanAscaResult.getError() != null) {
                    LOGGER.warn("ASCA Warning: " + (Objects.nonNull(scanAscaResult.getError().getDescription()) ?
                            scanAscaResult.getError().getDescription() : scanAscaResult.getError()));
                    return;
                }

                // Log the results
                LOGGER.info(scanAscaResult.getScanDetails().size() + " security best practice violations were found in " + file.getPath());

                // Update problems on the UI thread
                ApplicationManager.getApplication().invokeLater(() -> updateProblems(scanAscaResult, file, project));

            } catch (IOException | CxConfig.InvalidCLIConfigException | URISyntaxException | CxException | InterruptedException e) {
                LOGGER.error("Error during ASCA scan.", e);
            }
        });
    }

    private boolean ignoreFiles(VirtualFile file) {
        // Ignore non-local files
        return !file.isInLocalFileSystem();
    }

    private void updateProblems(ScanResult scanAscaResult, VirtualFile file, Project project) {
        ApplicationManager.getApplication().invokeLater(() -> {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            if (psiFile == null) {
                return;
            }

            InspectionManager inspectionManager = InspectionManager.getInstance(project);
            List<ProblemDescriptor> problemDescriptors = getProblemDescriptors(scanAscaResult, inspectionManager, psiFile);

            if (!problemDescriptors.isEmpty()) {
                // Register or apply the problem descriptors with the inspection tool or problem view
                LOGGER.info(problemDescriptors.size() + " problems added to the file.");
                PsiDocumentManager.getInstance(project).commitAllDocuments();
            }
        });
    }

    private static @NotNull List<ProblemDescriptor> getProblemDescriptors(ScanResult scanAscaResult, InspectionManager inspectionManager, PsiFile psiFile) {
        List<ProblemDescriptor> problemDescriptors = new ArrayList<>();

        for (ScanDetail res : scanAscaResult.getScanDetails()) {
            String description = res.getRuleName() + " - " + res.getRemediationAdvise();
            ProblemDescriptor problemDescriptor = inspectionManager.createProblemDescriptor(
                    psiFile, description, true, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true);
            problemDescriptors.add(problemDescriptor);
        }
        return problemDescriptors;
    }


    private String saveTempFile(String fileName, String content) {
        try {
            Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), ASCA_DIR);
            Files.createDirectories(tempDir);
            Path tempFilePath = tempDir.resolve(fileName);
            Files.write(tempFilePath, content.getBytes());
            LOGGER.info("Temp file was saved in: " + tempFilePath);
            return tempFilePath.toString();
        } catch (IOException e) {
            LOGGER.error("Failed to save temporary file:", e);
            return null;
        }
    }

    public void installAsca() {
        try {
            ScanResult res = ASCA.installAsca();
            if (res.getError()!= null) {
                String errorMessage = "ASCA Installation Error: " + res.getError().getDescription();
                LOGGER.error(errorMessage);
            }
        } catch (Exception e) {
            LOGGER.error(e);
            LOGGER.warn("Error during ASCA installation.");
        }
    }

    private void deleteFile(String filePath) {
        try {
            File file = new File(filePath);
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to delete file", e);
        }
    }
}
