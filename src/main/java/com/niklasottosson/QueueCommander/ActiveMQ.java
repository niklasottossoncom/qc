package com.niklasottosson.QueueCommander;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.management.ObjectName;

import com.niklasottosson.QueueCommander.model.Configuration;
import com.niklasottosson.QueueCommander.model.Queue;
import com.niklasottosson.QueueCommander.model.QueueLoadResult;
import com.niklasottosson.QueueCommander.model.QueueMessage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.jms.*;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public class ActiveMQ {
    private static final String DEFAULT_JOLOKIA_URL = "http://localhost:8161/api/jolokia/";

    private Connection connection;
    private Session session;
    private Configuration configuration;


    public ActiveMQ() {

    }

    public ActiveMQ(Configuration config) {
        setConfig(config);

    }

    public void setConfig(Configuration conf) {
        this.configuration = conf;
    }

    // Not used when ActiveMQ
    public boolean connect() {

        return true;
    }

    // Not used when ActiveMQ
    public boolean disconnect() {
        return true;
    }

    public List<Queue> getQueueList() {
        return getQueueListResult().getQueues();
    }

    public QueueLoadResult getQueueListResult() {
        List<Queue> result = new ArrayList<>();

        try {
            HttpClient client = createConfiguredClient();

            // 1) Find all queue MBeans
            String searchPayload =
                    "{\"type\":\"search\",\"mbean\":\"org.apache.activemq:type=Broker,brokerName=*,destinationType=Queue,destinationName=*\"}";
            String searchBody = postJolokia(client, getJolokiaUrl(), getUsername(), getPassword(), searchPayload);

            List<String> mbeans = extractMBeansFromSearch(searchBody);

            mbeans.sort(String::compareToIgnoreCase);

            // 2) Read Name + QueueSize for each queue MBean
            for (String mbean : mbeans) {
                String readPayload =
                        "{\"type\":\"read\",\"mbean\":\"" + escapeJson(mbean) + "\",\"attribute\":[\"Name\",\"QueueSize\"]}";
                String readBody = postJolokia(client, getJolokiaUrl(), getUsername(), getPassword(), readPayload);

                String name = extractJsonString(readBody, "Name");
                long depth = extractJsonLong(readBody, "QueueSize");

                if (name == null || name.isEmpty()) {
                    // Fallback: derive queue name from ObjectName if Name is missing
                    name = extractDestinationNameFromMBean(mbean);
                }

                result.add(new Queue(name != null ? name : mbean, (int) depth));
            }
        } catch (Exception e) {
            return QueueLoadResult.failure(buildQueueLoadErrorMessage(e));
        }

        System.out.println("Found " + result.size() + " queues via Jolokia.");
        String message = result.isEmpty() ? "Connected, but no queues were returned." : "Connected to " + getDisplayName() + ".";
        return QueueLoadResult.success(result, message);
    }

    public List<String> getQueueMessages(String queueName, int maxMessages) {
        List<String> messages = new ArrayList<>();
        for (QueueMessage message : getQueueMessageDetails(queueName, maxMessages)) {
            messages.add(message.getPreview());
        }
        return messages;
    }

    public List<QueueMessage> getQueueMessageDetails(String queueName, int maxMessages) {
        List<QueueMessage> messages = new ArrayList<>();
        int effectiveMax = Math.max(1, maxMessages);

        try {
            HttpClient client = createConfiguredClient();

            String searchPayload =
                    "{\"type\":\"search\",\"mbean\":\"org.apache.activemq:type=Broker,brokerName=*,destinationType=Queue,destinationName=*\"}";
            String searchBody = postJolokia(client, getJolokiaUrl(), getUsername(), getPassword(), searchPayload);

            List<String> mbeans = extractMBeansFromSearch(searchBody);
            String queueMBean = findQueueMBean(queueName, mbeans);
            if (queueMBean == null) {
                messages.add(QueueMessage.info("Queue not found: " + queueName));
                return messages;
            }

            String browsePayload =
                    "{\"type\":\"exec\",\"mbean\":\"" + escapeJson(queueMBean) + "\",\"operation\":\"browse()\"}";
            String browseBody = postJolokia(client, getJolokiaUrl(), getUsername(), getPassword(), browsePayload);

            List<String> messageObjects = extractJsonObjectsFromValueArray(browseBody);
            int count = Math.min(messageObjects.size(), effectiveMax);

            for (int i = 0; i < count; i++) {
                String object = messageObjects.get(i);
                String id = safeValue(extractJsonString(object, "JMSMessageID"), "<no-id>");
                String content = extractMessageBody(object);
                String preview = String.format("%03d  %s  %s", i + 1, id, truncate(content, 120));
                messages.add(new QueueMessage(id, content, preview, true));
            }

            if (messages.isEmpty()) {
                messages.add(QueueMessage.info("No messages in queue."));
            }
        } catch (Exception e) {
            messages.add(QueueMessage.info("Failed to read messages: " + e.getMessage()));
        }

        return messages;
    }

    private String postJolokia(HttpClient client, String url, String user, String pass, String payload)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Origin", "http://localhost:8161")
                .POST(HttpRequest.BodyPublishers.ofString(payload));

        if (user != null && pass != null) {
            String raw = user + ":" + pass;
            String basic = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + basic);
        }

        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Jolokia HTTP " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private List<String> extractMBeansFromSearch(String json) {
        List<String> out = new ArrayList<>();

        // Pull the "value": [ ... ] section
        Matcher valueMatcher = Pattern.compile("\"value\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL).matcher(json);
        if (!valueMatcher.find()) {
            return out;
        }

        String arrayBody = valueMatcher.group(1);

        // Pull each quoted MBean string
        Matcher itemMatcher = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(arrayBody);
        while (itemMatcher.find()) {
            out.add(unescapeJson(itemMatcher.group(1)));
        }

        return out;
    }

    private String extractJsonString(String json, String field) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        return m.find() ? unescapeJson(m.group(1)) : null;
    }

    private long extractJsonLong(String json, String field) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(\\d+)").matcher(json);
        return m.find() ? Long.parseLong(m.group(1)) : 0L;
    }

    private String extractDestinationNameFromMBean(String mbean) {
        try {
            ObjectName objectName = new ObjectName(mbean);
            return objectName.getKeyProperty("destinationName");
        } catch (Exception ignored) {
            Matcher m = Pattern.compile("destinationName=([^,]+)").matcher(mbean);
            return m.find() ? m.group(1) : null;
        }
    }

    private String findQueueMBean(String queueName, List<String> mbeans) {
        String expectedName = normalizeQueueName(queueName);
        for (String mbean : mbeans) {
            String destinationName = normalizeQueueName(extractDestinationNameFromMBean(mbean));
            if (expectedName.equals(destinationName)) {
                return mbean;
            }
        }
        return null;
    }

    private String normalizeQueueName(String name) {
        if (name == null) {
            return "";
        }
        String normalized = name.trim();
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    private List<String> extractJsonObjectsFromValueArray(String json) {
        List<String> out = new ArrayList<>();
        Matcher valueMatcher = Pattern.compile("\\\"value\\\"\\s*:\\s*\\[(.*)]", Pattern.DOTALL).matcher(json);
        if (!valueMatcher.find()) {
            return out;
        }

        String arrayBody = valueMatcher.group(1);
        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escape = false;

        for (int i = 0; i < arrayBody.length(); i++) {
            char c = arrayBody.charAt(i);

            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            if (c == '"') {
                inString = true;
                continue;
            }

            if (c == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    out.add(arrayBody.substring(start, i + 1));
                    start = -1;
                }
            }
        }

        return out;
    }

    private String extractMessageBody(String jsonObject) {
        String text = extractJsonString(jsonObject, "Text");
        if (text != null) {
            return text;
        }

        String body = extractJsonString(jsonObject, "Body");
        if (body != null) {
            return body;
        }

        return "<non-text message>";
    }

    private String safeValue(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String unescapeJson(String s) {
        if (s == null) return null;
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private HttpClient createConfiguredClient() throws Exception {
        if (configuration == null || configuration.getTruststoreLocation() == null || configuration.getTruststoreLocation().trim().isEmpty()) {
            return HttpClient.newBuilder().build();
        }
        return createHttpClientWithTruststore(
                configuration.getTruststoreLocation(),
                configuration.getTruststorePassword(),
                configuration.getTruststoreType()
        );
    }

    private HttpClient createHttpClientWithTruststore(String truststorePath, String truststorePassword, String truststoreType)
            throws Exception {
        // Load the truststore
        KeyStore trustStore = KeyStore.getInstance(hasText(truststoreType) ? truststoreType : "JKS");
        try (InputStream fis = openLocation(truststorePath)) {
            trustStore.load(fis, hasText(truststorePassword) ? truststorePassword.toCharArray() : null);
        }

        // Create TrustManagerFactory with the truststore
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        // Create SSLContext with the trust managers
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), new java.security.SecureRandom());

        // Create HttpClient with the SSLContext
        return HttpClient.newBuilder()
                .sslContext(sslContext)
                .build();
    }

    private String getJolokiaUrl() {
        return configuration != null && hasText(configuration.getJolokiaUrl())
                ? configuration.getJolokiaUrl()
                : DEFAULT_JOLOKIA_URL;
    }

    private String getUsername() {
        return configuration != null ? configuration.getUser() : null;
    }

    private String getPassword() {
        return configuration != null ? configuration.getPassword() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private InputStream openLocation(String location) throws IOException {
        if (location.startsWith("file:")) {
            return URI.create(location).toURL().openStream();
        }
        return new FileInputStream(location);
    }

    private String buildQueueLoadErrorMessage(Exception exception) {
        String detail = exception.getMessage();
        if (!hasText(detail)) {
            detail = exception.getClass().getSimpleName();
        }
        return "Unable to reach " + getDisplayName() + " (" + getJolokiaUrl() + "): " + detail;
    }

    private String getDisplayName() {
        return configuration != null && hasText(configuration.getQmanager())
                ? configuration.getQmanager()
                : "queue manager";
    }


}
