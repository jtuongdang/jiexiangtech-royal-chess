package com.jiexiangtech.chess.desktop;

import java.util.ArrayList;
import java.util.List;
import com.jiexiangtech.chess.domain.Piece;
import com.jiexiangtech.chess.domain.Position;
import com.jiexiangtech.chess.domain.Square;

/** Sorts all board pieces strictly back-to-front (far rank -> near rank) for 2.5D painter's order. */
final class PieceDepthSorter {
    record Entry(int square, Piece piece, int visualRow, int visualCol) {}

    private PieceDepthSorter() {}

    static List<Entry> backToFront(Position position, boolean flipped) {
        List<Entry> entries = new ArrayList<>(32);
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int sq = flipped ? Square.at(7 - col, row) : Square.at(col, 7 - row);
                Piece piece = position.piece(sq);
                if (piece != null) entries.add(new Entry(sq, piece, row, col));
            }
        }
        return entries;
    }
}
