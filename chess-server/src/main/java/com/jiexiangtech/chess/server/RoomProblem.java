package com.jiexiangtech.chess.server;

import org.springframework.http.HttpStatus;

final class RoomProblem extends RuntimeException {
    final HttpStatus status;
    final String code;

    RoomProblem(HttpStatus status, String code) {
        super(code);
        this.status = status;
        this.code = code;
    }
}
