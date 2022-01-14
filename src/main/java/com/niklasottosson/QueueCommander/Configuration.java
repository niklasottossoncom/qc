package com.niklasottosson.QueueCommander;

public class Configuration {
    private String host;
    private int port;
    private String qmanager;
    private String channel;
    private String user;
    private String password;

    public Configuration(String host, int port, String qmanager, String channel, String user, String password){
        this.host = host;
        this.port = port;
        this.qmanager = qmanager;
        this.channel = channel;
        this.user = user;
        this.password = password;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getQmanager() {
        return qmanager;
    }

    public void setQmanager(String qmanager) {
        this.qmanager = qmanager;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return "Configuration{" +
                "host='" + host + '\'' +
                ", qmanager='" + qmanager + '\'' +
                '}';
    }
}
