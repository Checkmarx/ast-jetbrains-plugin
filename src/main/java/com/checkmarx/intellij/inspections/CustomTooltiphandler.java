package com.checkmarx.intellij.inspections;

import com.intellij.codeInsight.highlighting.TooltipLinkHandler;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;

public class CustomTooltiphandler extends TooltipLinkHandler {

    @Override
    public boolean handleLink(@NotNull String refSuffix, @NotNull Editor editor) {
        switch (refSuffix) {
            case "fix":
                System.out.println("Fixing issue...");
                break;

            case "ignore":
                System.out.println("Ignoring issue...");
                break;

            case "openDetails":
                System.out.println("Opening details...");
                break;

            default:
                System.out.println("Unknown action: " + refSuffix);
        }

        return true;
    }


}
