package com.checkmarx.intellij.realtimeScanners.scanners.oss;

import com.checkmarx.ast.realtime.RealtimeLocation;
import com.checkmarx.ast.ossrealtime.OssRealtimeScanPackage;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.realtimeScanners.basescanner.BaseScannerService;
import com.checkmarx.intellij.realtimeScanners.configuration.ScannerConfig;
import com.checkmarx.intellij.settings.global.CxWrapperFactory;
import com.checkmarx.intellij.realtimeScanners.dto.CxProblems;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.checkmarx.ast.ossrealtime.OssRealtimeResults;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;


public class OssScannerService extends BaseScannerService {
    private static final Logger LOGGER = Utils.getLogger(OssScannerService.class);

    public OssScannerService(){
      super(createConfig());
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

    protected Path getTempSubFolderPath(String baseTempDir, PsiFile document){
        String baseTempPath = super.getTempSubFolderPath(baseTempDir);
        String relativePath = document.getName();
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

    public OssRealtimeResults scan(PsiFile document, String uri) {
        LOGGER.info("------------SCAN STARTED OSS---------------"+uri);

        com.checkmarx.ast.ossrealtime.OssRealtimeResults scanResults;
        if(!this.shouldScanFile(uri)){
            return null;
        }
        Path tempSubFolder = this.getTempSubFolderPath(Constants.RealTimeConstants.OSS_REALTIME_SCANNER_DIRECTORY, document);

        try {
            this.createTempFolder(tempSubFolder);
            String mainTempPath=this.saveMainManifestFile(tempSubFolder, uri,document.getText());
            this.saveCompanionFile(tempSubFolder, uri);
            Path tempPath=Path.of(mainTempPath);
            System.out.println(Files.exists(tempPath) && Files.isReadable(tempPath));

            LOGGER.info("Scan has started On: "+mainTempPath);
            LOGGER.info("scanned file is -->"+uri);

            scanResults= CxWrapperFactory.build().ossRealtimeScan(mainTempPath,"");
            return  scanResults;

        } catch (IOException | CxException | InterruptedException e) {
         LOGGER.warn("Error occurred during OSS realTime scan",e);
        }
        finally {
            LOGGER.info("Deleting temporary folder");
            deleteTempFolder(tempSubFolder);
        }
        return null;
    }

}
