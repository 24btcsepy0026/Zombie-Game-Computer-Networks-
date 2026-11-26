package com.zombiesurvival.client.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Main Menu Screen for Zombie Escape.
 *
 * Page 1 [INTRO]:
 *   - "Start Game" -> navigates to Page 2
 *   - "Settings"   -> opens settings
 *
 * Page 2 [MODE_SELECT]:
 *   - "Play Solo"        -> play alone with AI zombies
 *   - "Play Multiplayer" -> navigates to Page 3
 *   - "Back"             -> returns to Intro
 *
 * Page 3 [MULTIPLAYER]:
 *   - "Invite Members" -> shows Port & IP address to share (Host)
 *   - "Join a Lobby"   -> enter IP & Port to connect (Client)
 *   - "Back"           -> returns to Mode Select
 *
 * Page 4 [INVITE_LOBBY] (Host Lobby):
 *   - Displays IP & Port
 *   - Copy IP button
 *   - Connected players list
 *   - Start Game button
 *   - Back button
 *
 * Page 5 [CLIENT_LOBBY] (Client Lobby):
 *   - Displays Host username & connected players
 *   - Waiting notice
 *   - Leave Lobby button
 */
public class MainMenuScreen extends JPanel {

    public enum ViewState {
        INTRO,
        MODE_SELECT,
        MULTIPLAYER,
        INVITE_LOBBY,
        CLIENT_LOBBY
    }

    public static final int DEFAULT_PORT = 8080;

    private ViewState currentView = ViewState.INTRO;
    private int selectedIndex = 0;
    private float pulseAngle = 0f;
    private Timer animTimer;

    private String localIpAddress = "127.0.0.1";
    private String playerName = System.getProperty("user.name", "Player");
    private String hostPlayerName = "";
    private final List<String> connectedPlayers = new ArrayList<>();
    private String copiedNotice = "";
    private long copiedNoticeTimer = 0;

    // Interactive button bounds
    private final List<Rectangle> buttonBounds = new ArrayList<>();
    private int hoveredButton = -1;
    private int pressedButton = -1;

    // Procedural cached background textures (regenerated only on resize)
    private BufferedImage cachedGrain;
    private BufferedImage cachedVignette;
    private BufferedImage cachedFog;
    private int cachedW = -1;
    private int cachedH = -1;

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
        setBackground(UITheme.COLOR_BG_DARK);
        setFocusable(true);

        detectLocalIp();

        // 30 FPS animation timer (lightweight, skips repaint if hidden)
        animTimer = new Timer(33, e -> {
            if (!isShowing()) return;
            pulseAngle += 0.05f;
            if (copiedNoticeTimer > 0 && System.currentTimeMillis() > copiedNoticeTimer) {
                copiedNotice = "";
                copiedNoticeTimer = 0;
            }
            repaint();
        });
        animTimer.start();

