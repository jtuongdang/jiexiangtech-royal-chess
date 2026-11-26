package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
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
    private int selected = -1, pressed = -1;
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
        setPreferredSize(new Dimension(640, 640));
        setMinimumSize(new Dimension(160, 160));
        timer = new Timer(16, event -> {
            if (progress() >= 1 && (!celebrating || celebrationProgress() >= 1)) {
                celebrating = false;
                ((Timer) event.getSource()).stop();
            }
            repaint();
        });
        var input = new MouseAdapter() {
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
    private double progress() { return animatedAt == 0 ? 1 : Math.min(1, (System.nanoTime() - animatedAt) / 190_000_000.0); }
    private double celebrationProgress() { return Math.min(1, (System.nanoTime() - celebrationAt) / 1_150_000_000.0); }

    private boolean select(int square) {
        Piece piece = position.piece(square);
        if (piece == null || piece.side() != position.turn()) return false;
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
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, preferences.highQuality()
                ? RenderingHints.VALUE_INTERPOLATION_BICUBIC : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        BoardCoordinateMapper board = geometry();
        int x = board.x(), y = board.y(), size = board.tile() * 8;
        g.setColor(new Color(0, 0, 0, 125));
        g.fillRoundRect(x - 27, y - 22, size + 54, size + 54, 17, 17);
        g.setPaint(new GradientPaint(x, y - 25, new Color(0x423122), x + size, y + size, new Color(0x201913)));
        g.fillRoundRect(x - 24, y - 24, size + 48, size + 48, 12, 12);
        g.setColor(new Color(0x967641));
        g.drawRoundRect(x - 23, y - 23, size + 46, size + 46, 11, 11);
        g.setColor(new Color(0x5E4C32));
        g.drawRoundRect(x - 20, y - 20, size + 40, size + 40, 9, 9);
        g.setColor(RoyalStyle.GOLD);
        g.drawRect(x - 2, y - 2, size + 3, size + 3);

        for (int square = 0; square < 64; square++) drawTile(g, board.bounds(square), square);
        if (last != null) {
            fill(g, board.bounds(last.from()), new Color(242, 208, 104, 97));
            fill(g, board.bounds(last.to()), new Color(242, 208, 104, 112));
        }
        for (Move candidate : candidates) drawLegal(g, board.bounds(candidate.to()), position.captured(candidate) != null);
        if (selected >= 0) outline(g, board.bounds(selected), RoyalStyle.PALE);
        if (hint != null) {
            outline(g, board.bounds(hint.from()), RoyalStyle.SUCCESS);
            outline(g, board.bounds(hint.to()), RoyalStyle.SUCCESS);
        }
        if (check) {
            Rectangle king = board.bounds(position.king(position.turn()));
            g.setPaint(new RadialGradientPaint((float) king.getCenterX(), (float) king.getCenterY(), board.tile() * .66f,
                    new float[]{0, 1}, new Color[]{new Color(223, 47, 32, 220), new Color(115, 16, 5, 25)}));
            g.fill(king);
        }
        double t = progress();
        if (t < 1 && disappearing != null) {
            g.setComposite(AlphaComposite.SrcOver.derive((float) (1 - t)));
            drawPiece(g, disappearing, board.bounds(capturedSquare));
            g.setComposite(AlphaComposite.SrcOver);
        }
        for (int square = 0; square < 64; square++) {
            Piece piece = position.piece(square);
            if (piece != null && !(square == selected && mouse != null) && !(t < 1 && last != null && square == last.to())) {
                drawPiece(g, piece, board.bounds(square));
            }
        }
        if (t < 1 && last != null) {
            Rectangle from = board.bounds(last.from()), to = board.bounds(last.to());
            double eased = 1 - Math.pow(1 - t, 3);
            from.translate((int) Math.round((to.x - from.x) * eased), (int) Math.round((to.y - from.y) * eased));
            drawPiece(g, position.piece(last.to()), from);
        }
        if (mouse != null && selected >= 0) {
            int tile = board.tile();
            drawPiece(g, position.piece(selected), new Rectangle(mouse.x - tile / 2, mouse.y - tile / 2, tile, tile));
        }
        if (celebrating) paintSparks(g, board, celebrationProgress());
        coordinates(g, board);
        g.dispose();
    }

    private void drawTile(Graphics2D g, Rectangle area, int square) {
        boolean light = (Square.file(square) + Square.rank(square)) % 2 != 0;
        BufferedImage image = assets.tile(light);
        if (image == null) fill(g, area, light ? new Color(0xCBB28E) : new Color(0x342922));
        else g.drawImage(image, area.x, area.y, area.width, area.height, null);
        Color glaze = ThemeManager.glaze(theme, light);
        if (glaze != null) fill(g, area, glaze);
    }

    private static void fill(Graphics2D g, Rectangle area, Color color) { g.setColor(color); g.fill(area); }

    private static void outline(Graphics2D g, Rectangle area, Color color) {
        g.setColor(color);
        g.setStroke(new BasicStroke(2.5f));
        g.drawRect(area.x + 2, area.y + 2, area.width - 4, area.height - 4);
        g.setStroke(new BasicStroke(1));
    }

    private static void drawLegal(Graphics2D g, Rectangle area, boolean capture) {
        int radius = capture ? area.width / 2 - 4 : area.width / 9;
        g.setColor(new Color(230, 190, 78, capture ? 225 : 178));
        var shape = new Ellipse2D.Double(area.getCenterX() - radius, area.getCenterY() - radius, 2 * radius, 2 * radius);
        if (capture) {
            g.setStroke(new BasicStroke(3)); g.draw(shape); g.setStroke(new BasicStroke(1));
        } else g.fill(shape);
    }

    private void drawPiece(Graphics2D g, Piece piece, Rectangle tile) {
        BufferedImage image = assets.piece(piece);
        int px = tile.width * preferences.pieceScale() / 100;
        int x = tile.x + (tile.width - px) / 2;
        int y = tile.y + tile.height - px - tile.height / 12;
        g.setColor(new Color(0, 0, 0, 100));
        g.fillOval(tile.x + tile.width / 4, tile.y + tile.height * 83 / 100, tile.width / 2, Math.max(2, tile.height / 10));
        if (image != null) g.drawImage(image, x, y, px, px, null);
        else {
            g.setColor(piece.side() == Side.WHITE ? RoyalStyle.IVORY : new Color(0x201913));
            g.fillOval(x + px / 4, y + px / 6, px / 2, px * 2 / 3);
            g.setColor(piece.side() == Side.WHITE ? RoyalStyle.BG : RoyalStyle.PALE);
            g.setFont(getFont().deriveFont(Font.BOLD, px / 3f));
            g.drawString(String.valueOf(piece.kind().symbol), x + px / 3, y + px / 2);
        }
    }

    private void coordinates(Graphics2D g, BoardCoordinateMapper b) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        g.setColor(RoyalStyle.PALE);
        for (int i = 0; i < 8; i++) {
            char file = (char) (flipped ? 'H' - i : 'A' + i);
            char rank = (char) (flipped ? '1' + i : '8' - i);
            g.drawString(String.valueOf(file), b.x() + b.tile() * i + b.tile() / 2 - 4, b.y() + b.tile() * 8 + 16);
            g.drawString(String.valueOf(rank), b.x() - 15, b.y() + b.tile() * i + b.tile() / 2 + 4);
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
