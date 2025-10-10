package com.checkmarx.intellij.realtimeScanners.scanners.oss;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.realtimeScanners.basescanner.BaseScannerCommandImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class OssScannerCommand extends BaseScannerCommandImpl {
   public OssScannerService ossScannerService ;
   private Project project;

   private static final Logger LOGGER = Utils.getLogger(OssScannerCommand.class);

    public OssScannerCommand(@NotNull Disposable parentDisposable, @NotNull Project project){
        super(parentDisposable, OssScannerService.createConfig());
        this.ossScannerService = new OssScannerService();
        this.project=project;
    }

    @Override
    protected void initializeScanner() {
        super.initializeScanner();
        scanAllManifestFilesInFolder();
    }

    private void scanAllManifestFilesInFolder(){
        LOGGER.info("Calling scanAllManifestFolder");
        List<String>matchedUris= new ArrayList<>();

        List<PathMatcher>pathMatchers= Constants.RealTimeConstants.MANIFEST_FILE_PATTERNS.stream()
                .map(p-> FileSystems.getDefault().getPathMatcher("glob:"+p))
                .collect(Collectors.toList());

        for (VirtualFile vroot: ProjectRootManager.getInstance(project).getContentRoots()){
            VfsUtilCore.iterateChildrenRecursively(vroot,null,file->{
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
        LOGGER.info("showing all matched URIs");
        matchedUris.forEach(System.out::println);
    }

}
