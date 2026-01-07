package com.checkmarx.intellij.devassist.ignore;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class TempItem {
    private String Title;
    private String SecretValue;
    private String SimilarityID;
    private String FileName;
    private Integer Line;
    private Integer RuleID;
    private String PackageManager;
    private String PackageName;
    private String PackageVersion;
    private String ImageName;
    private String ImageTag;

    public TempItem() {
    }


    public static TempItem forOss(String pm, String name, String version) {
        TempItem t = new TempItem();
        t.PackageManager = pm;
        t.PackageName = name;
        t.PackageVersion = version;
        return t;
    }

    public static TempItem forSecret(String title, String secretValue) {
        TempItem t = new TempItem();
        t.Title = title;
        t.SecretValue = secretValue;
        return t;
    }

    public static TempItem forIac(String title, String similarityId) {
        TempItem t = new TempItem();
        t.Title = title;
        t.SimilarityID = similarityId;
        return t;
    }

    public static TempItem forContainer(String imageName, String imageTag) {
        TempItem t = new TempItem();
        t.ImageName = imageName;
        t.ImageTag = imageTag;
        return t;
    }

    public static TempItem forAsca(String fileName, Integer line, Integer ruleId) {
        TempItem t = new TempItem();
        t.FileName = fileName;
        t.Line = line;
        t.RuleID = ruleId;
        return t;
    }
}
