package com.checkmarx.intellij.realtimeScanners.configuration;


import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.util.messages.MessageBusConnection;

import java.util.HashMap;

import java.util.Map;


@Service(Service.Level.APP)
public final class ConfigurationManager implements Disposable {

    public static final String KEY_OSS = "realtime.oss";
    public static final String KEY_SECRETS = "realtime.secrets";
    public static final String KEY_CONTAINERS = "realtime.containers";
    public static final String KEY_IAC = "realtime.iac";

    private final MessageBusConnection connection;
    private Map<String, Boolean> lastSnapshot;

    public ConfigurationManager() {
        this.connection = ApplicationManager.getApplication().getMessageBus().connect();
        this.lastSnapshot = snapshot();
    }


    private Map<String, Boolean> snapshot() {
        GlobalSettingsState st = GlobalSettingsState.getInstance();
        Map<String, Boolean> snap = new HashMap<>();
        snap.put(KEY_OSS, st.isOssRealtime());
        snap.put(KEY_SECRETS, st.isSecretDetectionRealtime());
        snap.put(KEY_CONTAINERS, st.isContainersRealtime());
        snap.put(KEY_IAC, st.isIacRealtime());
        return snap;
    }

    @Override
    public void dispose() {
        connection.dispose();
    }
}

