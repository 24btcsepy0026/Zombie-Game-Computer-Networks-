package com.zombiesurvival.client.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Main Menu Screen with multi-page navigation:
 *
 * Page 1 [INTRO]:
 *   - "Start Game" -> navigates to Page 2
 *   - "Settings"   -> opens settings
 *
 * Page 2 [MODE_SELECT] (from Start Game):
 *   - "Play Solo"        -> play alone with AI zombies
 *   - "Play Multiplayer" -> navigates to Page 3
 *   - "Back"             -> returns to Intro
 *
 * Page 3 [MULTIPLAYER] (from Play Multiplayer):
 *   - "Invite Members" -> shows Port & IP address to share with other systems
 *   - "Join a Lobby"   -> enter IP & Port to connect
 *   - "Back"           -> returns to Mode Select
 *
 * Page 4 [INVITE_LOBBY] (from Invite Members):
 *   - Displays IP & Port prominently
 *   - Copy IP button
 *   - Live Connected Players list
 *   - Start Match button
 *   - Back button
 */
public class MainMenuScreen extends JPanel {

    public enum ViewState {
        INTRO,          // 1. "Start Game", "Settings"
        MODE_SELECT,    // 2. "Play Solo", "Play Multiplayer"
        MULTIPLAYER,    // 3. "Invite Members", "Join a Lobby"
        INVITE_LOBBY    // 4. Displays IP, Port, Connected Players, Start Match
    }

    private static final Color DARK_BG       = new Color(10, 10, 16);
    private static final Color BLOOD_RED      = new Color(185, 25, 25);
    private static final Color BLOOD_RED_BRT  = new Color(230, 40, 40);
    private static final Color ZOMBIE_GREEN   = new Color(68, 140, 52);
    private static final Color GOLD_ACCENT    = new Color(245, 195, 45);
    private static final Color TEXT_GRAY      = new Color(175, 180, 195);

    public static final int DEFAULT_PORT = 8080;

