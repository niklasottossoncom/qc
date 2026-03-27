package com.niklasottosson.QueueCommander.model;

import java.util.Collections;
import java.util.List;

public class QueueLoadResult {
    private final List<Queue> queues;
    private final boolean success;
    private final String message;

    private QueueLoadResult(List<Queue> queues, boolean success, String message) {
        this.queues = queues;
        this.success = success;
        this.message = message;
    }

    public static QueueLoadResult success(List<Queue> queues, String message) {
        return new QueueLoadResult(queues, true, message);
    }

    public static QueueLoadResult failure(String message) {
        return new QueueLoadResult(Collections.emptyList(), false, message);
    }

    public List<Queue> getQueues() {
        return queues;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}

