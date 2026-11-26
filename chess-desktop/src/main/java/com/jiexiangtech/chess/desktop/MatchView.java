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
    private Preferences prefs;
    private final FocusModeController focusController;
    private final ChessBoardPanel board;
    private final RoyalMoveHistory moveHistory;
    private final RoyalCapturedTray captures;
    private final JLabel saveStatus = caption(" ");
    private final RoyalPlayerCard whiteCard, blackCard;
    private final Timer tick;
    private RoyalButton undoBtn, hintBtn, resignBtn, drawBtn, soundBtn;
    private RoyalIconButton drawerToggleBtn;
    private JPanel focusDrawer;
    private boolean active = true, soundEnabled, leaving, endDisplayed, flipped;
    private SwingWorker<ChessEngine.Analysis, Void> search;
    private int saveSequence;

    MatchView(RoyalFrame frame, GameSession session) {
        super(new BorderLayout());
        this.frame = frame;
        this.session = session;
        this.text = frame.text();
        this.prefs = frame.prefs();
        this.soundEnabled = prefs.sound();
        this.focusController = new FocusModeController(prefs.viewMode());
        setOpaque(false);

        board = new ChessBoardPanel(frame.assets(), prefs, session.options().theme(), this::canPlay,
                options -> promote(this, frame.assets(), text, session.game().position().turn()), this::play);
        flipped = flip(prefs, session.options().human());
        board.setFlipped(flipped);
        board.setFocusMode(focusController.isFocusMode());

        whiteCard = buildPlayerCard(Side.WHITE);
        blackCard = buildPlayerCard(Side.BLACK);
        moveHistory = new RoyalMoveHistory(text, null);
        captures = new RoyalCapturedTray(frame.assets(), text);

        rebuildLayout();

        tick = new Timer(100, event -> onTick());
        tick.setCoalesce(true);
        session.resume();
        refresh(false);
        tick.start();
        if (prefs.autosave()) persist(false, null);
        computer();
    }

    boolean isFocusMode() { return focusController.isFocusMode(); }
    GameSession session() { return session; }
    ChessBoardPanel boardPanel() { return board; }

    void setFocusMode(boolean enabled, boolean persistChoice) {
        Preferences.ViewMode next = enabled ? Preferences.ViewMode.ROYAL_FOCUS_25D : Preferences.ViewMode.CLASSIC_2D;
        if (focusController.mode() == next) return;
        focusController.setMode(next);
        prefs = prefs.withViewMode(next);
        board.setPreferences(prefs);
        board.setFocusMode(enabled);
        rebuildLayout();
        refresh(false);
        if (persistChoice) frame.updateViewMode(next);
        else frame.refreshShellAtmosphere();
    }

    private void toggleViewMode() {
        setFocusMode(!focusController.isFocusMode(), true);
    }

    private void rebuildLayout() {
        removeAll();
        if (focusController.isFocusMode()) {
            buildFocus25DLayout();
        } else {
            buildClassic2DLayout();
        }
        revalidate();
        repaint();
    }

    // =========================================================================
    // ROYAL FOCUS MODE 2.5D LAYOUT (1:1 REFERENCE STRUCTURE)
    // =========================================================================

    private void buildFocus25DLayout() {
        setLayout(new BorderLayout(0, 0));
        setBorder(new EmptyBorder(6, 14, 6, 14));

        // TOP AREA: JIEXIANGTECH horizontal logo + [Black Card | ChessMaster Royal Emblem | White Card]
        JPanel topBlock = transparent(new BorderLayout(0, 4));

        JPanel corpRow = transparent(new FlowLayout(FlowLayout.CENTER, 0, 0));
        corpRow.add(frame.branding().logo(BrandingManager.COMPANY_HORIZONTAL, 198, 38,
                BrandConstants.COMPANY + " " + BrandConstants.COMPANY_CHINESE));
        topBlock.add(corpRow, BorderLayout.NORTH);

        JPanel playerBar = transparent(new GridBagLayout());
        GridBagConstraints pc = new GridBagConstraints();
        pc.gridy = 0; pc.fill = GridBagConstraints.NONE; pc.anchor = GridBagConstraints.WEST;
        RoyalPlayerCard leftCard = flipped ? whiteCard : blackCard;
        RoyalPlayerCard rightCard = flipped ? blackCard : whiteCard;
        leftCard.setPreferredSize(new Dimension(346, 66));
        rightCard.setPreferredSize(new Dimension(346, 66));

        pc.gridx = 0; pc.weightx = 0.38; pc.insets = new Insets(0, 4, 0, 4);
        playerBar.add(leftCard, pc);

        pc.gridx = 1; pc.weightx = 0.24; pc.anchor = GridBagConstraints.CENTER; pc.insets = new Insets(0, 4, 0, 4);
        playerBar.add(buildCenterWordmark(), pc);

        pc.gridx = 2; pc.weightx = 0.38; pc.anchor = GridBagConstraints.EAST; pc.insets = new Insets(0, 4, 0, 4);
        playerBar.add(rightCard, pc);

        topBlock.add(playerBar, BorderLayout.SOUTH);
        add(topBlock, BorderLayout.NORTH);

        // CENTER STAGE: Dominant 2.5D Perspective Board (+ optional collapsible Move History / Captured drawer)
        JPanel stageWrap = transparent(new BorderLayout(10, 0));
        stageWrap.add(board, BorderLayout.CENTER);

        focusDrawer = new RoyalPanel(new BorderLayout(0, 10), new Color(20, 18, 22, 232),
                new Color(13, 12, 15, 240), LINE_GOLD, 14);
        focusDrawer.setPreferredSize(new Dimension(306, 400));
        focusDrawer.setBorder(new EmptyBorder(10, 10, 10, 10));
        focusDrawer.add(moveHistory, BorderLayout.CENTER);
        focusDrawer.add(captures, BorderLayout.SOUTH);
        focusDrawer.setVisible(focusController.isDrawerOpen());
        stageWrap.add(focusDrawer, BorderLayout.EAST);
        add(stageWrap, BorderLayout.CENTER);

        // BOTTOM HORIZONTAL PILL TOOLBAR (exact reference row + subtle Focus/Drawer/Pause utility pills)
        RoyalButton saveBtn = RoyalBottomToolbar.pillAction(text.get("save"), RoyalButton.Variant.GOLD_GLOW,
                RoyalIcons.Glyph.SAVE, () -> { if (!leaving) persist(true, null); });
        undoBtn = RoyalBottomToolbar.pillAction(text.get("undo"), RoyalButton.Variant.SECONDARY,
                RoyalIcons.Glyph.UNDO, this::undo);
        hintBtn = RoyalBottomToolbar.pillAction(text.get("hint"), RoyalButton.Variant.SECONDARY,
                RoyalIcons.Glyph.HINT, this::hint);
        drawBtn = RoyalBottomToolbar.pillAction(text.get("draw"), RoyalButton.Variant.SECONDARY,
                RoyalIcons.Glyph.DRAW, this::draw);
        resignBtn = RoyalBottomToolbar.pillAction(text.get("resign"), RoyalButton.Variant.DANGER,
                RoyalIcons.Glyph.RESIGN, this::resign);
        RoyalButton flipBtn = RoyalBottomToolbar.pillAction(text.get("flip"), RoyalButton.Variant.SECONDARY,
                RoyalIcons.Glyph.FLIP, this::flipBoard);
        soundBtn = RoyalBottomToolbar.pillAction(text.get(soundEnabled ? "soundOn" : "soundOff"),
                RoyalButton.Variant.SECONDARY, soundEnabled ? RoyalIcons.Glyph.SOUND_ON : RoyalIcons.Glyph.SOUND_OFF, this::sound);
        RoyalButton fullBtn = RoyalBottomToolbar.pillAction(text.get("fullscreen"), RoyalButton.Variant.SECONDARY,
                RoyalIcons.Glyph.FULLSCREEN, frame::toggleFullscreen);

        drawerToggleBtn = new RoyalIconButton(RoyalIcons.Glyph.DOC_LIST, text.get("toggleHistoryDrawer"), 15, 34, () -> {
            boolean open = focusController.toggleDrawer();
            focusDrawer.setVisible(open);
            drawerToggleBtn.setActive(open);
            revalidate();
            repaint();
        });
        drawerToggleBtn.setActive(focusController.isDrawerOpen());

        RoyalIconButton modeSwitchBtn = new RoyalIconButton(RoyalIcons.Glyph.SPARK,
                text.get("toggleFocusMode") + " (" + text.get("viewMode.CLASSIC_2D") + ")", 15, 34, this::toggleViewMode);
        modeSwitchBtn.setActive(true);

        RoyalIconButton pauseIconBtn = new RoyalIconButton(RoyalIcons.Glyph.PAUSE, text.get("pause"), 15, 34, frame::home);

        RoyalBottomToolbar toolbar = new RoyalBottomToolbar(
                saveBtn, undoBtn, hintBtn, drawBtn, resignBtn, flipBtn, soundBtn, fullBtn,
                drawerToggleBtn, modeSwitchBtn, pauseIconBtn);
        toolbar.setFocusDimmed(true);
        add(toolbar, BorderLayout.SOUTH);
    }

    private static JPanel buildCenterWordmark() {
        JPanel p = transparent(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = 0; c.insets = new Insets(0, 0, 2, 0);
        p.add(new JLabel(RoyalIcons.icon(RoyalIcons.Glyph.CROWN, 24, PALE)), c);

        JLabel main = new JLabel("CHESSMASTER");
        main.setForeground(new Color(0xF3D47E));
        main.setFont(serif(Font.BOLD, 21));
        c.gridy = 1; c.insets = new Insets(0, 0, 1, 0);
        p.add(main, c);

        JLabel sub = new JLabel("R O Y A L   E D I T I O N");
        sub.setForeground(new Color(0xC9A750));
        sub.setFont(sans(Font.BOLD, 9.5f));
        c.gridy = 2; c.insets = new Insets(0, 0, 0, 0);
        p.add(sub, c);
        return p;
    }

    // =========================================================================
    // CLASSIC 2D LAYOUT (PRESERVED 3-COLUMN LAYOUT WITH LIVE 2.5D SWITCH)
    // =========================================================================

    private void buildClassic2DLayout() {
        setLayout(new BorderLayout(16, 0));
        setBorder(new EmptyBorder(8, 12, 10, 16));

        JPanel centerStage = transparent(new BorderLayout(0, 4));
        JPanel brandHeader = transparent(new BorderLayout());
        JPanel brandMid = transparent(new FlowLayout(FlowLayout.CENTER, 0, 2));
        brandMid.add(frame.branding().logo(BrandingManager.COMPANY_HORIZONTAL, 224, 44,
                BrandConstants.COMPANY + " " + BrandConstants.COMPANY_CHINESE));
        brandHeader.add(brandMid, BorderLayout.CENTER);
        RoyalButton focusSwitch = RoyalBottomToolbar.pillAction(text.get("viewMode.ROYAL_FOCUS_25D"),
                RoyalButton.Variant.GOLD_GLOW, RoyalIcons.Glyph.SPARK, this::toggleViewMode);
        JPanel swWrap = transparent(new GridBagLayout());
        swWrap.add(focusSwitch);
        brandHeader.add(swWrap, BorderLayout.EAST);
        centerStage.add(brandHeader, BorderLayout.NORTH);

        centerStage.add(board, BorderLayout.CENTER);
        add(centerStage, BorderLayout.CENTER);

        JPanel rightPanel = transparent(new BorderLayout(0, 10));
        rightPanel.setPreferredSize(new Dimension(358, 560));
        rightPanel.setMinimumSize(new Dimension(308, 460));
        rightPanel.setBorder(new EmptyBorder(8, 0, 4, 0));

        whiteCard.setPreferredSize(new Dimension(340, 72));
        blackCard.setPreferredSize(new Dimension(340, 72));
        JPanel playersStack = transparent(new GridLayout(2, 1, 0, 8));
        playersStack.add(flipped ? whiteCard : blackCard);
        playersStack.add(flipped ? blackCard : whiteCard);
        rightPanel.add(playersStack, BorderLayout.NORTH);

        rightPanel.add(moveHistory, BorderLayout.CENTER);

        RoyalButton saveBtn = gameControl(text.get("save"), RoyalButton.Variant.GOLD_GLOW, RoyalIcons.Glyph.SAVE, () -> {
            if (!leaving) persist(true, null);
        });
        undoBtn = gameControl(text.get("undo"), RoyalButton.Variant.SECONDARY, RoyalIcons.Glyph.UNDO, this::undo);
        hintBtn = gameControl(text.get("hint"), RoyalButton.Variant.SECONDARY, RoyalIcons.Glyph.HINT, this::hint);
        drawBtn = gameControl(text.get("draw"), RoyalButton.Variant.SECONDARY, RoyalIcons.Glyph.DRAW, this::draw);
        resignBtn = gameControl(text.get("resign"), RoyalButton.Variant.DANGER, RoyalIcons.Glyph.RESIGN, this::resign);
        RoyalButton flipBtn = gameControl(text.get("flip"), RoyalButton.Variant.SECONDARY, RoyalIcons.Glyph.FLIP, this::flipBoard);
        soundBtn = gameControl(text.get(soundEnabled ? "soundOn" : "soundOff"), RoyalButton.Variant.SECONDARY,
                soundEnabled ? RoyalIcons.Glyph.SOUND_ON : RoyalIcons.Glyph.SOUND_OFF, this::sound);
        RoyalButton fullscreenBtn = gameControl(text.get("fullscreen"), RoyalButton.Variant.SECONDARY,
                RoyalIcons.Glyph.FULLSCREEN, frame::toggleFullscreen);
        RoyalButton pauseBtn = gameControl(text.get("pause"), RoyalButton.Variant.GHOST, RoyalIcons.Glyph.PAUSE, frame::home);
        pauseBtn.setPreferredSize(new Dimension(120, 32));

        JPanel row1 = transparent(new GridLayout(1, 3, 7, 0));
        row1.add(saveBtn); row1.add(undoBtn); row1.add(hintBtn);

        JPanel row2 = transparent(new GridBagLayout());
        GridBagConstraints rc = new GridBagConstraints();
        rc.gridy = 0; rc.fill = GridBagConstraints.BOTH; rc.weighty = 1;
        rc.gridx = 0; rc.weightx = 0.36; rc.insets = new Insets(0, 0, 0, 7);
        row2.add(drawBtn, rc);
        rc.gridx = 1; rc.weightx = 0.64; rc.insets = new Insets(0, 0, 0, 0);
        row2.add(resignBtn, rc);

        JPanel row3 = transparent(new GridLayout(1, 3, 7, 0));
        row3.add(flipBtn); row3.add(soundBtn); row3.add(fullscreenBtn);

        JPanel row4 = transparent(new BorderLayout(8, 0));
        saveStatus.setBorder(new EmptyBorder(0, 4, 0, 4));
        row4.add(saveStatus, BorderLayout.WEST);
        row4.add(pauseBtn, BorderLayout.CENTER);

        JPanel controls = transparent(new GridLayout(0, 1, 0, 7));
        controls.add(row1); controls.add(row2); controls.add(row3); controls.add(row4);

        JPanel southStack = transparent(new BorderLayout(0, 10));
        southStack.add(captures, BorderLayout.NORTH);
        southStack.add(controls, BorderLayout.SOUTH);
        rightPanel.add(southStack, BorderLayout.SOUTH);

        add(rightPanel, BorderLayout.EAST);
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

    private void flipBoard() {
        flipped = !flipped;
        board.flip();
        rebuildLayout();
        refresh(false);
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

        if (undoBtn != null) undoBtn.setEnabled(session.canUndo());
        if (hintBtn != null) hintBtn.setEnabled(canPlay());
        if (resignBtn != null) resignBtn.setEnabled(game.outcome() == null);
        if (drawBtn != null) {
            drawBtn.setEnabled(canPlay() && (canClaim() || session.options().mode() == MatchOptions.Mode.LOCAL && game.plyCount() >= 2));
        }
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
        if (soundBtn != null) {
            soundBtn.setText(text.get(soundEnabled ? "soundOn" : "soundOff"));
            soundBtn.setGlyph(soundEnabled ? RoyalIcons.Glyph.SOUND_ON : RoyalIcons.Glyph.SOUND_OFF);
        }
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