    private ViewState currentView = ViewState.INTRO;
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
        void onPlaySolo();
        void onInviteMembersHost();
        void onJoinLobby();
        void onStartMultiplayerGame();
        void onOpenSettings();
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
                        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        break;
                    }
                }
                if (hoveredButton == -1) {
                    setCursor(Cursor.getDefaultCursor());
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
        int maxButtons = buttonBounds.isEmpty() ? 2 : buttonBounds.size();
        if (keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_W) {
            selectedIndex = (selectedIndex - 1 + maxButtons) % maxButtons;
            repaint();
        } else if (keyCode == KeyEvent.VK_DOWN || keyCode == KeyEvent.VK_S) {
            selectedIndex = (selectedIndex + 1) % maxButtons;
            repaint();
        } else if (keyCode == KeyEvent.VK_ENTER || keyCode == KeyEvent.VK_SPACE) {
            triggerButtonAction(selectedIndex);
        } else if (keyCode == KeyEvent.VK_ESCAPE) {
            navigateBack();
        }
    }

    private void navigateBack() {
        switch (currentView) {
            case INTRO -> System.exit(0);
            case MODE_SELECT -> setView(ViewState.INTRO);
            case MULTIPLAYER -> setView(ViewState.MODE_SELECT);
            case INVITE_LOBBY -> setView(ViewState.MULTIPLAYER);
        }
    }

    private void triggerButtonAction(int index) {
        switch (currentView) {
            case INTRO -> {
                if (index == 0) {
                    // "Start game" -> go to Page 2
                    setView(ViewState.MODE_SELECT);
                } else if (index == 1) {
                    // "Settings"
                    if (listener != null) listener.onOpenSettings();
                }
            }
            case MODE_SELECT -> {
                if (index == 0) {
                    // "Play solo" -> solo game with AI zombies
                    if (listener != null) listener.onPlaySolo();
                } else if (index == 1) {
                    // "Play multiplayer" -> go to Page 3
                    setView(ViewState.MULTIPLAYER);
                } else if (index == 2) {
                    // Back
                    setView(ViewState.INTRO);
                }
            }
            case MULTIPLAYER -> {
                if (index == 0) {
                    // "Invite members" -> Host & show IP/Port
                    if (listener != null) listener.onInviteMembersHost();
                    setView(ViewState.INVITE_LOBBY);
                } else if (index == 1) {
                    // "Join a lobby" -> Enter address
                    if (listener != null) listener.onJoinLobby();
                } else if (index == 2) {
                    // Back
                    setView(ViewState.MODE_SELECT);
                }
            }
            case INVITE_LOBBY -> {
                if (index == 0) {
                    // Copy IP & Port
                    copyIpToClipboard();
                } else if (index == 1) {
                    // Start match
                    if (listener != null) listener.onStartMultiplayerGame();
                } else if (index == 2) {
                    // Back
                    setView(ViewState.MULTIPLAYER);
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
            String textToCopy = localIpAddress + ":" + DEFAULT_PORT;
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

        // 2. Corner Animated Zombie Sprites
        drawZombieSprite(g2, 25, 30, 80, false);
        drawZombieSprite(g2, w - 105, 30, 80, true);
        drawZombieSprite(g2, 25, h - 110, 80, false);
        drawZombieSprite(g2, w - 105, h - 110, 80, true);

        // 3. Title Section
        drawHeaderTitle(g2, w);

        // 4. Content according to currentView
        buttonBounds.clear();
        switch (currentView) {
            case INTRO -> drawIntroView(g2, w, h);
            case MODE_SELECT -> drawModeSelectView(g2, w, h);
            case MULTIPLAYER -> drawMultiplayerView(g2, w, h);
            case INVITE_LOBBY -> drawInviteLobbyView(g2, w, h);
        }

        // 5. Footer info
        drawFooter(g2, w, h);
    }

    private void drawBackgroundAtmosphere(Graphics2D g2, int w, int h) {
        RadialGradientPaint bgGrad = new RadialGradientPaint(
            w / 2f, h / 2f, Math.max(w, h) * 0.75f,
            new float[]{0f, 0.5f, 1f},
            new Color[]{new Color(18, 16, 28), new Color(10, 9, 16), new Color(4, 4, 8)}
        );
        g2.setPaint(bgGrad);
        g2.fillRect(0, 0, w, h);

        float fogOffset = (float) Math.sin(pulseAngle * 0.8) * 15f;
        g2.setColor(new Color(35, 75, 25, 22));
        g2.fillOval(-50, (int) (h - 120 + fogOffset), w + 100, 160);
        g2.setColor(new Color(20, 60, 20, 18));
        g2.fillOval(50, (int) (h - 90 - fogOffset), w - 100, 140);
    }

    private void drawZombieSprite(Graphics2D g2, int x, int y, int size, boolean flipX) {
        g2.setColor(new Color(0, 0, 0, 95));
        g2.fillOval(x + 5, y + size - 12, size - 10, 16);

        g2.setColor(ZOMBIE_GREEN);
        g2.fillRoundRect(x + 14, y + 26, size - 28, size - 32, 10, 10);

        g2.setColor(new Color(55, 115, 42));
        g2.fillOval(x + 18, y + 6, size - 36, size - 36);

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

        g2.setColor(new Color(45, 52, 65));
        g2.fillRect(x + 20, y + 42, size - 40, 14);

        g2.setColor(BLOOD_RED);
        g2.fillRect(x + 26, y + 36, 4, 8);
        g2.fillRect(x + 46, y + 48, 5, 9);
    }

    private void drawHeaderTitle(Graphics2D g2, int w) {
        String title = "ZOMBIE ESCAPE";

        float glowPulse = (float) (Math.sin(pulseAngle) * 3 + 6);
        for (int i = (int) glowPulse; i > 0; i--) {
            g2.setColor(new Color(185, 25, 25, (int)(15 + (glowPulse - i) * 12)));
            Font glowFont = new Font("Impact", Font.BOLD, 68 + i * 2);
            g2.setFont(glowFont);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(title, (w - fm.stringWidth(title)) / 2, 95);
        }

        g2.setFont(new Font("Impact", Font.BOLD, 68));
        FontMetrics fm = g2.getFontMetrics();
        int tx = (w - fm.stringWidth(title)) / 2;
        int ty = 95;

        g2.setColor(new Color(20, 2, 2));
        g2.drawString(title, tx + 3, ty + 3);

        g2.setColor(BLOOD_RED_BRT);
        g2.drawString(title, tx, ty);

        g2.setColor(new Color(160, 15, 15));
        for (int i = 0; i < title.length(); i++) {
            if (i % 2 == 0) {
                int cx = tx + fm.stringWidth(title.substring(0, i)) + 12;
                int dripLen = 10 + (int)(Math.sin(pulseAngle + i * 1.2) * 6);
                g2.fillRect(cx, ty + 6, 3, dripLen);
                g2.fillOval(cx - 1, ty + 6 + dripLen - 2, 5, 5);
            }
        }

        g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
        g2.setColor(GOLD_ACCENT);
        String sub = "CAN YOU SURVIVE THE NIGHT?";
        int subW = g2.getFontMetrics().stringWidth(sub);
        g2.drawString(sub, (w - subW) / 2, 126);
    }

    // ── PAGE 1: INTRO ("Start game", "Settings") ─────────────────────────────

    private void drawIntroView(Graphics2D g2, int w, int h) {
        int btnW = 380;
        int btnH = 65;
        int startY = 210;
        int spacing = 90;

        // Button 0: Start Game
        Rectangle r0 = new Rectangle((w - btnW) / 2, startY, btnW, btnH);
        buttonBounds.add(r0);
        drawStyledCardButton(g2, r0, 0,
            "▶  Start Game",
            "ENTER",
            "Begin your survival adventure",
            new Color(40, 150, 70), new Color(22, 75, 36));

        // Button 1: Settings
        Rectangle r1 = new Rectangle((w - btnW) / 2, startY + spacing, btnW, btnH);
        buttonBounds.add(r1);
        drawStyledCardButton(g2, r1, 1,
            "⚙  Settings",
            "CONFIG",
            "Sound, music, and volume settings",
            new Color(180, 110, 40), new Color(90, 55, 20));
    }

    // ── PAGE 2: MODE SELECT ("Play solo", "Play multiplayer", "Back") ────────

    private void drawModeSelectView(Graphics2D g2, int w, int h) {
        int btnW = 440;
        int btnH = 68;
        int startY = 180;
        int spacing = 85;

        // Section header
        drawSectionSubtitle(g2, w, "SELECT GAME MODE", startY - 22);

        // Button 0: Play Solo
        Rectangle r0 = new Rectangle((w - btnW) / 2, startY, btnW, btnH);
        buttonBounds.add(r0);
        drawStyledCardButton(g2, r0, 0,
            "1. Play Solo",
            "SOLO / AI",
            "Play alone with AI-generated zombies",
            new Color(40, 150, 70), new Color(22, 75, 36));

        // Button 1: Play Multiplayer
        Rectangle r1 = new Rectangle((w - btnW) / 2, startY + spacing, btnW, btnH);
        buttonBounds.add(r1);
        drawStyledCardButton(g2, r1, 1,
            "2. Play Multiplayer",
            "NETWORK",
            "Host or join network match with other systems",
            new Color(180, 50, 40), new Color(110, 25, 20));

        // Button 2: Back
        Rectangle r2 = new Rectangle((w - btnW) / 2, startY + spacing * 2, btnW, 50);
        buttonBounds.add(r2);
        drawStyledCardButton(g2, r2, 2,
            "◀  Back to Main Menu",
            "",
            "",
            new Color(120, 125, 140), new Color(30, 32, 42));
    }

    // ── PAGE 3: MULTIPLAYER ("Invite members", "Join a lobby", "Back") ───────

    private void drawMultiplayerView(Graphics2D g2, int w, int h) {
        int btnW = 460;
        int btnH = 68;
        int startY = 180;
        int spacing = 85;

        drawSectionSubtitle(g2, w, "MULTIPLAYER OPTIONS", startY - 22);

        // Button 0: Invite members
        Rectangle r0 = new Rectangle((w - btnW) / 2, startY, btnW, btnH);
        buttonBounds.add(r0);
        drawStyledCardButton(g2, r0, 0,
            "1. Invite Members",
            "HOST LOBBY",
            "Host match & display IP and Port for other systems",
            new Color(180, 50, 40), new Color(110, 25, 20));

        // Button 1: Join a lobby
        Rectangle r1 = new Rectangle((w - btnW) / 2, startY + spacing, btnW, btnH);
        buttonBounds.add(r1);
        drawStyledCardButton(g2, r1, 1,
            "2. Join a Lobby",
            "CONNECT",
            "Enter host IP address & Port to join",
            new Color(45, 110, 190), new Color(25, 60, 115));

        // Button 2: Back
        Rectangle r2 = new Rectangle((w - btnW) / 2, startY + spacing * 2, btnW, 50);
        buttonBounds.add(r2);
        drawStyledCardButton(g2, r2, 2,
            "◀  Back to Mode Selection",
            "",
            "",
            new Color(120, 125, 140), new Color(30, 32, 42));
    }

    // ── PAGE 4: INVITE LOBBY (Displays Port & IP Address) ─────────────────────

    private void drawInviteLobbyView(Graphics2D g2, int w, int h) {
        int panelW = 540;
        int panelX = (w - panelW) / 2;
        int panelY = 150;

        // Container
        g2.setColor(new Color(18, 20, 30, 240));
        g2.fillRoundRect(panelX, panelY, panelW, 350, 16, 16);
        g2.setStroke(new BasicStroke(1.8f));
        g2.setColor(new Color(180, 45, 45));
        g2.drawRoundRect(panelX, panelY, panelW, 350, 16, 16);

        // Panel Title
        g2.setFont(new Font("Segoe UI", Font.BOLD, 18));
        g2.setColor(GOLD_ACCENT);
        String header = "☣ MULTIPLAYER LOBBY - INVITE MEMBERS ☣";
        g2.drawString(header, panelX + (panelW - g2.getFontMetrics().stringWidth(header)) / 2, panelY + 30);

        // IP & PORT Box
        int ipBoxW = 460;
        int ipBoxH = 70;
        int ipBoxX = panelX + (panelW - ipBoxW) / 2;
        int ipBoxY = panelY + 46;

        g2.setColor(new Color(8, 10, 16));
        g2.fillRoundRect(ipBoxX, ipBoxY, ipBoxW, ipBoxH, 12, 12);
        g2.setColor(new Color(50, 140, 230));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(ipBoxX, ipBoxY, ipBoxW, ipBoxH, 12, 12);

        // Labels
        g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
        g2.setColor(new Color(150, 180, 220));
        g2.drawString("IP ADDRESS & PORT (SHARE WITH OTHER SYSTEMS):", ipBoxX + 16, ipBoxY + 20);

        String ipString = localIpAddress + "  :  " + DEFAULT_PORT;
        g2.setFont(new Font("Consolas", Font.BOLD, 22));
        g2.setColor(Color.WHITE);
        g2.drawString(ipString, ipBoxX + 16, ipBoxY + 50);

        // Copy IP Button
        int copyBtnW = 105;
        int copyBtnH = 34;
        Rectangle copyBtn = new Rectangle(ipBoxX + ipBoxW - copyBtnW - 12, ipBoxY + 18, copyBtnW, copyBtnH);
        buttonBounds.add(copyBtn);

        boolean copySel = (selectedIndex == 0 || hoveredButton == 0);
        g2.setColor(copySel ? new Color(30, 120, 200) : new Color(20, 75, 140));
        g2.fillRoundRect(copyBtn.x, copyBtn.y, copyBtn.width, copyBtn.height, 8, 8);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
        String copyText = copiedNotice.isEmpty() ? "COPY" : "COPIED!";
        g2.drawString(copyText, copyBtn.x + (copyBtn.width - g2.getFontMetrics().stringWidth(copyText)) / 2, copyBtn.y + 22);

        // Instructions
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        g2.setColor(new Color(180, 190, 205));
        int textY = ipBoxY + ipBoxH + 24;
        g2.drawString("1. Other systems run the client and select 'Join a Lobby'", panelX + 35, textY);
        g2.drawString("2. Enter this IP: " + localIpAddress + "  and Port: " + DEFAULT_PORT, panelX + 35, textY + 20);

        // Connected Players Box
        int plBoxW = 460;
        int plBoxH = 75;
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
            g2.drawString("Waiting for other players from different systems to connect...", plBoxX + 22, plBoxY + 62);
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
        String backText = "◀ BACK";
        g2.drawString(backText, backBtn.x + (backBtn.width - g2.getFontMetrics().stringWidth(backText)) / 2, backBtn.y + 28);
    }

    private void drawSectionSubtitle(Graphics2D g2, int w, String text, int y) {
        g2.setFont(new Font("Segoe UI", Font.BOLD, 15));
        g2.setColor(new Color(200, 205, 220));
        int sw = g2.getFontMetrics().stringWidth(text);
        g2.drawString(text, (w - sw) / 2, y);
    }

    private void drawStyledCardButton(Graphics2D g2, Rectangle r, int idx,
                                      String title, String badge, String subtitle,
                                      Color highlightCol, Color bgAccent) {
        boolean selected = (selectedIndex == idx || hoveredButton == idx);

        if (selected) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(pulseAngle * 3f);
            g2.setColor(new Color(highlightCol.getRed(), highlightCol.getGreen(), highlightCol.getBlue(), (int)(60 + 50 * pulse)));
            g2.fillRoundRect(r.x - 4, r.y - 4, r.width + 8, r.height + 8, 16, 16);
        }

        GradientPaint gp = new GradientPaint(
            r.x, r.y,
            selected ? bgAccent.brighter() : new Color(20, 22, 32),
            r.x, r.y + r.height,
            selected ? bgAccent : new Color(14, 15, 22)
        );
        g2.setPaint(gp);
        g2.fillRoundRect(r.x, r.y, r.width, r.height, 12, 12);

        g2.setStroke(new BasicStroke(selected ? 2.2f : 1.2f));
        g2.setColor(selected ? highlightCol.brighter() : new Color(55, 60, 75));
        g2.drawRoundRect(r.x, r.y, r.width, r.height, 12, 12);

        if (selected) {
            g2.setColor(highlightCol.brighter());
            int[] xp = { r.x + 14, r.x + 22, r.x + 14 };
            int[] yp = { r.y + r.height / 2 - 7, r.y + r.height / 2, r.y + r.height / 2 + 7 };
            g2.fillPolygon(xp, yp, 3);
        }

        g2.setFont(new Font("Segoe UI", Font.BOLD, subtitle.isEmpty() ? 17 : 18));
        g2.setColor(selected ? Color.WHITE : new Color(225, 230, 240));
        int titleY = subtitle.isEmpty() ? (r.y + r.height / 2 + 6) : (r.y + 28);
        g2.drawString(title, r.x + (selected ? 32 : 24), titleY);

        if (!subtitle.isEmpty()) {
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            g2.setColor(selected ? new Color(210, 225, 245) : TEXT_GRAY);
            g2.drawString(subtitle, r.x + (selected ? 32 : 24), r.y + 48);
        }

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
