package com.checkmarx.intellij.realtimeScanners.scanners.oss;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.realtimeScanners.basescanner.BaseScannerCommandImpl;
import com.checkmarx.intellij.realtimeScanners.configuration.ConfigurationManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;

import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.editor.Document;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class OssScannerCommand extends BaseScannerCommandImpl {
   public OssScannerService ossScannerService ;

   private final Project project;

   private static final Logger LOGGER = Utils.getLogger(OssScannerCommand.class);

    public OssScannerCommand(@NotNull Disposable parentDisposable, @NotNull Project project,@NotNull OssScannerService OssscannerService, @NotNull ConfigurationManager configurationManager){
        super(parentDisposable, OssScannerService.createConfig(),OssscannerService,configurationManager);
        this.ossScannerService = OssscannerService;
        this.project=project;
    }

    public OssScannerCommand(@NotNull Disposable parentDisposable,
                             @NotNull Project project) {
        this(parentDisposable, project, new OssScannerService(project),new ConfigurationManager());
    }

    @Override
    protected void initializeScanner() {
        super.initializeScanner();
        scanAllManifestFilesInFolder();
    }

    /**
     * Scans all manifest files when project is opened in IDE
     *
     */

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
                        Document doc = this.getDocument(file.get());
                        ossScannerService.scan(doc, uri);
                    }
                    catch(Exception e){
                        LOGGER.error("Scan has failed for manifest file: "+ uri);
                    }
                }
            }
    }

}
