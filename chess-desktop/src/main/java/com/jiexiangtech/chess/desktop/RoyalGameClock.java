package com.jiexiangtech.chess.desktop;

import java.awt.*;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import static com.jiexiangtech.chess.desktop.RoyalStyle.*;

final class RoyalGameClock extends JLabel {
    private boolean active;
    private boolean lowTime;
    private boolean timed = true;

    RoyalGameClock() {
        super("—", SwingConstants.CENTER);
        setOpaque(false);
        setFont(mono(Font.BOLD, 22f));
        setForeground(MUTED);
        setBorder(new EmptyBorder(6, 14, 6, 14));
        setPreferredSize(new Dimension(118, 40));
        setMinimumSize(new Dimension(104, 38));
    }

    void update(String text, boolean timed, boolean active, boolean lowTime) {
        this.timed = timed;
        this.active = active;
        this.lowTime = timed && lowTime;
        setText(timed ? text : "∞");
        setForeground(!timed ? MUTED : this.lowTime && active ? WARN_CLOCK : active ? PALE : new Color(0x989186));
        repaint();
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        if (active && lowTime) {
            g.setPaint(new GradientPaint(0, 0, new Color(0x3D181B), 0, h, new Color(0x240E10)));
            g.fillRoundRect(0, 0, w, h, 11, 11);
            g.setColor(new Color(235, 95, 85, 195));
            g.drawRoundRect(0, 0, w - 1, h - 1, 11, 11);
        } else if (active && timed) {
            g.setPaint(new GradientPaint(0, 0, new Color(0x2B2314), 0, h, new Color(0x1A150D)));
            g.fillRoundRect(0, 0, w, h, 11, 11);
            g.setColor(new Color(212, 175, 55, 175));
            g.drawRoundRect(0, 0, w - 1, h - 1, 11, 11);
        } else {
            g.setColor(new Color(0x111014));
            g.fillRoundRect(0, 0, w, h, 11, 11);
            g.setColor(new Color(0x2A251D));
            g.drawRoundRect(0, 0, w - 1, h - 1, 11, 11);
        }
        g.dispose();
        super.paintComponent(graphics);
    }
}
