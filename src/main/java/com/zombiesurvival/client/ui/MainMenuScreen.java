package com.zombiesurvival.client.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;

/**
 * Flashy main menu screen with zombies in corners and menu options
 */
public class MainMenuScreen extends JPanel {
    
    private static final Color DARK_BG = new Color(20, 20, 25);
    private static final Color BLOOD_RED = new Color(180, 0, 0);
    private static final Color ZOMBIE_GREEN = new Color(100, 150, 80);
    private static final Color HIGHLIGHT = new Color(220, 20, 20);
    
    private int selectedOption = 0;
    private String[] menuOptions;
    private boolean waitingForConnection = false;
    private String playerName = "";
    private java.util.List<String> connectedPlayers = new java.util.ArrayList<>();
    
    private MenuSelectionListener selectionListener;
    private float pulseAngle = 0f;
    private Timer animationTimer;
    
    public interface MenuSelectionListener {
        void onMenuSelected(String option);
    }
    
    public MainMenuScreen(MenuSelectionListener listener) {
        this.selectionListener = listener;
        this.menuOptions = new String[]{"Start Game", "Settings", "Exit"};
        setPreferredSize(new Dimension(1024, 768));
        setBackground(DARK_BG);
        setFocusable(true);
        
        // Animation timer for pulsing effects
        animationTimer = new Timer(50, e -> {
            pulseAngle += 0.1f;
            if (pulseAngle > Math.PI * 2) pulseAngle = 0;
            repaint();
        });
        animationTimer.start();
        
        // Keyboard controls
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_UP, KeyEvent.VK_W -> {
                        selectedOption = (selectedOption - 1 + menuOptions.length) % menuOptions.length;
                        repaint();
                    }
                    case KeyEvent.VK_DOWN, KeyEvent.VK_S -> {
                        selectedOption = (selectedOption + 1) % menuOptions.length;
                        repaint();
                    }
                    case KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> {
                        if (selectionListener != null) {
                            selectionListener.onMenuSelected(menuOptions[selectedOption]);
                        }
                    }
                    case KeyEvent.VK_ESCAPE -> {
                        if (selectedOption != menuOptions.length - 1) {
                            selectedOption = menuOptions.length - 1;
                            repaint();
                        }
                    }
                }
            }
        });
        
        // Mouse controls
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int newSelection = getMenuOptionAtPoint(e.getPoint());
                if (newSelection != -1 && newSelection != selectedOption) {
                    selectedOption = newSelection;
                    repaint();
                }
            }
        });
        
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int option = getMenuOptionAtPoint(e.getPoint());
                if (option != -1 && selectionListener != null) {
                    selectionListener.onMenuSelected(menuOptions[option]);
                }
            }
        });
    }
    
    private int getMenuOptionAtPoint(Point p) {
        int centerX = getWidth() / 2;
        int startY = getHeight() / 2 + 50;
        int spacing = 70;
        
        for (int i = 0; i < menuOptions.length; i++) {
            int y = startY + i * spacing;
            Rectangle bounds = new Rectangle(centerX - 150, y - 25, 300, 50);
            if (bounds.contains(p)) {
                return i;
            }
        }
        return -1;
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        
        int w = getWidth();
        int h = getHeight();
        
        // Draw dark vignette background
        drawVignette(g2, w, h);
        
        // Draw zombies in four corners
        drawZombieInCorner(g2, 50, 50, 150, false);      // Top-left
        drawZombieInCorner(g2, w - 200, 50, 150, true);  // Top-right
        drawZombieInCorner(g2, 50, h - 200, 150, false); // Bottom-left
        drawZombieInCorner(g2, w - 200, h - 200, 150, true); // Bottom-right
        
        // Draw title
        drawTitle(g2, w, h);
        
        // Draw menu options
        drawMenuOptions(g2, w, h);
        
        // Draw instructions
        drawInstructions(g2, w, h);
    }
    
    private void drawVignette(Graphics2D g2, int w, int h) {
        // Radial gradient for vignette effect
        Point2D center = new Point2D.Float(w / 2f, h / 2f);
        float radius = Math.max(w, h) * 0.8f;
        float[] dist = {0.0f, 0.7f, 1.0f};
        Color[] colors = {DARK_BG, DARK_BG, Color.BLACK};
        RadialGradientPaint vignette = new RadialGradientPaint(center, radius, dist, colors);
        g2.setPaint(vignette);
        g2.fillRect(0, 0, w, h);
    }
    
    private void drawZombieInCorner(Graphics2D g2, int x, int y, int size, boolean flipX) {
        g2.setStroke(new BasicStroke(3));
        
        // Zombie body
        g2.setColor(ZOMBIE_GREEN);
        g2.fillOval(x + size/3, y + size/4, size/3, size/2);
        
        // Zombie head
        g2.setColor(new Color(90, 130, 70));
        g2.fillOval(x + size/3, y, size/3, size/3);
        
        // Eyes (red, scary)
        g2.setColor(BLOOD_RED);
        int eyeSize = size / 15;
        float pulse = (float) Math.sin(pulseAngle) * 0.3f + 0.7f;
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pulse));
        
        if (flipX) {
            g2.fillOval(x + size/3 + 10, y + size/8, eyeSize, eyeSize);
            g2.fillOval(x + size/3 + 30, y + size/8, eyeSize, eyeSize);
        } else {
            g2.fillOval(x + size/2 - 20, y + size/8, eyeSize, eyeSize);
            g2.fillOval(x + size/2, y + size/8, eyeSize, eyeSize);
        }
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        
        // Arms
        g2.setColor(ZOMBIE_GREEN);
        if (flipX) {
            g2.fillRect(x + size/3 - 10, y + size/3, 15, size/3);
            g2.fillRect(x + 2*size/3, y + size/3, 15, size/3);
        } else {
            g2.fillRect(x + size/4, y + size/3, 15, size/3);
            g2.fillRect(x + 2*size/3 - 10, y + size/3, 15, size/3);
        }
        
        // Torn clothes
        g2.setColor(new Color(60, 60, 80));
        g2.drawLine(x + size/2 - 10, y + size/3, x + size/2 + 10, y + size/3 + 20);
        g2.drawLine(x + size/2 - 5, y + size/2, x + size/2 + 15, y + size/2 + 15);
        
        // Blood drips
        g2.setColor(BLOOD_RED);
        g2.setStroke(new BasicStroke(2));
        for (int i = 0; i < 3; i++) {
            int bx = x + size/3 + i * 15;
            g2.drawLine(bx, y + size/4, bx, y + size/4 + 10);
        }
    }
    
    private void drawTitle(Graphics2D g2, int w, int h) {
        String title = "ZOMBIE ESCAPE";
        
        // Pulsing glow effect
        float glowSize = (float) (Math.sin(pulseAngle) * 5 + 15);
        
        // Draw multiple layers for glow
        for (int i = 5; i > 0; i--) {
            float alpha = 0.15f * (6 - i);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g2.setColor(BLOOD_RED);
            Font glowFont = new Font("Impact", Font.BOLD, 90 + i * 2);
            g2.setFont(glowFont);
            FontMetrics fm = g2.getFontMetrics();
            int titleWidth = fm.stringWidth(title);
            g2.drawString(title, (w - titleWidth) / 2, h / 3);
        }
        
        // Main title
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        g2.setColor(BLOOD_RED);
        Font titleFont = new Font("Impact", Font.BOLD, 90);
        g2.setFont(titleFont);
        FontMetrics fm = g2.getFontMetrics();
        int titleWidth = fm.stringWidth(title);
        g2.drawString(title, (w - titleWidth) / 2, h / 3);
        
        // Dripping effect
        g2.setColor(new Color(150, 0, 0));
        int tx = (w - titleWidth) / 2;
        for (int i = 0; i < title.length(); i++) {
            if (i % 3 == 0) {
                int charX = tx + fm.stringWidth(title.substring(0, i));
                g2.fillRect(charX + 10, h / 3 + 5, 3, 15 + (int)(Math.sin(pulseAngle + i) * 5));
            }
        }
        
        // Subtitle
        g2.setFont(new Font("Arial", Font.ITALIC, 18));
        g2.setColor(new Color(150, 150, 150));
        String subtitle = "Can you survive the night?";
        int subWidth = g2.getFontMetrics().stringWidth(subtitle);
        g2.drawString(subtitle, (w - subWidth) / 2, h / 3 + 40);
    }
    
    private void drawMenuOptions(Graphics2D g2, int w, int h) {
        int centerX = w / 2;
        int startY = h / 2 + 50;
        int spacing = 70;
        
        Font menuFont = new Font("Arial", Font.BOLD, 28);
        g2.setFont(menuFont);
        
        for (int i = 0; i < menuOptions.length; i++) {
            boolean selected = (i == selectedOption);
            int y = startY + i * spacing;
            
            // Selection background
            if (selected) {
                float pulse = (float) (Math.sin(pulseAngle * 2) * 0.2 + 0.8);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f * pulse));
                g2.setColor(HIGHLIGHT);
                g2.fillRoundRect(centerX - 160, y - 30, 320, 55, 15, 15);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
                
                // Border
                g2.setStroke(new BasicStroke(2));
                g2.setColor(HIGHLIGHT);
                g2.drawRoundRect(centerX - 160, y - 30, 320, 55, 15, 15);
            }
            
            // Menu text
            FontMetrics fm = g2.getFontMetrics();
            int textWidth = fm.stringWidth(menuOptions[i]);
            
            if (selected) {
                // Shadow
                g2.setColor(Color.BLACK);
                g2.drawString(menuOptions[i], centerX - textWidth / 2 + 2, y + 2);
                
                // Main text
                g2.setColor(HIGHLIGHT);
                g2.drawString(menuOptions[i], centerX - textWidth / 2, y);
                
                // Selection indicator
                g2.setColor(BLOOD_RED);
                int arrowX = centerX - textWidth / 2 - 30;
                int[] xPoints = {arrowX - 10, arrowX, arrowX - 10};
                int[] yPoints = {y - 10, y - 3, y + 4};
                g2.fillPolygon(xPoints, yPoints, 3);
            } else {
                g2.setColor(new Color(180, 180, 180));
                g2.drawString(menuOptions[i], centerX - textWidth / 2, y);
            }
        }
        
        // Show connected players if in waiting mode
        if (waitingForConnection && !connectedPlayers.isEmpty()) {
            g2.setFont(new Font("Arial", Font.BOLD, 18));
            g2.setColor(new Color(200, 200, 200));
            int playersY = startY + menuOptions.length * spacing + 50;
            String playersText = "Connected Players: " + connectedPlayers.size();
            int playersWidth = g2.getFontMetrics().stringWidth(playersText);
            g2.drawString(playersText, centerX - playersWidth / 2, playersY);
            
            // List player names
            g2.setFont(new Font("Arial", Font.PLAIN, 16));
            g2.setColor(new Color(150, 150, 150));
            for (int i = 0; i < connectedPlayers.size(); i++) {
                String name = connectedPlayers.get(i);
                boolean isYou = name.equals(playerName);
                if (isYou) {
                    g2.setColor(HIGHLIGHT);
                    name += " (YOU)";
                } else {
                    g2.setColor(new Color(150, 150, 150));
                }
                int nameWidth = g2.getFontMetrics().stringWidth("• " + name);
                g2.drawString("• " + name, centerX - nameWidth / 2, playersY + 30 + i * 25);
            }
        }
    }
    
    private void drawInstructions(Graphics2D g2, int w, int h) {
        g2.setFont(new Font("Arial", Font.PLAIN, 14));
        g2.setColor(new Color(120, 120, 120));
        String controls = "Use ↑↓ or W/S to navigate • ENTER to select • ESC to exit";
        int controlsWidth = g2.getFontMetrics().stringWidth(controls);
        g2.drawString(controls, (w - controlsWidth) / 2, h - 30);
    }
    
    public void setWaitingMode(boolean waiting) {
        this.waitingForConnection = waiting;
        if (waiting) {
            this.menuOptions = new String[]{"Go to Lobby", "Invite Members"};
        } else {
            this.menuOptions = new String[]{"Start Game", "Settings", "Exit"};
        }
        this.selectedOption = 0;
        repaint();
    }
    
    public void setPlayerName(String name) {
        this.playerName = name;
        repaint();
    }
    
    public void setConnectedPlayers(java.util.List<String> players) {
        this.connectedPlayers = new java.util.ArrayList<>(players);
        repaint();
    }
    
    public void cleanup() {
        if (animationTimer != null) {
            animationTimer.stop();
        }
    }
}
