package com.jiexiangtech.chess.online;

import java.util.Locale;

/** Stable identifiers for the bundled player portraits. */
public enum RoyalAvatar {
    EMBLEM, KNIGHT, QUEEN, SAGE, ARCHER, SOVEREIGN, RANGER;

    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static RoyalAvatar byId(String id) {
        for (RoyalAvatar avatar : values()) {
            if (avatar.id().equals(id)) return avatar;
        }
        return EMBLEM;
    }
}
