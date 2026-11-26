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
    private final RoyalMoveHistory moveHistory;
    private final RoyalCapturedTray captures;
    private final JLabel statusPill;
    private final JLabel saveStatus = caption(" ");
    private final RoyalPlayerCard whiteCard, blackCard;
    private final JPanel boardArea;
    private final RoyalButton saveBtn, undo, hint, resign, draw, sound, flipBtn, fullscreenBtn, pauseBtn;
    private final Timer tick;
    private boolean active = true, soundEnabled, leaving, endDisplayed, flipped;
    private SwingWorker<ChessEngine.Analysis, Void> search;
    private int saveSequence;

    MatchView(RoyalFrame frame, GameSession session) {
        super(new BorderLayout(16, 0));
        this.frame = frame;
        this.session = session;
        this.text = frame.text();
        this.prefs = frame.prefs();
        this.soundEnabled = prefs.sound();
        setOpaque(false);
        setBorder(new EmptyBorder(12, 16, 12, 18));

        board = new ChessBoardPanel(frame.assets(), prefs, session.options().theme(), this::canPlay,
                options -> promote(this, frame.assets(), text, session.game().position().turn()), this::play);
        flipped = flip(prefs, session.options().human());
        board.setFlipped(flipped);

        // Player info cards anchored closely to the board stage
        whiteCard = new RoyalPlayerCard(frame.assets(), Side.WHITE,
                frame.playerName(session.options(), Side.WHITE), text.get("side.WHITE"), text.get("activeTurn"));
        blackCard = new RoyalPlayerCard(frame.assets(), Side.BLACK,
                frame.playerName(session.options(), Side.BLACK), text.get("side.BLACK"), text.get("activeTurn"));

        boardArea = transparent(new BorderLayout(0, 6));
        placePlayers();
        boardArea.add(board, BorderLayout.CENTER);
        add(boardArea, BorderLayout.CENTER);

        // Right cohesive luxury command panel
        RoyalPanel side = new RoyalPanel(new BorderLayout(0, 12), new Color(0x19171D), new Color(0x121115), LINE, 16);
        side.setPreferredSize(new Dimension(304, 520));
        side.setMinimumSize(new Dimension(276, 420));
        side.setBorder(new EmptyBorder(16, 16, 14, 16));

        // Top match header & status pill
        statusPill = pill(text.get("turn", text.get("side.WHITE")),
                new Color(212, 175, 55, 34), new Color(212, 175, 55, 130), PALE);
        JPanel titleRow = transparent(new BorderLayout(8, 0));
        titleRow.add(title(text.get("game"), 22), BorderLayout.WEST);
        titleRow.add(statusPill, BorderLayout.EAST);
        JPanel overview = stack(
                eyebrow(text.get("mode." + session.options().mode())),
                Box.createVerticalStrut(4),
                titleRow,
                Box.createVerticalStrut(10),
                divider());
        side.add(overview, BorderLayout.NORTH);

        // Center: custom move history + captured pieces tray
        moveHistory = new RoyalMoveHistory(text, null);
        captures = new RoyalCapturedTray(frame.assets(), text);

        JPanel movesHeader = transparent(new BorderLayout());
        movesHeader.add(eyebrow(text.get("moves")), BorderLayout.WEST);
        JPanel movesSection = transparent(new BorderLayout(0, 6));
        movesSection.add(movesHeader, BorderLayout.NORTH);
        movesSection.add(moveHistory, BorderLayout.CENTER);

        JPanel capturedSection = transparent(new BorderLayout(0, 6));
        capturedSection.setBorder(new EmptyBorder(8, 0, 0, 0));
        capturedSection.add(eyebrow(text.get("capture")), BorderLayout.NORTH);
        capturedSection.add(captures, BorderLayout.CENTER);

        JPanel centerStack = transparent(new BorderLayout(0, 4));
        centerStack.add(movesSection, BorderLayout.CENTER);
        centerStack.add(capturedSection, BorderLayout.SOUTH);
        side.add(centerStack, BorderLayout.CENTER);

        // South: hierarchical action buttons
        saveBtn = compactAction(text.get("save"), RoyalButton.Variant.PRIMARY, RoyalIcons.Glyph.SAVE, () -> {
            if (!leaving) persist(true, null);
        });
        undo = compactAction(text.get("undo"), RoyalButton.Variant.SECONDARY, RoyalIcons.Glyph.UNDO, this::undo);
        hint = compactAction(text.get("hint"), RoyalButton.Variant.SECONDARY, RoyalIcons.Glyph.HINT, this::hint);
        draw = compactAction(text.get("draw"), RoyalButton.Variant.SECONDARY, RoyalIcons.Glyph.DRAW, this::draw);
        flipBtn = compactAction(text.get("flip"), RoyalButton.Variant.SECONDARY, RoyalIcons.Glyph.FLIP, this::flipBoard);
        sound = compactAction(text.get(soundEnabled ? "soundOn" : "soundOff"), RoyalButton.Variant.SECONDARY,
                soundEnabled ? RoyalIcons.Glyph.SOUND_ON : RoyalIcons.Glyph.SOUND_OFF, this::sound);
        fullscreenBtn = compactAction(text.get("fullscreen"), RoyalButton.Variant.SECONDARY,
                RoyalIcons.Glyph.FULLSCREEN, frame::toggleFullscreen);
        resign = compactAction(text.get("resign"), RoyalButton.Variant.DANGER, RoyalIcons.Glyph.RESIGN, this::resign);
        pauseBtn = compactAction(text.get("pause"), RoyalButton.Variant.GHOST, RoyalIcons.Glyph.PAUSE, frame::home);

        JPanel actionGrid = transparent(new GridLayout(4, 2, 7, 7));
        actionGrid.add(saveBtn);
        actionGrid.add(undo);
        actionGrid.add(hint);
        actionGrid.add(draw);
        actionGrid.add(flipBtn);
        actionGrid.add(sound);
        actionGrid.add(fullscreenBtn);
        actionGrid.add(resign);

        JPanel lower = transparent(new BorderLayout(0, 7));
        JPanel statusLine = transparent(new BorderLayout());
        statusLine.add(divider(), BorderLayout.NORTH);
        saveStatus.setBorder(new EmptyBorder(6, 2, 0, 2));
        statusLine.add(saveStatus, BorderLayout.SOUTH);
        lower.add(statusLine, BorderLayout.NORTH);
        lower.add(actionGrid, BorderLayout.CENTER);
        lower.add(pauseBtn, BorderLayout.SOUTH);
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

    private static RoyalButton compactAction(String label, RoyalButton.Variant variant, RoyalIcons.Glyph glyph, Runnable action) {
        RoyalButton btn = new RoyalButton(label, variant, glyph, action);
        btn.setBorder(new EmptyBorder(7, 10, 7, 10));
        btn.setFont(sans(Font.BOLD, 12.5f));
        btn.setMinimumSize(new Dimension(42, 34));
        return btn;
    }

    GameSession session() { return session; }

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
        moveHistory.update(game, game.plyCount());
        captures.update(game, game.plyCount());

        Side turn = game.position().turn();
        boolean inCheck = game.outcome() == null && Rules.inCheck(game.position(), turn);
        String color = text.get("side." + turn);
        String statusText = game.outcome() != null ? text.get("reason." + game.outcome().reason())
                : session.computerTurn() ? text.get("thinking")
                : text.get(inCheck ? "check" : "turn", color);
        statusPill.setText(statusText);
        statusPill.setForeground(inCheck ? ERROR_TEXT : PALE);

        undo.setEnabled(session.canUndo());
        hint.setEnabled(canPlay());
        resign.setEnabled(game.outcome() == null);
        draw.setEnabled(canPlay() && (canClaim() || session.options().mode() == MatchOptions.Mode.LOCAL && game.plyCount() >= 2));
        paintClocks();
    }

    private void paintClocks() {
        var times = session.times();
        Game game = session.game();
        Side turn = game.position().turn();
        boolean ongoing = game.outcome() == null;
        boolean inCheck = ongoing && Rules.inCheck(game.position(), turn);
        boolean timed = session.options().timed();
        String badge = inCheck ? text.get("inCheckBadge") : text.get("activeTurn");
        whiteCard.update(timed ? clock(times.whiteMillis()) : "—", timed, ongoing && turn == Side.WHITE,
                timed && times.whiteMillis() <= 30_000L, inCheck && turn == Side.WHITE, badge);
        blackCard.update(timed ? clock(times.blackMillis()) : "—", timed, ongoing && turn == Side.BLACK,
                timed && times.blackMillis() <= 30_000L, inCheck && turn == Side.BLACK, badge);
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
                        statusPill.setText(text.get("hintMove", San.format(position, result.move())));
                        statusPill.setForeground(SUCCESS);
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
        sound.setGlyph(soundEnabled ? RoyalIcons.Glyph.SOUND_ON : RoyalIcons.Glyph.SOUND_OFF);
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
        String choice = RoyalDialog.choose(this, text.get("draw"), null, actions, text.get("cancel"));
        if (choice == null || !canPlay()) return;
        if (choice.equals(text.get("claim"))) session.claimDraw(null);
        else if (choice.equals(text.get("claimByMove"))) {
            var san = candidates.stream().map(move -> San.format(game.position(), move)).toList();
            String selection = RoyalDialog.choose(this, text.get("claimByMove"), null, san, text.get("cancel"));
            if (selection == null || !canPlay()) return;
            session.claimDraw(candidates.get(san.indexOf(selection)));
        } else if (frame.confirm("agree") && canPlay()) session.agreeDraw();
        if (game.outcome() != null) { refresh(false); finish(); }
    }

    private void resign() {
        if (leaving || session.game().outcome() != null
                || !RoyalDialog.confirm(this, text.get("resign"), text.get("confirmResign"),
                        text.get("resign"), text.get("cancel"), true)
                || session.game().outcome() != null) return;
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
            String choice = RoyalDialog.choose(this, text.get("save"), text.get("unsaved"),
                    List.of(text.get("save"), text.get("discard")), text.get("cancel"));
            if (choice == null) return;
            if (choice.equals(text.get("discard"))) { close(); next.run(); return; }
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
