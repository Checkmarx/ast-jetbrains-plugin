package com.checkmarx.intellij.ui;

import com.automation.remarks.junit5.Video;
import org.junit.jupiter.api.Test;

public class TestAuthentication extends BaseUITest{
    @Test
    @Video
    public void testASTAuthentication() {
        System.out.println(" ==========> TestAuthentication: testar autenticação");
        // Test wrong connection
        testASTConnection(false);

        // Test successfully connection
        testASTConnection(true);
    }
}
