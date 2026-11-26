package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.awt.image.BufferedImage;

/** Renders the warm overhead golden focus spotlight centered over the 2.5D chessboard. */
final class SpotlightRenderer {
    private final AssetManager assets;

    SpotlightRenderer(AssetManager assets) {
        this.assets = assets;
    }

    void paint(Graphics2D g, int width, int height, float intensity) {
        if (width <= 0 || height <= 0 || intensity <= 0.01f) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.SrcOver.derive(Math.min(1f, intensity)));
        BufferedImage mask = assets.focusAsset("lighting/spotlight-mask.png");
        int sw = (int) Math.round(width * 0.96);
        int sh = (int) Math.round(height * 0.94);
        int sx = (width - sw) / 2;
        int sy = (int) Math.round(height * 0.04);
        if (mask != null) {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(mask, sx, sy, sw, sh, null);
        } else {
            RoyalGlowEffect.paintAmbientSpotlight(g2, width * 0.5f, height * 0.52f, Math.max(width, height) * 0.48f,
                    new Color(236, 192, 82, 82));
        }
        g2.dispose();
    }
}
