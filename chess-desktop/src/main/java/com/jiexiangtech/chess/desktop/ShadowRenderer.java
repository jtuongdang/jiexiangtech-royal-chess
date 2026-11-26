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
        // The 3D piece sprite pedestal has a curved bottom rim (the widest base equator sits ~4.5% of pieceWidth
        // above the lowest pixel). Centering the contact shadow on the base plane (anchorY - baseCurveY)
        // eliminates the visual gap that otherwise makes grounded pieces look like they are hovering!
        double basePlaneY = anchorY - pieceWidth * 0.052 + liftPixels * 0.14;

        // Directional cast shadow along the tabletop plane (top-center warm studio light -> slight rear/side falloff)
        if (directional) {
            double castW = pieceWidth * (0.94 + 0.15 * liftFactor);
            double castH = tileWidth * (0.20 + 0.07 * liftFactor);
            AffineTransform old = g2.getTransform();
            g2.translate(anchorX + tileWidth * 0.11 + liftPixels * 0.22, basePlaneY + liftPixels * 0.08);
            g2.rotate(0.11);
            g2.setColor(new Color(0, 0, 0, Math.round((float) (58 * (1.0 - 0.35 * liftFactor)))));
            g2.fill(new Ellipse2D.Double(-castW * 0.46, -castH * 0.50, castW, castH));
            g2.setTransform(old);
        }

        // Tight grounded contact shadow directly hugging the piece's lower pedestal rim
        double sw = pieceWidth * (0.98 + 0.10 * liftFactor);
        double sh = Math.max(3.5, tileWidth * (0.165 + 0.05 * liftFactor));
        g2.setColor(new Color(0, 0, 0, Math.round((float) (130 * (1.0 - 0.30 * liftFactor)))));
        g2.fill(new Ellipse2D.Double(anchorX - sw * 0.51, basePlaneY - sh * 0.56, sw * 1.02, sh));
        g2.setColor(new Color(0, 0, 0, Math.round((float) (185 * (1.0 - 0.40 * liftFactor)))));
        g2.fill(new Ellipse2D.Double(anchorX - sw * 0.42, basePlaneY - sh * 0.42, sw * 0.84, sh * 0.68));
        g2.dispose();
    }
}
