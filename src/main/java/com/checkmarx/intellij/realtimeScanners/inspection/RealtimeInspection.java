package com.checkmarx.intellij.realtimeScanners.inspection;

import com.checkmarx.ast.ossrealtime.OssRealtimeResults;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.realtimeScanners.basescanner.BaseScannerService;
import com.checkmarx.intellij.realtimeScanners.basescanner.ScannerService;
import com.checkmarx.intellij.realtimeScanners.common.ScannerFactory;
import com.checkmarx.intellij.realtimeScanners.configuration.RealtimeScannerManager;
import com.checkmarx.intellij.realtimeScanners.scanners.oss.OssScannerService;
import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class RealtimeInspection extends LocalInspectionTool {

    @Getter
    @Setter
    private ScannerService<?> scannerService;
    private final Logger logger = Utils.getLogger(RealtimeInspection.class);

    private final Map<String,Long> fileTimeStamp= new ConcurrentHashMap<>();
    private final ScannerFactory scannerFactory= new ScannerFactory();

    @Override
    public ProblemDescriptor @NotNull [] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
        try {
            RealtimeScannerManager scannerManager = file.getProject().getService(RealtimeScannerManager.class);

            String path = file.getVirtualFile().getPath();
            Optional<ScannerService<?>> optScannerService= scannerFactory.findApplicationScanner(path);
            optScannerService.ifPresent(service -> scannerService = service);
            if (scannerManager == null || !scannerManager.isScannerActive(scannerService.getConfig().getEngineName())) {
                return ProblemDescriptor.EMPTY_ARRAY;
            }

            long currentModificationTime = file.getModificationStamp();
            if(fileTimeStamp.containsKey(path) && fileTimeStamp.get(path).equals(currentModificationTime)){
                return ProblemDescriptor.EMPTY_ARRAY;
            }
            fileTimeStamp.put(path, currentModificationTime);
            OssRealtimeResults ossRealtimeResults= (OssRealtimeResults) scannerService.scan(file,path);

        } catch (Exception e) {
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        return ProblemDescriptor.EMPTY_ARRAY;
    }

}

