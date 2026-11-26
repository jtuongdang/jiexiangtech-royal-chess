package com.jiexiangtech.chess.desktop;

import java.awt.*;
import javax.swing.JButton;
import javax.swing.border.EmptyBorder;
import static com.jiexiangtech.chess.desktop.RoyalStyle.*;

final class RoyalButton extends JButton {
    enum Variant { PRIMARY, SECONDARY, GHOST, DANGER, SEGMENT }

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
                : variant == Variant.DANGER ? DANGER_TEXT
                : variant == Variant.GHOST ? (selectedState ? PALE : MUTED)
                : IVORY;
        setForeground(fg);
        setIcon(glyph == null ? null : RoyalIcons.icon(glyph, 16, fg));
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
            g.setColor(new Color(0x18171A));
            g.fillRoundRect(1, 1, w - 2, h - 2, radius, radius);
            g.setColor(new Color(0x2B2720));
            g.drawRoundRect(1, 1, w - 3, h - 3, radius, radius);
        } else if (goldFill) {
            Color top = pressed ? GOLD.darker() : hover ? new Color(0xF7DF9B) : new Color(0xE6C45A);
            Color bottom = pressed ? new Color(0x8B681C) : hover ? GOLD : new Color(0xBD952A);
            g.setPaint(new GradientPaint(0, 1, top, 0, h - 1, bottom));
            g.fillRoundRect(1, 1, w - 2, h - 2, radius, radius);
            // Subtle top specular highlight
            g.setColor(new Color(255, 250, 224, pressed ? 40 : 95));
            g.drawRoundRect(2, 2, w - 5, h - 5, Math.max(4, radius - 2), Math.max(4, radius - 2));
            g.setColor(isFocusOwner() ? IVORY : hover ? PALE : GOLD);
            g.drawRoundRect(1, 1, w - 3, h - 3, radius, radius);
        } else if (variant == Variant.DANGER) {
            Color top = pressed ? new Color(0x2D1014) : hover ? DANGER_HOVER : DANGER_BG;
            Color bottom = pressed ? new Color(0x220C0F) : hover ? new Color(0x41161C) : new Color(0x2A1014);
            g.setPaint(new GradientPaint(0, 1, top, 0, h - 1, bottom));
            g.fillRoundRect(1, 1, w - 2, h - 2, radius, radius);
            g.setColor(isFocusOwner() ? DANGER_TEXT : hover ? new Color(0xB84751) : DANGER_BORDER);
            g.drawRoundRect(1, 1, w - 3, h - 3, radius, radius);
        } else if (variant == Variant.GHOST) {
            if (hover || pressed || selectedState) {
                g.setColor(pressed ? new Color(212, 175, 55, 36) : new Color(212, 175, 55, selectedState ? 28 : 18));
                g.fillRoundRect(1, 1, w - 2, h - 2, radius, radius);
                g.setColor(new Color(212, 175, 55, selectedState ? 95 : 55));
                g.drawRoundRect(1, 1, w - 3, h - 3, radius, radius);
            }
        } else {
            // SECONDARY or unselected SEGMENT
            Color top = pressed ? new Color(0x18161A) : hover ? new Color(0x30291D) : new Color(0x242126);
            Color bottom = pressed ? new Color(0x121114) : hover ? new Color(0x241E15) : new Color(0x1B191E);
            g.setPaint(new GradientPaint(0, 1, top, 0, h - 1, bottom));
            g.fillRoundRect(1, 1, w - 2, h - 2, radius, radius);
            g.setColor(new Color(255, 245, 220, 14));
            g.drawLine(radius / 2, 2, w - radius / 2, 2);
            g.setColor(isFocusOwner() ? PALE : hover ? LINE_GOLD : LINE);
            g.drawRoundRect(1, 1, w - 3, h - 3, radius, radius);
        }
        g.dispose();
        super.paintComponent(graphics);
    }
}
