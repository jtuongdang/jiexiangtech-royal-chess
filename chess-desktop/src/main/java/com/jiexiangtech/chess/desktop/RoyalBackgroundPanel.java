package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import static com.jiexiangtech.chess.desktop.RoyalStyle.*;

final class RoyalBackgroundPanel extends JPanel {
    private final BufferedImage artwork;
    private final int radius;
    private final boolean heroScrim;
    private final Color glowColor;

    RoyalBackgroundPanel(LayoutManager layout, BufferedImage artwork, int radius, boolean heroScrim) {
        this(layout, artwork, radius, heroScrim, new Color(212, 175, 55, 22));
    }

    RoyalBackgroundPanel(LayoutManager layout, BufferedImage artwork, int radius, boolean heroScrim, Color glowColor) {
        super(layout);
        this.artwork = artwork;
        this.radius = radius;
        this.heroScrim = heroScrim;
        this.glowColor = glowColor;
        setOpaque(radius == 0);
        setBackground(BG);
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) { g.dispose(); return; }

        Shape clip = radius > 0 ? new RoundRectangle2D.Double(0, 0, w, h, radius, radius) : new Rectangle(0, 0, w, h);
        g.setClip(clip);

        // Deep obsidian base
        g.setPaint(new GradientPaint(0, 0, new Color(0x0E0D10), w, h, new Color(0x09090B)));
        g.fillRect(0, 0, w, h);

        if (artwork != null) {
            BrandingManager.drawCover(g, artwork, w, h);
        }

        if (heroScrim) {
            // Left-to-right cinematic readability scrim so foreground copy & buttons always pop
            g.setPaint(new LinearGradientPaint(0, 0, w, 0,
                    new float[]{0f, 0.48f, 0.78f, 1f},
                    new Color[]{new Color(10, 9, 11, 225), new Color(14, 12, 14, 170),
                            new Color(14, 12, 14, 55), new Color(10, 9, 11, 95)}));
            g.fillRect(0, 0, w, h);
            g.setPaint(new GradientPaint(0, h * 0.65f, new Color(10, 9, 11, 0), 0, h, new Color(10, 9, 11, 145)));
            g.fillRect(0, 0, w, h);
        } else {
            // Subtle warm ambient gold glow for atmospheric app canvas
            float r = Math.max(w, h) * 0.62f;
            g.setPaint(new RadialGradientPaint(w * 0.45f, h * 0.18f, Math.max(1f, r),
                    new float[]{0f, 1f}, new Color[]{glowColor, new Color(0, 0, 0, 0)}));
            g.fillRect(0, 0, w, h);
        }

        g.setClip(null);
        if (radius > 0) {
            g.setColor(new Color(110, 85, 43, 150));
            g.drawRoundRect(0, 0, w - 1, h - 1, radius, radius);
        }
        g.dispose();
    }
}
