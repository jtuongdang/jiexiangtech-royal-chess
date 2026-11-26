package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.JPanel;
import com.jiexiangtech.chess.application.Preferences;
import com.jiexiangtech.chess.domain.*;

final class ChessBoardPanel extends JPanel {
    interface PromotionPicker { Kind choose(List<Move> options); }

    private final RoyalPieceRenderer pieceRenderer;
    private final Classic2DBoardRenderer classic2D;
    private final RoyalFocusBoardRenderer royalFocus25D;
    private final RoyalAnimationEngine animator;
    private Preferences preferences;
    private final String theme;
    private final BooleanSupplier playable;
    private final PromotionPicker promotion;
    private final Consumer<Move> onMove;
    private BoardRenderer activeRenderer;
    private boolean focusMode;
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
        this.pieceRenderer = new RoyalPieceRenderer(assets);
        this.classic2D = new Classic2DBoardRenderer(assets, pieceRenderer);
        this.royalFocus25D = new RoyalFocusBoardRenderer(assets, pieceRenderer);
        this.animator = new RoyalAnimationEngine(this);
        this.preferences = preferences;
        this.animator.setEnabled(preferences.animation());
        this.theme = theme;
        this.playable = playable;
        this.promotion = promotion;
        this.onMove = onMove;
        this.activeRenderer = classic2D;
        setOpaque(false);
        setPreferredSize(new Dimension(740, 740));
        setMinimumSize(new Dimension(280, 260));

        var input = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent event) {
                if (position == null || !playable.getAsBoolean()) {
                    if (hovered != -1) { hovered = -1; setCursor(Cursor.getDefaultCursor()); repaint(); }
                    return;
                }
                int sq = hitSquare(event.getX(), event.getY());
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
                int target = hitSquare(event.getX(), event.getY());
                if (target < 0) { clearSelection(); repaint(); return; }
                pressed = target;
                origin = event.getPoint();
            }

            @Override public void mouseDragged(MouseEvent event) {
                if (!playable.getAsBoolean() || origin == null || pressed < 0 || origin.distance(event.getPoint()) < 5) return;
                if (mouse == null && !select(pressed)) return;
                mouse = event.getPoint();
                hovered = hitSquare(event.getX(), event.getY());
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
                int target = hitSquare(event.getX(), event.getY());
                if (!dragging && target == from && selected == target) clearSelection();
                else if (target >= 0 && tryMove(target)) clearSelection();
                else if (!dragging && target >= 0 && !select(target)) clearSelection();
                repaint();
            }
        };
        addMouseListener(input);
        addMouseMotionListener(input);
    }

    void setFocusMode(boolean enabled) {
        this.focusMode = enabled;
        this.activeRenderer = enabled ? royalFocus25D : classic2D;
        repaint();
    }

    boolean isFocusMode() { return focusMode; }

    void setPreferences(Preferences next) {
        this.preferences = next;
        this.animator.setEnabled(next.animation());
        repaint();
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

    void selectSquare(int square) {
        if (position != null && select(square)) repaint();
    }

    void celebrate() { animator.triggerCelebration(); }

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

    private int hitSquare(double px, double py) {
        return activeRenderer.squareAt(getWidth(), getHeight(), flipped, px, py, position, preferences.pieceScale());
    }

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

        var state = new BoardRenderer.RenderState(
                getWidth(), getHeight(), position, flipped, theme, preferences,
                last, hint, selected, hovered, candidates, playable.getAsBoolean(), check,
                mouse, disappearing, capturedSquare,
                animator.moveProgress(), animator.easedMoveProgress(), animator.pulse(),
                animator.isCelebrating(), animator.celebrationProgress(),
                overlayTitle, overlaySubtitle, overlayAccent, (float) animator.overlayProgress());
        activeRenderer.paint(g, state);
        g.dispose();
    }
}
