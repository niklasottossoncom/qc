package com.niklasottosson.QueueCommander;

import java.io.FileInputStream;
import java.io.IOException;
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
import com.niklasottosson.QueueCommander.model.QueueMessage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.jms.*;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public class ActiveMQ {
    private static final String JOLOKIA_URL = "http://localhost:8161/api/jolokia/";
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "admin";
    private static final String TRUSTSTORE_PATH = "/Users/malen501/Development/certs/truststore.jks";
    private static final String TRUSTSTORE_PASSWORD = "password";

    private Connection connection;
    private Session session;


    public ActiveMQ() {

    }

    public ActiveMQ(Configuration config) {
        setConfig(config);

    }

    public void setConfig(Configuration conf) {

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
        List<Queue> result = new ArrayList<>();

        try {
            HttpClient client = createConfiguredClient();

            // 1) Find all queue MBeans
            String searchPayload =
                    "{\"type\":\"search\",\"mbean\":\"org.apache.activemq:type=Broker,brokerName=*,destinationType=Queue,destinationName=*\"}";
            String searchBody = postJolokia(client, JOLOKIA_URL, USERNAME, PASSWORD, searchPayload);

            List<String> mbeans = extractMBeansFromSearch(searchBody);

            mbeans.sort(String::compareToIgnoreCase);

            // 2) Read Name + QueueSize for each queue MBean
            for (String mbean : mbeans) {
                String readPayload =
                        "{\"type\":\"read\",\"mbean\":\"" + escapeJson(mbean) + "\",\"attribute\":[\"Name\",\"QueueSize\"]}";
                String readBody = postJolokia(client, JOLOKIA_URL, USERNAME, PASSWORD, readPayload);

                String name = extractJsonString(readBody, "Name");
                long depth = extractJsonLong(readBody, "QueueSize");

                if (name == null || name.isEmpty()) {
                    // Fallback: derive queue name from ObjectName if Name is missing
                    name = extractDestinationNameFromMBean(mbean);
                }

                result.add(new Queue(name != null ? name : mbean, (int) depth));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("Found " + result.size() + " queues via Jolokia.");
        return result;
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
            String searchBody = postJolokia(client, JOLOKIA_URL, USERNAME, PASSWORD, searchPayload);

            List<String> mbeans = extractMBeansFromSearch(searchBody);
            String queueMBean = findQueueMBean(queueName, mbeans);
            if (queueMBean == null) {
                messages.add(QueueMessage.info("Queue not found: " + queueName));
                return messages;
            }

            String browsePayload =
                    "{\"type\":\"exec\",\"mbean\":\"" + escapeJson(queueMBean) + "\",\"operation\":\"browse()\"}";
            String browseBody = postJolokia(client, JOLOKIA_URL, USERNAME, PASSWORD, browsePayload);

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
        if (TRUSTSTORE_PATH == null || TRUSTSTORE_PATH.trim().isEmpty()) {
            return HttpClient.newBuilder().build();
        }
        return createHttpClientWithTruststore(TRUSTSTORE_PATH, TRUSTSTORE_PASSWORD);
    }

    private HttpClient createHttpClientWithTruststore(String truststorePath, String truststorePassword)
            throws Exception {
        // Load the truststore
        KeyStore trustStore = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(truststorePath)) {
            trustStore.load(fis, truststorePassword.toCharArray());
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


}
