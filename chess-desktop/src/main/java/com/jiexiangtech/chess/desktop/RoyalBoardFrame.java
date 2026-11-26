package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

final class RoyalBoardFrame {
    static final int BEZEL = 26;

    private RoyalBoardFrame() {}

    static void paintOuterFrame(Graphics2D g, BoardCoordinateMapper board) {
        int x = board.x(), y = board.y(), size = board.tile() * 8;
        int fx = x - BEZEL, fy = y - BEZEL, fw = size + BEZEL * 2, fh = size + BEZEL * 2;

        // 1. Subtle golden ambient halo radiating behind the board frame
        float cx = x + size / 2f, cy = y + size / 2f;
        RoyalGlowEffect.paintAmbientSpotlight(g, cx, cy, size * 0.68f, new Color(218, 178, 68, 54));

        // 2. Deep floating drop shadow
        g.setColor(new Color(0, 0, 0, 175));
        g.fillRoundRect(fx - 4, fy + 4, fw + 8, fh + 6, 18, 18);

        // 3. Outer beveled metallic gold molding
        g.setPaint(new LinearGradientPaint(fx, fy, fx + fw, fy + fh,
                new float[]{0f, 0.28f, 0.52f, 0.78f, 1f},
                new Color[]{new Color(0xF6DE98), new Color(0xB98D2E), new Color(0x6E4F18),
                        new Color(0xC99E3A), new Color(0xF3D786)}));
        g.fillRoundRect(fx, fy, fw, fh, 14, 14);

        // Crisp top-left bright bevel highlight + bottom-right dark bronze bevel
        g.setColor(new Color(255, 245, 205, 130));
        g.drawRoundRect(fx + 1, fy + 1, fw - 3, fh - 3, 13, 13);

        // 4. Recessed obsidian-walnut coordinate channel
        int inset = 4;
        g.setPaint(new GradientPaint(fx + inset, fy + inset, new Color(0x211912),
                fx + fw - inset, fy + fh - inset, new Color(0x110E0B)));
        g.fillRoundRect(fx + inset, fy + inset, fw - inset * 2, fh - inset * 2, 10, 10);

        // Hairline inner bronze molding inside channel
        g.setColor(new Color(196, 154, 62, 115));
        g.drawRoundRect(fx + inset + 1, fy + inset + 1, fw - (inset + 1) * 2 - 1, fh - (inset + 1) * 2 - 1, 9, 9);

        // 5. Tight metallic gold fillet framing the 8x8 playing grid
        g.setPaint(new LinearGradientPaint(x - 3, y - 3, x + size + 3, y + size + 3,
                new float[]{0f, 0.5f, 1f},
                new Color[]{new Color(0xF2D37D), new Color(0xA67F28), new Color(0xE8C466)}));
        g.fillRect(x - 3, y - 3, size + 6, size + 6);
        g.setColor(new Color(0x120E0A));
        g.fillRect(x - 1, y - 1, size + 2, size + 2);

        // 6. Ornate metallic gold decorative corner accents at all 4 corners
        drawCornerFiligree(g, fx + 3, fy + 3, 1, 1);
        drawCornerFiligree(g, fx + fw - 3, fy + 3, -1, 1);
        drawCornerFiligree(g, fx + 3, fy + fh - 3, 1, -1);
        drawCornerFiligree(g, fx + fw - 3, fy + fh - 3, -1, -1);
    }

    static void paintInnerShadowAndCoordinates(Graphics2D g, BoardCoordinateMapper board) {
        int x = board.x(), y = board.y(), size = board.tile() * 8;

        // Subtle inner shadow around the 64-square marble perimeter
        g.setColor(new Color(0, 0, 0, 95));
        g.drawRect(x, y, size - 1, size - 1);
        g.setColor(new Color(0, 0, 0, 42));
        g.drawRect(x + 1, y + 1, size - 3, size - 3);

        // Clean champagne-gold rank (1-8) and file (A-H) coordinates in recessed channel
        Font font = RoyalStyle.sans(Font.BOLD, Math.max(11f, Math.min(13f, board.tile() * 0.165f)));
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics(font);
        g.setColor(new Color(0xE3C983));
        int channel = BEZEL - 4;
        for (int i = 0; i < 8; i++) {
            String file = String.valueOf((char) (board.flipped() ? 'H' - i : 'A' + i));
            String rank = String.valueOf((char) (board.flipped() ? '1' + i : '8' - i));
            int fx = x + board.tile() * i + (board.tile() - fm.stringWidth(file)) / 2;
            int fy = y + size + 3 + (channel + fm.getAscent() - fm.getDescent()) / 2;
            g.drawString(file, fx, fy);

            int rx = x - 3 - (channel + fm.stringWidth(rank)) / 2;
            int ry = y + board.tile() * i + (board.tile() + fm.getAscent() - fm.getDescent()) / 2;
            g.drawString(rank, rx, ry);
        }
    }

    private static void drawCornerFiligree(Graphics2D g, int ox, int oy, int sx, int sy) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.translate(ox, oy);
        g2.scale(sx, sy);

        // Sculpted metallic gold L-bracket + corner gem accent
        Path2D bracket = new Path2D.Double();
        bracket.moveTo(0, 15);
        bracket.lineTo(0, 2);
        bracket.quadTo(0, 0, 2, 0);
        bracket.lineTo(15, 0);
        g2.setColor(new Color(0xF9E5A8));
        g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(bracket);

        // Inner filigree scroll
        Path2D scroll = new Path2D.Double();
        scroll.moveTo(4, 11);
        scroll.quadTo(4, 4, 11, 4);
        g2.setColor(new Color(0xC99E38));
        g2.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(scroll);

        // Corner diamond rivet
        g2.setColor(new Color(0xFFF0BE));
        g2.fill(new RoundRectangle2D.Double(3.2, 3.2, 3.2, 3.2, 1.5, 1.5));
        g2.dispose();
    }
}
