package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import com.jiexiangtech.chess.domain.*;

/** Top-down Classic 2D Royal Luxury board renderer. */
final class Classic2DBoardRenderer implements BoardRenderer {
    private final AssetManager assets;
    private final RoyalPieceRenderer pieceRenderer;

    Classic2DBoardRenderer(AssetManager assets, RoyalPieceRenderer pieceRenderer) {
        this.assets = assets;
        this.pieceRenderer = pieceRenderer;
    }

    @Override
    public int squareAt(int width, int height, boolean flipped, double px, double py, Position position, int pieceScale) {
        return BoardCoordinateMapper.fit(width, height, flipped).squareAt((int) Math.round(px), (int) Math.round(py));
    }

    @Override
    public void paint(Graphics2D g, RenderState s) {
        BoardCoordinateMapper board = BoardCoordinateMapper.fit(s.width(), s.height(), s.flipped());
        RoyalBoardFrame.paintOuterFrame(g, board);

        for (int square = 0; square < 64; square++) drawTile(g, board.bounds(square), square, s.theme());

        if (s.last() != null) {
            fill(g, board.bounds(s.last().from()), new Color(238, 198, 86, 68));
            fill(g, board.bounds(s.last().to()), new Color(244, 208, 96, 94));
        }

        if (s.hovered() >= 0 && s.hovered() != s.selected() && s.playable()
                && (isOwn(s.position(), s.hovered()) || isCandidate(s.candidates(), s.hovered()))) {
            Rectangle hArea = board.bounds(s.hovered());
            fill(g, hArea, new Color(246, 218, 134, 34));
            outline(g, hArea, new Color(246, 218, 134, 155), 1.8f);
        }

        if (s.selected() >= 0) {
            Rectangle sArea = board.bounds(s.selected());
            fill(g, sArea, new Color(255, 224, 114, Math.round(115 + 35 * s.pulse())));
            RoyalGlowEffect.paintAmbientSpotlight(g, (float) sArea.getCenterX(), (float) sArea.getCenterY(),
                    sArea.width * 0.72f, new Color(255, 232, 138, Math.round(135 + 45 * s.pulse())));
            RoyalGlowEffect.paintOuterGlow(g, sArea.x, sArea.y, sArea.width, sArea.height, 4,
                    new Color(255, 222, 112, Math.round(165 + 50 * s.pulse())), 4);
            outline(g, sArea, new Color(0xFFF6C8), 3.0f);
        }

        if (s.check()) {
            Rectangle king = board.bounds(s.position().king(s.position().turn()));
            g.setPaint(new RadialGradientPaint((float) king.getCenterX(), (float) king.getCenterY(), board.tile() * 0.70f,
                    new float[]{0f, 0.62f, 1f},
                    new Color[]{new Color(235, 44, 36, Math.round(205 + 40 * s.pulse())),
                            new Color(180, 22, 16, 135), new Color(95, 10, 8, 22)}));
            g.fill(king);
            outline(g, king, new Color(255, 118, 102, Math.round(180 + 60 * s.pulse())), 2.2f);
        }

        for (Move candidate : s.candidates()) {
            drawLegal(g, board.bounds(candidate.to()), s.position().captured(candidate) != null, s.pulse());
        }

        if (s.hint() != null) {
            Rectangle hf = board.bounds(s.hint().from()), ht = board.bounds(s.hint().to());
            fill(g, hf, new Color(112, 227, 156, 48));
            fill(g, ht, new Color(112, 227, 156, 68));
            outline(g, hf, RoyalStyle.SUCCESS, 2.2f);
            outline(g, ht, RoyalStyle.SUCCESS, 2.2f);
        }

        double t = s.moveProgress();
        if (t < 1.0 && s.disappearing() != null && s.capturedSquare() >= 0) {
            g.setComposite(AlphaComposite.SrcOver.derive((float) Math.max(0.0, 1.0 - t)));
            pieceRenderer.drawPiece(g, s.disappearing(), board.bounds(s.capturedSquare()),
                    s.preferences().pieceScale(), s.preferences().highQuality(), isDark(s.capturedSquare()), false);
            g.setComposite(AlphaComposite.SrcOver);
        }

        for (int square = 0; square < 64; square++) {
            Piece piece = s.position().piece(square);
            if (piece != null && !(square == s.selected() && s.dragPoint() != null)
                    && !(t < 1.0 && s.last() != null && square == s.last().to())) {
                pieceRenderer.drawPiece(g, piece, board.bounds(square),
                        s.preferences().pieceScale(), s.preferences().highQuality(), isDark(square), false);
            }
        }

        if (t < 1.0 && s.last() != null) {
            Rectangle from = board.bounds(s.last().from()), to = board.bounds(s.last().to());
            double eased = s.easedMoveProgress();
            Rectangle moving = new Rectangle(
                    from.x + (int) Math.round((to.x - from.x) * eased),
                    from.y + (int) Math.round((to.y - from.y) * eased),
                    from.width, from.height);
            pieceRenderer.drawPiece(g, s.position().piece(s.last().to()), moving,
                    s.preferences().pieceScale(), s.preferences().highQuality(), isDark(s.last().to()), false);
        }

        if (s.hint() != null) drawHintArrow(g, board.bounds(s.hint().from()), board.bounds(s.hint().to()));

        if (s.dragPoint() != null && s.selected() >= 0) {
            int tile = board.tile();
            pieceRenderer.drawPiece(g, s.position().piece(s.selected()),
                    new Rectangle(s.dragPoint().x - tile / 2, s.dragPoint().y - tile / 2, tile, tile),
                    s.preferences().pieceScale(), s.preferences().highQuality(), true, true);
        }

        RoyalBoardFrame.paintInnerShadowAndCoordinates(g, board);
        if (s.celebrating()) paintSparks(g, board.x() + board.tile() * 4, board.y() + board.tile() * 4, board.tile() * 8, s.celebrationProgress());
        if (s.overlayTitle() != null) paintOutcomeOverlay(g, board.x(), board.y(), board.tile() * 8, board.tile() * 8,
                s.overlayTitle(), s.overlaySubtitle(), s.overlayAccent(), s.overlayAlpha());
    }

