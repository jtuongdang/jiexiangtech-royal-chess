package com.jiexiangtech.chess.desktop;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import com.jiexiangtech.chess.application.Puzzles;
import com.jiexiangtech.chess.domain.Game;
import com.jiexiangtech.chess.domain.Kind;
import com.jiexiangtech.chess.domain.Move;
import com.jiexiangtech.chess.domain.Piece;
import com.jiexiangtech.chess.domain.Side;
import static com.jiexiangtech.chess.desktop.RoyalStyle.*;
import static com.jiexiangtech.chess.desktop.ViewParts.*;

final class PuzzleView extends JPanel {
    private final RoyalFrame frame;
    private final Puzzles.Puzzle puzzle;
    private final Game game;
    private final ChessBoardPanel board;
    private final JLabel status = label(" ", PALE);
    private final RoyalButton hint;
    private boolean hinted, solved;

    PuzzleView(RoyalFrame frame, Puzzles.Puzzle puzzle) {
        super(new BorderLayout(16, 10));
        this.frame = frame;
        this.puzzle = puzzle;
        this.game = puzzle.game();
        I18n text = frame.text();
        setOpaque(false);
        setBorder(new EmptyBorder(14, 18, 14, 20));

        add(heading(text.get("puzzle." + puzzle.id()), text.get("mateOne"),
                button(text.get("puzzleBack"), RoyalButton.Variant.SECONDARY, RoyalIcons.Glyph.PREV, frame::puzzles)),
                BorderLayout.NORTH);

        board = new ChessBoardPanel(frame.assets(), frame.prefs(), frame.prefs().theme(), () -> !solved,
                options -> promote(this, frame.assets(), text, game.position().turn()), this::tryMove);
        board.setFlipped(flip(frame.prefs(), game.position().turn()));
        board.update(game.position(), null, false);
        add(board, BorderLayout.CENTER);

        RoyalPanel side = new RoyalPanel(new BorderLayout(0, 14), new Color(0x19171D), new Color(0x121115), LINE, 16);
        side.setPreferredSize(new Dimension(304, 420));
        side.setBorder(new EmptyBorder(18, 18, 18, 18));

        Side turn = game.position().turn();
        JPanel badges = row(
                pill(text.get("level." + puzzle.level()), new Color(212, 175, 55, 34), new Color(212, 175, 55, 130), PALE),
                pill("+" + puzzle.points() + " pts", new Color(0x211E24), LINE_GOLD, IVORY));

        RoyalPanel turnCard = new RoyalPanel(new BorderLayout(10, 0), new Color(0x1D1A15), new Color(0x141210), LINE_GOLD, 12);
        turnCard.setBorder(new EmptyBorder(10, 12, 10, 12));
        JComponent miniPiece = new JComponent() {
            { setPreferredSize(new Dimension(32, 32)); }
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                frame.assets().drawPieceFitted(g, Piece.of(turn, Kind.KING), 2, 1, getWidth() - 4, getHeight() - 2);
                g.dispose();
            }
        };
        turnCard.add(miniPiece, BorderLayout.WEST);
        turnCard.add(stack(
                eyebrow(text.get("puzzles")),
                label(text.get("turn", text.get("side." + turn)), IVORY)), BorderLayout.CENTER);

        side.add(stack(badges, Box.createVerticalStrut(12), turnCard, Box.createVerticalStrut(12),
                divider(), Box.createVerticalStrut(12), title(text.get("mateOne"), 20)), BorderLayout.NORTH);

        RoyalPanel feedback = new RoyalPanel(new BorderLayout(), new Color(0x131216), new Color(0x100F13), new Color(0x2B251C), 12);
        feedback.setBorder(new EmptyBorder(14, 14, 14, 14));
        status.setText("<html>" + text.get("mateOne") + "</html>");
        status.setForeground(MUTED);
        status.setVerticalAlignment(SwingConstants.TOP);
        status.setFont(sans(Font.PLAIN, 13.5f));
        feedback.add(status, BorderLayout.CENTER);
        side.add(feedback, BorderLayout.CENTER);

        hint = button(text.get("hint"), RoyalButton.Variant.PRIMARY, RoyalIcons.Glyph.HINT, () -> {
            hinted = true;
            board.showHint(puzzle.solution());
            status(text.get("puzzleHint"), PALE);
        });
        RoyalButton retry = button(text.get("puzzleRetry"), RoyalButton.Variant.SECONDARY, RoyalIcons.Glyph.UNDO,
                () -> frame.openPuzzle(puzzle));
        JPanel footer = transparent(new GridLayout(0, 1, 0, 8));
        footer.add(hint);
        footer.add(retry);
        side.add(footer, BorderLayout.SOUTH);
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
