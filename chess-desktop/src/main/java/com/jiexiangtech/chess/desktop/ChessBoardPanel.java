package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.JPanel;
import com.jiexiangtech.chess.application.Preferences;
import com.jiexiangtech.chess.domain.*;

final class ChessBoardPanel extends JPanel {
    interface PromotionPicker { Kind choose(List<Move> options); }

    private final AssetManager assets;
    private final RoyalPieceRenderer pieceRenderer;
    private final RoyalAnimationEngine animator;
    private final Preferences preferences;
    private final String theme;
    private final BooleanSupplier playable;
    private final PromotionPicker promotion;
    private final Consumer<Move> onMove;
    private Position position;
    private Move last, hint;
    private int selected = -1, pressed = -1, hovered = -1;
    private Point origin, mouse;
    private List<Move> candidates = List.of();
    private boolean flipped, check;
    private Piece disappearing;
    private int capturedSquare = -1;
    private String overlayTitle, overlaySubtitle;
    private Color overlayAccent = RoyalStyle.PALE;

    ChessBoardPanel(AssetManager assets, Preferences preferences, String theme, BooleanSupplier playable,
                    PromotionPicker promotion, Consumer<Move> onMove) {
        this.assets = assets;
        this.pieceRenderer = new RoyalPieceRenderer(assets);
        this.animator = new RoyalAnimationEngine(this);
        this.animator.setEnabled(preferences.animation());
        this.preferences = preferences;
        this.theme = theme;
        this.playable = playable;
        this.promotion = promotion;
        this.onMove = onMove;
        setOpaque(false);
        setPreferredSize(new Dimension(740, 740));
        setMinimumSize(new Dimension(280, 280));

        var input = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent event) {
                if (position == null || !playable.getAsBoolean()) {
                    if (hovered != -1) { hovered = -1; setCursor(Cursor.getDefaultCursor()); repaint(); }
                    return;
                }
                int sq = geometry().squareAt(event.getX(), event.getY());
                boolean interactive = sq >= 0 && (isOwnPiece(sq) || isCandidateTarget(sq));
                setCursor(Cursor.getPredefinedCursor(interactive ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                if (sq != hovered) { hovered = sq; repaint(); }
            }

            @Override public void mouseExited(MouseEvent event) {
                if (hovered != -1) { hovered = -1; repaint(); }
                setCursor(Cursor.getDefaultCursor());
            }

            @Override public void mousePressed(MouseEvent event) {
                if (position == null || !playable.getAsBoolean() || event.getButton() != MouseEvent.BUTTON1) return;
                int target = geometry().squareAt(event.getX(), event.getY());
                if (target < 0) { clearSelection(); repaint(); return; }
                pressed = target;
                origin = event.getPoint();
            }

            @Override public void mouseDragged(MouseEvent event) {
                if (!playable.getAsBoolean() || origin == null || pressed < 0 || origin.distance(event.getPoint()) < 5) return;
                if (mouse == null && !select(pressed)) return;
                mouse = event.getPoint();
                hovered = geometry().squareAt(event.getX(), event.getY());
                repaint();
            }

            @Override public void mouseReleased(MouseEvent event) {
                if (pressed < 0) return;
                int from = pressed;
                boolean dragging = mouse != null;
                pressed = -1;
                origin = null;
                mouse = null;
                if (!playable.getAsBoolean()) { repaint(); return; }
                int target = geometry().squareAt(event.getX(), event.getY());
                if (!dragging && target == from && selected == target) clearSelection();
                else if (target >= 0 && tryMove(target)) clearSelection();
                else if (!dragging && target >= 0 && !select(target)) clearSelection();
                repaint();
            }
        };
        addMouseListener(input);
        addMouseMotionListener(input);
    }

    void update(Position next, Move move, boolean animate) {
        disappearing = animate && position != null && move != null ? position.captured(move) : null;
        capturedSquare = disappearing != null && position.piece(move.to()) == null
                ? move.to() - 8 * position.turn().forward : move == null ? -1 : move.to();
        position = next;
        last = move;
        hint = null;
        check = Rules.inCheck(next, next.turn());
        clearSelection();
        if (animate && move != null) animator.triggerMove();
        else animator.clearMove();
        animator.setAmbientPulse(check);
        repaint();
    }

