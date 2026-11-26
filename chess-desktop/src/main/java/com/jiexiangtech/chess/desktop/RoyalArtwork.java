package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import com.jiexiangtech.chess.domain.Kind;
import com.jiexiangtech.chess.domain.Piece;
import com.jiexiangtech.chess.domain.Side;

final class RoyalArtwork extends JPanel {
    private final AssetManager assets;
    private final boolean banner;

    RoyalArtwork(AssetManager assets, boolean banner) {
        this.assets = assets;
        this.banner = banner;
        setOpaque(false);
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        int w = getWidth(), h = getHeight();
        if (w < 2 || h < 2) return;
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setPaint(new RadialGradientPaint(w * .69f, h * .44f, Math.max(w, h) * .74f,
                new float[]{0, .52f, 1}, new Color[]{new Color(0x4D3B1D), new Color(0x251D12), new Color(0x121110)}));
        g.fillRoundRect(0, 0, w, h, 22, 22);
        int center = (int) (w * (banner ? .78 : .68)), size = Math.min((int) (w * .89), (int) (h * .88));
        int base = (int) (h * (banner ? .94 : .85));
        for (int i = 1; i <= 3; i++) {
            int radius = size * (5 + i) / 13;
            g.setColor(new Color(212, 175, 55, 14 + 4 * i));
            g.drawOval(center - radius, base - size / 2 - radius, radius * 2, radius * 2);
        }
        BufferedImage board = assets.board();
        if (board != null) {
            int x = center - size * 3 / 4, height = Math.max(14, size / 5);
            g.setComposite(AlphaComposite.SrcOver.derive(.62f));
            g.drawImage(board, x, base - height * 2 / 3, size * 3 / 2, height, null);
            g.setComposite(AlphaComposite.SrcOver);
        }
        drawPiece(g, Piece.of(Side.BLACK, Kind.QUEEN), center + size / 6, base - size / 14, size * 4 / 5);
        drawPiece(g, Piece.of(Side.WHITE, Kind.KING), center - size / 7, base, size);
        g.setColor(new Color(0x554226));
        g.drawRoundRect(0, 0, w - 1, h - 1, 22, 22);
        g.dispose();
    }

    private void drawPiece(Graphics2D g, Piece piece, int center, int base, int size) {
        BufferedImage sprite = assets.piece(piece);
        if (sprite != null) g.drawImage(sprite, center - size / 2, base - size * 488 / 512, size, size, null);
    }
}
