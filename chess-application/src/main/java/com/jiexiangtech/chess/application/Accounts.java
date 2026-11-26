package com.jiexiangtech.chess.application;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public final class Accounts {
    public enum Problem { USERNAME, DISPLAY_NAME, PASSWORD, CONFIRMATION }
    public static final class Invalid extends IllegalArgumentException {
        private final Problem problem;
        public Invalid(Problem problem) { super(problem.name()); this.problem = problem; }
        public Problem problem() { return problem; }
    }

    private final ChessStore store;
    private final Passwords passwords;

    public Accounts(ChessStore store, Passwords passwords) {
        this.store = store;
        this.passwords = passwords;
    }

    public ChessStore.User register(String username, String displayName, char[] password, char[] confirmation) {
        try {
            String user = normalize(username);
            if (!user.matches("[a-z0-9_]{3,24}")) throw new Invalid(Problem.USERNAME);
            String display = displayName.strip();
            if (display.isEmpty() || display.length() > 32 || display.chars().anyMatch(Character::isISOControl)) {
                throw new Invalid(Problem.DISPLAY_NAME);
            }
            if (password.length < 8 || password.length > 128) throw new Invalid(Problem.PASSWORD);
            if (!Arrays.equals(password, confirmation)) throw new Invalid(Problem.CONFIRMATION);
            return store.register(user, display, passwords.hash(password));
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(confirmation, '\0');
        }
    }

    public Optional<ChessStore.User> signIn(String username, char[] password, boolean remember) {
        try {
            if (password.length > 128) return Optional.empty();
            var user = store.authenticate(normalize(username), password, passwords);
            if (user.isPresent()) store.rememberUsername(remember ? user.get().username() : "");
            return user;
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static String normalize(String username) { return username.strip().toLowerCase(Locale.ROOT); }
}
