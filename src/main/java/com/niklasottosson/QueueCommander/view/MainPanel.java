package com.niklasottosson.QueueCommander.view;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.GridLayout;
import com.googlecode.lanterna.gui2.Panel;

public class MainPanel {
    Panel basePanel;

    public MainPanel() {
        basePanel = new Panel();
    }

    public Panel init(int columns, int rows) {
        basePanel.setLayoutManager(new GridLayout(2));
        basePanel.setPreferredSize(new TerminalSize(columns - 6, rows - 6));

        return basePanel;
    }
}
