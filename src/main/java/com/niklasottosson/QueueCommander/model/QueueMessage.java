package com.niklasottosson.QueueCommander.model;

/**
 * Lightweight queue message view model used by the Lanterna UI.
 */
public class QueueMessage {
    private final String messageId;
    private final String body;
    private final String preview;
    private final boolean openable;

    public QueueMessage(String messageId, String body, String preview, boolean openable) {
        this.messageId = messageId;
        this.body = body;
        this.preview = preview;
        this.openable = openable;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getBody() {
        return body;
    }

    public String getPreview() {
        return preview;
    }

    public boolean isOpenable() {
        return openable;
    }

    public static QueueMessage info(String preview) {
        return new QueueMessage("", "", preview, false);
    }
}

