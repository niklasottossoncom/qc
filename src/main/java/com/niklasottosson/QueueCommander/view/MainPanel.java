package com.niklasottosson.QueueCommander.view;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.Panel;

public class MainPanel {
    Panel basePanel;

    public MainPanel() {
        basePanel = new Panel();
    }

    public Panel init(int columns, int rows) {
        basePanel.setLayoutManager(new LinearLayout(Direction.VERTICAL));
        basePanel.setPreferredSize(new TerminalSize(columns - 6, rows - 6));

        return basePanel;
    }
}
