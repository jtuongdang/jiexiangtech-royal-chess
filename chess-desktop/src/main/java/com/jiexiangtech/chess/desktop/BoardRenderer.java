package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.util.List;
import com.jiexiangtech.chess.application.Preferences;
import com.jiexiangtech.chess.domain.*;

/** Rendering and hit-testing strategy shared by Classic 2D and Royal Focus 2.5D modes. */
interface BoardRenderer {
    record RenderState(int width, int height, Position position, boolean flipped, String theme,
                       Preferences preferences, Move last, Move hint, int selected, int hovered,
                       List<Move> candidates, boolean playable, boolean check, Point dragPoint,
                       Move droppedMove, Point droppedAt, MoveAnimation motion, double moveProgress,
                       float pulse, boolean celebrating, double celebrationProgress,
                       String overlayTitle, String overlaySubtitle, Color overlayAccent, float overlayAlpha) {
        int heldSquare() { return droppedMove == null ? selected : droppedMove.from(); }
        Point heldPoint() { return droppedAt == null ? dragPoint : droppedAt; }

        boolean hidesPiece(int square) {
            return (heldPoint() != null && square == heldSquare()) || (motion != null
                    && (square == motion.move().to() || (motion.rook() != null && square == motion.rook().to())));
        }
    }

    void paint(Graphics2D g, RenderState state);

    int squareAt(int width, int height, boolean flipped, double px, double py,
                 Position position, int pieceScale, List<Move> candidates, boolean[] movableSquares);
}
