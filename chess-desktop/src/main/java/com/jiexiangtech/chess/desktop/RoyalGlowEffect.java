package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import static com.jiexiangtech.chess.desktop.RoyalStyle.*;

final class RoyalGlowEffect {
    private RoyalGlowEffect() {}

    static void paintOuterGlow(Graphics2D g, int x, int y, int w, int h, int radius, Color glowColor, int layers) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (int i = layers; i >= 1; i--) {
            float ratio = (float) i / layers;
            int alpha = Math.max(4, Math.round(glowColor.getAlpha() * (1f - ratio * 0.72f) / layers));
            g2.setColor(new Color(glowColor.getRed(), glowColor.getGreen(), glowColor.getBlue(), alpha));
            g2.setStroke(new BasicStroke(i * 2.2f));
            g2.draw(new RoundRectangle2D.Double(x - i * 0.5, y - i * 0.5, w + i, h + i, radius + i, radius + i));
        }
        g2.dispose();
    }

    static void paintMetallicGoldBorder(Graphics2D g, int x, int y, int w, int h, int radius, float strokeWidth, boolean bright) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color c1 = bright ? new Color(0xFAE4A4) : new Color(0xB89344);
        Color c2 = bright ? new Color(0xD4AF37) : new Color(0x695126);
        Color c3 = bright ? new Color(0xF5D989) : new Color(0x8C6D33);
        g2.setPaint(new LinearGradientPaint(x, y, x + w, y + h,
                new float[]{0f, 0.5f, 1f}, new Color[]{c1, c2, c3}));
        g2.setStroke(new BasicStroke(strokeWidth));
        g2.draw(new RoundRectangle2D.Double(x + strokeWidth / 2f, y + strokeWidth / 2f,
                w - strokeWidth, h - strokeWidth, radius, radius));
        g2.dispose();
    }

    static void paintAmbientSpotlight(Graphics2D g, float cx, float cy, float radius, Color centerColor) {
        if (radius <= 1f) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setPaint(new RadialGradientPaint(cx, cy, radius,
                new float[]{0f, 0.55f, 1f},
                new Color[]{centerColor,
                        new Color(centerColor.getRed(), centerColor.getGreen(), centerColor.getBlue(), centerColor.getAlpha() / 3),
                        new Color(0, 0, 0, 0)}));
        g2.fillOval(Math.round(cx - radius), Math.round(cy - radius), Math.round(radius * 2), Math.round(radius * 2));
        g2.dispose();
    }
}
