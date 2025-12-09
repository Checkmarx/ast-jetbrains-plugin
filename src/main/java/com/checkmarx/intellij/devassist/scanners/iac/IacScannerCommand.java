package com.checkmarx.intellij.devassist.scanners.iac;

import com.checkmarx.intellij.devassist.basescanner.BaseScannerCommand;
import com.checkmarx.intellij.devassist.configuration.ScannerConfig;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class IacScannerCommand extends BaseScannerCommand {

    private final Project project;
    private  final IacScannerService iacScannerService;

    private IacScannerCommand(@NotNull Disposable parentDisposable,  @NotNull  Project project, @NotNull IacScannerService iacScannerService) {
        super(parentDisposable, IacScannerService.createConfig());
        this.project=project;
        this.iacScannerService=iacScannerService;

    }

    public IacScannerCommand(@NotNull Disposable disposable, @NotNull Project project){
        this(disposable,project,new IacScannerService());
    }

    @Override
    public void initializeScanner(){
    }

    @Override
    public void dispose(){
        super.dispose();
    }


}
