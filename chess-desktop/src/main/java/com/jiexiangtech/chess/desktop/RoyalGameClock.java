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
        setFont(mono(Font.BOLD, 23f));
        setForeground(MUTED);
        setBorder(new EmptyBorder(6, 12, 6, 12));
        setPreferredSize(new Dimension(106, 42));
        setMinimumSize(new Dimension(96, 38));
    }

    void update(String text, boolean timed, boolean active, boolean lowTime) {
        this.timed = timed;
        this.active = active;
        this.lowTime = timed && lowTime;
        setText(timed ? text : "∞");
        setForeground(!timed ? MUTED : this.lowTime && active ? WARN_CLOCK : active ? new Color(0xF9E3A2) : new Color(0x9FA1A8));
        repaint();
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        if (active && lowTime) {
            g.setPaint(new GradientPaint(0, 0, new Color(0x42191C), 0, h, new Color(0x240E10)));
            g.fillRoundRect(0, 0, w, h, 10, 10);
            g.setColor(new Color(235, 95, 85, 200));
            g.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);
        } else if (active && timed) {
            g.setPaint(new GradientPaint(0, 0, new Color(36, 27, 13, 215), 0, h, new Color(22, 17, 9, 230)));
            g.fillRoundRect(0, 0, w, h, 10, 10);
            g.setColor(new Color(230, 188, 82, 170));
            g.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);
        } else {
            g.setColor(new Color(14, 13, 16, 205));
            g.fillRoundRect(0, 0, w, h, 10, 10);
            g.setColor(new Color(74, 62, 42, 120));
            g.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);
        }
        g.dispose();
        super.paintComponent(graphics);
    }
}
