package com.checkmarx.intellij.realtimeScanners.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor


public class Package{
    @JsonProperty("PackageManager") 
    public String packageManager;
    @JsonProperty("PackageName") 
    public String packageName;
    @JsonProperty("PackageVersion") 
    public String packageVersion;
    @JsonProperty("FilePath") 
    public String filePath;
    @JsonProperty("Locations") 
    public ArrayList<Location> locations;
    @JsonProperty("Status") 
    public String status;
    @JsonProperty("Vulnerabilities") 
    public ArrayList<Vulnerability> vulnerabilities;
}
