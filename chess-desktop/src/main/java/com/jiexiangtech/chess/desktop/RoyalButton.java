package com.jiexiangtech.chess.desktop;

import java.awt.*;
import javax.swing.JButton;
import javax.swing.border.EmptyBorder;
import static com.jiexiangtech.chess.desktop.RoyalStyle.*;

final class RoyalButton extends JButton {
    enum Variant { PRIMARY, GOLD_GLOW, SECONDARY, GHOST, DANGER, SEGMENT }

    private Variant variant;
    private RoyalIcons.Glyph glyph;
    private boolean selectedState;
    private int radius = 11;

    RoyalButton(String text, Variant variant, RoyalIcons.Glyph glyph, Runnable action) {
        super(text);
        this.variant = variant;
        this.glyph = glyph;
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setRolloverEnabled(true);
        setIconTextGap(8);
        setBorder(new EmptyBorder(9, 14, 9, 14));
        setFont(sans(Font.BOLD, 13f));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setMinimumSize(new Dimension(42, 36));
        syncAppearance();
        if (action != null) addActionListener(e -> action.run());
    }

    void setVariant(Variant variant) {
        this.variant = variant;
        syncAppearance();
        repaint();
    }

    void setGlyph(RoyalIcons.Glyph glyph) {
        this.glyph = glyph;
        syncAppearance();
        repaint();
    }

    void setSelectedState(boolean selected) {
        this.selectedState = selected;
        syncAppearance();
        repaint();
    }

    void setRadius(int radius) {
        this.radius = radius;
        repaint();
    }

    private void syncAppearance() {
        boolean goldFill = variant == Variant.PRIMARY || (variant == Variant.SEGMENT && selectedState);
        Color fg = !isEnabled() ? MUTED
                : goldFill ? BG
                : variant == Variant.GOLD_GLOW ? IVORY
                : variant == Variant.DANGER ? new Color(0xFFD2CE)
                : variant == Variant.GHOST ? (selectedState ? PALE : new Color(0xCFC6B6))
                : IVORY;
        Color iconColor = !isEnabled() ? MUTED
                : goldFill ? BG
                : variant == Variant.GOLD_GLOW ? PALE
                : variant == Variant.DANGER ? new Color(0xE86E68)
                : variant == Variant.GHOST ? (selectedState ? GOLD : new Color(0xC9A853))
                : new Color(0xDFBE68);
        setForeground(fg);
        setIcon(glyph == null ? null : RoyalIcons.icon(glyph, 16, iconColor));
    }

    @Override public void setEnabled(boolean b) {
        super.setEnabled(b);
        syncAppearance();
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        var m = getModel();
        boolean hover = isEnabled() && m.isRollover();
        boolean pressed = isEnabled() && m.isPressed();
        boolean goldFill = variant == Variant.PRIMARY || (variant == Variant.SEGMENT && selectedState);

        if (!isEnabled()) {
            g.setColor(new Color(22, 20, 24, 185));
            g.fillRoundRect(1, 1, w - 2, h - 2, radius, radius);
            g.setColor(new Color(0x302920));
            g.drawRoundRect(1, 1, w - 3, h - 3, radius, radius);
        } else if (goldFill) {
            Color top = pressed ? GOLD.darker() : hover ? new Color(0xF7DF9B) : new Color(0xE6C45A);
            Color bottom = pressed ? new Color(0x8B681C) : hover ? GOLD : new Color(0xBD952A);
            g.setPaint(new GradientPaint(0, 1, top, 0, h - 1, bottom));
            g.fillRoundRect(1, 1, w - 2, h - 2, radius, radius);
            g.setColor(new Color(255, 250, 224, pressed ? 40 : 95));
            g.drawRoundRect(2, 2, w - 5, h - 5, Math.max(4, radius - 2), Math.max(4, radius - 2));
            g.setColor(isFocusOwner() ? IVORY : hover ? PALE : GOLD);
            g.drawRoundRect(1, 1, w - 3, h - 3, radius, radius);
        } else if (variant == Variant.GOLD_GLOW) {
            // Signature reference Save/Action luminous amber-gold glass button
            if (hover) RoyalGlowEffect.paintOuterGlow(g, 2, 2, w - 4, h - 4, radius, new Color(236, 194, 78, 95), 2);
            Color top = pressed ? new Color(0x3D2E14) : hover ? new Color(0x5E471E) : new Color(0x493718);
            Color bottom = pressed ? new Color(0x231A0C) : hover ? new Color(0x362811) : new Color(0x281F10);
            g.setPaint(new GradientPaint(0, 1, top, 0, h - 1, bottom));
            g.fillRoundRect(1, 1, w - 2, h - 2, radius, radius);
            g.setColor(new Color(255, 236, 175, 42));
            g.drawLine(radius / 2, 2, w - radius / 2, 2);
            RoyalGlowEffect.paintMetallicGoldBorder(g, 1, 1, w - 2, h - 2, radius, 1.5f, true);
        } else if (variant == Variant.DANGER) {
            Color top = pressed ? new Color(0x2A1214) : hover ? new Color(0x4B1D22) : new Color(0x331619);
            Color bottom = pressed ? new Color(0x1C0C0E) : hover ? new Color(0x331417) : new Color(0x201012);
            g.setPaint(new GradientPaint(0, 1, top, 0, h - 1, bottom));
            g.fillRoundRect(1, 1, w - 2, h - 2, radius, radius);
            g.setColor(isFocusOwner() ? DANGER_TEXT : hover ? new Color(0xCC5858) : new Color(0x8C4343));
            g.drawRoundRect(1, 1, w - 3, h - 3, radius, radius);
        } else if (variant == Variant.GHOST) {
            Color bg = pressed ? new Color(212, 175, 55, 36) : hover ? new Color(212, 175, 55, 24) : new Color(22, 20, 24, 150);
            g.setColor(bg);
            g.fillRoundRect(1, 1, w - 2, h - 2, radius, radius);
            g.setColor(hover || selectedState ? new Color(212, 175, 55, 115) : new Color(96, 77, 44, 110));
            g.drawRoundRect(1, 1, w - 3, h - 3, radius, radius);
        } else {
            // SECONDARY or unselected SEGMENT — rich obsidian-bronze glass with metallic gold hairline
            Color top = pressed ? new Color(0x181518) : hover ? new Color(0x362C1B) : new Color(0x24201C);
            Color bottom = pressed ? new Color(0x111013) : hover ? new Color(0x241D13) : new Color(0x171516);
            g.setPaint(new GradientPaint(0, 1, top, 0, h - 1, bottom));
            g.fillRoundRect(1, 1, w - 2, h - 2, radius, radius);
            g.setColor(new Color(255, 242, 205, 18));
            g.drawLine(radius / 2, 2, w - radius / 2, 2);
            g.setColor(isFocusOwner() ? PALE : hover ? new Color(0xD4AF37) : new Color(0x6E572E));
            g.drawRoundRect(1, 1, w - 3, h - 3, radius, radius);
        }
        g.dispose();
        super.paintComponent(graphics);
    }
}
