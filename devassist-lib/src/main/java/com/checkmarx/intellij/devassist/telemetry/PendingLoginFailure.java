package com.checkmarx.intellij.devassist.telemetry;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PendingLoginFailure {

    private String authMethod = "";

    private String timestamp = "";

    private String reason = "";
}
