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
import javax.swing.Timer;
import com.jiexiangtech.chess.application.Preferences;
import com.jiexiangtech.chess.domain.*;

final class ChessBoardPanel extends JPanel {
    interface PromotionPicker { Kind choose(List<Move> options); }

    private final AssetManager assets;
    private final Preferences preferences;
    private final String theme;
    private final BooleanSupplier playable;
    private final PromotionPicker promotion;
    private final Consumer<Move> onMove;
    private final Timer timer;
    private Position position;
    private Move last, hint;
    private int selected = -1, pressed = -1, hovered = -1;
    private Point origin, mouse;
    private List<Move> candidates = List.of();
    private boolean flipped, check, celebrating;
    private long animatedAt, celebrationAt;
    private Piece disappearing;
    private int capturedSquare;

    ChessBoardPanel(AssetManager assets, Preferences preferences, String theme, BooleanSupplier playable,
                    PromotionPicker promotion, Consumer<Move> onMove) {
        this.assets = assets;
        this.preferences = preferences;
        this.theme = theme;
        this.playable = playable;
        this.promotion = promotion;
        this.onMove = onMove;
        setOpaque(false);
        setPreferredSize(new Dimension(700, 700));
        setMinimumSize(new Dimension(240, 240));
        timer = new Timer(16, event -> {
            if (progress() >= 1 && (!celebrating || celebrationProgress() >= 1)) {
                celebrating = false;
                ((Timer) event.getSource()).stop();
            }
            repaint();
        });
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
        animatedAt = animate && move != null && preferences.animation() ? System.nanoTime() : 0;
        if (animatedAt != 0) timer.start();
        repaint();
    }

    void showHint(Move move) { hint = move; repaint(); }
    void flip() { setFlipped(!flipped); }
    void setFlipped(boolean value) { flipped = value; clearSelection(); repaint(); }

    void celebrate() {
        if (!preferences.animation()) return;
        celebrating = true;
        celebrationAt = System.nanoTime();
        timer.start();
    }

    @Override public void removeNotify() { timer.stop(); super.removeNotify(); }

    private BoardCoordinateMapper geometry() { return BoardCoordinateMapper.fit(getWidth(), getHeight(), flipped); }
    private double progress() { return animatedAt == 0 ? 1 : Math.min(1, (System.nanoTime() - animatedAt) / 185_000_000.0); }
    private double celebrationProgress() { return Math.min(1, (System.nanoTime() - celebrationAt) / 1_150_000_000.0); }

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
        int x = board.x(), y = board.y(), size = board.tile() * 8;
        int bezel = 20;

        // Subtle ambient royal gold glow radiating behind the chessboard
        float cx = x + size / 2f, cy = y + size / 2f;
        float glowRadius = size * 0.64f;
        g.setPaint(new RadialGradientPaint(cx, cy, Math.max(1f, glowRadius),
                new float[]{0.55f, 0.82f, 1f},
                new Color[]{new Color(212, 175, 55, 42), new Color(212, 175, 55, 16), new Color(0, 0, 0, 0)}));
        g.fillOval((int) (cx - glowRadius), (int) (cy - glowRadius), (int) (glowRadius * 2), (int) (glowRadius * 2));

        // Layered drop shadow beneath frame
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRoundRect(x - bezel - 2, y - bezel + 2, size + bezel * 2 + 4, size + bezel * 2 + 4, 16, 16);

        // Luxury obsidian-walnut frame bezel
        g.setPaint(new GradientPaint(x - bezel, y - bezel, new Color(0x2D2318), x + size + bezel, y + size + bezel, new Color(0x14110F)));
        g.fillRoundRect(x - bezel, y - bezel, size + bezel * 2, size + bezel * 2, 13, 13);

        // Metallic gold outer rim & recessed dark inner bevel
        g.setPaint(new GradientPaint(x - bezel, y - bezel, new Color(0xD8B454), x + size + bezel, y + size + bezel, new Color(0x745828)));
        g.drawRoundRect(x - bezel, y - bezel, size + bezel * 2 - 1, size + bezel * 2 - 1, 13, 13);
        g.setColor(new Color(0x0E0C0A));
        g.drawRoundRect(x - bezel + 2, y - bezel + 2, size + bezel * 2 - 5, size + bezel * 2 - 5, 10, 10);

        // Tight metallic gold fillet framing the 8x8 tiles
        g.setColor(new Color(0xC9A23F));
        g.drawRect(x - 2, y - 2, size + 3, size + 3);
        g.setColor(new Color(0x15120E));
        g.drawRect(x - 1, y - 1, size + 1, size + 1);

        // Board squares
        for (int square = 0; square < 64; square++) drawTile(g, board.bounds(square), square);

        // Last move highlight
        if (last != null) {
            fill(g, board.bounds(last.from()), new Color(236, 198, 86, 78));
            fill(g, board.bounds(last.to()), new Color(242, 206, 98, 102));
        }

