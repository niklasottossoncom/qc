package com.niklasottosson.QueueCommander;

import com.niklasottosson.QueueCommander.model.Queue;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.List;

public class GUI {
    private Terminal terminal;
    private Screen screen;
    private Window window;
    private WindowBasedTextGUI gui;
    private Panel mainPanel;
    private Panel leftQueuePanel;
    private Panel rightQueuePanel;
    private int rows;
    private int columns;
    private ActionListBox leftABbox;
    private ActionListBox rightABbox;
    private Label leftLabel;
    private Label rightLabel;


    public GUI(){

    }

    public void init(){
        // Setup terminal and screen layers
        try {
            terminal = new DefaultTerminalFactory().createTerminal();
            // Create screen
            screen = new TerminalScreen(terminal);
            gui = new MultiWindowTextGUI(screen);
            screen.startScreen();
            screen.refresh();

            // Get current size
            rows = screen.getTerminalSize().getRows();
            columns = screen.getTerminalSize().getColumns();

            // Create panel to hold components
            window = new BasicWindow();

            // Setup main panel
            mainPanel = new Panel();
            mainPanel.setLayoutManager(new GridLayout(2));
            mainPanel.setPreferredSize(new TerminalSize(columns - 6, rows - 6));

            // Setup left panel
            leftQueuePanel = new Panel();
            leftQueuePanel.setPreferredSize(new TerminalSize(columns/2 - 6, rows - 10));
            leftLabel = new Label(this.getLabel(0));
            leftQueuePanel.addComponent(leftLabel);
            leftQueuePanel.addComponent(leftABbox);

            mainPanel.addComponent(leftQueuePanel.withBorder(Borders.singleLine("Left Panel")));

            // Setup right panel
            Button btn = new Button("Update", new Runnable() {
                public void run() {
                    update();
                }
            });

            rightQueuePanel = new Panel();
            rightQueuePanel.setPreferredSize(new TerminalSize(columns/2 - 6, rows - 10));
            rightLabel = new Label(this.getLabel(0));
            rightQueuePanel.addComponent(rightLabel);
            rightQueuePanel.addComponent(rightABbox);

            rightQueuePanel.addComponent(btn);

            mainPanel.addComponent(rightQueuePanel.withBorder(Borders.singleLine("Right Panel")));

            // Add legend
            Panel legend = new Panel();
            Label keysLabel = new Label("Q = Queue manager dialog  R = refresh table  ESC = quit program");
            legend.addComponent(keysLabel);
            mainPanel.addComponent(legend.withBorder(Borders.singleLine("Legend")));

            // Add everything to window
            window.setComponent(mainPanel.withBorder(Borders.singleLine("Queue Commander")));

            // Add window to gui and wait
            gui.addWindowAndWait(window);
            //this.gui.addWindow(window);

        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    public void update(){
        IBMMQ mq = new IBMMQ();

        if(mq.connect()){
            //System.out.println("Everything is ok");
        }
        else {
            //System.out.println("Everything is bad");
        }

        List<Queue> queues = mq.getQueueList();

        //System.out.println("Number of queues found: " + queues.size());


        // Remove old items
        leftABbox.clearItems();
        leftABbox.clearItems();

        int maxLength = getLongestQueueName(queues);

        leftLabel.setText(getLabel(maxLength));

        for(Queue queue: queues){
            this.leftABbox.addItem(queue.getActionBoxLabel(maxLength), null);
        }

        if(mq.disconnect()){
            //System.out.println("Disconnect successfull");
        }
        else {
            //System.out.println("Disconnect unsuccessfull");
        }

    }

    public void shutdown(){
        try {
            screen.stopScreen();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getLongestQueueName(List<Queue> queues){
        int longest = 0;
        for(Queue q: queues){
            if(longest < q.getName().length()){
                longest = q.getName().length();
            }
        }

        return longest;
    }

    public String getLabel(int maxLength){
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
