package com.jiexiangtech.chess.desktop;

import java.awt.Rectangle;
import com.jiexiangtech.chess.domain.Square;

record BoardCoordinateMapper(int x, int y, int tile, boolean flipped) {
    static BoardCoordinateMapper fit(int width, int height, boolean flipped) {
        int tile = Math.max(1, (Math.min(width, height) - 65) / 8);
        int span = tile * 8;
        return new BoardCoordinateMapper((width - span) / 2, (height - span) / 2, tile, flipped);
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
