package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.awt.image.BufferedImage;

/** Soft cinematic dark corner vignette focusing player attention squarely on the board. */
final class VignetteRenderer {
    private final AssetManager assets;

    VignetteRenderer(AssetManager assets) {
        this.assets = assets;
    }

    void paint(Graphics2D g, int width, int height, float opacity) {
        if (width <= 0 || height <= 0 || opacity <= 0.01f) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.SrcOver.derive(Math.min(1f, opacity)));
        BufferedImage mask = assets.focusAsset("lighting/vignette-mask.png");
        if (mask != null) {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(mask, 0, 0, width, height, null);
        }
        g2.dispose();
    }
}
