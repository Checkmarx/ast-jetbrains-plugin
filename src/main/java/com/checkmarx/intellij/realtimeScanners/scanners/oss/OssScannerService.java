package com.checkmarx.intellij.realtimeScanners.scanners.oss;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.realtimeScanners.basescanner.BaseScannerService;
import com.checkmarx.intellij.realtimeScanners.configuration.ScannerConfig;



public class OssScannerService extends BaseScannerService {



   public OssScannerService(){
      super(createConfig());
    }

    public static ScannerConfig createConfig() {
        return ScannerConfig.builder()
                .engineName(Constants.RealTimeConstants.OSS_REALTIME_SCANNER_ENGINE_NAME)
                .configSection(Constants.RealTimeConstants.OSS_REALTIME_SCANNER)
                .activateKey(Constants.RealTimeConstants.ACTIVATE_OSS_REALTIME_SCANNER)
                .errorMessage(Constants.RealTimeConstants.ERROR_OSS_REALTIME_SCANNER)
                .disabledMessage(Constants.RealTimeConstants.OSS_REALTIME_SCANNER_DISABLED)
                .enabledMessage(Constants.RealTimeConstants.OSS_REALTIME_SCANNER_START)
                .build();
    }
}
