package com.zombiesurvival.client.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.geom.*;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Pre-lobby menu screen.
 * Provides:
 *   1. "ENTER THE LOBBY" (Solo AI Mode - starts immediately with AI zombies)
 *   2. "INVITE MEMBERS"  (Multiplayer Host - shows IP to share with other systems)
 *   3. "JOIN A GAME"     (Client Connect - connects to a friend's hosted IP)
 */
public class MainMenuScreen extends JPanel {

    public enum ViewState {
        MAIN_CHOICE,   // Two main buttons: Enter Lobby vs Invite Members
        INVITE_LOBBY,  // Displays IP address, connection info, connected players, Start button
    }

    private static final Color DARK_BG       = new Color(10, 10, 16);
    private static final Color BLOOD_RED      = new Color(185, 25, 25);
    private static final Color BLOOD_RED_BRT  = new Color(230, 40, 40);
    private static final Color ZOMBIE_GREEN   = new Color(68, 140, 52);
    private static final Color ZOMBIE_EYE     = new Color(255, 30, 30);
    private static final Color GOLD_ACCENT    = new Color(245, 195, 45);
    private static final Color TEXT_GRAY      = new Color(175, 180, 195);

    private ViewState currentView = ViewState.MAIN_CHOICE;
    private int selectedIndex = 0;
    private float pulseAngle = 0f;
    private Timer animTimer;

    private String localIpAddress = "127.0.0.1";
    private String playerName = System.getProperty("user.name", "Player");
    private final List<String> connectedPlayers = new ArrayList<>();
    private String copiedNotice = "";
    private long copiedNoticeTimer = 0;

    // Interactive button bounds
    private final List<Rectangle> buttonBounds = new ArrayList<>();
    private int hoveredButton = -1;

    public interface MenuActionListener {
        void onEnterLobbySolo();
        void onInviteMembers();
        void onJoinGame();
        void onStartMultiplayerGame();
        void onBackToMain();
    }

    private final MenuActionListener listener;

    public MainMenuScreen(MenuActionListener listener) {
        this.listener = listener;
        setPreferredSize(new Dimension(800, 600));
        setBackground(DARK_BG);
        setFocusable(true);

        detectLocalIp();

        // 60 FPS animation timer
        animTimer = new Timer(16, e -> {
            pulseAngle += 0.05f;
            if (copiedNoticeTimer > 0 && System.currentTimeMillis() > copiedNoticeTimer) {
                copiedNotice = "";
                copiedNoticeTimer = 0;
            }
            repaint();
        });
        animTimer.start();

        // Keyboard navigation
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKeyPress(e.getKeyCode());
            }
        });

        // Mouse motion & click handling
        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                Point p = e.getPoint();
                int prev = hoveredButton;
                hoveredButton = -1;
                for (int i = 0; i < buttonBounds.size(); i++) {
                    if (buttonBounds.get(i).contains(p)) {
                        hoveredButton = i;
                        selectedIndex = i;
                        break;
                    }
                }
                if (prev != hoveredButton) repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                Point p = e.getPoint();
                for (int i = 0; i < buttonBounds.size(); i++) {
                    if (buttonBounds.get(i).contains(p)) {
                        triggerButtonAction(i);
                        break;
                    }
                }
            }
        };
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
    }

    private void handleKeyPress(int keyCode) {
        int maxButtons = (currentView == ViewState.MAIN_CHOICE) ? 3 : 3;
        if (keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_W) {
            selectedIndex = (selectedIndex - 1 + maxButtons) % maxButtons;
            repaint();
        } else if (keyCode == KeyEvent.VK_DOWN || keyCode == KeyEvent.VK_S) {
            selectedIndex = (selectedIndex + 1) % maxButtons;
            repaint();
        } else if (keyCode == KeyEvent.VK_ENTER || keyCode == KeyEvent.VK_SPACE) {
            triggerButtonAction(selectedIndex);
        } else if (keyCode == KeyEvent.VK_ESCAPE) {
            if (currentView == ViewState.INVITE_LOBBY) {
                setView(ViewState.MAIN_CHOICE);
                if (listener != null) listener.onBackToMain();
            } else {
                System.exit(0);
            }
        }
    }

    private void triggerButtonAction(int index) {
        if (currentView == ViewState.MAIN_CHOICE) {
            switch (index) {
                case 0 -> {
                    if (listener != null) listener.onEnterLobbySolo();
                }
                case 1 -> {
                    if (listener != null) listener.onInviteMembers();
                }
                case 2 -> {
                    if (listener != null) listener.onJoinGame();
                }
            }
        } else if (currentView == ViewState.INVITE_LOBBY) {
            switch (index) {
                case 0 -> copyIpToClipboard();
                case 1 -> {
                    if (listener != null) listener.onStartMultiplayerGame();
                }
                case 2 -> {
                    setView(ViewState.MAIN_CHOICE);
                    if (listener != null) listener.onBackToMain();
                }
            }
        }
    }

    public void setView(ViewState view) {
        this.currentView = view;
        this.selectedIndex = 0;
        this.hoveredButton = -1;
        repaint();
    }

    public ViewState getView() {
        return currentView;
    }

    public void setPlayerName(String name) {
        this.playerName = name;
        repaint();
    }

    public void setConnectedPlayers(List<String> players) {
        this.connectedPlayers.clear();
        if (players != null) {
            this.connectedPlayers.addAll(players);
        }
        repaint();
    }

    public String getLocalIpAddress() {
        return localIpAddress;
    }

    private void detectLocalIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                        String host = addr.getHostAddress();
                        if (!host.startsWith("127.")) {
                            this.localIpAddress = host;
                            return;
                        }
                    }
                }
            }
            this.localIpAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            this.localIpAddress = "127.0.0.1";
        }
    }

    private void copyIpToClipboard() {
        try {
            String textToCopy = localIpAddress + ":8080";
            StringSelection sel = new StringSelection(textToCopy);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
            copiedNotice = "COPIED TO CLIPBOARD!";
            copiedNoticeTimer = System.currentTimeMillis() + 2500;
            repaint();
        } catch (Exception ignored) {}
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // 1. Apocalyptic Background & Fog
        drawBackgroundAtmosphere(g2, w, h);

        // 2. Corner Zombies
        drawZombieSprite(g2, 25, 30, 80, false);
        drawZombieSprite(g2, w - 105, 30, 80, true);
        drawZombieSprite(g2, 25, h - 110, 80, false);
        drawZombieSprite(g2, w - 105, h - 110, 80, true);

        // 3. Title Section
        drawHeaderTitle(g2, w);

        // 4. Content according to currentView
        buttonBounds.clear();
        if (currentView == ViewState.MAIN_CHOICE) {
            drawMainChoiceView(g2, w, h);
        } else {
            drawInviteLobbyView(g2, w, h);
        }

        // 5. Footer info
        drawFooter(g2, w, h);
    }

    private void drawBackgroundAtmosphere(Graphics2D g2, int w, int h) {
        // Dark vignette gradient
        RadialGradientPaint bgGrad = new RadialGradientPaint(
            w / 2f, h / 2f, Math.max(w, h) * 0.75f,
            new float[]{0f, 0.5f, 1f},
            new Color[]{new Color(18, 16, 28), new Color(10, 9, 16), new Color(4, 4, 8)}
        );
        g2.setPaint(bgGrad);
        g2.fillRect(0, 0, w, h);

        // Subtle green toxic fog swirls at bottom
        float fogOffset = (float) Math.sin(pulseAngle * 0.8) * 15f;
        g2.setColor(new Color(35, 75, 25, 22));
        g2.fillOval(-50, (int) (h - 120 + fogOffset), w + 100, 160);
        g2.setColor(new Color(20, 60, 20, 18));
        g2.fillOval(50, (int) (h - 90 - fogOffset), w - 100, 140);
    }

    private void drawZombieSprite(Graphics2D g2, int x, int y, int size, boolean flipX) {
        // Ground shadow
        g2.setColor(new Color(0, 0, 0, 95));
        g2.fillOval(x + 5, y + size - 12, size - 10, 16);

        // Body
        g2.setColor(ZOMBIE_GREEN);
        g2.fillRoundRect(x + 14, y + 26, size - 28, size - 32, 10, 10);

        // Head
        g2.setColor(new Color(55, 115, 42));
        g2.fillOval(x + 18, y + 6, size - 36, size - 36);

        // Pulsing glowing red eyes
        float eyePulse = 0.5f + 0.5f * (float) Math.sin(pulseAngle * 2.5);
        int eyeAlpha = (int) (160 + 95 * eyePulse);
        g2.setColor(new Color(255, 30, 30, eyeAlpha));
        int eyeY = y + 18;
        if (flipX) {
            g2.fillOval(x + 26, eyeY, 7, 7);
            g2.fillOval(x + 44, eyeY, 7, 7);
        } else {
            g2.fillOval(x + 30, eyeY, 7, 7);
            g2.fillOval(x + 48, eyeY, 7, 7);
        }

        // Tattered clothes
        g2.setColor(new Color(45, 52, 65));
        g2.fillRect(x + 20, y + 42, size - 40, 14);

        // Blood stains
        g2.setColor(BLOOD_RED);
        g2.fillRect(x + 26, y + 36, 4, 8);
        g2.fillRect(x + 46, y + 48, 5, 9);
    }

    private void drawHeaderTitle(Graphics2D g2, int w) {
        String title = "ZOMBIE ESCAPE";

        // Pulsing red title glow
        float glowPulse = (float) (Math.sin(pulseAngle) * 3 + 6);
        for (int i = (int) glowPulse; i > 0; i--) {
            g2.setColor(new Color(185, 25, 25, (int)(15 + (glowPulse - i) * 12)));
            Font glowFont = new Font("Impact", Font.BOLD, 68 + i * 2);
            g2.setFont(glowFont);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(title, (w - fm.stringWidth(title)) / 2, 95);
        }

        // Main Title
        g2.setFont(new Font("Impact", Font.BOLD, 68));
        FontMetrics fm = g2.getFontMetrics();
        int tx = (w - fm.stringWidth(title)) / 2;
        int ty = 95;

        // Shadow
        g2.setColor(new Color(20, 2, 2));
        g2.drawString(title, tx + 3, ty + 3);

        // Primary text
        g2.setColor(BLOOD_RED_BRT);
        g2.drawString(title, tx, ty);

        // Dripping blood lines
        g2.setColor(new Color(160, 15, 15));
        for (int i = 0; i < title.length(); i++) {
            if (i % 2 == 0) {
                int cx = tx + fm.stringWidth(title.substring(0, i)) + 12;
                int dripLen = 10 + (int)(Math.sin(pulseAngle + i * 1.2) * 6);
                g2.fillRect(cx, ty + 6, 3, dripLen);
                g2.fillOval(cx - 1, ty + 6 + dripLen - 2, 5, 5);
            }
        }

        // Subtitle
        g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
        g2.setColor(GOLD_ACCENT);
        String sub = "☣ NETWORK MULTIPLAYER & AI SURVIVAL ☣";
        int subW = g2.getFontMetrics().stringWidth(sub);
        g2.drawString(sub, (w - subW) / 2, 126);
    }

    private void drawMainChoiceView(Graphics2D g2, int w, int h) {
        int btnW = 460;
        int btnH = 75;
        int startY = 165;
        int spacing = 95;

        // Button 1: ENTER THE LOBBY
        Rectangle r1 = new Rectangle((w - btnW) / 2, startY, btnW, btnH);
        buttonBounds.add(r1);
        drawStyledCardButton(g2, r1, 0,
            "1. ENTER THE LOBBY",
            "SOLO PLAY • AI GENERATED ZOMBIES ON THE SPOT",
            "Directly launches into the lobby with smart AI zombies!",
            new Color(40, 130, 60), new Color(25, 85, 40));

        // Button 2: INVITE MEMBERS
        Rectangle r2 = new Rectangle((w - btnW) / 2, startY + spacing, btnW, btnH);
        buttonBounds.add(r2);
        drawStyledCardButton(g2, r2, 1,
            "2. INVITE MEMBERS",
            "MULTIPLAYER HOST • DISPLAY IP & CONNECT FRIENDS",
            "Shows your local IP address for friends to join from terminal!",
            new Color(180, 50, 40), new Color(110, 25, 20));

        // Button 3: JOIN A GAME
        Rectangle r3 = new Rectangle((w - btnW) / 2, startY + spacing * 2, btnW, 60);
        buttonBounds.add(r3);
        drawStyledCardButton(g2, r3, 2,
            "3. JOIN A HOSTED GAME",
            "CONNECT AS CLIENT TO A FRIEND'S IP ADDRESS",
            "Enter server IP from another system",
            new Color(45, 95, 170), new Color(25, 55, 110));

        // Guidance banner
        int hintY = startY + spacing * 2 + 85;
        g2.setFont(new Font("Segoe UI", Font.ITALIC, 13));
        g2.setColor(new Color(150, 155, 175));
        String hint1 = "• Alone on this system? Click 'ENTER THE LOBBY' to play with AI zombies immediately!";
        String hint2 = "• Playing with friends? Click 'INVITE MEMBERS' to see your IP & share with them!";
        g2.drawString(hint1, (w - g2.getFontMetrics().stringWidth(hint1)) / 2, hintY);
        g2.drawString(hint2, (w - g2.getFontMetrics().stringWidth(hint2)) / 2, hintY + 22);
    }

    private void drawStyledCardButton(Graphics2D g2, Rectangle r, int idx,
                                      String title, String badge, String subtitle,
                                      Color highlightCol, Color bgAccent) {
        boolean selected = (selectedIndex == idx || hoveredButton == idx);

        // Outer glow on selection
        if (selected) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(pulseAngle * 3f);
            g2.setColor(new Color(highlightCol.getRed(), highlightCol.getGreen(), highlightCol.getBlue(), (int)(60 + 50 * pulse)));
            g2.fillRoundRect(r.x - 5, r.y - 5, r.width + 10, r.height + 10, 18, 18);
        }

        // Background box
        GradientPaint gp = new GradientPaint(
            r.x, r.y,
            selected ? bgAccent.brighter() : new Color(20, 22, 32),
            r.x, r.y + r.height,
            selected ? bgAccent : new Color(14, 15, 22)
        );
        g2.setPaint(gp);
        g2.fillRoundRect(r.x, r.y, r.width, r.height, 14, 14);

        // Border
        g2.setStroke(new BasicStroke(selected ? 2.5f : 1.2f));
        g2.setColor(selected ? highlightCol.brighter() : new Color(55, 60, 75));
        g2.drawRoundRect(r.x, r.y, r.width, r.height, 14, 14);

        // Indicator marker
        if (selected) {
            g2.setColor(highlightCol.brighter());
            int[] xp = { r.x + 14, r.x + 24, r.x + 14 };
            int[] yp = { r.y + r.height / 2 - 8, r.y + r.height / 2, r.y + r.height / 2 + 8 };
            g2.fillPolygon(xp, yp, 3);
        }

        // Title
        g2.setFont(new Font("Segoe UI", Font.BOLD, 19));
        g2.setColor(selected ? Color.WHITE : new Color(225, 230, 240));
        g2.drawString(title, r.x + (selected ? 34 : 26), r.y + 28);

        // Subtitle / description
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        g2.setColor(selected ? new Color(210, 225, 245) : TEXT_GRAY);
        g2.drawString(subtitle, r.x + (selected ? 34 : 26), r.y + 48);

        // Badge pill on right
        if (badge != null && !badge.isEmpty()) {
            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            FontMetrics bfm = g2.getFontMetrics();
            int bw = bfm.stringWidth(badge) + 16;
            int bx = r.x + r.width - bw - 14;
            int by = r.y + 14;
            g2.setColor(new Color(0, 0, 0, 140));
            g2.fillRoundRect(bx, by, bw, 20, 10, 10);
            g2.setColor(selected ? highlightCol.brighter() : highlightCol);
            g2.drawRoundRect(bx, by, bw, 20, 10, 10);
            g2.drawString(badge, bx + 8, by + 14);
        }
    }

    private void drawInviteLobbyView(Graphics2D g2, int w, int h) {
        int panelW = 540;
        int panelX = (w - panelW) / 2;
        int panelY = 150;

        // Main Lobby Container Box
        g2.setColor(new Color(18, 20, 30, 240));
        g2.fillRoundRect(panelX, panelY, panelW, 350, 16, 16);
        g2.setStroke(new BasicStroke(1.8f));
        g2.setColor(new Color(180, 45, 45));
        g2.drawRoundRect(panelX, panelY, panelW, 350, 16, 16);

        // Panel Title
        g2.setFont(new Font("Segoe UI", Font.BOLD, 18));
        g2.setColor(GOLD_ACCENT);
        String header = "☣ MULTIPLAYER LOBBY - INVITE MEMBERS ☣";
        g2.drawString(header, panelX + (panelW - g2.getFontMetrics().stringWidth(header)) / 2, panelY + 32);

        // IP Address Display Box (Glowing)
        int ipBoxW = 460;
        int ipBoxH = 65;
        int ipBoxX = panelX + (panelW - ipBoxW) / 2;
        int ipBoxY = panelY + 48;

        g2.setColor(new Color(8, 10, 16));
        g2.fillRoundRect(ipBoxX, ipBoxY, ipBoxW, ipBoxH, 12, 12);
        g2.setColor(new Color(50, 140, 230));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(ipBoxX, ipBoxY, ipBoxW, ipBoxH, 12, 12);

        // IP Labels
        g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
        g2.setColor(new Color(150, 180, 220));
        g2.drawString("YOUR SYSTEM IP ADDRESS (SHARE WITH PLAYERS):", ipBoxX + 16, ipBoxY + 20);

        String ipString = localIpAddress + " : 8080";
        g2.setFont(new Font("Consolas", Font.BOLD, 22));
        g2.setColor(Color.WHITE);
        g2.drawString(ipString, ipBoxX + 16, ipBoxY + 48);

        // Copy IP Button
        int copyBtnW = 105;
        int copyBtnH = 34;
        Rectangle copyBtn = new Rectangle(ipBoxX + ipBoxW - copyBtnW - 12, ipBoxY + 16, copyBtnW, copyBtnH);
        buttonBounds.add(copyBtn);

        boolean copySel = (selectedIndex == 0 || hoveredButton == 0);
        g2.setColor(copySel ? new Color(30, 120, 200) : new Color(20, 75, 140));
        g2.fillRoundRect(copyBtn.x, copyBtn.y, copyBtn.width, copyBtn.height, 8, 8);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
        String copyText = copiedNotice.isEmpty() ? "COPY IP" : "COPIED!";
        g2.drawString(copyText, copyBtn.x + (copyBtn.width - g2.getFontMetrics().stringWidth(copyText)) / 2, copyBtn.y + 22);

        // Terminal command hint
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        g2.setColor(new Color(180, 190, 205));
        int textY = ipBoxY + ipBoxH + 24;
        g2.drawString("1. Tell friends on your Wi-Fi to launch the game using:", panelX + 35, textY);
        g2.setFont(new Font("Consolas", Font.BOLD, 12));
        g2.setColor(GOLD_ACCENT);
        g2.drawString("run-client.bat", panelX + 370, textY);

        g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        g2.setColor(new Color(180, 190, 205));
        g2.drawString("2. They select 'Join a Game' and enter your IP: " + localIpAddress, panelX + 35, textY + 20);

        // Connected Players Box
        int plBoxW = 460;
        int plBoxH = 80;
        int plBoxX = panelX + (panelW - plBoxW) / 2;
        int plBoxY = textY + 36;

        g2.setColor(new Color(12, 14, 20));
        g2.fillRoundRect(plBoxX, plBoxY, plBoxW, plBoxH, 10, 10);
        g2.setColor(new Color(40, 45, 60));
        g2.drawRoundRect(plBoxX, plBoxY, plBoxW, plBoxH, 10, 10);

        int playerCount = Math.max(1, connectedPlayers.size());
        g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g2.setColor(new Color(120, 220, 130));
        g2.drawString("CONNECTED PLAYERS (" + playerCount + "):", plBoxX + 16, plBoxY + 22);

        g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        g2.setColor(Color.WHITE);
        if (connectedPlayers.isEmpty()) {
            g2.drawString("• " + playerName + " (Host / You)", plBoxX + 22, plBoxY + 44);
            g2.setColor(new Color(140, 145, 160));
            g2.drawString("Waiting for other players from different systems to connect...", plBoxX + 22, plBoxY + 64);
        } else {
            int py = plBoxY + 42;
            for (int i = 0; i < Math.min(3, connectedPlayers.size()); i++) {
                String p = connectedPlayers.get(i);
                boolean isYou = p.equals(playerName);
                g2.drawString("• " + p + (isYou ? " (Host / You)" : ""), plBoxX + 22 + (i % 2) * 200, py + (i / 2) * 20);
            }
        }

        // Action Buttons: START GAME vs BACK
        int btnW = 220;
        int btnH = 46;
        int btnY = panelY + 285;

        // Button 1: START GAME NOW
        Rectangle startBtn = new Rectangle(panelX + 35, btnY, btnW, btnH);
        buttonBounds.add(startBtn);
        boolean startSel = (selectedIndex == 1 || hoveredButton == 1);
        g2.setColor(startSel ? new Color(45, 175, 75) : new Color(30, 120, 50));
        g2.fillRoundRect(startBtn.x, startBtn.y, startBtn.width, startBtn.height, 10, 10);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
        String startText = (playerCount > 1) ? "▶ START MATCH (PvP)" : "▶ START GAME (SOLO/AI)";
        g2.drawString(startText, startBtn.x + (startBtn.width - g2.getFontMetrics().stringWidth(startText)) / 2, startBtn.y + 28);

        // Button 2: BACK TO MENU
        Rectangle backBtn = new Rectangle(panelX + panelW - btnW - 35, btnY, btnW, btnH);
        buttonBounds.add(backBtn);
        boolean backSel = (selectedIndex == 2 || hoveredButton == 2);
        g2.setColor(backSel ? new Color(170, 45, 45) : new Color(100, 30, 30));
        g2.fillRoundRect(backBtn.x, backBtn.y, backBtn.width, backBtn.height, 10, 10);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
        String backText = "◀ BACK TO MENU";
        g2.drawString(backText, backBtn.x + (backBtn.width - g2.getFontMetrics().stringWidth(backText)) / 2, backBtn.y + 28);
    }

    private void drawFooter(Graphics2D g2, int w, int h) {
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        g2.setColor(new Color(110, 115, 130));
        String nav = "Click buttons or use ↑/↓/W/S to navigate • ENTER to select • ESC to go back";
        g2.drawString(nav, (w - g2.getFontMetrics().stringWidth(nav)) / 2, h - 20);
    }

    public void cleanup() {
        if (animTimer != null) {
            animTimer.stop();
        }
    }
}