        // Hover highlight on playable piece or candidate target
        if (hovered >= 0 && hovered != selected && playable.getAsBoolean() && (isOwnPiece(hovered) || isCandidateTarget(hovered))) {
            Rectangle hArea = board.bounds(hovered);
            fill(g, hArea, new Color(242, 215, 139, 34));
            outline(g, hArea, new Color(242, 215, 139, 135), 1.6f);
        }

        // Selected square warm gold glow
        if (selected >= 0) {
            Rectangle sArea = board.bounds(selected);
            fill(g, sArea, new Color(212, 175, 55, 76));
            outline(g, sArea, RoyalStyle.PALE, 2.4f);
        }

        // Check warning crimson radial aura
        if (check) {
            Rectangle king = board.bounds(position.king(position.turn()));
            g.setPaint(new RadialGradientPaint((float) king.getCenterX(), (float) king.getCenterY(), board.tile() * 0.68f,
                    new float[]{0f, 0.65f, 1f},
                    new Color[]{new Color(230, 48, 36, 225), new Color(175, 24, 18, 130), new Color(95, 10, 8, 20)}));
            g.fill(king);
            outline(g, king, new Color(255, 115, 100, 210), 2.0f);
        }

        // Legal move indicators
        for (Move candidate : candidates) {
            drawLegal(g, board.bounds(candidate.to()), position.captured(candidate) != null);
        }

        // Hint highlight & subtle directional arrow
        if (hint != null) {
            Rectangle hf = board.bounds(hint.from()), ht = board.bounds(hint.to());
            fill(g, hf, new Color(112, 227, 156, 46));
            fill(g, ht, new Color(112, 227, 156, 64));
            outline(g, hf, RoyalStyle.SUCCESS, 2.2f);
            outline(g, ht, RoyalStyle.SUCCESS, 2.2f);
        }

        double t = progress();
        // Fading captured piece
        if (t < 1 && disappearing != null && capturedSquare >= 0) {
            g.setComposite(AlphaComposite.SrcOver.derive((float) (1 - t)));
            drawPiece(g, disappearing, board.bounds(capturedSquare), isDarkSquare(capturedSquare), false);
            g.setComposite(AlphaComposite.SrcOver);
        }

        // Static pieces
        for (int square = 0; square < 64; square++) {
            Piece piece = position.piece(square);
            if (piece != null && !(square == selected && mouse != null) && !(t < 1 && last != null && square == last.to())) {
                drawPiece(g, piece, board.bounds(square), isDarkSquare(square), false);
            }
        }

        // Moving piece animation
        if (t < 1 && last != null) {
            Rectangle from = board.bounds(last.from()), to = board.bounds(last.to());
            double eased = 1 - Math.pow(1 - t, 3);
            Rectangle moving = new Rectangle(
                    from.x + (int) Math.round((to.x - from.x) * eased),
                    from.y + (int) Math.round((to.y - from.y) * eased),
                    from.width, from.height);
            drawPiece(g, position.piece(last.to()), moving, isDarkSquare(last.to()), false);
        }

        // Hint arrow drawn over pieces for instant clarity
        if (hint != null) {
            drawHintArrow(g, board.bounds(hint.from()), board.bounds(hint.to()));
        }

        // Dragged piece (elevated above board)
        if (mouse != null && selected >= 0) {
            int tile = board.tile();
            drawPiece(g, position.piece(selected), new Rectangle(mouse.x - tile / 2, mouse.y - tile / 2, tile, tile), true, true);
        }

        if (celebrating) paintSparks(g, board, celebrationProgress());
        coordinates(g, board, bezel);
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

