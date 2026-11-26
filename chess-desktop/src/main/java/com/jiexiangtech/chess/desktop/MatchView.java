package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import com.jiexiangtech.chess.application.*;
import com.jiexiangtech.chess.domain.*;
import com.jiexiangtech.chess.infrastructure.AlphaBetaChessEngine;
import static com.jiexiangtech.chess.desktop.RoyalStyle.*;
import static com.jiexiangtech.chess.desktop.ViewParts.*;

final class MatchView extends JPanel {
    private final RoyalFrame frame;
    private final GameSession session;
    private final I18n text;
    private final Preferences prefs;
    private final ChessBoardPanel board;
    private final Moves moves;
    private final Captures captures;
    private final JTable table;
    private final JLabel status = label(" ", PALE), saveStatus = label(" ", MUTED);
    private final JLabel whiteClock = title("—", 26), blackClock = title("—", 26);
    private final JPanel whiteCard, blackCard, boardArea, side;
    private final JButton undo, hint, resign, draw, sound;
    private final Timer tick;
    private boolean active = true, soundEnabled, leaving, endDisplayed;
    private SwingWorker<ChessEngine.Analysis, Void> search;
    private int saveSequence;

    MatchView(RoyalFrame frame, GameSession session) {
        super(new BorderLayout(12, 0));
        this.frame = frame;
        this.session = session;
        this.text = frame.text();
        this.prefs = frame.prefs();
        this.soundEnabled = prefs.sound();
        setOpaque(false);
        setBorder(new EmptyBorder(12, 10, 12, 14));
        board = new ChessBoardPanel(frame.assets(), prefs, session.options().theme(), this::canPlay,
                options -> promote(this, text), this::play);
        flipped = flip(prefs, session.options().human());
        board.setFlipped(flipped);
        boardArea = transparent(new BorderLayout(0, 3));
        whiteCard = player(Side.WHITE, whiteClock);
        blackCard = player(Side.BLACK, blackClock);
        placePlayers();
        boardArea.add(board, BorderLayout.CENTER);
        add(boardArea, BorderLayout.CENTER);

        moves = new Moves(text);
        table = moves.table();
        captures = new Captures(frame.assets(), text);
        side = card(new BorderLayout(0, 12));
        side.setPreferredSize(new Dimension(276, 440));
        side.setBorder(new EmptyBorder(15, 13, 15, 13));
        JPanel overview = stack(title(text.get("game"), 23), Box.createVerticalStrut(7), status);
        side.add(overview, BorderLayout.NORTH);
        JPanel log = transparent(new BorderLayout(0, 8));
        log.add(label(text.get("moves"), PALE), BorderLayout.NORTH);
        log.add(scroll(table), BorderLayout.CENTER);
        log.add(stack(label(text.get("capture"), MUTED), captures), BorderLayout.SOUTH);
        side.add(log, BorderLayout.CENTER);
        JPanel actions = transparent(new GridLayout(0, 2, 5, 5));
        undo = button(text.get("undo"), false, this::undo);
        hint = button(text.get("hint"), false, this::hint);
        resign = button(text.get("resign"), false, this::resign);
        draw = button(text.get("draw"), false, this::draw);
        sound = button(text.get(soundEnabled ? "soundOn" : "soundOff"), false, this::sound);
        actions.add(button(text.get("save"), true, () -> { if (!leaving) persist(true, null); }));
        actions.add(undo);
        actions.add(hint);
        actions.add(draw);
        actions.add(resign);
        actions.add(button(text.get("flip"), false, this::flipBoard));
        actions.add(sound);
        actions.add(button(text.get("fullscreen"), false, frame::toggleFullscreen));
        JPanel lower = transparent(new BorderLayout(0, 7));
        lower.add(saveStatus, BorderLayout.NORTH);
        lower.add(actions, BorderLayout.CENTER);
        lower.add(button(text.get("pause"), false, frame::home), BorderLayout.SOUTH);
        side.add(lower, BorderLayout.SOUTH);
        add(side, BorderLayout.EAST);

        tick = new Timer(100, event -> onTick());
        tick.setCoalesce(true);
        session.resume();
        refresh(false);
        tick.start();
        if (prefs.autosave()) persist(false, null);
        computer();
    }

    GameSession session() { return session; }

    private JPanel player(Side color, JLabel clock) {
        JPanel card = card(new BorderLayout(10, 0));
        card.setBorder(new EmptyBorder(7, 13, 7, 13));
        var marker = label("●", color == Side.WHITE ? IVORY : GOLD);
        card.add(marker, BorderLayout.WEST);
        card.add(stack(label(frame.playerName(session.options(), color), IVORY),
                label(text.get("side." + color), MUTED)), BorderLayout.CENTER);
        card.add(clock, BorderLayout.EAST);
        return card;
    }

