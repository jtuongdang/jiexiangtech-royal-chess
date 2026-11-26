package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.awt.image.BufferedImage;
import com.jiexiangtech.chess.domain.Piece;

/** Simulates subtle glossy marble reflections beneath pieces, strictly clipped to the 8x8 board polygon. */
final class ReflectionRenderer {
    private final AssetManager assets;

    ReflectionRenderer(AssetManager assets) {
        this.assets = assets;
    }

    void paintPieceReflection(Graphics2D g, Piece piece, double anchorX, double anchorY,
                              int pieceWidth, int pieceHeight, double liftPixels) {
        if (piece == null || liftPixels > 6.0) return;
        BufferedImage img = assets.piece(piece);
        if (img == null) return;
        Rectangle src = assets.pieceBounds(piece);

        int refH = Math.max(6, (int) Math.round(pieceHeight * 0.32));
        int rx = (int) Math.round(anchorX - pieceWidth / 2.0);
        int ry = (int) Math.round(anchorY - 1);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.SrcOver.derive(0.14f));
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        // Vertically inverted crop of the lower 36% of the piece base
        int srcBaseH = Math.max(2, (int) Math.round(src.height * 0.36));
        int srcY0 = src.y + src.height;
        int srcY1 = src.y + src.height - srcBaseH;
        g2.drawImage(img, rx, ry, rx + pieceWidth, ry + refH,
                src.x, srcY0, src.x + src.width, srcY1, null);
        g2.dispose();
    }
}
