package com.jiexiangtech.chess.desktop;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import com.jiexiangtech.chess.domain.Move;
import com.jiexiangtech.chess.domain.Piece;
import com.jiexiangtech.chess.domain.Position;
import com.jiexiangtech.chess.domain.Rules;
import com.jiexiangtech.chess.domain.Square;

/**
 * Royal Arena projection for the interactive 2.5D board. Drawing and hit-testing share
 * the same transform, so a change in camera, window size or orientation keeps the squares aligned.
 */
final class PerspectiveCoordinateMapper {
    static final double FRAME_LEFT = 0.050;
    static final double FRAME_RIGHT = 0.050;
    static final double FRAME_TOP = 0.026;
    static final double FRAME_BOTTOM = 0.068;

    private final int width, height;
    private final boolean flipped;
    private final PerspectiveProjection projection;
    private final double slabThickness;
    private final Point2D.Double outerTL, outerTR, outerBR, outerBL;

    private PerspectiveCoordinateMapper(int width, int height, boolean flipped,
                                        PerspectiveProjection projection, double slabThickness) {
        this.width = width;
        this.height = height;
        this.flipped = flipped;
        this.projection = projection;
        this.slabThickness = slabThickness;
        this.outerTL = projection.project(-FRAME_LEFT, -FRAME_TOP);
        this.outerTR = projection.project(1.0 + FRAME_RIGHT, -FRAME_TOP);
        this.outerBR = projection.project(1.0 + FRAME_RIGHT, 1.0 + FRAME_BOTTOM);
        this.outerBL = projection.project(-FRAME_LEFT, 1.0 + FRAME_BOTTOM);
    }

    static PerspectiveCoordinateMapper fit(int width, int height, boolean flipped) {
        double w = Math.max(240, width);
        double h = Math.max(200, height);

        double targetOuterH = h * 0.76;
        double maxBottomW = w * 0.875;
        double bottomOuterW = Math.min(maxBottomW, targetOuterH * 1.83);
        double outerH = Math.min(targetOuterH, bottomOuterW * 0.548);
        double topOuterW = bottomOuterW * 0.74;
        double slab = Math.max(16.0, outerH * 0.09);

        // Anchor board slightly lower in the stage canvas so front slab sits right above the bottom toolbar
        double cx = w * 0.50;
        double botY = Math.min(h - slab - 8.0, h * 0.89);
        double topY = Math.max(h * 0.12, botY - outerH);

        PerspectiveProjection outerProj = new PerspectiveProjection(
                new Point2D.Double(cx - topOuterW * 0.5, topY),
                new Point2D.Double(cx + topOuterW * 0.5, topY),
                new Point2D.Double(cx + bottomOuterW * 0.5, botY),
                new Point2D.Double(cx - bottomOuterW * 0.5, botY));

        double totalU = 1.0 + FRAME_LEFT + FRAME_RIGHT;
        double totalV = 1.0 + FRAME_TOP + FRAME_BOTTOM;
        double uMin = FRAME_LEFT / totalU;
        double uMax = (1.0 + FRAME_LEFT) / totalU;
        double vMin = FRAME_TOP / totalV;
        double vMax = (1.0 + FRAME_TOP) / totalV;

        PerspectiveProjection innerProj = new PerspectiveProjection(
                outerProj.project(uMin, vMin),
                outerProj.project(uMax, vMin),
                outerProj.project(uMax, vMax),
                outerProj.project(uMin, vMax));

        return new PerspectiveCoordinateMapper(width, height, flipped, innerProj, slab);
    }

    int width() { return width; }
    int height() { return height; }
    boolean flipped() { return flipped; }
    PerspectiveProjection projection() { return projection; }
    double slabThickness() { return slabThickness; }
    Point2D.Double outerTL() { return outerTL; }
    Point2D.Double outerTR() { return outerTR; }
    Point2D.Double outerBR() { return outerBR; }
    Point2D.Double outerBL() { return outerBL; }

    int visualCol(int square) { return flipped ? 7 - Square.file(square) : Square.file(square); }
    int visualRow(int square) { return flipped ? Square.rank(square) : 7 - Square.rank(square); }

    double centerU(int square) { return (visualCol(square) + 0.5) / 8.0; }
    double anchorV(int square) {
        int row = visualRow(square);
        // Perspective-corrected footprint grounding: far-rank tiles are more foreshortened vertically
        // while front-rank tiles sit deep toward the front edge so no rank appears floating above its tile.
        double offset = 0.80 + 0.08 * (row / 7.0);
        return (row + offset) / 8.0;
    }

    Point2D.Double squareAnchor(int square) {
        return projection.project(centerU(square), anchorV(square));
    }

