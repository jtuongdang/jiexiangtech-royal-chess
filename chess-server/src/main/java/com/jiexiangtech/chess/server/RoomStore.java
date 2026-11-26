package com.jiexiangtech.chess.server;

interface RoomStore {
    RoomDocument load(String id);
    boolean insert(RoomDocument room);
    boolean replace(long expectedVersion, RoomDocument room);
    void ping();
}
