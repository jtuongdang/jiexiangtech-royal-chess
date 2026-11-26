package com.jiexiangtech.chess.domain;

public record Piece(Side side, Kind kind) {
    private static final Piece[][] PIECES = new Piece[Side.values().length][Kind.values().length];

    static {
        for (Side side : Side.values()) {
            for (Kind kind : Kind.values()) PIECES[side.ordinal()][kind.ordinal()] = new Piece(side, kind);
        }
    }

    public static Piece of(Side side, Kind kind) { return PIECES[side.ordinal()][kind.ordinal()]; }
    public char fen() { return side == Side.WHITE ? kind.symbol : Character.toLowerCase(kind.symbol); }
}
