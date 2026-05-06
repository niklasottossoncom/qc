package com.niklasottosson.QueueCommander;

import com.niklasottosson.QueueCommander.model.ApplicationSettings;
import com.niklasottosson.QueueCommander.model.Configuration;
import com.niklasottosson.QueueCommander.model.Queue;
import com.niklasottosson.QueueCommander.model.QueueLoadResult;
import com.niklasottosson.QueueCommander.model.QueueMessage;
import dev.tamboui.toolkit.app.ToolkitApp;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.ListElement;
import dev.tamboui.toolkit.event.EventResult;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static dev.tamboui.toolkit.Toolkit.*;

/**
 * Queue Commander — ActiveMQ queue and message viewer.
 *
 * Built with TamboUI for the terminal UI.
 *
 * Key bindings (main queue list):
 *   ↑ / ↓       — navigate queues
 *   Enter        — open message list for selected queue
 *   R            — refresh queue list
 *   Q            — select queue manager
 *   Esc / q      — quit
 */
public class QC extends ToolkitApp {

    private static final int MESSAGE_PREVIEW_LIMIT = 200;

    // ── Application backend ──────────────────────────────────────────────────
    private ApplicationSettings settings;
    private Configuration currentConfiguration;
    private ActiveMQ queueManager;

    // ── UI state ─────────────────────────────────────────────────────────────
    private List<Queue> queues = new ArrayList<>();
    private List<QueueMessage> messages = new ArrayList<>();
    private String statusMessage = "Status: Ready";
    private Queue viewedQueue = null;
    private QueueMessage viewedMessage = null;

    // ── View state ───────────────────────────────────────────────────────────
    private enum View { QUEUE_LIST, MESSAGE_LIST, MESSAGE_DETAIL, QMANAGER_DIALOG }
    private View currentView = View.QUEUE_LIST;
    private View previousView = null;

    // ── Persistent list elements (preserve scroll/selection state) ───────────
    private final ListElement<String> queueListEl = new ListElement<String>()
            .id("queueList").focusable().autoScroll();

    private final ListElement<String> messageListEl = new ListElement<String>()
            .id("messageList").focusable().autoScroll();

    private final ListElement<String> qmanagerListEl = new ListElement<String>()
            .id("qmanagerList").focusable().autoScroll();

    private final ListElement<String> messageDetailEl = new ListElement<String>()
            .id("messageDetail").focusable().autoScroll();

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void onStart() {
        try {
            settings = ConfigurationLoader.load();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load configuration: " + e.getMessage(), e);
        }
        currentConfiguration = settings.getActiveConfiguration();
        queueManager = new ActiveMQ(currentConfiguration);
        refresh();
    }

    // ── Render ───────────────────────────────────────────────────────────────

