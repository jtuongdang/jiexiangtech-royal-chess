package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;

/** Renders tabletop board drop shadows, grounded contact shadows, and directional light cast shadows. */
final class ShadowRenderer {
    private final AssetManager assets;

    ShadowRenderer(AssetManager assets) {
        this.assets = assets;
    }

    void paintBoardDropShadow(Graphics2D g, PerspectiveCoordinateMapper mapper) {
        BufferedImage mask = assets.focusAsset("shadows/board-shadow.png");
        Rectangle box = mapper.boardBoundingBox();
        if (mask != null) {
            g.drawImage(mask, box.x - 28, box.y + 8, box.width + 56, box.height + 34, null);
        } else {
            g.setColor(new Color(0, 0, 0, 155));
            g.fillRoundRect(box.x, box.y + 12, box.width, box.height, 28, 28);
        }
    }

    void paintPieceShadow(Graphics2D g, double anchorX, double anchorY, double pieceWidth, double tileWidth,
                          double liftPixels, boolean directional) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        double liftFactor = Math.max(0.0, Math.min(1.0, liftPixels / Math.max(12.0, tileWidth)));

        // Directional cast shadow along the tabletop plane (top-center warm studio light -> slight rear/side falloff)
        if (directional) {
            double castW = pieceWidth * (0.95 + 0.15 * liftFactor);
            double castH = tileWidth * (0.28 + 0.08 * liftFactor);
            AffineTransform old = g2.getTransform();
            g2.translate(anchorX + tileWidth * 0.14 + liftPixels * 0.22, anchorY + tileWidth * 0.04 + liftPixels * 0.10);
            g2.rotate(0.14);
            g2.setColor(new Color(0, 0, 0, Math.round((float) (62 * (1.0 - 0.35 * liftFactor)))));
            g2.fill(new Ellipse2D.Double(-castW * 0.45, -castH * 0.48, castW, castH));
            g2.setTransform(old);
        }

        // Tight grounded contact shadow directly beneath piece base
        double sw = pieceWidth * (0.98 + 0.12 * liftFactor);
        double sh = Math.max(4.0, tileWidth * (0.22 + 0.06 * liftFactor));
        g2.setColor(new Color(0, 0, 0, Math.round((float) (115 * (1.0 - 0.30 * liftFactor)))));
        g2.fill(new Ellipse2D.Double(anchorX - sw * 0.52, anchorY - sh * 0.46, sw * 1.04, sh));
        g2.setColor(new Color(0, 0, 0, Math.round((float) (165 * (1.0 - 0.40 * liftFactor)))));
        g2.fill(new Ellipse2D.Double(anchorX - sw * 0.39, anchorY - sh * 0.34, sw * 0.78, sh * 0.68));
        g2.dispose();
    }
}
