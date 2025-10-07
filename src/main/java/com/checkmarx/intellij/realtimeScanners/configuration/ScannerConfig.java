package com.checkmarx.intellij.realtimeScanners.configuration;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class ScannerConfig {
    private String engineName;
    private String configSection;
    private String activateKey;
    private String enabledMessage;
    private String disabledMessage;
    private String errorMessage;
}
