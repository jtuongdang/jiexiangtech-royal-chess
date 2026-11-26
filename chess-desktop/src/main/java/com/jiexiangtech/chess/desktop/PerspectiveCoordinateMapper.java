package com.jiexiangtech.chess.desktop;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import com.jiexiangtech.chess.domain.Piece;
import com.jiexiangtech.chess.domain.Position;
import com.jiexiangtech.chess.domain.Square;

/**
 * Reference-calibrated 2.5D perspective coordinate mapper for Royal Focus Mode.
 * Computes exact board trapezoid corners, 3D slab thickness, piece screen anchors,
 * and inverse mouse hit-testing (including upright piece billboard silhouettes).
 */
final class PerspectiveCoordinateMapper {
    static final double FRAME_LEFT = 0.088;
    static final double FRAME_RIGHT = 0.088;
    static final double FRAME_TOP = 0.046;
    static final double FRAME_BOTTOM = 0.106;

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

    /**
     * Fits the 2.5D perspective chessboard proportionally inside the center stage canvas,
     * preserving the exact taper ratio (0.625) and height-to-width ratio from royal-focus-reference.png.
     */
    static PerspectiveCoordinateMapper fit(int width, int height, boolean flipped) {
        double w = Math.max(240, width);
        double h = Math.max(200, height);

        // Headroom above far rank for tall King/Queen crowns + slab thickness at bottom
        double usableH = h * 0.80;
        double maxBottomOuterW = Math.min(w * 0.92, usableH * 2.24);
        double outerH = Math.min(usableH * 0.87, maxBottomOuterW * 0.432);
        double bottomOuterW = Math.min(maxBottomOuterW, outerH / 0.412);
        double topOuterW = bottomOuterW * 0.625;
        double slab = Math.max(15.0, outerH * 0.114);

        double cx = w * 0.5;
        double topY = Math.max(h * 0.135, (h - (outerH + slab)) * 0.54);
        double botY = topY + outerH;

        // Solve outer trapezoid first, then derive inner 8x8 corners via consistent frame ratios
        PerspectiveProjection outerProj = new PerspectiveProjection(
                new Point2D.Double(cx - topOuterW * 0.5, topY),
                new Point2D.Double(cx + topOuterW * 0.5, topY),
                new Point2D.Double(cx + bottomOuterW * 0.5, botY),
                new Point2D.Double(cx - bottomOuterW * 0.5, botY));

        double uMin = FRAME_LEFT / (1.0 + FRAME_LEFT + FRAME_RIGHT);
        double uMax = (1.0 + FRAME_LEFT) / (1.0 + FRAME_LEFT + FRAME_RIGHT);
        double vMin = FRAME_TOP / (1.0 + FRAME_TOP + FRAME_BOTTOM);
        double vMax = (1.0 + FRAME_TOP) / (1.0 + FRAME_TOP + FRAME_BOTTOM);

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
    double anchorV(int square) { return (visualRow(square) + 0.74) / 8.0; }

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

    /** Pure board-plane inverse homography hit test (exact 64-square test). */
    int boardSquareAt(double px, double py) {
        Point2D.Double uv = projection.unproject(px, py);
        if (uv.x < 0.0 || uv.x >= 1.0 || uv.y < 0.0 || uv.y >= 1.0) return -1;
        int col = Math.min(7, Math.max(0, (int) Math.floor(uv.x * 8.0)));
        int row = Math.min(7, Math.max(0, (int) Math.floor(uv.y * 8.0)));
        return flipped ? Square.at(7 - col, row) : Square.at(col, 7 - row);
    }

    /**
     * Full 2.5D hit-test: prioritizes the upright piece silhouette (front-to-back) when clicking
     * an elevated piece crown protruding over a rear square, then falls back to the board plane.
     */
    int squareAt(double px, double py, Position position, int pieceScalePercent) {
        if (position != null) {
            double userScale = pieceScalePercent / 97.0;
            // Front-to-back scan so closer pieces win overlap hits
            for (int row = 7; row >= 0; row--) {
                for (int col = 0; col < 8; col++) {
                    int sq = flipped ? Square.at(7 - col, row) : Square.at(col, 7 - row);
                    Piece piece = position.piece(sq);
                    if (piece == null) continue;
                    Point2D.Double anchor = squareAnchor(sq);
                    double tileW = squareWidth(sq);
                    double ph = tileW * 1.18 * RoyalPieceRenderer.heightRatio(piece.kind()) * userScale;
                    double pw = tileW * 0.54 * userScale;
                    if (px >= anchor.x - pw * 0.5 && px <= anchor.x + pw * 0.5
                            && py >= anchor.y - ph && py <= anchor.y + tileW * 0.08) {
                        return sq;
                    }
                }
            }
        }
        return boardSquareAt(px, py);
    }
}
