package com.checkmarx.intellij.devassist.ignore;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public final class TempItem {
    public String Title;
    public String SecretValue;
    public String SimilarityID;
    public String FileName;
    public Integer Line;
    public Integer RuleID;
    public String PackageManager;
    public String PackageName;
    public String PackageVersion;

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

    public static TempItem forAsca(String fileName, Integer line, Integer ruleId) {
        TempItem t = new TempItem();
        t.FileName = fileName;
        t.Line = line;
        t.RuleID = ruleId;
        return t;
    }
}
