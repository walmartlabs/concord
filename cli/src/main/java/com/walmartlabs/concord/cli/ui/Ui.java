package com.walmartlabs.concord.cli.ui;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.LinearLayout.Alignment;
import com.googlecode.lanterna.gui2.LinearLayout.GrowPolicy;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Ui {

    private final Panel rootPanel;
    private final List<UiLogPanel> logPanels = new ArrayList<>();

    public Ui() {
        this.rootPanel = new Panel(new LinearLayout(Direction.VERTICAL));
        rootPanel.setLayoutData(LinearLayout.createLayoutData(Alignment.Fill, GrowPolicy.CanGrow));
        onCreateLogSegment(0, "system");
    }

    public void run() throws Exception {
        try (Terminal terminal = new DefaultTerminalFactory().createTerminal()) {
            Screen screen = new TerminalScreen(terminal);
            screen.startScreen();

            BasicWindow window = new BasicWindow("Running process...");
            window.setHints(List.of(Window.Hint.FULL_SCREEN));
            window.setComponent(rootPanel);

            MultiWindowTextGUI gui = new MultiWindowTextGUI(screen, new DefaultWindowManager(), new EmptySpace(TextColor.ANSI.BLUE));
            gui.addWindowAndWait(window);
        }
    }

    public void onCreateLogSegment(long segmentId, String name) {
        UiLogPanel panel = getOrCreateLogPanel(segmentId);
        synchronized (panel) {
            panel.setName(name);
        }
    }

    public void appendLog(long segmentId, byte[] ab) {
        UiLogPanel panel = getOrCreateLogPanel(segmentId);
        var line = new String(ab, UTF_8);
        synchronized (panel) {
            panel.appendLog(line);
        }
    }

    private UiLogPanel getOrCreateLogPanel(long segmentId) {
        return logPanels.stream()
                .filter(p -> p.getSegmentId() == segmentId).findFirst()
                .orElseGet(() -> {
                    var panel = new UiLogPanel(segmentId);
                    synchronized (this) {
                        logPanels.add(panel);
                        rootPanel.addComponent(panel);
                    }
                    return panel;
                });
    }
}
