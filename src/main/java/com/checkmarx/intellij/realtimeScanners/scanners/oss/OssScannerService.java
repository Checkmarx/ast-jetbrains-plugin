package com.checkmarx.intellij.realtimeScanners.scanners.oss;

import com.checkmarx.ast.ossrealtime.OssRealtimeLocation;
import com.checkmarx.ast.ossrealtime.OssRealtimeScanPackage;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.realtimeScanners.basescanner.BaseScannerService;
import com.checkmarx.intellij.realtimeScanners.configuration.ScannerConfig;
import com.checkmarx.intellij.settings.global.CxWrapperFactory;
import com.checkmarx.intellij.realtimeScanners.dto.CxProblems;
import com.checkmarx.intellij.service.ProblemHolderService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.ArrayList;


public class OssScannerService extends BaseScannerService {
    private static final Logger LOGGER = Utils.getLogger(OssScannerService.class);
    private Project project;

    public OssScannerService(Project project){
      super(createConfig());
      this.project=project;
    }

    public static ScannerConfig createConfig() {
        return ScannerConfig.builder()
                .engineName(Constants.RealTimeConstants.OSS_REALTIME_SCANNER_ENGINE_NAME)
                .configSection(Constants.RealTimeConstants.OSS_REALTIME_SCANNER)
                .activateKey(Constants.RealTimeConstants.ACTIVATE_OSS_REALTIME_SCANNER)
                .errorMessage(Constants.RealTimeConstants.ERROR_OSS_REALTIME_SCANNER)
                .disabledMessage(Constants.RealTimeConstants.OSS_REALTIME_SCANNER_DISABLED)
                .enabledMessage(Constants.RealTimeConstants.OSS_REALTIME_SCANNER_START)
                .build();
    }

    private boolean isManifestFilePatternMatching(String filePath){
        List<PathMatcher> pathMatchers = Constants.RealTimeConstants.MANIFEST_FILE_PATTERNS.stream()
                .map(p -> FileSystems.getDefault().getPathMatcher("glob:" + p))
                .collect(Collectors.toList());
        for(PathMatcher pathMatcher:pathMatchers){
            if(pathMatcher.matches(Paths.get(filePath))){
                return true;
            }
        }
        return false;
    }

    public boolean shouldScanFile(String filePath){
       if(!super.shouldScanFile(filePath)){
           return  false;
       }
       return this.isManifestFilePatternMatching(filePath);
    }

    public String getRelativePath(Document document){
        if (this.project == null || document == null) {
            return "";
        }
        VirtualFile file = FileDocumentManager.getInstance().getFile(document);
        if (file == null) {
            return "";
        }
        VirtualFile rootFile = null;
        for (VirtualFile root : ProjectRootManager.getInstance(project).getContentRoots()) {
            if (VfsUtilCore.isAncestor(root, file, false)) {
                rootFile = root;
                break;
            }
        }
        String rootPath = (rootFile != null) ? rootFile.getPath() : project.getBasePath();
        if (rootPath == null) {
            return file.getName();
        }
        try {
            Path relative = Paths.get(rootPath).relativize(Paths.get(file.getPath()));
            return relative.toString().replace("\\", "/"); 
        } catch (Exception e) {
            return file.getName();
        }
    }

    public String toSafeTempFileName(String relativePath) {
        String baseName = Paths.get(relativePath).getFileName().toString();
        String hash = this.generateFileHash(relativePath);
        return baseName + "-" + hash + ".tmp";
    }

    public String generateFileHash(String relativePath) {
        try {
            LocalTime time = LocalTime.now();
            String timeSuffix = String.format("%02d%02d", time.getMinute(), time.getSecond());
            String combined = relativePath + timeSuffix;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.substring(0, 16);
        }
        catch (NoSuchAlgorithmException  e){
           // TODO : add the logger that you are using diff way
            return Integer.toHexString((relativePath + System.currentTimeMillis()).hashCode());
        }
    }

    protected Path getTempSubFolderPath(String baseTempDir, Document document){
        String baseTempPath = super.getTempSubFolderPath(baseTempDir);
        String relativePath = this.getRelativePath(document);
        return Paths.get(baseTempPath,toSafeTempFileName(relativePath));
    }

