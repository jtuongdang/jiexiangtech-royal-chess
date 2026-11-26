package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JCheckBox;
import static com.jiexiangtech.chess.desktop.RoyalStyle.*;

final class RoyalToggle extends JCheckBox {
    private boolean hovered;

    RoyalToggle(String text, boolean selected) {
        super(text, selected);
        setOpaque(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setForeground(IVORY);
        setFont(sans(Font.PLAIN, 14f));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setIconTextGap(12);
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { hovered = false; repaint(); }
        });
        setIcon(new SwitchIcon());
    }

    private final class SwitchIcon implements javax.swing.Icon {
        private static final int W = 44, H = 24;

        @Override public void paintIcon(Component c, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean on = isSelected();
            if (on) {
                g.setPaint(new GradientPaint(x, y, new Color(0xE2BF56), x, y + H, GOLD_DEEP));
                g.fillRoundRect(x, y, W, H, H, H);
                g.setColor(hovered ? PALE : GOLD);
                g.drawRoundRect(x, y, W - 1, H - 1, H, H);
            } else {
                g.setColor(hovered ? new Color(0x2B2720) : new Color(0x1E1C21));
                g.fillRoundRect(x, y, W, H, H, H);
                g.setColor(hovered ? LINE_GOLD : LINE);
                g.drawRoundRect(x, y, W - 1, H - 1, H, H);
            }
            int knob = H - 6;
            int kx = on ? x + W - knob - 3 : x + 3;
            int ky = y + 3;
            g.setColor(new Color(0, 0, 0, 90));
            g.fillOval(kx, ky + 1, knob, knob);
            g.setColor(on ? IVORY : new Color(0xB5AFA4));
            g.fillOval(kx, ky, knob, knob);
            g.dispose();
        }

        @Override public int getIconWidth() { return W; }
        @Override public int getIconHeight() { return H; }
    }
}
