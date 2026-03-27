package com.niklasottosson.QueueCommander;

import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.terminal.swing.AWTTerminalFontConfiguration;
import com.niklasottosson.QueueCommander.model.Configuration;
import com.niklasottosson.QueueCommander.model.Queue;
import com.niklasottosson.QueueCommander.model.QueueMessage;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import com.niklasottosson.QueueCommander.view.MainPanel;
import org.apache.commons.lang3.StringUtils;

import java.awt.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static java.lang.Thread.sleep;

/**
 * Created by malen on 2019-02-18.
 *
 * Docs
 * https://github.com/mabe02/lanterna/blob/master/docs/contents.md
 *
 */
public class QC {
    private static final int MESSAGE_PREVIEW_LIMIT = 200;

    private static Terminal terminal;
    private static Screen screen;
    private static Window window;
    private static WindowBasedTextGUI gui;
    private static Panel queuePanel;
    private static int rows;
    private static int columns;
    private static ActionListBox aBbox;
    private static ActionListBox rightABbox;
    private static Label label;
    private static Label queueManagerLabel;
    private static ActiveMQ queueManager;

    public static <leftQueueManagerLabel> void main(String[] args) throws IOException {
        //Configuration configuration = new Configuration("localhost", 1414, "QM1", "DEV.ADMIN.SVRCONN", "admin", "passw0rd");
        Configuration configuration = new Configuration("192.168.0.105", 1414, "QM1", "DEV.ADMIN.SVRCONN", "admin", "passw0rd");

        queueManager = new ActiveMQ(configuration);

        // Setup terminal and screen layers
        try {
            DefaultTerminalFactory factory = new DefaultTerminalFactory();
            factory.setTerminalEmulatorFontConfiguration(
                AWTTerminalFontConfiguration.newInstance(
                    new Font("Monospaced", Font.PLAIN, 12) // Doed not work in IntelliJ (depends on the terminal app used)
                )
            );
            //factory.setInitialTerminalSize(new TerminalSize(120, 40)); // columns x rows

            terminal = factory.createTerminal();
            // Create screen
            screen = new TerminalScreen(terminal);
            gui = new MultiWindowTextGUI(screen);

            aBbox = new ActionListBox();
            aBbox.addItem("--- Queues ---", null);

            screen.startScreen();
            screen.refresh();

            // Get current size
            rows = screen.getTerminalSize().getRows();
            columns = screen.getTerminalSize().getColumns();

            // Create window to hold components
            window = new BasicWindow();

            // Setup main panel
            Panel mainPanel = new MainPanel().init(columns, rows);

            // Setup left panel
            queuePanel = new Panel();
            queuePanel.setPreferredSize(new TerminalSize(columns - 8, rows - 13)); // full width, fill above legend
            queueManagerLabel = new Label("Queue manager: " + configuration.getQmanager());
            label = new Label(getLabel(0));
            queuePanel.addComponent(queueManagerLabel);
            queuePanel.addComponent(label);
            queuePanel.addComponent(aBbox);

            mainPanel.addComponent(queuePanel.withBorder(Borders.singleLine()));

            // Add legend
            Panel legend = new Panel();
            Label keysLabel = new Label("Q = Queue manager dialog  R = refresh table  ESC = quit program");
            legend.addComponent(keysLabel);
            mainPanel.addComponent(legend.withBorder(Borders.singleLine("Legend")));

            // Add everything to window
            window.setComponent(mainPanel.withBorder(Borders.singleLine("Queue Commander")));

            // Add gui listener
            gui.addListener(new TextGUI.Listener() {
                @Override
                public boolean onUnhandledKeyStroke(TextGUI textGUI, KeyStroke key) {
                if(key.getKeyType() == KeyType.Escape) {
                    Window activeWindow = gui.getActiveWindow();
                    if (activeWindow != null && activeWindow != window) {
                        activeWindow.close();
                        return true;
                    }
                    try {
                        screen.stopScreen();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return true;
                }
                if(key.getCharacter() == Character.valueOf('r') || key.getCharacter() == Character.valueOf('R')) {
                    System.out.println("Update");
                    update();
                }
                if(key.getCharacter() == Character.valueOf('q') || key.getCharacter() == Character.valueOf('Q')) {
                    System.out.println("Qmanager");
                }

                return false;
                }
            });

            // Add window to gui and wait
            gui.addWindowAndWait(window);



        } catch (IOException e) {
            e.printStackTrace();
        }


        try {
            sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //gui.shutdown();
        screen.stopScreen();

    }

    public static void update() {
        if(queueManager.connect()){
            //System.out.println("Everything is ok");
        }
        else {
            //System.out.println("Everything is bad");
        }

        List<Queue> queues = queueManager.getQueueList();

        System.out.println("Number of queues found: " + queues.size());


        // Remove old items
        aBbox.clearItems();
        aBbox.clearItems();

        int maxLength = getLongestQueueName(queues);

        label.setText(getLabel(maxLength));

        for(Queue queue: queues){
            aBbox.addItem(queue.getActionBoxLabel(maxLength), () -> openQueueMessagesView(queue));
        }

        if(queueManager.disconnect()){
            //System.out.println("Disconnect successfull");
        }
        else {
            //System.out.println("Disconnect unsuccessfull");
        }

    }

    public static int getLongestQueueName(List<Queue> queues){
        int longest = 0;
        for(Queue q: queues){
            if(longest < q.getName().length()){
                longest = q.getName().length();
            }
        }

        return longest;
    }

    public static String getLabel(int maxLength){
        String label = "Name";

        // At init we do not know the max length
        if(maxLength == 0){
            maxLength = 30;
        }

        label = StringUtils.rightPad(label, maxLength);
        label += "    Depth";

        return label;
    }

    private static void openQueueMessagesView(Queue queue) {
        BasicWindow messagesWindow = new BasicWindow("Messages: " + queue.getName());
        messagesWindow.setHints(Arrays.asList(Window.Hint.MODAL, Window.Hint.CENTERED));

        Panel content = new Panel(new LinearLayout(Direction.VERTICAL));
        content.addComponent(new Label("Queue: " + queue.getName()));
        content.addComponent(new EmptySpace(new TerminalSize(1, 1)));

        int messageListWidth = Math.max(60, columns - 16);
        int messageListHeight = Math.max(12, rows - 12);
        ActionListBox messageList = new ActionListBox(new TerminalSize(messageListWidth, messageListHeight));

        List<QueueMessage> messages = queueManager.getQueueMessageDetails(queue.getName(), MESSAGE_PREVIEW_LIMIT);
        for (QueueMessage message : messages) {
            if (message.isOpenable()) {
                messageList.addItem(message.getPreview(), () -> openMessageDetailView(queue, message));
            } else {
                messageList.addItem(message.getPreview(), null);
            }
        }

        content.addComponent(messageList.withBorder(Borders.singleLine("Messages")));
        content.addComponent(new com.googlecode.lanterna.gui2.Button("Close", messagesWindow::close));

        messagesWindow.setComponent(content);
        gui.addWindowAndWait(messagesWindow);
    }

    private static void openMessageDetailView(Queue queue, QueueMessage message) {
        BasicWindow messageWindow = new BasicWindow("Message Details");
        messageWindow.setHints(Arrays.asList(Window.Hint.MODAL, Window.Hint.CENTERED));

        Panel content = new Panel(new LinearLayout(Direction.VERTICAL));
        content.addComponent(new Label("Queue: " + queue.getName()));
        content.addComponent(new Label("Message ID:"));

        TextBox idBox = new TextBox(new TerminalSize(Math.max(60, columns - 20), 3), message.getMessageId(), TextBox.Style.MULTI_LINE);
        idBox.setReadOnly(true);
        content.addComponent(idBox.withBorder(Borders.singleLine()));

        content.addComponent(new Label("Body:"));
        TextBox bodyBox = new TextBox(new TerminalSize(Math.max(60, columns - 20), Math.max(10, rows - 16)), message.getBody(), TextBox.Style.MULTI_LINE);
        bodyBox.setReadOnly(true);
        content.addComponent(bodyBox.withBorder(Borders.singleLine()));

        content.addComponent(new com.googlecode.lanterna.gui2.Button("Close", messageWindow::close));
        messageWindow.setComponent(content);
        gui.addWindowAndWait(messageWindow);
    }

}

/*
//Timer function
class SayHello extends TimerTask {
    public void run() {
       System.out.println("Hello World!");
    }
}

// And From your main() method or any other method
Timer timer = new Timer();
timer.schedule(new SayHello(), 0, 5000);
-----------------------------
public class RemindTask extends TimerTask {
    public void run() {
      System.out.println(" Hello World!");
    }
    public static void main(String[] args){
       Timer timer = new Timer();
       timer.schedule(new RemindTask(), 3000,3000);
    }
  }



 */
