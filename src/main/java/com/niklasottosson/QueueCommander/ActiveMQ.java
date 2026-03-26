package com.niklasottosson.QueueCommander;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.niklasottosson.QueueCommander.model.Configuration;
import com.niklasottosson.QueueCommander.model.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.jms.*;

public class ActiveMQ {
    private Connection connection;
    private Session session;


    public ActiveMQ() {

    }

    public ActiveMQ(Configuration config) {
        setConfig(config);

    }

    public void setConfig(Configuration conf) {

    }

    public boolean connect() {
        // Create a ConnectionFactory
        //ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616");
/*
        try {

            // Create a Connection
            //connection = connectionFactory.createConnection();
            //connection.start();

            // Create a Session
            //session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        } catch (JMSException e) {
            e.printStackTrace();
        }
*/
        return true;
    }

    public boolean disconnect() {
        /*
        try {
            session.close();
            connection.close();
        } catch (JMSException e) {
            e.printStackTrace();
            return false;
        }
*/
        return true;
    }

    public List<Queue> getQueueList() {
        List<Queue> result = new ArrayList<>();

        // Adjust as needed or wire from Configuration/setConfig
        final String jolokiaUrl = "http://localhost:8161/api/jolokia/";
        final String username = "admin";
        final String password = "admin";

        try {
            HttpClient client = HttpClient.newHttpClient();

            // 1) Find all queue MBeans
            String searchPayload =
                    "{\"type\":\"search\",\"mbean\":\"org.apache.activemq:type=Broker,brokerName=*,destinationType=Queue,destinationName=*\"}";
            String searchBody = postJolokia(client, jolokiaUrl, username, password, searchPayload);

            List<String> mbeans = extractMBeansFromSearch(searchBody);

            // 2) Read Name + QueueSize for each queue MBean
            for (String mbean : mbeans) {
                String readPayload =
                        "{\"type\":\"read\",\"mbean\":\"" + escapeJson(mbean) + "\",\"attribute\":[\"Name\",\"QueueSize\"]}";
                String readBody = postJolokia(client, jolokiaUrl, username, password, readPayload);

                String name = extractJsonString(readBody, "Name");
                long depth = extractJsonLong(readBody, "QueueSize");

                if (name == null || name.isEmpty()) {
                    // Fallback: derive queue name from ObjectName if Name is missing
                    name = extractDestinationNameFromMBean(mbean);
                }

                result.add(new Queue(name != null ? name : mbean, (int) depth, "N/A", "N/A"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("Found " + result.size() + " queues via Jolokia.");
        return result;
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
        Matcher m = Pattern.compile("destinationName=([^,]+)$").matcher(mbean);
        return m.find() ? m.group(1) : null;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String unescapeJson(String s) {
        if (s == null) return null;
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }

}
