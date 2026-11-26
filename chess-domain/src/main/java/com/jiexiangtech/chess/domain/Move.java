package com.jiexiangtech.chess.domain;

public record Move(int from, int to, Kind promotion) {
    public Move {
        if (!Square.valid(from) || !Square.valid(to) || from == to
                || promotion == Kind.PAWN || promotion == Kind.KING) throw new IllegalArgumentException("Invalid move");
    }

    public Move(int from, int to) { this(from, to, null); }

    public static Move uci(String text) {
        if (text.length() != 4 && text.length() != 5) throw new IllegalArgumentException("Invalid UCI: " + text);
        return new Move(Square.parse(text.substring(0, 2)), Square.parse(text.substring(2, 4)),
                text.length() == 5 ? Kind.from(text.charAt(4)) : null);
    }

    public String uci() {
        return Square.name(from) + Square.name(to) + (promotion == null ? "" : Character.toLowerCase(promotion.symbol));
    }
}