    void showHint(Move move) { hint = move; repaint(); }
    void flip() { setFlipped(!flipped); }
    void setFlipped(boolean value) { flipped = value; clearSelection(); repaint(); }

    /** Allows programmatic square selection (used in interactive UX and visual QA checks). */
    void selectSquare(int square) {
        if (position != null && select(square)) repaint();
    }

    void celebrate() {
        animator.triggerCelebration();
    }

    void showOutcomeOverlay(String title, String subtitle, Color accent) {
        this.overlayTitle = title;
        this.overlaySubtitle = subtitle;
        this.overlayAccent = accent;
        animator.triggerOverlayFade();
        repaint();
    }

    void clearOutcomeOverlay() {
        this.overlayTitle = null;
        this.overlaySubtitle = null;
        animator.clearOverlay();
        repaint();
    }

    @Override public void removeNotify() { animator.stop(); super.removeNotify(); }

    private BoardCoordinateMapper geometry() { return BoardCoordinateMapper.fit(getWidth(), getHeight(), flipped); }

    private boolean isOwnPiece(int sq) {
        Piece p = position.piece(sq);
        return p != null && p.side() == position.turn();
    }

    private boolean isCandidateTarget(int sq) {
        for (Move m : candidates) if (m.to() == sq) return true;
        return false;
    }

    private boolean select(int square) {
        if (!isOwnPiece(square)) return false;
        selected = square;
        candidates = Rules.legalMoves(position, square);
        animator.setAmbientPulse(true);
        return true;
    }

    private boolean tryMove(int to) {
        List<Move> matching = candidates.stream().filter(move -> move.to() == to).toList();
        if (matching.isEmpty()) return false;
        Move move = matching.get(0);
        if (matching.size() > 1) {
            Kind kind = promotion.choose(matching);
            if (kind == null) return false;
            move = matching.stream().filter(candidate -> candidate.promotion() == kind).findFirst().orElseThrow();
        }
        if (playable.getAsBoolean()) onMove.accept(move);
        return true;
    }