    private String saveMainManifestFile(Path tempSubFolder, String originalFilePath,String content) throws IOException {
          Path originalPath = Paths.get(originalFilePath);
          String fileName = originalPath.getFileName().toString();
          Path tempFilePath = Paths.get(tempSubFolder.toString(), fileName);
          Files.writeString(tempFilePath, content, StandardCharsets.UTF_8);
          return tempFilePath.toString();
    }

    private String saveCompanionFile(Path tempFolderPath,String originalFilePath){
      String companionFileName=getCompanionFileName(Paths.get(originalFilePath).getFileName().toString());
      if(companionFileName.isEmpty()){
          return null;
        }
        Path companionOriginalPath = Paths.get(Paths.get(originalFilePath).getParent().toString(), companionFileName);
        if (!Files.exists(companionOriginalPath)) {
            return null;
        }
        Path companionTempPath = Paths.get(tempFolderPath.toString(), companionFileName);
        try {

            Files.copy(companionOriginalPath, companionTempPath, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Filed saved");
            return companionTempPath.toString();

        } catch (IOException e) {
            //TODO improve the logger
            LOGGER.warn("Error occurred during OSS realTime scan",e);
            return null;
        }
    }

    private String getCompanionFileName(String fileName){
        if(fileName.equals("package.json")){
            return "package-lock.json";
        }
        if(fileName.contains(".csproj")){
            return "package.lock.json";
        }
        return "";
    }

    @Override
    public void scan(Document document, String uri) {

        List<CxProblems> problemsList = new ArrayList<>();

        if(!this.shouldScanFile(uri)){
            return;
        }
        String originalFilePath = uri;
        Path tempSubFolder = this.getTempSubFolderPath(Constants.RealTimeConstants.OSS_REALTIME_SCANNER_DIRECTORY, document);
        com.checkmarx.ast.ossrealtime.OssRealtimeResults scanResults;
        try {
            this.createTempFolder(tempSubFolder);
            String mainTempPath=this.saveMainManifestFile(tempSubFolder,originalFilePath,document.getText());
            this.saveCompanionFile(tempSubFolder,originalFilePath);
            System.out.println(Files.exists(Path.of(mainTempPath)) && Files.isReadable(Path.of(mainTempPath)));

            LOGGER.info("Scan has started On: "+mainTempPath);
            LOGGER.info("scanned file is -->"+uri);
            scanResults= CxWrapperFactory.build().ossRealtimeScan(mainTempPath,"");
            System.out.println("scanResults--->"+scanResults);

            problemsList.addAll(buildCxProblems(scanResults.getPackages()));

        } catch (IOException | CxException | InterruptedException e) {
            // TODO this msg needs be improved
         LOGGER.warn("Error occurred during OSS realTime scan",e);
        }

        finally {
           this.deleteTempFolder(tempSubFolder);
        }

        // Persist in project service
         ProblemHolderService.getInstance(project)
                .addProblems(originalFilePath, problemsList);
    }

    /**
     * After getting the entire scan result pass to this method to build the CxProblems for custom tool window
     *
     */
    public static List<CxProblems> buildCxProblems(List<OssRealtimeScanPackage> pkgs) {
        return pkgs.stream()
                .map(pkg -> {
                    CxProblems problem = new CxProblems();
                    if (pkg.getLocations() != null && !pkg.getLocations().isEmpty()) {
                        for (OssRealtimeLocation location : pkg.getLocations()) {
                            problem.addLocation(location.getLine(), location.getStartIndex(), location.getEndIndex());
                        }
                    }
                    problem.setTitle(pkg.getPackageName());
                    problem.setPackageVersion(pkg.getPackageVersion());
                    problem.setScannerType(Constants.RealTimeConstants.OSS_REALTIME_SCANNER_ENGINE_NAME);
                    problem.setSeverity(pkg.getStatus());
                    // Optionally set other fields if available, e.g. description, cve, etc.
                    return problem;
                })
                .collect(Collectors.toList());
    }
}