    private boolean flipped;
    private void placePlayers() {
        boardArea.remove(whiteCard);
        boardArea.remove(blackCard);
        boardArea.add(flipped ? whiteCard : blackCard, BorderLayout.NORTH);
        boardArea.add(flipped ? blackCard : whiteCard, BorderLayout.SOUTH);
        boardArea.revalidate();
        boardArea.repaint();
    }

    private void flipBoard() {
        flipped = !flipped;
        board.flip();
        placePlayers();
    }

    private boolean canPlay() { return active && !leaving && session.game().outcome() == null && !session.computerTurn(); }

    private void play(Move move) {
        if (!active || leaving || endDisplayed) return;
        cancelSearch();
        boolean moved = session.play(move);
        refresh(moved);
        if (session.game().outcome() != null) {
            finish();
            return;
        }
        if (!moved) return;
        if (soundEnabled) {
            Game.Ply ply = session.game().moves().get(session.game().plyCount() - 1);
            String cue = Rules.inCheck(session.game().position(), session.game().position().turn()) ? "check"
                    : ply.captured() == null ? "move" : "capture";
            frame.sounds().play(cue);
        }
        if (prefs.autosave()) persist(false, null);
        computer();
    }

    private void onTick() {
        if (!active || leaving) return;
        if (session.checkTimeout()) {
            refresh(false);
            finish();
        }
        paintClocks();
    }

    private void refresh(boolean animation) {
        Game game = session.game();
        Move last = game.plyCount() == 0 ? null : game.moves().get(game.plyCount() - 1).move();
        board.update(game.position(), last, animation);
        moves.update(game, game.plyCount());
        captures.update(game, game.plyCount());
        if (table.getRowCount() > 0) table.scrollRectToVisible(table.getCellRect(table.getRowCount() - 1, 0, true));
        Side turn = game.position().turn();
        String color = text.get("side." + turn);
        status.setText(game.outcome() != null ? text.get("reason." + game.outcome().reason())
                : session.computerTurn() ? text.get("thinking")
                : text.get(Rules.inCheck(game.position(), turn) ? "check" : "turn", color));
        undo.setEnabled(session.canUndo());
        hint.setEnabled(canPlay());
        resign.setEnabled(game.outcome() == null);
        draw.setEnabled(canPlay() && (canClaim() || session.options().mode() == MatchOptions.Mode.LOCAL && game.plyCount() >= 2));
        paintClocks();
    }

    private void paintClocks() {
        var times = session.times();
        Side turn = session.game().position().turn();
        whiteClock.setText(session.options().timed() ? clock(times.whiteMillis()) : "—");
        blackClock.setText(session.options().timed() ? clock(times.blackMillis()) : "—");
        whiteClock.setForeground(turn == Side.WHITE ? PALE : MUTED);
        blackClock.setForeground(turn == Side.BLACK ? PALE : MUTED);
    }

    private void computer() {
        if (!active || leaving || session.game().outcome() != null || !session.computerTurn()) return;
        ChessEngine engine = frame.engines().forLevel(session.options().level());
        startSearch(engine, Duration.ofMillis(switch (session.options().level()) {
            case EASY -> 70;
            case MEDIUM -> 350;
            case HARD -> 800;
            case EXPERT -> 1500;
        }), false);
    }

    private void hint() {
        if (!canPlay()) return;
        session.markAssisted();
        if (prefs.autosave()) persist(false, null);
        startSearch(new AlphaBetaChessEngine(false), Duration.ofMillis(700), true);
    }

    private void startSearch(ChessEngine engine, Duration budget, boolean showingHint) {
        cancelSearch();
        Position position = session.game().position();
        search = new SwingWorker<>() {
            @Override protected ChessEngine.Analysis doInBackground() {
                return engine.analyze(position, budget).orElse(null);
            }

            @Override protected void done() {
                if (search != this || !active || leaving || position != session.game().position()
                        || session.game().outcome() != null) return;
                search = null;
                try {
                    var result = get();
                    if (result == null) return;
                    if (showingHint) {
                        board.showHint(result.move());
                        status.setText(text.get("hintMove", San.format(position, result.move())));
                    } else play(result.move());
                } catch (CancellationException ignored) {
                    // A newer action owns the board.
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    frame.error("storeError", e.getCause());
                    if (!showingHint) {
                        var legal = Rules.legalMoves(position);
                        if (!legal.isEmpty()) play(legal.get(0));
                    }
                }
            }
        };
        search.execute();
    }

