package com.niklasottosson.QueueCommander.model;

import java.util.ArrayList;
import java.util.List;

public class ApplicationSettings {
    private String activeQmanager;
    private int fontSize = 12;
    private int windowColumns = 120;
    private int windowRows = 40;
    private final List<Configuration> qmanagers = new ArrayList<>();

    public String getActiveQmanager() {
        return activeQmanager;
    }

    public void setActiveQmanager(String activeQmanager) {
        this.activeQmanager = activeQmanager;
    }

    public int getFontSize() {
        return fontSize;
    }

    public void setFontSize(int fontSize) {
        if (fontSize > 0) {
            this.fontSize = fontSize;
        }
    }

    public int getWindowColumns() {
        return windowColumns;
    }

    public void setWindowColumns(int windowColumns) {
        if (windowColumns > 0) {
            this.windowColumns = windowColumns;
        }
    }

    public int getWindowRows() {
        return windowRows;
    }

    public void setWindowRows(int windowRows) {
        if (windowRows > 0) {
            this.windowRows = windowRows;
        }
    }

    public List<Configuration> getQmanagers() {
        return qmanagers;
    }

    public Configuration findConfiguration(String qmanagerName) {
        if (qmanagerName == null) {
            return null;
        }
        String selected = qmanagerName.trim();
        for (Configuration configuration : qmanagers) {
            if (selected.equals(configuration.getQmanager())) {
                return configuration;
            }
        }
        return null;
    }

    public Configuration getActiveConfiguration() {
        if (qmanagers.isEmpty()) {
            throw new IllegalStateException("No queue managers configured in application.yaml");
        }
        if (activeQmanager == null || activeQmanager.trim().isEmpty()) {
            return qmanagers.get(0);
        }
        Configuration configuration = findConfiguration(activeQmanager);
        if (configuration != null) {
            return configuration;
        }
        throw new IllegalStateException("Configured active queue manager not found: " + activeQmanager);
    }
}

