package com.zombiesurvival.client.ui;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Custom styled card button renderer for menu screens.
 * Features:
 * - Vertical gradient with dark apocalyptic styling
 * - Rounded corners with anti-aliasing
 * - Clipped procedural noise / grunge overlay
 * - Text shadow for maximum legibility
 * - Toxic green glowing halo when selected
 * - Smooth hover transition
 * - Pressed state with darker fill and +1px mechanical offset
 * - Custom Graphics2D vector icons
 */
public final class StyledButton {

    public enum IconType {
        NONE,
        PLAY,
        GEAR,
        BACK
    }

    private StyledButton() {}

    /**
     * Renders a styled menu button onto the provided graphics context.
     */
    public static void draw(Graphics2D g2, Rectangle r,
                            String title, IconType iconType,
                            String badge, String subtitle,
                            Color highlightCol, Color bgAccent,
                            boolean selected, boolean hovered, boolean pressed,
                            float pulseAngle) {

        int arc = 14;
        int yOffset = pressed ? 2 : 0;
        int xOffset = pressed ? 1 : 0;

        // 1. Selection Green Glow Halo
        if (selected) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(pulseAngle * 3f);
            int glowAlpha = (int) (60 + 55 * pulse);
            Color glowColor = new Color(
                UITheme.COLOR_TOXIC_GREEN_BRT.getRed(),
                UITheme.COLOR_TOXIC_GREEN_BRT.getGreen(),
                UITheme.COLOR_TOXIC_GREEN_BRT.getBlue(),
                glowAlpha
            );

            // Multi-layered soft glow
            for (int i = 6; i >= 2; i -= 2) {
                g2.setColor(new Color(glowColor.getRed(), glowColor.getGreen(), glowColor.getBlue(), glowAlpha / (i / 2 + 1)));
                g2.fillRoundRect(r.x - i, r.y - i, r.width + i * 2, r.height + i * 2, arc + 4, arc + 4);
            }
        } else if (hovered) {
            // Subtle hover ambient glow
            g2.setColor(new Color(highlightCol.getRed(), highlightCol.getGreen(), highlightCol.getBlue(), 35));
            g2.fillRoundRect(r.x - 3, r.y - 3, r.width + 6, r.height + 6, arc + 2, arc + 2);
        }

        // 2. Vertical Gradient Fill
        Color topColor;
        Color botColor;
        if (pressed) {
            topColor = bgAccent.darker().darker();
            botColor = new Color(10, 11, 16);
        } else if (selected) {
            topColor = new Color(
                Math.min(255, (int)(bgAccent.getRed() * 1.35f + 15)),
                Math.min(255, (int)(bgAccent.getGreen() * 1.35f + 25)),
                Math.min(255, (int)(bgAccent.getBlue() * 1.35f + 15))
            );
            botColor = bgAccent;
        } else if (hovered) {
            topColor = new Color(34, 38, 52);
            botColor = new Color(18, 20, 28);
        } else {
            topColor = new Color(24, 27, 36);
            botColor = new Color(13, 14, 20);
        }

        GradientPaint gp = new GradientPaint(
            r.x, r.y + yOffset, topColor,
            r.x, r.y + r.height + yOffset, botColor
        );
        g2.setPaint(gp);
        RoundRectangle2D btnShape = new RoundRectangle2D.Float(r.x + xOffset, r.y + yOffset, r.width, r.height, arc, arc);
        g2.fill(btnShape);

        // 3. Clipped Grunge / Noise Overlay
        Shape oldClip = g2.getClip();
        g2.clip(btnShape);
        g2.setPaint(UITheme.NOISE_TEXTURE);
        g2.fill(btnShape);
        // Subtle top bevel highlight
        g2.setColor(new Color(255, 255, 255, selected ? 45 : (hovered ? 25 : 12)));
        g2.drawLine(r.x + xOffset + arc / 2, r.y + yOffset + 1, r.x + xOffset + r.width - arc / 2, r.y + yOffset + 1);
        g2.setClip(oldClip);

        // 4. Rounded Border
        Color borderColor;
        float strokeW;
        if (selected) {
            borderColor = UITheme.COLOR_TOXIC_GREEN_BRT;
            strokeW = 2.4f;
        } else if (hovered) {
            borderColor = highlightCol.brighter();
            strokeW = 1.6f;
        } else {
            borderColor = UITheme.COLOR_BTN_BORDER_NORMAL;
            strokeW = 1.2f;
        }

