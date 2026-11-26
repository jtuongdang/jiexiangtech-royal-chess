package com.jiexiangtech.chess.domain;

public enum Side {
    WHITE(1), BLACK(-1);

    public final int forward;
    Side(int forward) { this.forward = forward; }
    public Side opposite() { return this == WHITE ? BLACK : WHITE; }
}
