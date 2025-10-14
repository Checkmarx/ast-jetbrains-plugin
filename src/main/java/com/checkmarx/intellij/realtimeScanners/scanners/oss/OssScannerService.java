package com.checkmarx.intellij.realtimeScanners.scanners.oss;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.realtimeScanners.basescanner.BaseScannerService;
import com.checkmarx.intellij.realtimeScanners.configuration.ScannerConfig;
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
        PathMatcher pathMatcher= FileSystems.getDefault().getPathMatcher("glob:"+filePath);
        return pathMatcher.matches(Paths.get(filePath));
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
        if(!this.shouldScanFile(uri)){
            return;
        }
        String originalFilePath = uri;
        Path tempSubFolder = this.getTempSubFolderPath(Constants.RealTimeConstants.OSS_REALTIME_SCANNER_DIRECTORY, document);

        try {
            this.createTempFolder(tempSubFolder);
            String mainTempPath=this.saveMainManifestFile(tempSubFolder,originalFilePath,document.getText());
            this.saveCompanionFile(tempSubFolder,originalFilePath);

            // Call scan method

        } catch (IOException e) {
            // TODO this msg needs be improved
         LOGGER.warn("Error occurred during OSS realTime scan",e);
        }
        finally {
            this.deleteTempFolder(tempSubFolder);
        }
    }
}