    private void cancelSearch() {
        if (search != null) search.cancel(true);
        search = null;
    }

    private void undo() {
        if (!session.canUndo() || leaving) return;
        cancelSearch();
        session.undo();
        refresh(false);
        if (session.game().outcome() != null) finish();
        else {
            if (prefs.autosave()) persist(false, null);
            computer();
        }
    }

    private void sound() {
        soundEnabled = !soundEnabled;
        sound.setText(text.get(soundEnabled ? "soundOn" : "soundOff"));
        if (soundEnabled) frame.sounds().play("move");
    }

    private List<Move> claimMoves() {
        var game = session.game();
        return Rules.legalMoves(game.position()).stream().filter(move -> game.claimable(move) != null).toList();
    }

    private boolean canClaim() {
        Game game = session.game();
        return game.outcome() == null && (game.claimable(null) != null || !claimMoves().isEmpty());
    }

    private void draw() {
        if (!canPlay()) return;
        var game = session.game();
        boolean agreement = session.options().mode() == MatchOptions.Mode.LOCAL && game.plyCount() >= 2;
        boolean current = game.claimable(null) != null;
        List<Move> candidates = claimMoves();
        var actions = new java.util.ArrayList<String>();
        if (current) actions.add(text.get("claim"));
        if (!candidates.isEmpty()) actions.add(text.get("claimByMove"));
        if (agreement) actions.add(text.get("offer"));
        if (actions.isEmpty()) return;
        Object choice = JOptionPane.showInputDialog(this, text.get("draw"), text.get("draw"),
                JOptionPane.PLAIN_MESSAGE, null, actions.toArray(), actions.get(0));
        if (choice == null || !canPlay()) return;
        if (choice.equals(text.get("claim"))) session.claimDraw(null);
        else if (choice.equals(text.get("claimByMove"))) {
            var san = candidates.stream().map(move -> San.format(game.position(), move)).toList();
            Object selection = JOptionPane.showInputDialog(this, text.get("claimByMove"), text.get("draw"),
                    JOptionPane.PLAIN_MESSAGE, null, san.toArray(), san.get(0));
            if (selection == null || !canPlay()) return;
            session.claimDraw(candidates.get(san.indexOf(selection)));
        } else if (frame.confirm("agree") && canPlay()) session.agreeDraw();
        if (game.outcome() != null) { refresh(false); finish(); }
    }

    private void resign() {
        if (leaving || session.game().outcome() != null || !frame.confirm("confirmResign") || session.game().outcome() != null) return;
        session.resign();
        refresh(false);
        finish();
    }

    private void persist(boolean manual, Runnable after) {
        var snapshot = session.snapshot();
        int sequence = ++saveSequence;
        frame.save(snapshot, receipt -> {
            if (!active || sequence != saveSequence) return;
            saveStatus.setForeground(MUTED);
            saveStatus.setText(text.get("saved"));
            if (manual) frame.setStatus(text.get("saved"));
            if (after != null) after.run();
        }, failure -> {
            if (!active || sequence != saveSequence) return;
            saveStatus.setForeground(ERROR_TEXT);
            saveStatus.setText(text.get("error"));
            if (leaving) {
                leaving = false;
                session.resume();
                refresh(false);
                computer();
            }
            frame.error("saveError", failure);
        });
    }

    private void finish() {
        if (endDisplayed) return;
        endDisplayed = true;
        tick.stop();
        cancelSearch();
        if (soundEnabled) frame.sounds().play("result");
        if (session.game().outcome().winner() != null) board.celebrate();
        var snapshot = session.snapshot();
        frame.save(snapshot, receipt -> {
            if (active && !leaving) frame.result(this, snapshot, receipt);
        }, failure -> {
            if (active && !leaving) {
                frame.error("saveError", failure);
                if (active && !leaving) frame.result(this, snapshot, null);
            }
        });
    }

    void leave(Runnable next) {
        if (!active || leaving) return;
        if (session.game().outcome() == null && !prefs.autosave()) {
            int answer = JOptionPane.showOptionDialog(this, text.get("unsaved"), text.get("save"),
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                    new Object[]{text.get("save"), text.get("discard"), text.get("cancel")}, text.get("save"));
            if (answer < 0 || answer == 2) return;
            if (answer == 1) { close(); next.run(); return; }
        }
        leaving = true;
        cancelSearch();
        session.pause();
        persist(false, () -> { close(); next.run(); });
    }

    void close() {
        active = false;
        tick.stop();
        cancelSearch();
        session.pause();
    }
}
