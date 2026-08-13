package com.zombiesurvival.client.ui;

import com.zombiesurvival.client.GameClient;
import com.zombiesurvival.shared.ChatMessage;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;

/**
 * Right-side chat panel: scrolling message history + text input.
 */
public class ChatPanel extends JPanel {

    private static final Color BG_DARK   = new Color(12, 12, 18);
    private static final Color BG_INPUT  = new Color(22, 22, 32);
    private static final Color FG_TEXT   = new Color(200, 200, 210);
    private static final Color FG_TITLE  = new Color(140, 200, 140);
    private static final Color BORDER_C  = new Color(50, 55, 70);
    private static final Font  FONT_MSG  = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font  FONT_TITLE= new Font("Segoe UI", Font.BOLD, 13);

    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final JList<String>  list;
    private final JTextField     input;
    private final GameClient     client;

    public ChatPanel(GameClient client) {
        this.client = client;
        setPreferredSize(new Dimension(240, 0));
        setBackground(BG_DARK);
        setLayout(new BorderLayout(0, 0));
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, BORDER_C));

        // Title
        JLabel title = new JLabel("  💬 CHAT", SwingConstants.LEFT);
        title.setFont(FONT_TITLE);
        title.setForeground(FG_TITLE);
        title.setBackground(new Color(18, 22, 30));
        title.setOpaque(true);
        title.setBorder(new EmptyBorder(8, 8, 8, 8));
        title.setPreferredSize(new Dimension(240, 34));
        add(title, BorderLayout.NORTH);

        // Message list
        list = new JList<>(model);
        list.setBackground(BG_DARK);
        list.setForeground(FG_TEXT);
        list.setFont(FONT_MSG);
        list.setSelectionBackground(BG_DARK);
        list.setCellRenderer(new ChatCellRenderer());
        list.setBorder(new EmptyBorder(4, 4, 4, 4));

        JScrollPane scrollPane = new JScrollPane(list);
        scrollPane.setBackground(BG_DARK);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setBackground(BG_DARK);
        add(scrollPane, BorderLayout.CENTER);

        // Input area
        JPanel inputPanel = new JPanel(new BorderLayout(4, 0));
        inputPanel.setBackground(BG_INPUT);
        inputPanel.setBorder(new EmptyBorder(6, 6, 6, 6));

        input = new JTextField();
        input.setBackground(new Color(30, 32, 45));
        input.setForeground(FG_TEXT);
        input.setCaretColor(FG_TEXT);
        input.setFont(FONT_MSG);
        input.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_C, 1),
            new EmptyBorder(4, 6, 4, 6)));
        input.addActionListener(e -> sendChat());

        JButton sendBtn = new JButton("▶");
        sendBtn.setBackground(new Color(50, 120, 60));
        sendBtn.setForeground(Color.WHITE);
        sendBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        sendBtn.setBorder(new EmptyBorder(5, 8, 5, 8));
        sendBtn.setFocusPainted(false);
        sendBtn.addActionListener(e -> sendChat());

        inputPanel.add(input, BorderLayout.CENTER);
        inputPanel.add(sendBtn, BorderLayout.EAST);
        add(inputPanel, BorderLayout.SOUTH);
    }

    private void sendChat() {
        String text = input.getText().trim();
        if (!text.isEmpty()) {
            client.sendMessage(new ChatMessage("", text));
            input.setText("");
        }
        // Return keyboard focus to game window
        SwingUtilities.getWindowAncestor(this).requestFocusInWindow();
    }

    /** Add a line to the chat history (call from EDT). */
    public void addMessage(String text) {
        SwingUtilities.invokeLater(() -> {
            model.addElement(text);
            list.ensureIndexIsVisible(model.size() - 1);
        });
    }

    // ── Custom cell renderer ──────────────────────────────────────────────────

    private static class ChatCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                       int idx, boolean sel, boolean focus) {
            JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, idx, sel, focus);
            String text = value.toString();
            lbl.setBackground(BG_DARK);
            lbl.setFont(FONT_MSG);
            lbl.setBorder(new EmptyBorder(1, 2, 1, 2));
            // Color-code by content
            if (text.startsWith("**") || text.startsWith("===")) {
                lbl.setForeground(new Color(220, 180, 50));
            } else if (text.startsWith("[") && text.contains("]:")) {
                lbl.setForeground(new Color(120, 200, 255));
            } else {
                lbl.setForeground(FG_TEXT);
            }
            // Wrap long lines manually (JList doesn't wrap)
            if (text.length() > 28) {
                String wrapped = "<html>" + text.replace("&", "&amp;")
                                                 .replace("<", "&lt;")
                                                 .replace(">", "&gt;") + "</html>";
                lbl.setText(wrapped);
            }
            return lbl;
        }
    }
}
