package com.checkmarx.intellij.realtimeScanners.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Location{
    @JsonProperty("Line") 
    public int line;
    @JsonProperty("StartIndex") 
    public int startIndex;
    @JsonProperty("EndIndex") 
    public int endIndex;
}