    private void clearSelection() {
        selected = pressed = -1;
        origin = mouse = null;
        candidates = List.of();
        animator.setAmbientPulse(check);
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (position == null) return;
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, preferences.highQuality()
                ? RenderingHints.VALUE_INTERPOLATION_BICUBIC : RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        BoardCoordinateMapper board = geometry();
        RoyalBoardFrame.paintOuterFrame(g, board);

        // 64 marble tiles
        for (int square = 0; square < 64; square++) drawTile(g, board.bounds(square), square);

        // Last move highlight
        if (last != null) {
            fill(g, board.bounds(last.from()), new Color(238, 198, 86, 68));
            fill(g, board.bounds(last.to()), new Color(244, 208, 96, 94));
        }

        // Hover highlight on playable piece or candidate target
        if (hovered >= 0 && hovered != selected && playable.getAsBoolean() && (isOwnPiece(hovered) || isCandidateTarget(hovered))) {
            Rectangle hArea = board.bounds(hovered);
            fill(g, hArea, new Color(246, 218, 134, 34));
            outline(g, hArea, new Color(246, 218, 134, 155), 1.8f);
        }

        float pulse = animator.pulse();

        // Selected square luminous golden bloom (matches reference e4 glow)
        if (selected >= 0) {
            Rectangle sArea = board.bounds(selected);
            int glowAlpha = Math.round(115 + 35 * pulse);
            fill(g, sArea, new Color(255, 224, 114, glowAlpha));
            RoyalGlowEffect.paintAmbientSpotlight(g, (float) sArea.getCenterX(), (float) sArea.getCenterY(),
                    sArea.width * 0.72f, new Color(255, 232, 138, Math.round(135 + 45 * pulse)));
            RoyalGlowEffect.paintOuterGlow(g, sArea.x, sArea.y, sArea.width, sArea.height, 4,
                    new Color(255, 222, 112, Math.round(165 + 50 * pulse)), 4);
            outline(g, sArea, new Color(0xFFF6C8), 3.0f);
        }

        // Check warning crimson radial pulse
        if (check) {
            Rectangle king = board.bounds(position.king(position.turn()));
            int coreAlpha = Math.round(205 + 40 * pulse);
            g.setPaint(new RadialGradientPaint((float) king.getCenterX(), (float) king.getCenterY(), board.tile() * 0.70f,
                    new float[]{0f, 0.62f, 1f},
                    new Color[]{new Color(235, 44, 36, coreAlpha), new Color(180, 22, 16, 135), new Color(95, 10, 8, 22)}));
            g.fill(king);
            outline(g, king, new Color(255, 118, 102, Math.round(180 + 60 * pulse)), 2.2f);
        }

        // Legal move indicators (luminous sage-gold disc / capture ring matching reference e5/e6)
        for (Move candidate : candidates) {
            drawLegal(g, board.bounds(candidate.to()), position.captured(candidate) != null, pulse);
        }

        // Hint square highlight
        if (hint != null) {
            Rectangle hf = board.bounds(hint.from()), ht = board.bounds(hint.to());
            fill(g, hf, new Color(112, 227, 156, 48));
            fill(g, ht, new Color(112, 227, 156, 68));
            outline(g, hf, RoyalStyle.SUCCESS, 2.2f);
            outline(g, ht, RoyalStyle.SUCCESS, 2.2f);
        }

        double t = animator.moveProgress();
        // Fading captured piece
        if (t < 1.0 && disappearing != null && capturedSquare >= 0) {
            g.setComposite(AlphaComposite.SrcOver.derive((float) Math.max(0.0, 1.0 - t)));
            pieceRenderer.drawPiece(g, disappearing, board.bounds(capturedSquare),
                    preferences.pieceScale(), preferences.highQuality(), isDarkSquare(capturedSquare), false);
            g.setComposite(AlphaComposite.SrcOver);
        }

        // Static pieces
        for (int square = 0; square < 64; square++) {
            Piece piece = position.piece(square);
            if (piece != null && !(square == selected && mouse != null) && !(t < 1.0 && last != null && square == last.to())) {
                pieceRenderer.drawPiece(g, piece, board.bounds(square),
                        preferences.pieceScale(), preferences.highQuality(), isDarkSquare(square), false);
            }
        }

        // Smooth moving piece animation
        if (t < 1.0 && last != null) {
            Rectangle from = board.bounds(last.from()), to = board.bounds(last.to());
            double eased = animator.easedMoveProgress();
            Rectangle moving = new Rectangle(
                    from.x + (int) Math.round((to.x - from.x) * eased),
                    from.y + (int) Math.round((to.y - from.y) * eased),
                    from.width, from.height);
            pieceRenderer.drawPiece(g, position.piece(last.to()), moving,
                    preferences.pieceScale(), preferences.highQuality(), isDarkSquare(last.to()), false);
        }

        // Hint directional arrow
        if (hint != null) {
            drawHintArrow(g, board.bounds(hint.from()), board.bounds(hint.to()));
        }

        // Elevated dragged piece
        if (mouse != null && selected >= 0) {
            int tile = board.tile();
            pieceRenderer.drawPiece(g, position.piece(selected),
                    new Rectangle(mouse.x - tile / 2, mouse.y - tile / 2, tile, tile),
                    preferences.pieceScale(), preferences.highQuality(), true, true);
        }

        RoyalBoardFrame.paintInnerShadowAndCoordinates(g, board);

        if (animator.isCelebrating()) {
            paintSparks(g, board, animator.celebrationProgress());
        }

        if (overlayTitle != null) {
            paintOutcomeOverlay(g, board, (float) animator.overlayProgress());
        }
        g.dispose();
    }

    private static boolean isDarkSquare(int square) {
        return (Square.file(square) + Square.rank(square)) % 2 == 0;
    }

