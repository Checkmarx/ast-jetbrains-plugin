package com.checkmarx.intellij.realtimeScanners.inspection;

import com.checkmarx.ast.ossrealtime.OssRealtimeResults;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.realtimeScanners.basescanner.ScannerService;
import com.checkmarx.intellij.realtimeScanners.common.ScannerFactory;
import com.checkmarx.intellij.realtimeScanners.utils.ScannerUtils;
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
            logger.info("OssRealTimeResults-->"+ossRealtimeResults);
        } catch (Exception e) {
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        return ProblemDescriptor.EMPTY_ARRAY;
    }

}