    @Override
    protected Element render() {

        // Synchronise focus when the active view changes.
        if (currentView != previousView) {
            previousView = currentView;
            String focusId = switch (currentView) {
                case QUEUE_LIST      -> "queueList";
                case MESSAGE_LIST    -> "messageList";
                case QMANAGER_DIALOG -> "qmanagerList";
                case MESSAGE_DETAIL  -> "messageDetail";
            };
            runner().focusManager().setFocus(focusId);
        }

        // ── Queue-manager selector list ───────────────────────────────────
        List<Configuration> configs = settings.getQmanagers();
        qmanagerListEl.items(buildQmanagerItems(configs));
        qmanagerListEl.onKeyEvent(event -> {
            if (event.isCancel()) {
                currentView = View.QUEUE_LIST;
                return EventResult.HANDLED;
            }
            if (event.isSelect() || event.isConfirm()) {
                int idx = qmanagerListEl.selected();
                if (idx >= 0 && idx < configs.size()) {
                    Configuration selected = configs.get(idx);
                    if (!selected.getQmanager().equals(currentConfiguration.getQmanager())) {
                        currentConfiguration = selected;
                        settings.setActiveQmanager(selected.getQmanager());
                        queueManager.setConfig(selected);
                        refresh();
                    }
                }
                currentView = View.QUEUE_LIST;
                return EventResult.HANDLED;
            }
            return EventResult.UNHANDLED;
        });

        // ── Queue list ────────────────────────────────────────────────────
        int maxLength = getLongestQueueName(queues);
        queueListEl.items(buildQueueItems(maxLength));
        queueListEl.onKeyEvent(event -> {
            if (event.isCharIgnoreCase('r')) {
                refresh();
                return EventResult.HANDLED;
            }
            if (event.isCharIgnoreCase('q')) {
                currentView = View.QMANAGER_DIALOG;
                return EventResult.HANDLED;
            }
            if (event.isCancel()) {
                quit();
                return EventResult.HANDLED;
            }
            if (event.isSelect() || event.isConfirm()) {
                int idx = queueListEl.selected();
                if (idx >= 0 && idx < queues.size()) {
                    viewedQueue = queues.get(idx);
                    loadMessages(viewedQueue.getName());
                    currentView = View.MESSAGE_LIST;
                }
                return EventResult.HANDLED;
            }
            return EventResult.UNHANDLED;
        });

        // ── Message list ──────────────────────────────────────────────────
        messageListEl.items(buildMessageItems());
        messageListEl.onKeyEvent(event -> {
            if (event.isCancel()) {
                currentView = View.QUEUE_LIST;
                return EventResult.HANDLED;
            }
            if (event.isSelect() || event.isConfirm()) {
                int idx = messageListEl.selected();
                if (idx >= 0 && idx < messages.size()) {
                    QueueMessage msg = messages.get(idx);
                    if (msg.isOpenable()) {
                        viewedMessage = msg;
                        currentView = View.MESSAGE_DETAIL;
                    }
                }
                return EventResult.HANDLED;
            }
            return EventResult.UNHANDLED;
        });

        // ── Message detail ────────────────────────────────────────────────
        if (viewedMessage != null) {
            messageDetailEl.items(buildMessageDetailLines(viewedMessage));
        }
        messageDetailEl.onKeyEvent(event -> {
            if (event.isCancel()) {
                currentView = View.MESSAGE_LIST;
                return EventResult.HANDLED;
            }
            return EventResult.UNHANDLED;
        });

        // ── Main view ─────────────────────────────────────────────────────
        String headerLabel = getLabel(maxLength);

        Element mainView = panel("Queue Commander",
            column(
                row(
                    text("Queue manager: "),
                    text(currentConfiguration.getQmanager()).bold().cyan(),
                    spacer(),
                    text("[Q] change  [R] refresh  [Esc] quit")
                ),
                text(statusMessage),
                text(headerLabel).bold(),
                queueListEl,
                panel("Legend",
                    text("↑↓ Navigate   Enter = Open queue   Q = Change queue manager   R = Refresh   Esc = Quit")
                )
            )
        ).rounded();

        // ── Overlaid dialogs ──────────────────────────────────────────────
        if (currentView == View.QMANAGER_DIALOG) {
            return stack(
                mainView,
                dialog("Select Queue Manager",
                    column(
                        text("Choose a queue manager:"),
                        qmanagerListEl,
                        text("[Enter] Select   [Esc] Cancel")
                    )
                ).rounded()
            );
        }

        if (currentView == View.MESSAGE_LIST) {
            return stack(
                mainView,
                dialog("Messages: " + (viewedQueue != null ? viewedQueue.getName() : ""),
                    column(
                        messageListEl,
                        text("[Enter] Open message   [Esc] Back")
                    )
                ).rounded()
            );
        }

        if (currentView == View.MESSAGE_DETAIL) {
            return stack(
                mainView,
                dialog("Message Detail",
                    column(
                        messageDetailEl,
                        text("[↑↓] Scroll   [Esc] Back")
                    )
                ).rounded()
            );
        }

        return mainView;
    }

    // ── Backend helpers ──────────────────────────────────────────────────────

    private void refresh() {
        statusMessage = "Status: Loading...";
        QueueLoadResult result = queueManager.getQueueListResult();
        queues = new ArrayList<>(result.getQueues());
        statusMessage = "Status: " + result.getMessage();
        if (!result.isSuccess()) {
            queues = new ArrayList<>();
        }
    }

    private void loadMessages(String queueName) {
        messages = queueManager.getQueueMessageDetails(queueName, MESSAGE_PREVIEW_LIMIT);
    }

    // ── Item builders ────────────────────────────────────────────────────────

    private String[] buildQueueItems(int maxLength) {
        if (queues.isEmpty()) {
            return new String[]{"No queues found."};
        }
        return queues.stream()
                .map(q -> q.getActionBoxLabel(maxLength))
                .toArray(String[]::new);
    }

    private String[] buildMessageItems() {
        if (messages.isEmpty()) {
            return new String[]{"No messages in queue."};
        }
        return messages.stream()
                .map(QueueMessage::getPreview)
                .toArray(String[]::new);
    }

    private String[] buildQmanagerItems(List<Configuration> configs) {
        return configs.stream()
                .map(c -> (c.getQmanager().equals(currentConfiguration.getQmanager()) ? "* " : "  ")
                        + c.getQmanager())
                .toArray(String[]::new);
    }

    private String[] buildMessageDetailLines(QueueMessage msg) {
        List<String> lines = new ArrayList<>();
        lines.add("Queue:      " + (viewedQueue != null ? viewedQueue.getName() : ""));
        lines.add("Message ID: " + msg.getMessageId());
        lines.add("");
        lines.add("─── Body ──────────────────────────────────────────────────────────────");
        String body = msg.getBody();
        if (body == null || body.isEmpty()) {
            lines.add("<empty body>");
        } else {
            for (String line : body.split("\n", -1)) {
                lines.add(line);
            }
        }
        return lines.toArray(new String[0]);
    }

    // ── Label helpers ────────────────────────────────────────────────────────

    private static int getLongestQueueName(List<Queue> queues) {
        int longest = 0;
        for (Queue q : queues) {
            if (q.getName().length() > longest) {
                longest = q.getName().length();
            }
        }
        return longest;
    }

    private static String getLabel(int maxLength) {
        if (maxLength == 0) {
            maxLength = 30;
        }
        return StringUtils.rightPad("Name", maxLength) + "    Depth";
    }

    // ── Entry point ──────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        new QC().run();
    }
}