    private void drawTile(Graphics2D g, Rectangle area, int square, String theme) {
        boolean light = !isDark(square);
        BufferedImage image = assets.tile(light);
        if (image == null) fill(g, area, light ? new Color(0xCBB28E) : new Color(0x2D241E));
        else g.drawImage(image, area.x, area.y, area.width, area.height, null);
        Color glaze = ThemeManager.glaze(theme, light);
        if (glaze != null) fill(g, area, glaze);
    }

    private static boolean isDark(int sq) { return (Square.file(sq) + Square.rank(sq)) % 2 == 0; }
    private static boolean isOwn(Position pos, int sq) { Piece p = pos.piece(sq); return p != null && p.side() == pos.turn(); }
    private static boolean isCandidate(java.util.List<Move> list, int sq) {
        for (Move m : list) if (m.to() == sq) return true;
        return false;
    }

    private static void fill(Graphics2D g, Rectangle area, Color color) { g.setColor(color); g.fill(area); }

    private static void outline(Graphics2D g, Rectangle area, Color color, float stroke) {
        g.setColor(color);
        g.setStroke(new BasicStroke(stroke));
        int inset = Math.max(1, Math.round(stroke / 2f));
        g.drawRect(area.x + inset, area.y + inset, area.width - inset * 2 - 1, area.height - inset * 2 - 1);
        g.setStroke(new BasicStroke(1));
    }

    private static void drawLegal(Graphics2D g, Rectangle area, boolean capture, float pulse) {
        double cx = area.getCenterX(), cy = area.getCenterY();
        if (capture) {
            double r = area.width * 0.43;
            g.setColor(new Color(0, 0, 0, 120));
            g.setStroke(new BasicStroke(Math.max(4f, area.width * 0.085f)));
            g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
            g.setColor(new Color(246, 212, 108, Math.round(205 + 40 * pulse)));
            g.setStroke(new BasicStroke(Math.max(2.6f, area.width * 0.052f)));
            g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
            g.setStroke(new BasicStroke(1));
        } else {
            RoyalGlowEffect.paintAmbientSpotlight(g, (float) cx, (float) cy, area.width * 0.44f,
                    new Color(172, 198, 106, Math.round(62 + 26 * pulse)));
            double r = Math.max(6.5, area.width * 0.21);
            g.setColor(new Color(176, 194, 108, Math.round(135 + 35 * pulse)));
            g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
            g.setColor(new Color(235, 240, 175, Math.round(145 + 45 * pulse)));
            g.setStroke(new BasicStroke(1.5f));
            g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
            g.setStroke(new BasicStroke(1));
        }
    }

