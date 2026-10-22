package com.zombiesurvival.client.ui;

import com.zombiesurvival.client.GameClient;
import com.zombiesurvival.shared.ChatMessage;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;

/**
 * Right-side chat panel:
 * - Restyled header with custom Graphics2D chat bubble icon and horror font
 * - Dark custom scrollbar (DarkScrollBarUI)
 * - Restyled dark input field with toxic-green focus glow
 * - Custom send button with Graphics2D paper airplane / send icon
 */
public class ChatPanel extends JPanel {

    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final JList<String>  list;
    private final JTextField     input;
    private final JButton        sendBtn;
    private final GameClient     client;

    public ChatPanel(GameClient client) {
        this.client = client;
        setPreferredSize(new Dimension(250, 0));
        setBackground(UITheme.COLOR_BG_DARK);
        setLayout(new BorderLayout(0, 0));
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, UITheme.COLOR_PANEL_BORDER));

        // 1. Custom Header Panel with Vector Chat Bubble Icon
        JPanel headerPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int w = getWidth(), h = getHeight();
                GradientPaint gp = new GradientPaint(0, 0, new Color(18, 22, 32), 0, h, new Color(12, 14, 20));
                g2.setPaint(gp);
                g2.fillRect(0, 0, w, h);

                // Bottom accent line
                g2.setColor(new Color(45, 55, 75));
                g2.fillRect(0, h - 1, w, 1);
                g2.setColor(new Color(55, 185, 65, 50));
                g2.fillRect(0, h - 2, w, 1);

                // Vector Chat Bubble Icon (Replacing missing glyph emoji)
                UITheme.drawChatBubbleIcon(g2, 12, (h - 14) / 2, 18, 14, UITheme.COLOR_TOXIC_GREEN_BRT);

                // Title Text with Shadow
                g2.setFont(UITheme.FONT_CHAT_HEADER);
                g2.setColor(Color.BLACK);
                g2.drawString("CHAT", 37, 24);
                g2.setColor(UITheme.COLOR_TEXT_MAIN);
                g2.drawString("CHAT", 36, 23);
            }
        };
        headerPanel.setPreferredSize(new Dimension(250, 36));
        add(headerPanel, BorderLayout.NORTH);

        // 2. Message History List
        list = new JList<>(model);
        list.setBackground(UITheme.COLOR_BG_DARK);
        list.setForeground(UITheme.COLOR_TEXT_MAIN);
        list.setFont(UITheme.FONT_CHAT_MSG);
        list.setSelectionBackground(new Color(25, 30, 42));
        list.setCellRenderer(new ChatCellRenderer());
        list.setBorder(new EmptyBorder(6, 6, 6, 6));

        JScrollPane scrollPane = new JScrollPane(list);
        scrollPane.setBackground(UITheme.COLOR_BG_DARK);
        scrollPane.getViewport().setBackground(UITheme.COLOR_BG_DARK);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        // Install Dark Custom Scrollbar
        JScrollBar verticalBar = scrollPane.getVerticalScrollBar();
        verticalBar.setUI(new UITheme.DarkScrollBarUI());
        verticalBar.setPreferredSize(new Dimension(8, 0));
        verticalBar.setBackground(UITheme.COLOR_BG_DARK);
        add(scrollPane, BorderLayout.CENTER);

        // 3. Input Panel (South)
        JPanel inputPanel = new JPanel(new BorderLayout(6, 0));
        inputPanel.setBackground(new Color(14, 16, 24));
        inputPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        input = new JTextField();
        input.setBackground(new Color(22, 25, 36));
        input.setForeground(UITheme.COLOR_TEXT_MAIN);
        input.setCaretColor(UITheme.COLOR_TOXIC_GREEN_BRT);
        input.setFont(UITheme.FONT_CHAT_MSG);

        // Styled Border with Dynamic Toxic-Green Focus Glow
        input.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UITheme.COLOR_PANEL_BORDER, 1),
            new EmptyBorder(6, 8, 6, 8)
        ));
        input.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                input.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UITheme.COLOR_TOXIC_GREEN_BRT, 1),
                    new EmptyBorder(6, 8, 6, 8)
                ));
            }

            @Override
            public void focusLost(FocusEvent e) {
                input.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UITheme.COLOR_PANEL_BORDER, 1),
                    new EmptyBorder(6, 8, 6, 8)
                ));
            }
        });
        input.addActionListener(e -> sendChat());

        // Custom Vector Send Button (Replacing missing glyph "▶")
        sendBtn = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth(), h = getHeight();
                boolean pressed = getModel().isPressed();
                boolean rollover = getModel().isRollover();

                // Button Fill
                Color top = pressed ? new Color(20, 60, 25) : (rollover ? new Color(45, 145, 60) : new Color(32, 105, 45));
                Color bot = pressed ? new Color(12, 40, 16) : (rollover ? new Color(30, 95, 40) : new Color(20, 68, 30));
                g2.setPaint(new GradientPaint(0, 0, top, 0, h, bot));
                g2.fillRoundRect(0, 0, w, h, 8, 8);

                // Border
                g2.setColor(rollover ? UITheme.COLOR_TOXIC_GREEN_BRT : new Color(45, 120, 55));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);

                // Paper Airplane / Send Icon
                int iconSize = 13;
                int ix = (w - iconSize) / 2 + (pressed ? 1 : 0);
                int iy = (h - iconSize) / 2 + (pressed ? 1 : 0);
                UITheme.drawSendIcon(g2, ix, iy, iconSize, Color.WHITE);
            }
        };
        sendBtn.setPreferredSize(new Dimension(38, 32));
        sendBtn.setContentAreaFilled(false);
        sendBtn.setBorderPainted(false);
        sendBtn.setFocusPainted(false);
        sendBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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
        SwingUtilities.getWindowAncestor(this).requestFocusInWindow();
    }

    public void addMessage(String text) {
        SwingUtilities.invokeLater(() -> {
            model.addElement(text);
            list.ensureIndexIsVisible(model.size() - 1);
        });
    }

    // Custom Cell Renderer with Dark Apocalyptic Colors
    private static class ChatCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                       int idx, boolean sel, boolean focus) {
            JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, idx, sel, focus);
            String text = value.toString();
            lbl.setBackground(UITheme.COLOR_BG_DARK);
            lbl.setFont(UITheme.FONT_CHAT_MSG);
            lbl.setBorder(new EmptyBorder(2, 4, 2, 4));

            if (text.startsWith("**") || text.startsWith("===")) {
                lbl.setForeground(UITheme.COLOR_GOLD_BRT);
            } else if (text.startsWith("[") && text.contains("]:")) {
                lbl.setForeground(new Color(110, 195, 255));
            } else {
                lbl.setForeground(UITheme.COLOR_TEXT_MAIN);
            }

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
