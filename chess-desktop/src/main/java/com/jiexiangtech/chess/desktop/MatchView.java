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
    private final JLabel saveStatus = caption(" ");
    private final RoyalPlayerCard whiteCard, blackCard;
    private final JPanel playersStack;
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
        setBorder(new EmptyBorder(8, 12, 10, 16));

        // =====================================================================
        // CENTER CHESSBOARD STAGE (~55%): JIEXIANGTECH horizontal header + Large Framed Board
        // =====================================================================
        JPanel centerStage = transparent(new BorderLayout(0, 4));
        JPanel brandHeader = transparent(new FlowLayout(FlowLayout.CENTER, 0, 2));
        brandHeader.add(frame.branding().logo(BrandingManager.COMPANY_HORIZONTAL, 224, 44,
                BrandConstants.COMPANY + " " + BrandConstants.COMPANY_CHINESE));
        centerStage.add(brandHeader, BorderLayout.NORTH);

        board = new ChessBoardPanel(frame.assets(), prefs, session.options().theme(), this::canPlay,
                options -> promote(this, frame.assets(), text, session.game().position().turn()), this::play);
        flipped = flip(prefs, session.options().human());
        board.setFlipped(flipped);
        centerStage.add(board, BorderLayout.CENTER);
        add(centerStage, BorderLayout.CENTER);

        // =====================================================================
        // RIGHT GAME PANEL (~30%): Both Player Cards + Move History + Captured + Controls
        // =====================================================================
        JPanel rightPanel = transparent(new BorderLayout(0, 10));
        rightPanel.setPreferredSize(new Dimension(358, 560));
        rightPanel.setMinimumSize(new Dimension(308, 460));
        rightPanel.setBorder(new EmptyBorder(8, 0, 4, 0));

        // 1. Player Cards (Black + White stacked at top of Right Panel)
        whiteCard = buildPlayerCard(Side.WHITE);
        blackCard = buildPlayerCard(Side.BLACK);
        playersStack = transparent(new GridLayout(2, 1, 0, 8));
        placePlayers();
        rightPanel.add(playersStack, BorderLayout.NORTH);

        // 2. Move History (flexible center card)
        moveHistory = new RoyalMoveHistory(text, null);
        rightPanel.add(moveHistory, BorderLayout.CENTER);

        // 3. Lower Stack: Captured Pieces + Reference-Matched Control Grid
        captures = new RoyalCapturedTray(frame.assets(), text);

        saveBtn = gameControl(text.get("save"), RoyalButton.Variant.GOLD_GLOW, RoyalIcons.Glyph.SAVE, () -> {
            if (!leaving) persist(true, null);
        });
        undo = gameControl(text.get("undo"), RoyalButton.Variant.SECONDARY, RoyalIcons.Glyph.UNDO, this::undo);
        hint = gameControl(text.get("hint"), RoyalButton.Variant.SECONDARY, RoyalIcons.Glyph.HINT, this::hint);
        draw = gameControl(text.get("draw"), RoyalButton.Variant.SECONDARY, RoyalIcons.Glyph.DRAW, this::draw);
        resign = gameControl(text.get("resign"), RoyalButton.Variant.DANGER, RoyalIcons.Glyph.RESIGN, this::resign);
        flipBtn = gameControl(text.get("flip"), RoyalButton.Variant.SECONDARY, RoyalIcons.Glyph.FLIP, this::flipBoard);
        sound = gameControl(text.get(soundEnabled ? "soundOn" : "soundOff"), RoyalButton.Variant.SECONDARY,
                soundEnabled ? RoyalIcons.Glyph.SOUND_ON : RoyalIcons.Glyph.SOUND_OFF, this::sound);
        fullscreenBtn = gameControl(text.get("fullscreen"), RoyalButton.Variant.SECONDARY,
                RoyalIcons.Glyph.FULLSCREEN, frame::toggleFullscreen);
        pauseBtn = gameControl(text.get("pause"), RoyalButton.Variant.GHOST, RoyalIcons.Glyph.PAUSE, frame::home);
        pauseBtn.setPreferredSize(new Dimension(120, 32));

        // Row 1: Save | Undo | Hint (3 equal columns)
        JPanel row1 = transparent(new GridLayout(1, 3, 7, 0));
        row1.add(saveBtn);
        row1.add(undo);
        row1.add(hint);

        // Row 2: Draw (1/3) | Resign (2/3) — exact reference visual rhythm
        JPanel row2 = transparent(new GridBagLayout());
        GridBagConstraints rc = new GridBagConstraints();
        rc.gridy = 0; rc.fill = GridBagConstraints.BOTH; rc.weighty = 1;
        rc.gridx = 0; rc.weightx = 0.36; rc.insets = new Insets(0, 0, 0, 7);
        row2.add(draw, rc);
        rc.gridx = 1; rc.weightx = 0.64; rc.insets = new Insets(0, 0, 0, 0);
        row2.add(resign, rc);

        // Row 3: Flip Board | Sound On | Full Screen (3 equal columns)
        JPanel row3 = transparent(new GridLayout(1, 3, 7, 0));
        row3.add(flipBtn);
        row3.add(sound);
        row3.add(fullscreenBtn);

        // Row 4: Save status + Pause & Return
        JPanel row4 = transparent(new BorderLayout(8, 0));
        saveStatus.setBorder(new EmptyBorder(0, 4, 0, 4));
        row4.add(saveStatus, BorderLayout.WEST);
        row4.add(pauseBtn, BorderLayout.CENTER);

        JPanel controls = transparent(new GridLayout(0, 1, 0, 7));
        controls.add(row1);
        controls.add(row2);
        controls.add(row3);
        controls.add(row4);

        JPanel southStack = transparent(new BorderLayout(0, 10));
        southStack.add(captures, BorderLayout.NORTH);
        southStack.add(controls, BorderLayout.SOUTH);
        rightPanel.add(southStack, BorderLayout.SOUTH);

        add(rightPanel, BorderLayout.EAST);

        tick = new Timer(100, event -> onTick());
        tick.setCoalesce(true);
        session.resume();
        refresh(false);
        tick.start();
        if (prefs.autosave()) persist(false, null);
        computer();
    }

    private RoyalPlayerCard buildPlayerCard(Side side) {
        MatchOptions opts = session.options();
        boolean isComputer = opts.mode() == MatchOptions.Mode.COMPUTER && opts.human() != side;
        String sideName = text.get("side." + side);
        String name;
        String badge;
        int elo;
        if (opts.mode() == MatchOptions.Mode.COMPUTER) {
            if (isComputer) {
                name = sideName + " (" + text.get("level." + opts.level()) + ")";
                badge = text.get("badgeAi");
                elo = switch (opts.level()) {
                    case EASY -> 900;
                    case MEDIUM -> 1350;
                    case HARD -> 1750;
                    case EXPERT -> 2150;
                };
            } else {
                name = sideName + " (" + frame.displayLabel() + ")";
                badge = text.get("badgeYou");
                elo = frame.currentRating();
            }
        } else {
            name = sideName;
            badge = text.get("badgeLocal");
            elo = frame.currentRating();
        }
        return new RoyalPlayerCard(frame.assets(), side, name, badge, isComputer, text.get("elo", String.valueOf(elo)));
    }

    private static RoyalButton gameControl(String label, RoyalButton.Variant variant, RoyalIcons.Glyph glyph, Runnable action) {
        RoyalButton btn = new RoyalButton(label, variant, glyph, action);
        btn.setBorder(new EmptyBorder(8, 8, 8, 8));
        btn.setFont(sans(Font.BOLD, 12f));
        btn.setIconTextGap(6);
        btn.setMinimumSize(new Dimension(42, 36));
        btn.setPreferredSize(new Dimension(96, 36));
        btn.setToolTipText(label);
        return btn;
    }

    GameSession session() { return session; }
    ChessBoardPanel boardPanel() { return board; }

    private void placePlayers() {
        playersStack.removeAll();
        playersStack.add(flipped ? whiteCard : blackCard);
        playersStack.add(flipped ? blackCard : whiteCard);
        playersStack.revalidate();
        playersStack.repaint();
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

        String whiteSub = ongoing && session.computerTurn() && turn == Side.WHITE ? text.get("thinking")
                : inCheck && turn == Side.WHITE ? text.get("inCheckBadge") : null;
        String blackSub = ongoing && session.computerTurn() && turn == Side.BLACK ? text.get("thinking")
                : inCheck && turn == Side.BLACK ? text.get("inCheckBadge") : null;

        whiteCard.update(timed ? clock(times.whiteMillis()) : "—", timed, ongoing && turn == Side.WHITE,
                timed && times.whiteMillis() <= 30_000L, inCheck && turn == Side.WHITE, whiteSub);
        blackCard.update(timed ? clock(times.blackMillis()) : "—", timed, ongoing && turn == Side.BLACK,
                timed && times.blackMillis() <= 30_000L, inCheck && turn == Side.BLACK, blackSub);
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
                        saveStatus.setForeground(SUCCESS);
                        saveStatus.setText(text.get("hintMove", San.format(position, result.move())));
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
        Outcome outcome = session.game().outcome();
        if (soundEnabled) frame.sounds().play("result");
        if (outcome.winner() != null) board.celebrate();
        boolean isDraw = outcome.winner() == null;
        boolean isVictory = !isDraw && (session.options().mode() == MatchOptions.Mode.LOCAL || outcome.winner() == session.options().human());
        Color accent = isDraw ? new Color(0xD8DCE4) : isVictory ? PALE : DANGER_TEXT;
        String headline = isDraw ? text.get("draw")
                : session.options().mode() == MatchOptions.Mode.LOCAL ? text.get("winner", text.get("side." + outcome.winner()))
                : text.get(isVictory ? "victory" : "defeat");
        board.showOutcomeOverlay(headline, text.get("reason." + outcome.reason()), accent);

        var snapshot = session.snapshot();
        frame.save(snapshot, receipt -> {
            if (active && !leaving) {
                Timer delay = new Timer(950, e -> { if (active && !leaving) frame.result(this, snapshot, receipt); });
                delay.setRepeats(false);
                delay.start();
            }
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
