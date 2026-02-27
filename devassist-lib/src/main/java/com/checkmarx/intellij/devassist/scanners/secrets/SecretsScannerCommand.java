package com.checkmarx.intellij.devassist.scanners.secrets;

import com.checkmarx.intellij.common.utils.Utils;
import com.checkmarx.intellij.devassist.basescanner.BaseScannerCommand;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class SecretsScannerCommand extends BaseScannerCommand {
    public SecretsScannerService secretsScannerService;
    private final Project project;
    private static final Logger LOGGER = Utils.getLogger(SecretsScannerCommand.class);

    private SecretsScannerCommand(@NotNull Disposable parentDisposable, @NotNull Project project, @NotNull SecretsScannerService secretsScannerService) {
        super(parentDisposable, SecretsScannerService.createConfig());
        this.secretsScannerService = secretsScannerService;
        this.project = project;
    }

    public SecretsScannerCommand(@NotNull Disposable parentDisposable,
                                 @NotNull Project project) {
        this(parentDisposable, project, new SecretsScannerService());
    }

    /**
     * Initializes the secrets scanner for real-time scanning
     */
    @Override
    protected void initializeScanner() {
        LOGGER.info("Secrets scanner: initialized for real-time scanning");
    }

    /**
     * Disposes the listeners automatically
     * Triggered when project is closed
     */
    @Override
    public void dispose() {
        super.dispose();
    }
}
