package com.jiexiangtech.chess.desktop;

import java.awt.*;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import static com.jiexiangtech.chess.desktop.RoyalStyle.*;

class RoyalPanel extends JPanel {
    private final Color topColor;
    private final Color bottomColor;
    private Color borderColor;
    private final int radius;

    RoyalPanel(LayoutManager layout) {
        this(layout, new Color(0x18161B), SURFACE, LINE, 16);
    }

    RoyalPanel(LayoutManager layout, Color topColor, Color bottomColor, Color borderColor, int radius) {
        super(layout);
        this.topColor = topColor;
        this.bottomColor = bottomColor;
        this.borderColor = borderColor;
        this.radius = radius;
        setOpaque(false);
        setBorder(new EmptyBorder(16, 18, 16, 18));
    }

    void setBorderColor(Color borderColor) {
        this.borderColor = borderColor;
        repaint();
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        g.setPaint(new GradientPaint(0, 0, topColor, 0, h, bottomColor));
        g.fillRoundRect(0, 0, w, h, radius, radius);
        // Hairline inner top highlight
        g.setColor(new Color(255, 245, 215, 16));
        g.drawRoundRect(1, 1, w - 3, h - 3, Math.max(2, radius - 2), Math.max(2, radius - 2));
        g.setColor(borderColor);
        g.drawRoundRect(0, 0, w - 1, h - 1, radius, radius);
        g.dispose();
        super.paintComponent(graphics);
    }
}