    private static void drawLegal(Graphics2D g, Rectangle area, boolean capture) {
        double cx = area.getCenterX(), cy = area.getCenterY();
        if (capture) {
            double r = area.width * 0.43;
            g.setColor(new Color(0, 0, 0, 110));
            g.setStroke(new BasicStroke(Math.max(4f, area.width * 0.08f)));
            g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
            g.setColor(new Color(242, 206, 98, 225));
            g.setStroke(new BasicStroke(Math.max(2.6f, area.width * 0.052f)));
            g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
            g.setStroke(new BasicStroke(1));
        } else {
            double r = Math.max(4.5, area.width * 0.14);
            g.setColor(new Color(0, 0, 0, 95));
            g.fill(new Ellipse2D.Double(cx - r - 1.2, cy - r - 1.2, (r + 1.2) * 2, (r + 1.2) * 2));
            g.setColor(new Color(238, 198, 84, 215));
            g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
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

        g.setColor(new Color(112, 227, 156, 175));
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

    private static double heightFactor(Kind kind) {
        return switch (kind) {
            case KING -> 0.93;
            case QUEEN -> 0.89;
            case BISHOP -> 0.84;
            case KNIGHT -> 0.82;
            case ROOK -> 0.78;
            case PAWN -> 0.73;
        };
    }

    private void drawPiece(Graphics2D g, Piece piece, Rectangle tile, boolean onDarkSquare, boolean dragged) {
        if (piece == null) return;
        BufferedImage image = assets.piece(piece);
        Rectangle src = assets.pieceBounds(piece);

        double userScale = preferences.pieceScale() / 97.0;
        double lift = dragged ? 1.07 : 1.0;
        double targetH = tile.height * heightFactor(piece.kind()) * userScale * lift;
        double maxW = tile.width * 0.86 * lift;
        double scale = Math.min(targetH / src.height, maxW / src.width);

        int dw = Math.max(4, (int) Math.round(src.width * scale));
        int dh = Math.max(4, (int) Math.round(src.height * scale));
        int baseY = tile.y + (int) Math.round(tile.height * 0.915) - (dragged ? Math.max(4, tile.height / 10) : 0);
        int dx = tile.x + (tile.width - dw) / 2;
        int dy = baseY - dh;

        // Subtle warm golden rim lift behind black pieces standing on dark squares so silhouettes stay unmistakable
        if (piece.side() == Side.BLACK && onDarkSquare && !dragged) {
            float glowX = tile.x + tile.width / 2f;
            float glowY = dy + dh * 0.56f;
            float glowR = Math.max(8f, Math.max(dw, dh) * 0.62f);
            g.setPaint(new RadialGradientPaint(glowX, glowY, glowR,
                    new float[]{0f, 0.58f, 1f},
                    new Color[]{new Color(228, 194, 110, 62), new Color(196, 158, 76, 26), new Color(0, 0, 0, 0)}));
            g.fillOval((int) (glowX - glowR), (int) (glowY - glowR), (int) (glowR * 2), (int) (glowR * 2));
        }

        // Realistic two-layer contact shadow anchored directly beneath the piece base
        int shadowW = Math.max(8, (int) Math.round(dw * (dragged ? 1.06 : 0.96)));
        int shadowH = Math.max(4, (int) Math.round(tile.height * (dragged ? 0.16 : 0.13)));
        int shadowX = tile.x + (tile.width - shadowW) / 2;
        int shadowY = tile.y + (int) Math.round(tile.height * 0.915) - shadowH / 2 + (dragged ? 3 : 0);
        g.setColor(new Color(0, 0, 0, dragged ? 75 : 95));
        g.fillOval(shadowX - 2, shadowY, shadowW + 4, shadowH + 2);
        g.setColor(new Color(0, 0, 0, dragged ? 85 : 135));
        g.fillOval(shadowX + shadowW / 10, shadowY + 1, shadowW * 4 / 5, Math.max(2, shadowH * 2 / 3));

        if (image != null) {
            g.drawImage(image, dx, dy, dx + dw, dy + dh,
                    src.x, src.y, src.x + src.width, src.y + src.height, null);
        } else {
            g.setColor(piece.side() == Side.WHITE ? RoyalStyle.IVORY : new Color(0x201913));
            g.fillOval(dx, dy, dw, dh);
            g.setColor(piece.side() == Side.WHITE ? RoyalStyle.BG : RoyalStyle.PALE);
            g.setFont(getFont().deriveFont(Font.BOLD, dh / 2.2f));
            g.drawString(String.valueOf(piece.kind().symbol), dx + dw / 3, dy + dh * 2 / 3);
        }
    }

    private void coordinates(Graphics2D g, BoardCoordinateMapper b, int bezel) {
        Font font = RoyalStyle.sans(Font.BOLD, Math.max(10.5f, Math.min(12.5f, b.tile() * 0.16f)));
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics(font);
        g.setColor(new Color(0xD5BA7A));
        for (int i = 0; i < 8; i++) {
            String file = String.valueOf((char) (flipped ? 'H' - i : 'A' + i));
            String rank = String.valueOf((char) (flipped ? '1' + i : '8' - i));
            int fx = b.x() + b.tile() * i + (b.tile() - fm.stringWidth(file)) / 2;
            int fy = b.y() + b.tile() * 8 + (bezel + fm.getAscent() - fm.getDescent()) / 2;
            g.drawString(file, fx, fy);

            int rx = b.x() - (bezel + fm.stringWidth(rank)) / 2;
            int ry = b.y() + b.tile() * i + (b.tile() + fm.getAscent() - fm.getDescent()) / 2;
            g.drawString(rank, rx, ry);
        }
    }

    private static void paintSparks(Graphics2D g, BoardCoordinateMapper b, double t) {
        g.setColor(new Color(246, 207, 113, (int) (210 * (1 - t))));
        int span = b.tile() * 8;
        for (int i = 0; i < 36; i++) {
            double angle = i * 2.39996;
            double r = span * (.14 + (i % 7) * .025) * t;
            int x = b.x() + span / 2 + (int) (Math.cos(angle) * r * 2.5);
            int y = b.y() + span / 2 + (int) (Math.sin(angle) * r * 2.5 + t * t * 40);
            g.fillOval(x, y, 2 + i % 4, 2 + i % 4);
        }
    }
}
