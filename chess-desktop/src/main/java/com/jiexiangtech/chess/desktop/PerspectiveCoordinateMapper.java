package com.jiexiangtech.chess.desktop;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import com.jiexiangtech.chess.domain.Piece;
import com.jiexiangtech.chess.domain.Position;
import com.jiexiangtech.chess.domain.Square;

/**
 * Reference-calibrated 1:1 2.5D perspective coordinate mapper for Royal Focus Mode.
 * Matched directly to docs/design/royal-focus-reference.png (1024x576):
 * - Outer frame top Y = 30.9% of window H, front lip Y = 82.6% of window H
 * - Outer frame taper (topWidth / bottomWidth) = 0.588
 * - Slender frame bezels (L=0.050, R=0.050, T=0.026, B=0.068) so the 8x8 grid occupies 89% of board height
 * - Front 3D slab thickness = 10.5% of board height
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

        // In Royal Focus Mode, ChessBoardPanel sits between the top player bar (~15.5% window H)
        // and the bottom pill toolbar (~7.0% window H), spanning ~77.5% of window H.
        // To place outer top at 30.9% window H and front lip at 82.6% window H:
        // topY_in_panel = 0.196 * h, outerH = 0.672 * h, bottomOuterW = outerH * 2.48 (clamped to 0.875 * w).
        double targetOuterH = h * 0.672;
        double maxBottomW = w * 0.875;
        double bottomOuterW = Math.min(maxBottomW, targetOuterH * 2.48);
        double outerH = Math.min(targetOuterH, bottomOuterW * 0.403);
        double topOuterW = bottomOuterW * 0.588; // exact 0.588 reference taper!
        double slab = Math.max(18.0, outerH * 0.106);

        // Anchor board slightly lower in the stage canvas so front slab sits right above the bottom toolbar
        double cx = w * 0.492;
        double botY = Math.min(h - slab - 4.0, h * 0.868);
        double topY = Math.max(h * 0.14, botY - outerH);

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
    double anchorV(int square) { return (visualRow(square) + 0.71) / 8.0; }

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
        if (position != null) {
            double userScale = pieceScalePercent / 97.0;
            for (int row = 7; row >= 0; row--) {
                for (int col = 0; col < 8; col++) {
                    int sq = flipped ? Square.at(7 - col, row) : Square.at(col, 7 - row);
                    Piece piece = position.piece(sq);
                    if (piece == null) continue;
                    Point2D.Double anchor = squareAnchor(sq);
                    double tileW = squareWidth(sq);
                    double ph = tileW * 1.18 * RoyalPieceRenderer.heightRatio(piece.kind()) * userScale;
                    double pw = tileW * 0.62 * userScale;
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
