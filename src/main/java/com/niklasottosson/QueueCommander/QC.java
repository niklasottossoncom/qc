package com.niklasottosson.QueueCommander;

import com.niklasottosson.QueueCommander.model.Queue;
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

import java.io.IOException;
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
    private static Terminal terminal;
    private static Screen screen;
    private static Window window;
    private static WindowBasedTextGUI gui;
    private static Panel leftQueuePanel;
    private static Panel rightQueuePanel;
    private static int rows;
    private static int columns;
    private static ActionListBox leftABbox;
    private static ActionListBox rightABbox;
    private static Label leftLabel;
    private static Label leftQueueManagerLabel;
    private static Label rightLabel;
    private static IBMMQ leftQueueManager;
    private static IBMMQ rightQueueManager;
    private static Configuration leftConfiguration;
    private static Configuration rightConfiguration;

    public static <leftQueueManagerLabel> void main(String[] args) throws IOException {
        //Configuration configuration = new Configuration("localhost", 1414, "QM1", "DEV.ADMIN.SVRCONN", "admin", "passw0rd");
        Configuration leftConfiguration = new Configuration("192.168.0.105", 1414, "QM1", "DEV.ADMIN.SVRCONN", "admin", "passw0rd");

        leftQueueManager = new IBMMQ(leftConfiguration);
        //rightQueueManager = new IBMMQ(rightConfiguration);
        //GUI gui = new GUI();

        // Setup terminal and screen layers
        try {
            terminal = new DefaultTerminalFactory().createTerminal();
            // Create screen
            screen = new TerminalScreen(terminal);
            gui = new MultiWindowTextGUI(screen);

            leftABbox = new ActionListBox();
            leftABbox.addItem("--- Queues ---", null);
            rightABbox = new ActionListBox();
            rightABbox.addItem("--- Queues ---", null);

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
            leftQueuePanel = new Panel();
            leftQueuePanel.setPreferredSize(new TerminalSize(columns/2 - 6, rows - 10));
            leftQueueManagerLabel = new Label("Queue manager: " + leftConfiguration.getQmanager());
            leftLabel = new Label(getLabel(0));
            leftQueuePanel.addComponent(leftQueueManagerLabel);
            leftQueuePanel.addComponent(leftLabel);
            leftQueuePanel.addComponent(leftABbox);

            mainPanel.addComponent(leftQueuePanel.withBorder(Borders.singleLine()));


            // Setup right panel
            rightQueuePanel = new Panel();
            rightQueuePanel.setPreferredSize(new TerminalSize(columns/2 - 6, rows - 10));
            rightLabel = new Label(getLabel(0));
            rightQueuePanel.addComponent(rightLabel);
            rightQueuePanel.addComponent(rightABbox);

            mainPanel.addComponent(rightQueuePanel.withBorder(Borders.singleLine()));

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
                    try {
                        screen.stopScreen();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return true;
                }
                if(key.getCharacter() == Character.valueOf('r') || key.getCharacter() == Character.valueOf('R')) {
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

    public static void update(){
        if(leftQueueManager.connect()){
            //System.out.println("Everything is ok");
        }
        else {
            //System.out.println("Everything is bad");
        }

        List<Queue> queues = leftQueueManager.getQueueList();

        //System.out.println("Number of queues found: " + queues.size());


        // Remove old items
        leftABbox.clearItems();
        leftABbox.clearItems();

        int maxLength = getLongestQueueName(queues);

        leftLabel.setText(getLabel(maxLength));

        for(Queue queue: queues){
            leftABbox.addItem(queue.getActionBoxLabel(maxLength), null);
        }

        if(leftQueueManager.disconnect()){
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
            maxLength = 35;
        }

        label = StringUtils.rightPad(label, maxLength);
        label += " Depth           PutDate               GetDate";

        return label;
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
