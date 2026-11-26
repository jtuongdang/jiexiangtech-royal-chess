package com.jiexiangtech.chess.desktop;

import java.awt.Rectangle;
import com.jiexiangtech.chess.domain.Square;

record BoardCoordinateMapper(int x, int y, int tile, boolean flipped) {
    // Reference bounds are measured from Interface-2. The regularized runtime grid remains square.
    static final int REFERENCE_GRID_WIDTH = 713, REFERENCE_GRID_HEIGHT = 730;
    static final int FRAME_LEFT = 68, FRAME_RIGHT = 47, FRAME_TOP = 43, FRAME_BOTTOM = 69;
    private static final int PANEL_MARGIN = 8;

    static BoardCoordinateMapper fit(int width, int height, boolean flipped) {
        double widthRatio = (REFERENCE_GRID_WIDTH + FRAME_LEFT + FRAME_RIGHT) / (double) REFERENCE_GRID_WIDTH;
        double heightRatio = (REFERENCE_GRID_HEIGHT + FRAME_TOP + FRAME_BOTTOM) / (double) REFERENCE_GRID_HEIGHT;
        int tile = Math.max(1, (int) (Math.min((width - PANEL_MARGIN) / widthRatio, (height - PANEL_MARGIN) / heightRatio) / 8));
        int span = tile * 8;
        int left = bezel(span, FRAME_LEFT, REFERENCE_GRID_WIDTH);
        int right = bezel(span, FRAME_RIGHT, REFERENCE_GRID_WIDTH);
        int top = bezel(span, FRAME_TOP, REFERENCE_GRID_HEIGHT);
        int bottom = bezel(span, FRAME_BOTTOM, REFERENCE_GRID_HEIGHT);
        return new BoardCoordinateMapper((width - span - left - right) / 2 + left,
                (height - span - top - bottom) / 2 + top, tile, flipped);
    }

    int leftBezel() { return bezel(tile * 8, FRAME_LEFT, REFERENCE_GRID_WIDTH); }
    int rightBezel() { return bezel(tile * 8, FRAME_RIGHT, REFERENCE_GRID_WIDTH); }
    int topBezel() { return bezel(tile * 8, FRAME_TOP, REFERENCE_GRID_HEIGHT); }
    int bottomBezel() { return bezel(tile * 8, FRAME_BOTTOM, REFERENCE_GRID_HEIGHT); }

    private static int bezel(int span, int pixels, int referenceSpan) {
        return (int) Math.round(span * (double) pixels / referenceSpan);
    }

    Rectangle bounds(int square) {
        int col = flipped ? 7 - Square.file(square) : Square.file(square);
        int row = flipped ? Square.rank(square) : 7 - Square.rank(square);
        return new Rectangle(x + col * tile, y + row * tile, tile, tile);
    }

    int squareAt(int px, int py) {
        int dx = px - x, dy = py - y;
        if (dx < 0 || dy < 0 || dx >= tile * 8 || dy >= tile * 8) return -1;
        int col = dx / tile, row = dy / tile;
        return flipped ? Square.at(7 - col, row) : Square.at(col, 7 - row);
    }
}
