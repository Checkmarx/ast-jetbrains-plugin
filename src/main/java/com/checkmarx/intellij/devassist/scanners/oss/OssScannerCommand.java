package com.checkmarx.intellij.devassist.scanners.oss;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.common.ScanResult;
import com.checkmarx.intellij.devassist.basescanner.BaseScannerCommand;
import com.checkmarx.intellij.devassist.dto.CxProblems;
import com.checkmarx.intellij.devassist.inspection.RealtimeInspection;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class OssScannerCommand extends BaseScannerCommand {
    public OssScannerService ossScannerService ;
    private final Project project;
    private static final Logger LOGGER = Utils.getLogger(OssScannerCommand.class);

    public OssScannerCommand(@NotNull Disposable parentDisposable, @NotNull Project project,@NotNull OssScannerService OssscannerService){
        super(parentDisposable, OssScannerService.createConfig(),OssscannerService);
        this.ossScannerService = OssscannerService;
        this.project=project;
    }

    public OssScannerCommand(@NotNull Disposable parentDisposable,
                             @NotNull Project project) {
        this(parentDisposable, project, new OssScannerService());
    }

    @Override
    protected void initializeScanner() {
        scanAllManifestFilesInFolder();
    }

    private void scanAllManifestFilesInFolder(){
        List<String> matchedUris = new ArrayList<>();

        List<PathMatcher> pathMatchers = Constants.RealTimeConstants.MANIFEST_FILE_PATTERNS.stream()
                    .map(p -> FileSystems.getDefault().getPathMatcher("glob:" + p))
                    .collect(Collectors.toList());

            for (VirtualFile vRoot : ProjectRootManager.getInstance(project).getContentRoots()) {
                VfsUtilCore.iterateChildrenRecursively(vRoot, null, file -> {
                    if (!file.isDirectory() && !file.getPath().contains("/node_modules/")) {
                        String path = file.getPath();
                        for (PathMatcher matcher : pathMatchers) {
                            if (matcher.matches(Paths.get(path))) {
                                matchedUris.add(path);
                                break;
                            }
                        }
                    }
                    return true;
                });
            }
            for (String uri : matchedUris) {
                Optional<VirtualFile> file = Optional.ofNullable(this.findVirtualFile(uri));
                if (file.isPresent()) {
                    try {
                        PsiFile psiFile= PsiManager.getInstance(project).findFile(file.get());
                        ScanResult<?> ossRealtimeResults=  ossScannerService.scan(psiFile, uri);
                        List<CxProblems> problemsList = new ArrayList<>();
                        problemsList.addAll(RealtimeInspection.buildCxProblems(ossRealtimeResults.getPackages()));
                        ProblemHolderService.getInstance(psiFile.getProject())
                                    .addProblems(file.get().getPath(), problemsList);

                    }
                    catch(Exception e){
                        LOGGER.warn("Scan failed for manifest file: "+ uri +" Exception:"+ e);
                    }
                }
            }
         }

    @Override
    public void dispose(){
        super.dispose();
    }

}
