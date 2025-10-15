package com.checkmarx.intellij.realtimeScanners.configuration;

import com.checkmarx.intellij.settings.SettingsListener;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.util.messages.MessageBusConnection;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Service(Service.Level.APP)
public final class ConfigurationManager implements Disposable {

    /** Distinct logical keys mirroring VS Code settings identifiers */
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

    /** Basic scanner config abstraction */
    /*public static class ScannerConfig {
        private final String key;
        private final Supplier<Boolean> activeSupplier;
        private final String engineName;

        public ScannerConfig(String key, Supplier<Boolean> activeSupplier, String engineName) {
            this.key = key;
            this.activeSupplier = activeSupplier;
            this.engineName = engineName;
        }
        public String key() { return key; }
        public String engineName() { return engineName; }
        public boolean isActive() { return activeSupplier.get(); }
    }*/

    /** Returns true if the given scanner config is currently enabled */
   /* public boolean isScannerActive(ScannerConfig config) {
        return config != null && config.isActive();
    }*/

    /**
     * Register a listener similar to VS Code's onDidChangeConfiguration.
     * The callback receives a Predicate<String> affectsConfiguration which you can call with one of the KEY_* constants.
     */
    public Disposable registerConfigChangeListener(java.util.function.Consumer<Predicate<String>> callback) {
        connection.subscribe(SettingsListener.SETTINGS_APPLIED, new SettingsListener() {
            @Override
            public void settingsApplied() {
                Map<String, Boolean> current = snapshot();
                Set<String> changed = diff(lastSnapshot, current);
                if (!changed.isEmpty()) {
                    Predicate<String> affects = changed::contains;
                    callback.accept(affects);
                }
                lastSnapshot = current;
            }
        });
        return this; // disposable returns self (dispose will disconnect)
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

    private static Set<String> diff(Map<String, Boolean> oldMap, Map<String, Boolean> newMap) {
        Set<String> changed = new HashSet<>();
        for (String k : newMap.keySet()) {
            Boolean o = oldMap.get(k);
            Boolean n = newMap.get(k);
            if (o == null || !o.equals(n)) {
                changed.add(k);
            }
        }
        return changed;
    }

    @Override
    public void dispose() {
        connection.dispose();
    }
}