    Point2D.Double squareCenter(int square) {
        return projection.project((visualCol(square) + 0.5) / 8.0, (visualRow(square) + 0.5) / 8.0);
    }

    double squareWidthAt(double v) {
        return projection.localSquareWidth(v);
    }

    double squareWidth(int square) {
        return projection.localSquareWidth(anchorV(square));
    }

    Rectangle boardBoundingBox() {
        int minX = (int) Math.floor(Math.min(outerTL.x, outerBL.x)) - 4;
        int maxX = (int) Math.ceil(Math.max(outerTR.x, outerBR.x)) + 4;
        int minY = (int) Math.floor(outerTL.y) - 4;
        int maxY = (int) Math.ceil(outerBL.y + slabThickness) + 4;
        return new Rectangle(minX, minY, Math.max(1, maxX - minX), Math.max(1, maxY - minY));
    }

    int boardSquareAt(double px, double py) {
        Point2D.Double uv = projection.unproject(px, py);
        if (uv.x < 0.0 || uv.x >= 1.0 || uv.y < 0.0 || uv.y >= 1.0) return -1;
        int col = Math.min(7, Math.max(0, (int) Math.floor(uv.x * 8.0)));
        int row = Math.min(7, Math.max(0, (int) Math.floor(uv.y * 8.0)));
        return flipped ? Square.at(7 - col, row) : Square.at(col, 7 - row);
    }

    int squareAt(double px, double py, Position position, int pieceScalePercent) {
        return squareAt(px, py, position, pieceScalePercent, java.util.List.of());
    }

    /**
     * Precision 2.5D hit-testing:
     * 1. Any click landing on a legal candidate move's board square (or enemy piece body on a capture square)
     *    resolves immediately to that candidate destination square (never blocked by the selected piece's head).
     * 2. Any click landing on a board square containing one of the side-to-move's own movable pieces resolves
     *    to that square (never stolen by a blocked piece standing in front of it, e.g. d1 Queen in front of d2 Pawn).
     * 3. Clicking the protruding upper silhouette of a movable own piece selects that piece.
     * 4. Falls back to exact inverse homography boardSquareAt(px, py).
     */
    int squareAt(double px, double py, Position position, int pieceScalePercent, java.util.List<Move> candidates) {
        return squareAt(px, py, position, pieceScalePercent, candidates, null);
    }

    int squareAt(double px, double py, Position position, int pieceScalePercent,
                 java.util.List<Move> candidates, boolean[] movableSquares) {
        int planeSq = boardSquareAt(px, py);
        if (position == null) return planeSq;

        if (candidates != null && !candidates.isEmpty()) {
            if (planeSq >= 0) {
                for (Move m : candidates) {
                    if (m.to() == planeSq) return planeSq;
                }
            }
            for (Move m : candidates) {
                int to = m.to();
                Piece targetPiece = position.piece(to);
                if (targetPiece != null && hitsPieceSilhouette(px, py, to, targetPiece, pieceScalePercent)) {
                    return to;
                }
            }
        }

        if (planeSq >= 0) {
            Piece onPlane = position.piece(planeSq);
            if (onPlane != null && onPlane.side() == position.turn() && isMovable(position, planeSq, movableSquares)) {
                return planeSq;
            }
        }

        for (int row = 7; row >= 0; row--) {
            for (int col = 0; col < 8; col++) {
                int sq = flipped ? Square.at(7 - col, row) : Square.at(col, 7 - row);
                Piece piece = position.piece(sq);
                if (piece == null || piece.side() != position.turn()) continue;
                if (isMovable(position, sq, movableSquares) && hitsPieceSilhouette(px, py, sq, piece, pieceScalePercent)) {
                    return sq;
                }
            }
        }

        return planeSq;
    }

    private static boolean isMovable(Position position, int sq, boolean[] movableSquares) {
        return movableSquares != null ? movableSquares[sq] : !Rules.legalMoves(position, sq).isEmpty();
    }

    private boolean hitsPieceSilhouette(double px, double py, int sq, Piece piece, int pieceScalePercent) {
        double userScale = pieceScalePercent / 97.0;
        Point2D.Double anchor = squareAnchor(sq);
        double tileW = squareWidth(sq);
        double ph = tileW * 1.16 * RoyalPieceRenderer.heightRatio(piece.kind()) * userScale;
        if (py < anchor.y - ph || py > anchor.y + tileW * 0.04) return false;
        // Tapered silhouette width: wider near the base (0.54 * tileW), narrower near the crown (0.32 * tileW)
        double t = Math.max(0.0, Math.min(1.0, (anchor.y - py) / Math.max(1.0, ph)));
        double halfW = tileW * (0.27 - 0.11 * t) * userScale;
        return Math.abs(px - anchor.x) <= halfW;
    }
}
