package com.checkmarx.intellij.devassist.scanners.iac;

import com.checkmarx.intellij.devassist.basescanner.BaseScannerCommand;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * IAC Scanner Command that manages the lifecycle of IAC realtime scanning.
 * Integrates with the scanner registry system to handle enabling/disabling of IAC scanning.
 */
public class IacScannerCommand extends BaseScannerCommand {

    private final Project project;
    private final IacScannerService iacScannerService;

    private IacScannerCommand(@NotNull Disposable parentDisposable,  @NotNull  Project project, @NotNull IacScannerService iacScannerService) {
        super(parentDisposable, IacScannerService.createConfig());
        this.project=project;
        this.iacScannerService=iacScannerService;
    }

    public IacScannerCommand(@NotNull Disposable disposable, @NotNull Project project){
        this(disposable,project,new IacScannerService());
    }

    /**
     * Initializes the scanner, invoked after registration of the scanner
     */

    @Override
    public void initializeScanner(){
    }

    @Override
    public void dispose(){
        super.dispose();
    }


}