    static void drawHintArrow(Graphics2D g, Rectangle from, Rectangle to) {
        drawArrow(g, from.getCenterX(), from.getCenterY(), to.getCenterX(), to.getCenterY(), from.width);
    }

    static void drawArrow(Graphics2D g, double x1, double y1, double x2, double y2, double refSize) {
        double angle = Math.atan2(y2 - y1, x2 - x1);
        double head = Math.max(12, refSize * 0.25);
        double sx = x1 + Math.cos(angle) * refSize * 0.16;
        double sy = y1 + Math.sin(angle) * refSize * 0.16;
        double ex = x2 - Math.cos(angle) * head * 0.72;
        double ey = y2 - Math.sin(angle) * head * 0.72;

        g.setColor(new Color(112, 227, 156, 190));
        g.setStroke(new BasicStroke((float) Math.max(4.0, refSize * 0.085), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(sx, sy, ex, ey));

        Path2D arrow = new Path2D.Double();
        arrow.moveTo(x2 - Math.cos(angle) * refSize * 0.06, y2 - Math.sin(angle) * refSize * 0.06);
        arrow.lineTo(x2 - head * Math.cos(angle - Math.PI / 6), y2 - head * Math.sin(angle - Math.PI / 6));
        arrow.lineTo(x2 - head * Math.cos(angle + Math.PI / 6), y2 - head * Math.sin(angle + Math.PI / 6));
        arrow.closePath();
        g.fill(arrow);
        g.setStroke(new BasicStroke(1));
    }

    static void paintSparks(Graphics2D g, double cx, double cy, double span, double t) {
        g.setColor(new Color(246, 207, 113, (int) (220 * (1 - t))));
        for (int i = 0; i < 42; i++) {
            double angle = i * 2.39996;
            double r = span * (.14 + (i % 7) * .026) * t;
            int x = (int) Math.round(cx + Math.cos(angle) * r * 2.5);
            int y = (int) Math.round(cy + Math.sin(angle) * r * 2.5 + t * t * 42);
            g.fillOval(x, y, 2 + i % 4, 2 + i % 4);
        }
    }

    static void paintOutcomeOverlay(Graphics2D g, int x, int y, int w, int h,
                                    String title, String subtitle, Color accent, float alpha) {
        if (alpha <= 0.01f) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.SrcOver.derive(alpha));
        g2.setColor(new Color(8, 7, 10, 172));
        g2.fillRoundRect(x, y, w, h, 14, 14);

        int cw = Math.min(w - 40, 340), ch = 126;
        int cx = x + (w - cw) / 2, cy = y + (h - ch) / 2;
        RoyalGlowEffect.paintOuterGlow(g2, cx, cy, cw, ch, 16,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 115), 4);
        g2.setPaint(new GradientPaint(cx, cy, new Color(0x211D16), cx, cy + ch, new Color(0x111014)));
        g2.fillRoundRect(cx, cy, cw, ch, 16, 16);
        RoyalGlowEffect.paintMetallicGoldBorder(g2, cx, cy, cw, ch, 16, 1.8f, true);

        g2.setFont(RoyalStyle.serif(Font.BOLD, 34));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(accent);
        g2.drawString(title, cx + (cw - fm.stringWidth(title)) / 2, cy + 62);

        if (subtitle != null) {
            g2.setFont(RoyalStyle.sans(Font.PLAIN, 14f));
            FontMetrics sfm = g2.getFontMetrics();
            g2.setColor(RoyalStyle.IVORY);
            g2.drawString(subtitle, cx + (cw - sfm.stringWidth(subtitle)) / 2, cy + 94);
        }
        g2.dispose();
    }
}
