package com.checkmarx.intellij.devassist.scanners.oss;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.common.ScanResult;
import com.checkmarx.intellij.devassist.basescanner.BaseScannerCommand;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AppUIExecutor;
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
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;

import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class OssScannerCommand extends BaseScannerCommand {
    public OssScannerService ossScannerService;
    private final Project project;
    private static final Logger LOGGER = Utils.getLogger(OssScannerCommand.class);

    private OssScannerCommand(@NotNull Disposable parentDisposable, @NotNull Project project, @NotNull OssScannerService OssscannerService) {
        super(parentDisposable, OssScannerService.createConfig());
        this.ossScannerService = OssscannerService;
        this.project = project;
    }

    public OssScannerCommand(@NotNull Disposable parentDisposable,
                             @NotNull Project project) {
        this(parentDisposable, project, new OssScannerService());
    }

    /**
     * Initializes the scanner , invoked after registration of the scanner
     */

    @Override
    protected void initializeScanner() {
        if(!DevAssistUtils.isInternetConnectivity()){
            return;
        }
        new Task.Backgroundable(project, Bundle.message(Resource.STARTING_CHECKMARX_OSS_SCAN), false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator){
                indicator.setIndeterminate(true);
                indicator.setText(Bundle.message(Resource.STARTING_CHECKMARX_OSS_SCAN));
                scanAllManifestFilesInFolder();
            }
        }.queue();
    }

    /**
     * Scans all manifest Files in the opened project
     * Happens on project opened or when  scanner is enabled
     * Iterates the recursively each file in the root expect node modules , if it matches the manifest files pattern
     * appends the matchFiles path in List and triggers separate scan on each of them
     */

    private void scanAllManifestFilesInFolder() {
        List<String> matchedURIs = new ArrayList<>();

        List<PathMatcher> pathMatchers = Constants.RealTimeConstants.MANIFEST_FILE_PATTERNS.stream()
                .map(p -> FileSystems.getDefault().getPathMatcher("glob:" + p))
                .collect(Collectors.toList());

        for (VirtualFile vRoot : ProjectRootManager.getInstance(project).getContentRoots()) {
            if(Objects.nonNull(vRoot)){
                VfsUtilCore.iterateChildrenRecursively(vRoot, null, file -> {
                    if (!file.isDirectory() && !file.getPath().contains("/node_modules/") && file.exists()) {
                        String path = file.getPath();
                        for (PathMatcher matcher : pathMatchers) {
                            if (matcher.matches(Paths.get(path))) {
                                matchedURIs.add(path);
                                break;
                            }
                        }
                    }
                    return true;
                });
            }
        }
        for (String uri : matchedURIs) {
            Optional<VirtualFile> file = Optional.ofNullable(this.findVirtualFile(uri));
            if (file.isPresent()) {
                try {
                    PsiFile psiFile = ReadAction.compute(()->
                            PsiManager.getInstance(project).findFile(file.get()));
                    if (Objects.isNull(psiFile)) {
                        return;
                    }
                    ScanResult<?> ossRealtimeResults = ossScannerService.scan(psiFile, uri);
                    List<ScanIssue> problemsList = new ArrayList<>(ossRealtimeResults.getIssues());
                    ProblemHolderService.addToCxOneFindings(psiFile, problemsList);
                } catch (Exception e) {
                    LOGGER.warn("Scan failed for manifest file: " + uri + " with exception:" + e);
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
