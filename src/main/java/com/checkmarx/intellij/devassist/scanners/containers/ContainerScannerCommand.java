package com.checkmarx.intellij.devassist.scanners.containers;

import com.checkmarx.intellij.devassist.basescanner.BaseScannerCommand;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class ContainerScannerCommand extends BaseScannerCommand {

    private final Project project;
    private final ContainerScannerService containerScannerService;

    private ContainerScannerCommand(@NotNull Disposable disposable, @NotNull Project project, @NotNull ContainerScannerService containerScannerService ) {
        super(disposable, ContainerScannerService.createConfig());
        this.containerScannerService = containerScannerService;
        this.project = project;
    }

    public ContainerScannerCommand(@NotNull Disposable disposable, Project project) {
       this(disposable, project, new ContainerScannerService());
    }

    @Override
    public void initializeScanner(){
    }

    @Override
    public void dispose(){
        super.dispose();
    }


}
