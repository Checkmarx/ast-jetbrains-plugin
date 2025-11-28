package com.checkmarx.intellij.devassist.scanners.secrets;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.common.ScanResult;
import com.checkmarx.intellij.devassist.basescanner.BaseScannerCommand;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class SecretsScannerCommand extends BaseScannerCommand {
    public SecretsScannerService secretsScannerService;
    private final Project project;
    private static final Logger LOGGER = Utils.getLogger(SecretsScannerCommand.class);

    private SecretsScannerCommand(@NotNull Disposable parentDisposable, @NotNull Project project, @NotNull SecretsScannerService secretsScannerService) {
        super(parentDisposable, SecretsScannerService.createConfig());
        this.secretsScannerService = secretsScannerService;
        this.project = project;
    }

    public SecretsScannerCommand(@NotNull Disposable parentDisposable,
                                @NotNull Project project) {
        this(parentDisposable, project, new SecretsScannerService());
    }

    /**
     * Initializes the scanner, invoked after registration of the scanner
     * Secrets scanner relies on RealtimeInspection for file-by-file scanning
     * No bulk project scanning is performed to avoid excessive CLI calls
     */
    @Override
    protected void initializeScanner() {
        LOGGER.info("Secrets scanner initialized - ready for real-time scanning");
    }

    /**
     * Scans all files in the opened project for secrets
     * Happens on project opened or when scanner is enabled
     * Iterates recursively through each file in the project root except excluded directories,
     * and triggers separate scan on each eligible file
     */
    private void scanAllFilesInProject() {
        List<String> matchedURIs = new ArrayList<>();

        for (VirtualFile vRoot : ProjectRootManager.getInstance(project).getContentRoots()) {
            if (Objects.nonNull(vRoot)) {
                VfsUtilCore.iterateChildrenRecursively(vRoot, null, file -> {
                    if (!file.isDirectory() &&
                        !file.getPath().contains("/node_modules/") &&
                        !file.getPath().contains("/.git/") &&
                        !file.getPath().contains("/build/") &&
                        !file.getPath().contains("/target/") &&
                        !file.getPath().contains("/.idea/") &&
                        !file.getPath().contains("/bin/") &&
                        !file.getPath().contains("/obj/") &&
                        file.exists()) {

                        String path = file.getPath();
                        if (secretsScannerService.shouldScanFile(path)) {
                            matchedURIs.add(path);
                        }
                    }
                    return true;
                });
            }
        }

        LOGGER.info("Found " + matchedURIs.size() + " files eligible for secrets scanning");

        for (String uri : matchedURIs) {
            Optional<VirtualFile> file = Optional.ofNullable(this.findVirtualFile(uri));
            if (file.isPresent()) {
                try {
                    PsiFile psiFile = ReadAction.compute(() ->
                            PsiManager.getInstance(project).findFile(file.get()));
                    if (Objects.isNull(psiFile)) {
                        continue;
                    }

                    ScanResult<?> secretsRealtimeResults = secretsScannerService.scan(psiFile, uri);
                    if (Objects.isNull(secretsRealtimeResults)) {
                        LOGGER.debug("Scan returned no results for file: " + uri);
                        continue;
                    }

                    ProblemHolderService.addToCxOneFindings(psiFile, secretsRealtimeResults.getIssues());
                } catch (Exception e) {
                    LOGGER.warn("Scan failed for file: " + uri + " with exception:" + e);
                }
            }
        }
    }

    /**
     * Disposes the listeners automatically
     * Triggered when project is closed
     */
    @Override
    public void dispose() {
        super.dispose();
    }
}
