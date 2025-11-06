package com.checkmarx.intellij.realtimeScanners.inspection;

import com.checkmarx.ast.ossrealtime.OssRealtimeResults;
import com.checkmarx.ast.ossrealtime.OssRealtimeScanPackage;
import com.checkmarx.ast.realtime.RealtimeLocation;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.realtimeScanners.basescanner.ScannerService;
import com.checkmarx.intellij.realtimeScanners.common.ScannerFactory;
import com.checkmarx.intellij.realtimeScanners.utils.ScannerUtils;
import com.checkmarx.intellij.realtimeScanners.dto.CxProblems;
import com.checkmarx.intellij.service.ProblemHolderService;
import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class RealtimeInspection extends LocalInspectionTool {

    @Getter
    @Setter
    private ScannerFactory scannerFactory= new ScannerFactory();

    private final Logger logger = Utils.getLogger(RealtimeInspection.class);
    private final Map<String,Long> fileTimeStamp= new ConcurrentHashMap<>();


    @Override
    public ProblemDescriptor @NotNull [] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
        try {

            String path = file.getVirtualFile().getPath();
            Optional<ScannerService<?>> scannerService= scannerFactory.findRealTimeScanner(path);

            if(scannerService.isEmpty()){
               return ProblemDescriptor.EMPTY_ARRAY;
            }

            if (!ScannerUtils.isScannerActive(scannerService.get().getConfig().getEngineName())){
                return  ProblemDescriptor.EMPTY_ARRAY;
            }

            long currentModificationTime = file.getModificationStamp();
            if(fileTimeStamp.containsKey(path) && fileTimeStamp.get(path).equals(currentModificationTime)){
                return ProblemDescriptor.EMPTY_ARRAY;
            }
            fileTimeStamp.put(path, currentModificationTime);
            OssRealtimeResults ossRealtimeResults= (OssRealtimeResults) scannerService.get().scan(file,path);

            List<CxProblems> problemsList = new ArrayList<>();

            problemsList.addAll(buildCxProblems(ossRealtimeResults.getPackages()));


            VirtualFile virtualFile = file.getVirtualFile();
            if (virtualFile != null) {
                ProblemHolderService.getInstance(file.getProject())
                        .addProblems(file.getVirtualFile().getPath(), problemsList);
            }

        } catch (Exception e) {
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        return ProblemDescriptor.EMPTY_ARRAY;
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
                        for (RealtimeLocation location : pkg.getLocations()) {
                            problem.addLocation(location.getLine()+1, location.getStartIndex(), location.getEndIndex());
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



