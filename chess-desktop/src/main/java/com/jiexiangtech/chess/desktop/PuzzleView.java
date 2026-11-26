package com.jiexiangtech.chess.desktop;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import com.jiexiangtech.chess.application.Puzzles;
import com.jiexiangtech.chess.domain.Game;
import com.jiexiangtech.chess.domain.Move;
import static com.jiexiangtech.chess.desktop.RoyalStyle.*;
import static com.jiexiangtech.chess.desktop.ViewParts.*;

final class PuzzleView extends JPanel {
    private final RoyalFrame frame;
    private final Puzzles.Puzzle puzzle;
    private final Game game;
    private final ChessBoardPanel board;
    private final JLabel status = label(" ", PALE);
    private final JButton hint;
    private boolean hinted, solved;

    PuzzleView(RoyalFrame frame, Puzzles.Puzzle puzzle) {
        super(new BorderLayout(18, 10));
        this.frame = frame;
        this.puzzle = puzzle;
        this.game = puzzle.game();
        I18n text = frame.text();
        setOpaque(false);
        setBorder(new EmptyBorder(16, 14, 16, 20));
        add(heading(text.get("puzzle." + puzzle.id()), button(text.get("puzzleBack"), false, frame::puzzles)),
                BorderLayout.NORTH);
        board = new ChessBoardPanel(frame.assets(), frame.prefs(), frame.prefs().theme(), () -> !solved,
                options -> promote(this, text), this::tryMove);
        board.setFlipped(flip(frame.prefs(), game.position().turn()));
        board.update(game.position(), null, false);
        add(board, BorderLayout.CENTER);
        var side = card(new BorderLayout(0, 14));
        side.setPreferredSize(new Dimension(275, 300));
        JLabel level = label(text.get("level." + puzzle.level()), PALE);
        side.add(stack(level, Box.createVerticalStrut(10), title(text.get("mateOne"), 20),
                Box.createVerticalStrut(12), label(text.get("turn", text.get("side." + game.position().turn())), MUTED)),
                BorderLayout.NORTH);
        status.setVerticalAlignment(SwingConstants.TOP);
        side.add(status, BorderLayout.CENTER);
        hint = button(text.get("hint"), true, () -> {
            hinted = true;
            board.showHint(puzzle.solution());
            status(text.get("puzzleHint"), PALE);
        });
        side.add(stack(hint, Box.createVerticalStrut(10), button(text.get("puzzleRetry"), false, () -> frame.openPuzzle(puzzle))),
                BorderLayout.SOUTH);
        add(side, BorderLayout.EAST);
    }

    private void tryMove(Move move) {
        solved = Puzzles.solve(game, move);
        if (solved) {
            board.update(game.position(), move, true);
            board.celebrate();
            hint.setEnabled(false);
            if (frame.prefs().sound()) frame.sounds().play("result");
            status(frame.text().get("puzzleSolved"), SUCCESS);
        } else status(frame.text().get("puzzleWrong"), ERROR_TEXT);
        frame.recordPuzzle(this, puzzle, solved, hinted);
    }

    void reward(int points) {
        if (solved && points > 0) status(frame.text().get("puzzleReward", points), SUCCESS);
    }

    private void status(String message, Color color) {
        status.setText("<html>" + message + "</html>");
        status.setForeground(color);
    }
}
