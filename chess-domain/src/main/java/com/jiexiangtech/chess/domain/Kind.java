package com.jiexiangtech.chess.domain;

public enum Kind {
    KING('K', 0), QUEEN('Q', 900), ROOK('R', 500), BISHOP('B', 330), KNIGHT('N', 320), PAWN('P', 100);

    public final char symbol;
    public final int value;
    Kind(char symbol, int value) { this.symbol = symbol; this.value = value; }

    public static Kind from(char symbol) {
        for (Kind kind : values()) {
            if (kind.symbol == Character.toUpperCase(symbol)) return kind;
        }
        throw new IllegalArgumentException("Unknown piece: " + symbol);
    }
}
