CREATE TABLE users (
    id INTEGER PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    password_hash TEXT NOT NULL
);
INSERT INTO users(id, username, display_name, password_hash) VALUES(1, '@guest', 'Guest', '');
CREATE TABLE player_statistics (
    user_id INTEGER PRIMARY KEY REFERENCES users(id),
    rating INTEGER NOT NULL DEFAULT 1200 CHECK(rating >= 0),
    wins INTEGER NOT NULL DEFAULT 0,
    losses INTEGER NOT NULL DEFAULT 0,
    draws INTEGER NOT NULL DEFAULT 0,
    streak INTEGER NOT NULL DEFAULT 0,
    puzzle_points INTEGER NOT NULL DEFAULT 0
);
INSERT INTO player_statistics(user_id) VALUES(1);
CREATE TABLE themes (id TEXT PRIMARY KEY);
INSERT INTO themes VALUES('royal'), ('royal-slate');
CREATE TABLE user_themes (
    user_id INTEGER NOT NULL REFERENCES users(id),
    theme_id TEXT NOT NULL REFERENCES themes(id),
    PRIMARY KEY(user_id, theme_id)
);
CREATE TABLE games (
    id TEXT PRIMARY KEY,
    owner_id INTEGER NOT NULL REFERENCES users(id),
    mode TEXT NOT NULL CHECK(mode IN ('LOCAL','COMPUTER')),
    human TEXT NOT NULL CHECK(human IN ('WHITE','BLACK')),
    level TEXT NOT NULL CHECK(level IN ('EASY','MEDIUM','HARD','EXPERT')),
    base_ms INTEGER NOT NULL CHECK(base_ms >= 0),
    increment_ms INTEGER NOT NULL CHECK(increment_ms >= 0),
    theme_id TEXT NOT NULL REFERENCES themes(id),
    started TEXT NOT NULL,
    updated TEXT NOT NULL,
    initial_fen TEXT NOT NULL,
    current_fen TEXT NOT NULL,
    white_ms INTEGER NOT NULL CHECK(white_ms >= 0),
    black_ms INTEGER NOT NULL CHECK(black_ms >= 0),
    reason TEXT,
    winner TEXT CHECK(winner IN ('WHITE','BLACK')),
    assisted INTEGER NOT NULL CHECK(assisted IN (0,1)),
    elapsed_ms INTEGER NOT NULL CHECK(elapsed_ms >= 0),
    CHECK(reason IS NOT NULL OR winner IS NULL)
);
CREATE INDEX games_owner_updated ON games(owner_id, updated DESC);
CREATE TABLE game_moves (
    game_id TEXT NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    ply INTEGER NOT NULL CHECK(ply > 0),
    uci TEXT NOT NULL CHECK(length(uci) IN (4,5)),
    san TEXT NOT NULL,
    white_ms INTEGER NOT NULL CHECK(white_ms >= 0),
    black_ms INTEGER NOT NULL CHECK(black_ms >= 0),
    PRIMARY KEY(game_id, ply)
);
CREATE TABLE rating_history (
    game_id TEXT PRIMARY KEY REFERENCES games(id),
    user_id INTEGER NOT NULL REFERENCES users(id),
    rating_before INTEGER NOT NULL,
    rating_after INTEGER NOT NULL
);
CREATE TABLE puzzles (id TEXT PRIMARY KEY, difficulty TEXT NOT NULL, fen TEXT NOT NULL, solution_uci TEXT NOT NULL, points INTEGER NOT NULL);
CREATE TABLE puzzle_attempts (
    id INTEGER PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id),
    puzzle_id TEXT NOT NULL REFERENCES puzzles(id),
    solved INTEGER NOT NULL CHECK(solved IN (0,1)),
    hinted INTEGER NOT NULL CHECK(hinted IN (0,1)),
    points INTEGER NOT NULL CHECK(points >= 0),
    attempted TEXT NOT NULL
);
CREATE INDEX puzzle_attempts_user ON puzzle_attempts(user_id, id DESC);
CREATE UNIQUE INDEX puzzle_reward_once ON puzzle_attempts(user_id, puzzle_id) WHERE points > 0;
CREATE TABLE achievements (id TEXT PRIMARY KEY);
INSERT INTO achievements VALUES('first-victory'), ('checkmate-master'), ('win-streak'),
 ('puzzle-master'), ('ai-challenger'), ('royal-champion');
CREATE TABLE user_achievements (
    user_id INTEGER NOT NULL REFERENCES users(id),
    achievement_id TEXT NOT NULL REFERENCES achievements(id),
    unlocked TEXT NOT NULL,
    PRIMARY KEY(user_id, achievement_id)
);
CREATE TABLE user_settings (
    user_id INTEGER PRIMARY KEY REFERENCES users(id),
    language TEXT NOT NULL CHECK(language IN ('en','vi')),
    theme_id TEXT NOT NULL REFERENCES themes(id),
    sound INTEGER NOT NULL CHECK(sound IN (0,1)),
    animation INTEGER NOT NULL CHECK(animation IN (0,1)),
    orientation TEXT NOT NULL CHECK(orientation IN ('AUTO','WHITE','BLACK')),
    piece_scale INTEGER NOT NULL CHECK(piece_scale BETWEEN 70 AND 105),
    quality INTEGER NOT NULL CHECK(quality IN (0,1)),
    autosave INTEGER NOT NULL CHECK(autosave IN (0,1))
);
CREATE TABLE app_settings (key TEXT PRIMARY KEY, value TEXT NOT NULL);
