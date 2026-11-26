package com.jiexiangtech.chess.desktop;

import java.awt.*;
import javax.swing.JButton;
import javax.swing.border.EmptyBorder;
import static com.jiexiangtech.chess.desktop.RoyalStyle.*;

final class RoyalIconButton extends JButton {
    private RoyalIcons.Glyph glyph;
    private boolean active;
    private boolean danger;
    private final int iconSize;

    RoyalIconButton(RoyalIcons.Glyph glyph, String tooltip, Runnable action) {
        this(glyph, tooltip, 17, 38, action);
    }

    RoyalIconButton(RoyalIcons.Glyph glyph, String tooltip, int iconSize, int boxSize, Runnable action) {
        this.glyph = glyph;
        this.iconSize = iconSize;
        setToolTipText(tooltip);
        getAccessibleContext().setAccessibleName(tooltip);
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setRolloverEnabled(true);
        setBorder(new EmptyBorder(4, 4, 4, 4));
        setPreferredSize(new Dimension(boxSize, boxSize));
        setMinimumSize(new Dimension(boxSize, boxSize));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (action != null) addActionListener(e -> action.run());
    }

    void setGlyph(RoyalIcons.Glyph glyph) { this.glyph = glyph; repaint(); }
    void setActive(boolean active) { this.active = active; repaint(); }
    void setDanger(boolean danger) { this.danger = danger; repaint(); }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        var m = getModel();
        boolean hover = isEnabled() && m.isRollover();
        boolean pressed = isEnabled() && m.isPressed();

        if (!isEnabled()) {
            g.setColor(new Color(0x161518));
            g.fillRoundRect(1, 1, w - 2, h - 2, 10, 10);
            g.setColor(new Color(0x28241D));
            g.drawRoundRect(1, 1, w - 3, h - 3, 10, 10);
        } else if (active) {
            g.setPaint(new GradientPaint(0, 1, new Color(0x3D3019), 0, h - 1, new Color(0x282012)));
            g.fillRoundRect(1, 1, w - 2, h - 2, 10, 10);
            g.setColor(GOLD);
            g.drawRoundRect(1, 1, w - 3, h - 3, 10, 10);
        } else if (danger) {
            g.setColor(pressed ? new Color(0x2C1014) : hover ? DANGER_HOVER : DANGER_BG);
            g.fillRoundRect(1, 1, w - 2, h - 2, 10, 10);
            g.setColor(hover ? DANGER_TEXT : DANGER_BORDER);
            g.drawRoundRect(1, 1, w - 3, h - 3, 10, 10);
        } else {
            Color bg = pressed ? new Color(0x17151A) : hover ? new Color(0x2E271C) : new Color(0x1E1C21);
            g.setColor(bg);
            g.fillRoundRect(1, 1, w - 2, h - 2, 10, 10);
            g.setColor(isFocusOwner() ? PALE : hover ? LINE_GOLD : LINE);
            g.drawRoundRect(1, 1, w - 3, h - 3, 10, 10);
        }

        Color tint = !isEnabled() ? new Color(0x5A554E)
                : active ? PALE
                : danger ? DANGER_TEXT
                : hover ? PALE : IVORY;
        RoyalIcons.draw(g, glyph, (w - iconSize) / 2, (h - iconSize) / 2, iconSize, tint);
        g.dispose();
    }
}
