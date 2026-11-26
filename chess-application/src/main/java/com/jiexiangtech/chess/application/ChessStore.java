package com.jiexiangtech.chess.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.jiexiangtech.chess.domain.Outcome;

public interface ChessStore {
    long GUEST_ID = 1;

    record User(long id, String username, String displayName) {
        public boolean guest() { return id == GUEST_ID; }
    }

    record Summary(UUID id, MatchOptions options, Outcome outcome, Instant updated, int plies) {}
    record Profile(User user, int rating, int wins, int losses, int draws, int streak, int puzzlePoints,
                   List<String> achievements) {
        public Profile { achievements = List.copyOf(achievements); }
        public int played() { return wins + losses + draws; }
    }
    record PuzzleAttempt(String puzzleId, boolean solved, boolean hinted, int points, Instant date) {}

    enum SaveResult { SAVED, ALREADY_FINISHED }
    final class DuplicateUsernameException extends RuntimeException {}
    final class StorageException extends RuntimeException {
        public StorageException(Throwable cause) { super(cause); }
    }

    User register(String username, String displayName, String passwordHash);
    Optional<User> authenticate(String username, char[] password, Passwords verifier);
    SaveResult save(MatchSnapshot snapshot);
    Optional<MatchSnapshot> load(long owner, UUID id);
    List<Summary> history(long owner);
    Profile profile(long owner);
    List<Profile> leaderboard();
    Preferences settings(long owner, String defaultLanguage);
    void settings(long owner, Preferences settings);
    String rememberedUsername();
    void rememberUsername(String username);
    int recordPuzzle(long owner, String puzzleId, boolean solved, boolean hinted);
    List<PuzzleAttempt> puzzleHistory(long owner);
}