    private void drawTile(Graphics2D g, Rectangle area, int square) {
        boolean light = !isDarkSquare(square);
        BufferedImage image = assets.tile(light);
        if (image == null) fill(g, area, light ? new Color(0xCBB28E) : new Color(0x2D241E));
        else g.drawImage(image, area.x, area.y, area.width, area.height, null);
        Color glaze = ThemeManager.glaze(theme, light);
        if (glaze != null) fill(g, area, glaze);
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
            // Soft luminous sage-gold disc matching reference legal move targets
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

    private static void drawHintArrow(Graphics2D g, Rectangle from, Rectangle to) {
        double x1 = from.getCenterX(), y1 = from.getCenterY();
        double x2 = to.getCenterX(), y2 = to.getCenterY();
        double angle = Math.atan2(y2 - y1, x2 - x1);
        double head = Math.max(12, from.width * 0.24);
        double sx = x1 + Math.cos(angle) * from.width * 0.18;
        double sy = y1 + Math.sin(angle) * from.width * 0.18;
        double ex = x2 - Math.cos(angle) * head * 0.72;
        double ey = y2 - Math.sin(angle) * head * 0.72;

        g.setColor(new Color(112, 227, 156, 185));
        g.setStroke(new BasicStroke(Math.max(4f, from.width * 0.085f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(sx, sy, ex, ey));

        Path2D arrow = new Path2D.Double();
        arrow.moveTo(x2 - Math.cos(angle) * from.width * 0.08, y2 - Math.sin(angle) * from.width * 0.08);
        arrow.lineTo(x2 - head * Math.cos(angle - Math.PI / 6), y2 - head * Math.sin(angle - Math.PI / 6));
        arrow.lineTo(x2 - head * Math.cos(angle + Math.PI / 6), y2 - head * Math.sin(angle + Math.PI / 6));
        arrow.closePath();
        g.fill(arrow);
        g.setStroke(new BasicStroke(1));
    }

    private void paintOutcomeOverlay(Graphics2D g, BoardCoordinateMapper b, float alpha) {
        if (alpha <= 0.01f) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.SrcOver.derive(alpha));
        int x = b.x(), y = b.y(), span = b.tile() * 8;
        g2.setColor(new Color(8, 7, 10, 172));
        g2.fillRect(x, y, span, span);

        int cw = Math.min(span - 40, 340), ch = 126;
        int cx = x + (span - cw) / 2, cy = y + (span - ch) / 2;
        RoyalGlowEffect.paintOuterGlow(g2, cx, cy, cw, ch, 16,
                new Color(overlayAccent.getRed(), overlayAccent.getGreen(), overlayAccent.getBlue(), 110), 4);
        g2.setPaint(new GradientPaint(cx, cy, new Color(0x211D16), cx, cy + ch, new Color(0x111014)));
        g2.fillRoundRect(cx, cy, cw, ch, 16, 16);
        RoyalGlowEffect.paintMetallicGoldBorder(g2, cx, cy, cw, ch, 16, 1.8f, true);

        g2.setFont(RoyalStyle.serif(Font.BOLD, 34));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(overlayAccent);
        g2.drawString(overlayTitle, cx + (cw - fm.stringWidth(overlayTitle)) / 2, cy + 62);

        if (overlaySubtitle != null) {
            g2.setFont(RoyalStyle.sans(Font.PLAIN, 14f));
            FontMetrics sfm = g2.getFontMetrics();
            g2.setColor(RoyalStyle.IVORY);
            g2.drawString(overlaySubtitle, cx + (cw - sfm.stringWidth(overlaySubtitle)) / 2, cy + 94);
        }
        g2.dispose();
    }

    private static void paintSparks(Graphics2D g, BoardCoordinateMapper b, double t) {
        g.setColor(new Color(246, 207, 113, (int) (220 * (1 - t))));
        int span = b.tile() * 8;
        for (int i = 0; i < 42; i++) {
            double angle = i * 2.39996;
            double r = span * (.14 + (i % 7) * .026) * t;
            int x = b.x() + span / 2 + (int) (Math.cos(angle) * r * 2.5);
            int y = b.y() + span / 2 + (int) (Math.sin(angle) * r * 2.5 + t * t * 42);
            g.fillOval(x, y, 2 + i % 4, 2 + i % 4);
        }
    }
}