        // Keyboard navigation (↑/↓/W/S, ENTER, ESC)
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKeyPress(e.getKeyCode());
            }
        });

        // Mouse motion & click handling with pressed state
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
            public void mousePressed(MouseEvent e) {
                Point p = e.getPoint();
                pressedButton = -1;
                for (int i = 0; i < buttonBounds.size(); i++) {
                    if (buttonBounds.get(i).contains(p)) {
                        pressedButton = i;
                        repaint();
                        break;
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (pressedButton != -1) {
                    pressedButton = -1;
                    repaint();
                }
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
            if (currentView == ViewState.INVITE_LOBBY && connectedPlayers.size() >= 2) {
                if (listener != null) listener.onStartMultiplayerGame();
                return;
            }
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
            case CLIENT_LOBBY -> setView(ViewState.MULTIPLAYER);
        }
    }

    private void triggerButtonAction(int index) {
        switch (currentView) {
            case INTRO -> {
                if (index == 0) {
                    setView(ViewState.MODE_SELECT);
                } else if (index == 1) {
                    if (listener != null) listener.onOpenSettings();
                }
            }
            case MODE_SELECT -> {
                if (index == 0) {
                    if (listener != null) listener.onPlaySolo();
                } else if (index == 1) {
                    setView(ViewState.MULTIPLAYER);
                } else if (index == 2) {
                    setView(ViewState.INTRO);
                }
            }
            case MULTIPLAYER -> {
                if (index == 0) {
                    if (listener != null) listener.onInviteMembersHost();
                    setView(ViewState.INVITE_LOBBY);
                } else if (index == 1) {
                    if (listener != null) listener.onJoinLobby();
                } else if (index == 2) {
                    setView(ViewState.MODE_SELECT);
                }
            }
            case INVITE_LOBBY -> {
                if (index == 0) {
                    copyIpToClipboard();
                } else if (index == 1) {
                    int count = Math.max(1, connectedPlayers.size());
                    if (count >= 2) {
                        if (listener != null) listener.onStartMultiplayerGame();
                    }
                } else if (index == 2) {
                    setView(ViewState.MULTIPLAYER);
                }
            }
            case CLIENT_LOBBY -> {
                if (index == 0) {
                    setView(ViewState.MULTIPLAYER);
                }
            }
        }
    }

    public void setView(ViewState view) {
        this.currentView = view;
        this.selectedIndex = 0;
        this.hoveredButton = -1;
        this.pressedButton = -1;
        repaint();
    }

    public ViewState getView() {
        return currentView;
    }

    public void setPlayerName(String name) {
        this.playerName = name;
        repaint();
    }

    public void setHostPlayerName(String host) {
        this.hostPlayerName = (host != null && !host.isEmpty()) ? host : "";
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

        // 1. Procedural Cached Atmosphere (Grain, Vignette, Fog Glow)
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
            case CLIENT_LOBBY -> drawClientLobbyView(g2, w, h);
        }

        // 5. Footer info
        drawFooter(g2, w, h);
    }

    /**
     * Procedural background generation cached in BufferedImages.
     * Only regenerated when width or height changes.
     */
    private void drawBackgroundAtmosphere(Graphics2D g2, int w, int h) {
        if (w <= 0 || h <= 0) return;

        // Check if cached textures need recreation
        if (w != cachedW || h != cachedH || cachedGrain == null || cachedVignette == null || cachedFog == null) {
            cachedW = w;
            cachedH = h;
            cachedGrain = UITheme.createFilmGrainImage(w, h, 0.40f);
            cachedVignette = UITheme.createVignetteImage(w, h);
            cachedFog = UITheme.createFogGlowImage(w, h);
        }

        // 1. Base dark radial gradient
        RadialGradientPaint bgGrad = new RadialGradientPaint(
            w / 2f, h / 2f, Math.max(w, h) * 0.75f,
            new float[]{0f, 0.5f, 1f},
            new Color[]{new Color(18, 16, 28), new Color(10, 9, 16), new Color(4, 4, 8)}
        );
        g2.setPaint(bgGrad);
        g2.fillRect(0, 0, w, h);

        // 2. Strong green fog glow from bottom (cached image)
        if (cachedFog != null) {
            g2.drawImage(cachedFog, 0, 0, null);
        }

        // 3. Animated subtle fog drift
        float fogOffset = (float) Math.sin(pulseAngle * 0.8) * 12f;
        g2.setColor(new Color(35, 95, 30, 24));
        g2.fillOval(-60, (int) (h - 130 + fogOffset), w + 120, 150);
        g2.setColor(new Color(20, 70, 25, 20));
        g2.fillOval(50, (int) (h - 95 - fogOffset), w - 100, 130);

        // 4. Vignette overlay (cached image)
        if (cachedVignette != null) {
            g2.drawImage(cachedVignette, 0, 0, null);
        }

        // 5. Film grain overlay (cached image)
        if (cachedGrain != null) {
            g2.drawImage(cachedGrain, 0, 0, null);
        }
    }

    private void drawZombieSprite(Graphics2D g2, int x, int y, int size, boolean flipX) {
        g2.setColor(new Color(0, 0, 0, 95));
        g2.fillOval(x + 5, y + size - 12, size - 10, 16);

        g2.setColor(UITheme.COLOR_TOXIC_GREEN);
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

        g2.setColor(UITheme.COLOR_BLOOD_RED);
        g2.fillRect(x + 26, y + 36, 4, 8);
        g2.fillRect(x + 46, y + 48, 5, 9);
    }

    private void drawHeaderTitle(Graphics2D g2, int w) {
        String title = "ZOMBIE ESCAPE";

        // Multi-layered Horror Title Glow
        float glowPulse = (float) (Math.sin(pulseAngle) * 3 + 6);
        for (int i = (int) glowPulse; i > 0; i--) {
            g2.setColor(new Color(185, 25, 25, (int)(14 + (glowPulse - i) * 10)));
            g2.setFont(UITheme.FONT_TITLE_LARGE);
            FontMetrics gfm = g2.getFontMetrics();
            g2.drawString(title, (w - gfm.stringWidth(title)) / 2, 95);
        }

        g2.setFont(UITheme.FONT_TITLE_LARGE);
        FontMetrics fm = g2.getFontMetrics();
        int tx = (w - fm.stringWidth(title)) / 2;
        int ty = 95;

        // Dark Shadow
        g2.setColor(new Color(15, 2, 2));
        g2.drawString(title, tx + 4, ty + 4);

        // Main Blood Red Text
        g2.setColor(UITheme.COLOR_BLOOD_RED_BRT);
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

        // Subtitle using Horror Font Family
        g2.setFont(UITheme.FONT_BADGE);
        g2.setColor(UITheme.COLOR_GOLD);
        String sub = "CAN YOU SURVIVE THE NIGHT?";
        int subW = g2.getFontMetrics().stringWidth(sub);
        g2.drawString(sub, (w - subW) / 2, 126);
    }

    // ── PAGE 1: INTRO ("Start game", "Settings") ─────────────────────────────

    private void drawIntroView(Graphics2D g2, int w, int h) {
        int btnW = 420;
        int btnH = 68;
        int startY = 210;
        int spacing = 95;

        // Button 0: Start Game
        Rectangle r0 = new Rectangle((w - btnW) / 2, startY, btnW, btnH);
        buttonBounds.add(r0);
        StyledButton.draw(g2, r0,
            "Start Game",
            StyledButton.IconType.PLAY,
            "ENTER",
            "Begin your survival adventure",
            UITheme.COLOR_TOXIC_GREEN, UITheme.COLOR_TOXIC_GREEN_DARK,
            selectedIndex == 0, hoveredButton == 0, pressedButton == 0,
            pulseAngle);

        // Button 1: Settings
        Rectangle r1 = new Rectangle((w - btnW) / 2, startY + spacing, btnW, btnH);
        buttonBounds.add(r1);
        StyledButton.draw(g2, r1,
            "Settings",
            StyledButton.IconType.GEAR,
            "CONFIG",
            "Sound, music, and volume settings",
            UITheme.COLOR_GOLD, new Color(75, 45, 18),
            selectedIndex == 1, hoveredButton == 1, pressedButton == 1,
            pulseAngle);
    }

    // ── PAGE 2: MODE SELECT ("Play solo", "Play multiplayer", "Back") ────────

    private void drawModeSelectView(Graphics2D g2, int w, int h) {
        int btnW = 460;
        int btnH = 68;
        int startY = 180;
        int spacing = 88;

        drawSectionSubtitle(g2, w, "SELECT GAME MODE", startY - 24);

        // Button 0: Play Solo
        Rectangle r0 = new Rectangle((w - btnW) / 2, startY, btnW, btnH);
        buttonBounds.add(r0);
        StyledButton.draw(g2, r0,
            "Play Solo",
            StyledButton.IconType.PLAY,
            "SOLO / AI",
            "Play alone with AI-generated zombies",
            UITheme.COLOR_TOXIC_GREEN, UITheme.COLOR_TOXIC_GREEN_DARK,
            selectedIndex == 0, hoveredButton == 0, pressedButton == 0,
            pulseAngle);

        // Button 1: Play Multiplayer
        Rectangle r1 = new Rectangle((w - btnW) / 2, startY + spacing, btnW, btnH);
        buttonBounds.add(r1);
        StyledButton.draw(g2, r1,
            "Play Multiplayer",
            StyledButton.IconType.PLAY,
            "NETWORK",
            "Host or join network match with other systems",
            UITheme.COLOR_BLOOD_RED_BRT, UITheme.COLOR_BLOOD_RED_DARK,
            selectedIndex == 1, hoveredButton == 1, pressedButton == 1,
            pulseAngle);

        // Button 2: Back
        Rectangle r2 = new Rectangle((w - btnW) / 2, startY + spacing * 2, btnW, 54);
        buttonBounds.add(r2);
        StyledButton.draw(g2, r2,
            "Back to Main Menu",
            StyledButton.IconType.BACK,
            "ESC",
            "",
            new Color(130, 135, 150), new Color(25, 28, 38),
            selectedIndex == 2, hoveredButton == 2, pressedButton == 2,
            pulseAngle);
    }

    // ── PAGE 3: MULTIPLAYER ("Invite members", "Join a lobby", "Back") ───────

    private void drawMultiplayerView(Graphics2D g2, int w, int h) {
        int btnW = 480;
        int btnH = 68;
        int startY = 180;
        int spacing = 88;

        drawSectionSubtitle(g2, w, "MULTIPLAYER OPTIONS", startY - 24);

        // Button 0: Invite members
        Rectangle r0 = new Rectangle((w - btnW) / 2, startY, btnW, btnH);
        buttonBounds.add(r0);
        StyledButton.draw(g2, r0,
            "Invite Members",
            StyledButton.IconType.PLAY,
            "HOST LOBBY",
            "Host match & display IP and Port for other systems",
            UITheme.COLOR_BLOOD_RED_BRT, UITheme.COLOR_BLOOD_RED_DARK,
            selectedIndex == 0, hoveredButton == 0, pressedButton == 0,
            pulseAngle);

        // Button 1: Join a lobby
        Rectangle r1 = new Rectangle((w - btnW) / 2, startY + spacing, btnW, btnH);
        buttonBounds.add(r1);
        StyledButton.draw(g2, r1,
            "Join a Lobby",
            StyledButton.IconType.PLAY,
            "CONNECT",
            "Enter host IP address & Port to join",
            new Color(55, 130, 235), new Color(22, 55, 105),
            selectedIndex == 1, hoveredButton == 1, pressedButton == 1,
            pulseAngle);

        // Button 2: Back
        Rectangle r2 = new Rectangle((w - btnW) / 2, startY + spacing * 2, btnW, 54);
        buttonBounds.add(r2);
        StyledButton.draw(g2, r2,
            "Back to Mode Selection",
            StyledButton.IconType.BACK,
            "ESC",
            "",
            new Color(130, 135, 150), new Color(25, 28, 38),
            selectedIndex == 2, hoveredButton == 2, pressedButton == 2,
            pulseAngle);
    }

    // ── PAGE 4: INVITE LOBBY (Host Lobby) ────────────────────────────────────

    private void drawInviteLobbyView(Graphics2D g2, int w, int h) {
        int panelW = 560;
        int panelX = (w - panelW) / 2;
        int panelY = 145;

        g2.setColor(UITheme.COLOR_BG_PANEL);
        g2.fillRoundRect(panelX, panelY, panelW, 360, 16, 16);
        g2.setStroke(new BasicStroke(1.8f));
        g2.setColor(UITheme.COLOR_BLOOD_RED);
        g2.drawRoundRect(panelX, panelY, panelW, 360, 16, 16);

        // Panel Title
        g2.setFont(UITheme.FONT_TITLE_MEDIUM);
        g2.setColor(UITheme.COLOR_GOLD);
        String header = "MULTIPLAYER LOBBY - HOSTING";
        g2.drawString(header, panelX + (panelW - g2.getFontMetrics().stringWidth(header)) / 2, panelY + 34);

        // IP & PORT Box
        int ipBoxW = 480;
        int ipBoxH = 68;
        int ipBoxX = panelX + (panelW - ipBoxW) / 2;
        int ipBoxY = panelY + 48;

        g2.setColor(new Color(8, 10, 16));
        g2.fillRoundRect(ipBoxX, ipBoxY, ipBoxW, ipBoxH, 12, 12);
        g2.setColor(new Color(50, 140, 230));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(ipBoxX, ipBoxY, ipBoxW, ipBoxH, 12, 12);

        g2.setFont(UITheme.FONT_HUD_LABEL);
        g2.setColor(new Color(150, 180, 220));
        g2.drawString("IP ADDRESS & PORT (SHARE WITH OTHER SYSTEMS):", ipBoxX + 16, ipBoxY + 20);

        String ipString = localIpAddress + "  :  " + DEFAULT_PORT;
        g2.setFont(new Font("Consolas", Font.BOLD, 22));
        g2.setColor(Color.WHITE);
        g2.drawString(ipString, ipBoxX + 16, ipBoxY + 48);

        // Copy IP Button
        int copyBtnW = 110;
        int copyBtnH = 32;
        Rectangle copyBtn = new Rectangle(ipBoxX + ipBoxW - copyBtnW - 12, ipBoxY + 18, copyBtnW, copyBtnH);
        buttonBounds.add(copyBtn);

        boolean copySel = (selectedIndex == 0 || hoveredButton == 0);
        g2.setColor(copySel ? new Color(30, 120, 200) : new Color(20, 75, 140));
        g2.fillRoundRect(copyBtn.x, copyBtn.y, copyBtn.width, copyBtn.height, 8, 8);
        g2.setColor(Color.WHITE);
        g2.setFont(UITheme.FONT_BADGE);
        String copyText = copiedNotice.isEmpty() ? "COPY" : "COPIED!";
        g2.drawString(copyText, copyBtn.x + (copyBtn.width - g2.getFontMetrics().stringWidth(copyText)) / 2, copyBtn.y + 22);

        // Connected Players Box
        int plBoxW = 480;
        int plBoxH = 95;
        int plBoxX = panelX + (panelW - plBoxW) / 2;
        int plBoxY = ipBoxY + ipBoxH + 16;

        g2.setColor(new Color(12, 14, 20));
        g2.fillRoundRect(plBoxX, plBoxY, plBoxW, plBoxH, 10, 10);
        g2.setColor(new Color(40, 45, 60));
        g2.drawRoundRect(plBoxX, plBoxY, plBoxW, plBoxH, 10, 10);

        int playerCount = Math.max(1, connectedPlayers.size());
        boolean hasMembers = (connectedPlayers.size() >= 2);

        g2.setFont(UITheme.FONT_BADGE);
        g2.setColor(UITheme.COLOR_TOXIC_GREEN_BRT);
        g2.drawString("CONNECTED SYSTEMS (" + playerCount + "):", plBoxX + 16, plBoxY + 22);

        String currentHost = hostPlayerName.isEmpty() ? playerName : hostPlayerName;
        if (connectedPlayers.isEmpty() || connectedPlayers.size() == 1) {
            drawPlayerRow(g2, currentHost, true, plBoxX + 18, plBoxY + 48);

            float waitPulse = 0.5f + 0.5f * (float) Math.sin(pulseAngle * 2.5f);
            g2.setColor(new Color(235, 195, 75, (int)(160 + 95 * waitPulse)));
            g2.setFont(new Font("Segoe UI", Font.ITALIC, 11));
            g2.drawString("Waiting for other systems to connect...", plBoxX + 18, plBoxY + 76);
        } else {
            int py = plBoxY + 48;
            for (int i = 0; i < Math.min(4, connectedPlayers.size()); i++) {
                String p = connectedPlayers.get(i);
                boolean isHost = p.equals(currentHost);
                drawPlayerRow(g2, p, isHost, plBoxX + 18 + (i % 2) * 225, py + (i / 2) * 24);
            }
        }

        // Action Buttons: START GAME vs BACK
        int btnW = 230;
        int btnH = 50;
        int btnY = panelY + 290;

        // Button 1: START GAME
        Rectangle startBtn = new Rectangle(panelX + 35, btnY, btnW, btnH);
        buttonBounds.add(startBtn);
        if (hasMembers) {
            StyledButton.draw(g2, startBtn,
                "START GAME",
                StyledButton.IconType.PLAY,
                "ENTER",
                "",
                UITheme.COLOR_TOXIC_GREEN, UITheme.COLOR_TOXIC_GREEN_DARK,
                selectedIndex == 1, hoveredButton == 1, pressedButton == 1,
                pulseAngle);
        } else {
            g2.setColor(new Color(25, 28, 38));
            g2.fillRoundRect(startBtn.x, startBtn.y, startBtn.width, startBtn.height, 10, 10);
            g2.setColor(new Color(50, 55, 70));
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(startBtn.x, startBtn.y, startBtn.width, startBtn.height, 10, 10);
            g2.setColor(new Color(110, 120, 135));
            g2.setFont(UITheme.FONT_BADGE);
            String waitText = "WAITING FOR PLAYERS...";
            g2.drawString(waitText, startBtn.x + (startBtn.width - g2.getFontMetrics().stringWidth(waitText)) / 2, startBtn.y + 31);
        }

        // Button 2: BACK
        Rectangle backBtn = new Rectangle(panelX + panelW - btnW - 35, btnY, btnW, btnH);
        buttonBounds.add(backBtn);
        StyledButton.draw(g2, backBtn,
            "BACK",
            StyledButton.IconType.BACK,
            "ESC",
            "",
            UITheme.COLOR_BLOOD_RED_BRT, UITheme.COLOR_BLOOD_RED_DARK,
            selectedIndex == 2, hoveredButton == 2, pressedButton == 2,
            pulseAngle);
    }

    // ── PAGE 5: CLIENT LOBBY ─────────────────────────────────────────────────

    private void drawClientLobbyView(Graphics2D g2, int w, int h) {
        int panelW = 560;
        int panelX = (w - panelW) / 2;
        int panelY = 145;

        g2.setColor(UITheme.COLOR_BG_PANEL);
        g2.fillRoundRect(panelX, panelY, panelW, 360, 16, 16);
        g2.setStroke(new BasicStroke(1.8f));
        g2.setColor(new Color(50, 130, 220));
        g2.drawRoundRect(panelX, panelY, panelW, 360, 16, 16);

        // Panel Title
        g2.setFont(UITheme.FONT_TITLE_MEDIUM);
        g2.setColor(UITheme.COLOR_GOLD);
        String header = "MULTIPLAYER LOBBY - JOINED";
        g2.drawString(header, panelX + (panelW - g2.getFontMetrics().stringWidth(header)) / 2, panelY + 34);

        // Main System (Host) Card
        int hostBoxW = 480;
        int hostBoxH = 55;
        int hostBoxX = panelX + (panelW - hostBoxW) / 2;
        int hostBoxY = panelY + 46;

        g2.setColor(new Color(12, 22, 36));
        g2.fillRoundRect(hostBoxX, hostBoxY, hostBoxW, hostBoxH, 10, 10);
        g2.setColor(new Color(60, 145, 230));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(hostBoxX, hostBoxY, hostBoxW, hostBoxH, 10, 10);

        g2.setFont(UITheme.FONT_HUD_LABEL);
        g2.setColor(new Color(140, 195, 255));
        g2.drawString("MAIN SYSTEM (HOST):", hostBoxX + 16, hostBoxY + 18);

        String mainSystemName = hostPlayerName.isEmpty() ? (!connectedPlayers.isEmpty() ? connectedPlayers.get(0) : "Host") : hostPlayerName;
        drawPlayerRow(g2, mainSystemName, true, hostBoxX + 16, hostBoxY + 40);

        // Connected Players Box
        int plBoxW = 480;
        int plBoxH = 95;
        int plBoxX = panelX + (panelW - plBoxW) / 2;
        int plBoxY = hostBoxY + hostBoxH + 16;

        g2.setColor(new Color(12, 14, 20));
        g2.fillRoundRect(plBoxX, plBoxY, plBoxW, plBoxH, 10, 10);
        g2.setColor(new Color(40, 45, 60));
        g2.drawRoundRect(plBoxX, plBoxY, plBoxW, plBoxH, 10, 10);

        int playerCount = Math.max(1, connectedPlayers.size());
        g2.setFont(UITheme.FONT_BADGE);
        g2.setColor(UITheme.COLOR_TOXIC_GREEN_BRT);
        g2.drawString("ALL CONNECTED PLAYERS (" + playerCount + "):", plBoxX + 16, plBoxY + 22);

        int py = plBoxY + 48;
        for (int i = 0; i < Math.min(4, connectedPlayers.size()); i++) {
            String p = connectedPlayers.get(i);
            boolean isHost = p.equals(mainSystemName);
            boolean isYou = p.equals(playerName);
            String label = p + (isYou ? " (You)" : "");
            drawPlayerRow(g2, label, isHost, plBoxX + 18 + (i % 2) * 225, py + (i / 2) * 24);
        }

        // Waiting Notice
        int waitY = plBoxY + plBoxH + 24;
        float waitPulse = 0.5f + 0.5f * (float) Math.sin(pulseAngle * 2.5f);
        g2.setColor(new Color(235, 195, 75, (int)(160 + 95 * waitPulse)));
        g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
        String waitMsg = "Waiting for main system (" + mainSystemName + ") to start the game...";
        g2.drawString(waitMsg, panelX + (panelW - g2.getFontMetrics().stringWidth(waitMsg)) / 2, waitY);

        g2.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        g2.setColor(UITheme.COLOR_TEXT_MUTED);
        String waitHint = "The match will launch automatically when the host starts.";
        g2.drawString(waitHint, panelX + (panelW - g2.getFontMetrics().stringWidth(waitHint)) / 2, waitY + 18);

        // Single Action Button: LEAVE LOBBY
        int btnW = 200;
        int btnH = 46;
        int btnY = panelY + 292;
        Rectangle leaveBtn = new Rectangle(panelX + (panelW - btnW) / 2, btnY, btnW, btnH);
        buttonBounds.add(leaveBtn);

        StyledButton.draw(g2, leaveBtn,
            "LEAVE LOBBY",
            StyledButton.IconType.BACK,
            "ESC",
            "",
            UITheme.COLOR_BLOOD_RED_BRT, UITheme.COLOR_BLOOD_RED_DARK,
            selectedIndex == 0, hoveredButton == 0, pressedButton == 0,
            pulseAngle);
    }

    private void drawPlayerRow(Graphics2D g2, String name, boolean isHost, int x, int y) {
        g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g2.setColor(Color.WHITE);
        String label = (isHost ? "[Host] " : "• ") + name;
        g2.drawString(label, x, y);

        FontMetrics fm = g2.getFontMetrics();
        int labelW = fm.stringWidth(label);

        // Beside it: green "Connected" badge
        int badgeX = x + labelW + 6;
        int badgeY = y - 11;
        String connStr = "Connected";
        g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
        FontMetrics bfm = g2.getFontMetrics();
        int bw = bfm.stringWidth(connStr) + 10;

        g2.setColor(new Color(25, 95, 45, 190));
        g2.fillRoundRect(badgeX, badgeY, bw, 15, 6, 6);
        g2.setColor(new Color(65, 215, 100));
        g2.drawRoundRect(badgeX, badgeY, bw, 15, 6, 6);
        g2.drawString(connStr, badgeX + 5, badgeY + 11);
    }

    private void drawSectionSubtitle(Graphics2D g2, int w, String text, int y) {
        g2.setFont(UITheme.FONT_TITLE_MEDIUM);
        g2.setColor(UITheme.COLOR_TEXT_MAIN);
        int sw = g2.getFontMetrics().stringWidth(text);
        g2.drawString(text, (w - sw) / 2, y);
    }

    private void drawFooter(Graphics2D g2, int w, int h) {
        g2.setFont(UITheme.FONT_FOOTER);
        g2.setColor(UITheme.COLOR_TEXT_MUTED);
        String nav = "Click buttons or use [W/S] or Arrow Keys to navigate • [ENTER] to select • [ESC] to go back";
        g2.drawString(nav, (w - g2.getFontMetrics().stringWidth(nav)) / 2, h - 20);
    }

    public void cleanup() {
        if (animTimer != null) {
            animTimer.stop();
        }
    }
}
