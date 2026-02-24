package com.checkmarx.intellij.common.wrapper;

import com.checkmarx.ast.wrapper.CommandRequest;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;

public class RunScanAction extends AnAction {

    private final CliCommandService service = new CliCommandService();

    public RunScanAction() {
        super("Run Scan");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        CommandRequest request = new CommandRequest("scan")
                .addArg("--path=/src");

        service.run(request, result -> {
            ApplicationManager.getApplication().invokeLater(() -> {
                Messages.showInfoMessage(
                        e.getProject(),
                        result.getRawOutput(),
                        "CLI Scan Results"
                );
            });
        });
    }
}