package com.checkmarx.intellij.realtimeScanners.scanners.oss;

import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.realtimeScanners.configuration.RealtimeScannerManager;
import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

public class OSSInspection extends LocalInspectionTool {

    @Getter
    @Setter
    private  OssScannerService  ossScannerService= new OssScannerService();
    private final Logger logger = Utils.getLogger(OSSInspection.class);

//    @Override
//    public void inspectionStarted(@NotNull LocalInspectionToolSession session, boolean isOnTheFly){
//        try {
//            logger.info("Inside inspection");
//
//            RealtimeScannerManager scannerManager = session.getFile().getProject().getService(RealtimeScannerManager.class);
//            if (scannerManager == null || !scannerManager.isScannerActive(ossScannerService.config.getEngineName())) {
//                return;
//            }
//            String path = session.getFile().getVirtualFile().getPath();
//            ossScannerService.scan(session.getFile(), path);
//
//        } catch (Exception e) {
//            logger.warn("Error occured");
//        }
//    }


//    @Override
//    public @NotNull PsiElementVisitor buildVisitor(
//            @NotNull ProblemsHolder holder,
//            boolean isOnTheFly) {
//
//        Project project = holder.getProject();
//        PsiFile psiFile = holder.getFile();
//
//
//        RealtimeScannerManager scannerManager = project.getService(RealtimeScannerManager.class);
//        if (scannerManager == null ||
//                !scannerManager.isScannerActive(ossScannerService.config.getEngineName())) {
//            return PsiElementVisitor.EMPTY_VISITOR;
//        }
//
//        String path = psiFile.getVirtualFile() != null ? psiFile.getVirtualFile().getPath() : null;
//
//        if (path != null) {
//            try {
//                ossScannerService.scan(psiFile, path);
//            } catch (Exception e) {
//                logger.warn("Error occurred during scan", e);
//            }
//        }
//
//        return PsiElementVisitor.EMPTY_VISITOR;
//    }


    @Override
    public ProblemDescriptor @NotNull [] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
        try {
              logger.info("Inside checkFile");
            RealtimeScannerManager scannerManager = file.getProject().getService(RealtimeScannerManager.class);
            if (scannerManager == null || !scannerManager.isScannerActive(ossScannerService.config.getEngineName())) {
                return ProblemDescriptor.EMPTY_ARRAY;
            }
            String path = file.getVirtualFile().getPath();
            ossScannerService.scan(file, path);

        } catch (Exception e) {
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        return ProblemDescriptor.EMPTY_ARRAY;
    }



}
