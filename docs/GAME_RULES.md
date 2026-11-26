# Game rules

The engine generates and applies the legal moves of all six pieces, including both castles,
en passant and all four promotion choices. A move cannot expose the moving side’s king
or capture the opponent’s king. FEN keeps castling rights, en passant and move counters.

Checkmate takes precedence over automatic move-count draws. Stalemate and detected dead
positions end play. Repetition compares the board, side to move, castling rights and a
legally capturable en passant target. Threefold repetition and 50 moves per side without
a pawn move or capture can be claimed in the current position or with a legal intended
move. Fivefold repetition and 75 such moves per side end the game automatically.

Local two-player games can end by draw agreement after each player has moved. Resignation
and clock expiry award the game to the opponent when that opponent can possibly mate.
The engine identifies standard mating-material cases and sealed king-and-pawn walls.
More unusual positions that require a general proof of mate reachability remain a gap.
[FIDE Laws of Chess, articles 3, 5, 6 and 9](https://handbook.fide.com/chapter/E012023)

The clock uses monotonic elapsed time. On each completed move it settles time, credits the
increment, and runs the opponent’s clock. Navigation pauses it. Undo restores the previous
position and corresponding clock checkpoint. A computer match undoes the last human
decision and its computer reply when both are present. Hints and undo remove rating
eligibility.

Imported PGN accepts one standard chess game, optional FEN, comments, NAGs and ignored
side variations. PGN export includes the standard headers and SAN main line. An imported
game is available for replay during that session. Import does not merge it into SQLite history.
