package com.checkmarx.intellij.devassist.scanners.asca;

import com.checkmarx.intellij.common.utils.Utils;
import com.checkmarx.intellij.devassist.basescanner.BaseScannerCommand;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * ASCA Scanner Command that manages the lifecycle of ASCA realtime scanning.
 * Integrates with the scanner registry system to handle enabling/disabling of ASCA scanning.
 */
public class AscaScannerCommand extends BaseScannerCommand {
    public AscaScannerService ascaScannerService;
    private final Project project;
    private static final Logger LOGGER = Utils.getLogger(AscaScannerCommand.class);

    private AscaScannerCommand(@NotNull Disposable parentDisposable, @NotNull Project project, @NotNull AscaScannerService ascaScannerService) {
        super(parentDisposable, AscaScannerService.createConfig());
        this.ascaScannerService = ascaScannerService;
        this.project = project;
    }

    public AscaScannerCommand(@NotNull Disposable parentDisposable,
                              @NotNull Project project) {
        this(parentDisposable, project, new AscaScannerService());
    }

    /**
     * Initializes the ASCA scanner for real-time scanning.
     * This is called when the scanner is enabled.
     */
    @Override
    protected void initializeScanner() {
        LOGGER.info("ASCA scanner: initialized for real-time scanning");
    }

    /**
     * Disposes the listeners automatically.
     * Triggered when project is closed or scanner is disabled.
     */
    @Override
    public void dispose() {
        super.dispose();
    }
}