        g2.setStroke(new BasicStroke(strokeW));
        g2.setColor(borderColor);
        g2.drawRoundRect(r.x + xOffset, r.y + yOffset, r.width, r.height, arc, arc);

        // 5. Draw Custom Vector Icon (Replacing missing glyph boxes)
        int contentX = r.x + xOffset + (selected ? 28 : 22);
        int centerY = r.y + yOffset + (subtitle.isEmpty() ? r.height / 2 : r.height / 2 - 3);

        if (iconType == IconType.PLAY) {
            int iconSize = 14;
            Color iconColor = selected ? UITheme.COLOR_TOXIC_GREEN_BRT : (hovered ? Color.WHITE : new Color(160, 220, 175));
            UITheme.drawPlayIcon(g2, contentX, centerY - iconSize / 2, iconSize, iconColor);
            contentX += iconSize + 12;
        } else if (iconType == IconType.GEAR) {
            int iconSize = 16;
            Color iconColor = selected ? UITheme.COLOR_GOLD_BRT : (hovered ? Color.WHITE : new Color(210, 170, 110));
            UITheme.drawGearIcon(g2, contentX, centerY - iconSize / 2, iconSize, iconColor);
            contentX += iconSize + 12;
        } else if (iconType == IconType.BACK) {
            int iconSize = 14;
            Color iconColor = selected ? UITheme.COLOR_BLOOD_RED_BRT : (hovered ? Color.WHITE : new Color(180, 185, 200));
            UITheme.drawBackArrowIcon(g2, contentX, centerY - iconSize / 2, iconSize, iconColor);
            contentX += iconSize + 12;
        }

        // 6. Title Text with Shadow
        g2.setFont(UITheme.FONT_BUTTON);
        int titleBaseline = subtitle.isEmpty() ? (r.y + yOffset + r.height / 2 + 8) : (r.y + yOffset + 28);

        // Text Shadow
        g2.setColor(UITheme.COLOR_TEXT_SHADOW);
        g2.drawString(title, contentX + 2, titleBaseline + 2);

        // Text Main
        Color titleColor = selected ? Color.WHITE : (hovered ? new Color(245, 248, 255) : UITheme.COLOR_TEXT_MAIN);
        g2.setColor(titleColor);
        g2.drawString(title, contentX, titleBaseline);

        // 7. Subtitle Text
        if (!subtitle.isEmpty()) {
            g2.setFont(UITheme.FONT_BUTTON_SUB);
            int subBaseline = r.y + yOffset + 48;
            g2.setColor(new Color(0, 0, 0, 160));
            g2.drawString(subtitle, contentX + 1, subBaseline + 1);
            g2.setColor(selected ? new Color(210, 235, 225) : UITheme.COLOR_TEXT_MUTED);
            g2.drawString(subtitle, contentX, subBaseline);
        }

        // 8. Badge Tag (Uses same horror font family!)
        if (badge != null && !badge.isEmpty()) {
            g2.setFont(UITheme.FONT_BADGE);
            FontMetrics bfm = g2.getFontMetrics();
            int badgePaddingH = 14;
            int bw = bfm.stringWidth(badge) + badgePaddingH * 2;
            int bh = 22;
            int bx = r.x + xOffset + r.width - bw - 14;
            int by = r.y + yOffset + (r.height - bh) / 2;

            // Badge Background
            g2.setColor(new Color(8, 10, 16, 210));
            g2.fillRoundRect(bx, by, bw, bh, 8, 8);

            // Badge Border
            Color badgeBorderColor = selected ? UITheme.COLOR_TOXIC_GREEN_BRT : (hovered ? highlightCol.brighter() : highlightCol);
            g2.setColor(badgeBorderColor);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(bx, by, bw, bh, 8, 8);

            // Badge Text Shadow & Text
            int btx = bx + badgePaddingH;
            int bty = by + 16;
            g2.setColor(Color.BLACK);
            g2.drawString(badge, btx + 1, bty + 1);
            g2.setColor(selected ? UITheme.COLOR_TOXIC_GREEN_BRT : highlightCol.brighter());
            g2.drawString(badge, btx, bty);
        }
    }
}
