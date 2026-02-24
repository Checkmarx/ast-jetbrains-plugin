package com.checkmarx.intellij.common.wrapper;

import com.checkmarx.ast.wrapper.CliExecutor;
import com.checkmarx.ast.wrapper.CommandRequest;
import com.checkmarx.ast.wrapper.CommandResponse;
import com.intellij.openapi.application.ApplicationManager;

import java.util.function.Consumer;

class CliCommandService {
    private final CliExecutor executor;

    public CliCommandService() {
        this.executor = new CliExecutor();
    }

    public CliCommandService(CliExecutor executor) {
        this.executor = executor;
    }

    public void run(CommandRequest request, Consumer<CommandResponse> callback) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            CommandResponse result = executor.execute(request);
            callback.accept(result);
        });
    }
}
