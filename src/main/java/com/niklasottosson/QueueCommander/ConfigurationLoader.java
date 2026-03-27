package com.niklasottosson.QueueCommander;

import com.niklasottosson.QueueCommander.model.ApplicationSettings;
import com.niklasottosson.QueueCommander.model.Configuration;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConfigurationLoader {
    private static final String CLASSPATH_CONFIG = "application.yaml";
    private static final String SYSTEM_PROPERTY = "qc.config";
    private static final String ENVIRONMENT_VARIABLE = "QC_CONFIG";

    private ConfigurationLoader() {
    }

    public static ApplicationSettings load() throws IOException {
        Map<String, Object> merged = new LinkedHashMap<>();
        mergeInto(merged, loadClasspathConfig());

        Path externalConfig = resolveExternalConfig();
        if (externalConfig != null && Files.exists(externalConfig)) {
            try (InputStream inputStream = Files.newInputStream(externalConfig)) {
                mergeInto(merged, loadYaml(inputStream));
            }
        }

        return mapSettings(merged);
    }

    private static Map<String, Object> loadClasspathConfig() throws IOException {
        try (InputStream inputStream = ConfigurationLoader.class.getClassLoader().getResourceAsStream(CLASSPATH_CONFIG)) {
            if (inputStream == null) {
                return new LinkedHashMap<>();
            }
            return loadYaml(inputStream);
        }
    }

    private static Map<String, Object> loadYaml(InputStream inputStream) {
        Yaml yaml = new Yaml();
        Object loaded = yaml.load(inputStream);
        if (loaded instanceof Map<?, ?> loadedMap) {
            return toStringKeyMap(loadedMap);
        }
        return new LinkedHashMap<>();
    }

    private static Path resolveExternalConfig() {
        String systemProperty = System.getProperty(SYSTEM_PROPERTY);
        if (hasText(systemProperty)) {
            return toPath(systemProperty.trim());
        }

        String environmentValue = System.getenv(ENVIRONMENT_VARIABLE);
        if (hasText(environmentValue)) {
            return toPath(environmentValue.trim());
        }

        Path workingDirectoryConfig = Paths.get(CLASSPATH_CONFIG).toAbsolutePath().normalize();
        if (Files.exists(workingDirectoryConfig)) {
            return workingDirectoryConfig;
        }

        return null;
    }

    private static Path toPath(String location) {
        if (location.startsWith("file:")) {
            return Paths.get(URI.create(location));
        }
        return Paths.get(location);
    }

    private static ApplicationSettings mapSettings(Map<String, Object> root) {
        ApplicationSettings settings = new ApplicationSettings();
        Map<String, Object> qc = getMap(root, "qc");

        settings.setActiveQmanager(getString(qc, "active-qmanager", getString(qc, "activeQmanager", null)));

        Map<String, Object> ui = getMap(qc, "ui");
        settings.setFontSize(getInt(ui, "font-size", getInt(ui, "fontSize", settings.getFontSize())));
        Map<String, Object> window = getMap(ui, "window");
        settings.setWindowColumns(getInt(window, "columns", settings.getWindowColumns()));
        settings.setWindowRows(getInt(window, "rows", settings.getWindowRows()));

        Map<String, Object> ssl = getMap(qc, "ssl");
        Map<String, Object> truststore = getMap(ssl, "truststore");
        String truststoreLocation = getString(truststore, "location", null);
        String truststorePassword = getString(truststore, "password", null);
        String truststoreType = getString(truststore, "type", null);

        for (Map<String, Object> queueManagerMap : getMapList(qc, "qmanagers")) {
            Configuration configuration = new Configuration();
            configuration.setQmanager(getString(queueManagerMap, "name", null));
            configuration.setHost(getString(queueManagerMap, "host", null));
            configuration.setPort(getInt(queueManagerMap, "port", 0));
            configuration.setChannel(getString(queueManagerMap, "channel", null));
            configuration.setUser(getString(queueManagerMap, "username", getString(queueManagerMap, "user", null)));
            configuration.setPassword(getString(queueManagerMap, "password", null));
            configuration.setJolokiaUrl(getString(queueManagerMap, "url", getString(queueManagerMap, "jolokia-url", null)));
            configuration.setTruststoreLocation(getString(queueManagerMap, "truststore-location", truststoreLocation));
            configuration.setTruststorePassword(getString(queueManagerMap, "truststore-password", truststorePassword));
            configuration.setTruststoreType(getString(queueManagerMap, "truststore-type", truststoreType));
            settings.getQmanagers().add(configuration);
        }

        return settings;
    }

    private static void mergeInto(Map<String, Object> target, Map<String, Object> source) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object targetValue = target.get(entry.getKey());
            Object sourceValue = entry.getValue();

            if (targetValue instanceof Map<?, ?> targetMap && sourceValue instanceof Map<?, ?> sourceMap) {
                Map<String, Object> mergedChild = toStringKeyMap(targetMap);
                mergeInto(mergedChild, toStringKeyMap(sourceMap));
                target.put(entry.getKey(), mergedChild);
            } else {
                target.put(entry.getKey(), sourceValue);
            }
        }
    }

    private static Map<String, Object> getMap(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value instanceof Map<?, ?> mapValue) {
            return toStringKeyMap(mapValue);
        }
        return new LinkedHashMap<>();
    }

    private static List<Map<String, Object>> getMapList(Map<String, Object> source, String key) {
        Object value = source.get(key);
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> listValue) {
            for (Object item : listValue) {
                if (item instanceof Map<?, ?> itemMap) {
                    result.add(toStringKeyMap(itemMap));
                }
            }
        }
        return result;
    }

    private static String getString(Map<String, Object> source, String key, String defaultValue) {
        Object value = source.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private static int getInt(Map<String, Object> source, String key, int defaultValue) {
        Object value = source.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String stringValue && hasText(stringValue)) {
            try {
                return Integer.parseInt(stringValue.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static Map<String, Object> toStringKeyMap(Map<?, ?> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : input.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }
}

