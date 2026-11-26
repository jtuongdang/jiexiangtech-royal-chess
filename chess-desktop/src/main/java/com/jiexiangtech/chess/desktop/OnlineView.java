package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.net.http.WebSocket;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import com.jiexiangtech.chess.domain.*;
import com.jiexiangtech.chess.online.OnlineProtocol;
import com.jiexiangtech.chess.online.OnlineProtocol.*;
import com.jiexiangtech.chess.online.OnlineProtocol.Action;
import static com.jiexiangtech.chess.desktop.RoyalStyle.*;
import static com.jiexiangtech.chess.desktop.ViewParts.*;

final class OnlineView extends JPanel {
    private static final String TAB_CREATE = "create";
    private static final String TAB_JOIN = "join";

    private final RoyalFrame frame;
    private final I18n text;
    private final OnlineClient client = new OnlineClient();
    private final OnlineSeatFile seatFile;
    private final FocusModeController focus;
    private final Timer timer = new Timer(200, event -> tick());
    private final Timer coldStartTimer;
    private final JLabel notice = label(" ", MUTED);
    private final JLabel gameStatus = label(" ", PALE);
    private final JLabel whitePlayer = label(" ", MUTED);
    private final JLabel blackPlayer = label(" ", MUTED);
    private final JLabel whiteClock = title("—", 24);
    private final JLabel blackClock = title("—", 24);
    private OnlineClient.Seat seat;
    private OnlineClient.Seat lastSeat;
    private OnlineClient.Seat attempt;
    private boolean creatingAttempt;
    private Snapshot snapshot;
    private Command pending;
    private ChessBoardPanel board;
    private JTextArea moves;
    private JPanel waitingBanner;
    private JButton resign, draw, claim, backToLobby, resumeCreate, resumeJoin;
    private WebSocket socket;
    private long lastSyncNano, nextPollNano, nextSocketNano;
    private boolean closed, loading, reading, sending, connecting, connected;
    private boolean resyncNeeded, outcomeAnnounced;

    OnlineView(RoyalFrame frame, long localProfile) {
        super(new BorderLayout(12, 10));
        this.frame = frame;
        text = frame.text();
        seatFile = new OnlineSeatFile(localProfile);
        focus = new FocusModeController(frame.prefs().viewMode());
        coldStartTimer = new Timer(5000, event -> {
            if (loading && !closed) showNotice("onlineColdStart", PALE);
        });
        coldStartTimer.setRepeats(false);
        setOpaque(false);
        setBorder(new EmptyBorder(16, 18, 16, 18));
        lobby();
        background(CompletableFuture.supplyAsync(() -> {
            try { return seatFile.read(); } catch (Exception ignored) { return null; }
        }), (saved, failure) -> {
            lastSeat = saved;
            updateResumeButtons();
        });
    }

    void close() {
        closed = true;
        timer.stop();
        coldStartTimer.stop();
        if (socket != null) socket.abort();
    }

    private void returnToLobby() {
        timer.stop();
        coldStartTimer.stop();
        if (socket != null) {
            socket.abort();
            socket = null;
        }
        seat = null;
        snapshot = null;
        pending = null;
        connected = false;
        connecting = false;
        reading = false;
        sending = false;
        resyncNeeded = false;
        outcomeAnnounced = false;
        waitingBanner = null;
        backToLobby = null;
        lobby();
    }

    private void updateResumeButtons() {
        boolean enabled = lastSeat != null && !loading;
        if (resumeCreate != null) resumeCreate.setEnabled(enabled);
        if (resumeJoin != null) resumeJoin.setEnabled(enabled);
    }

    private void lobby() {
        removeAll();
        notice.setText(" ");
        add(heading(text.get("online"), null), BorderLayout.NORTH);

        String defaultServer = System.getProperty("royal.serverUrl", System.getenv("CHESS_SERVER_URL"));
        JTextField server = field(false);
        server.setColumns(30);
        server.setText(defaultServer == null ? BrandConstants.ONLINE_SERVER : defaultServer);

        JTextField createName = field(false);
        createName.setText(frame.displayLabel());
        JTextField joinName = field(false);
        joinName.setText(frame.displayLabel());

        JPasswordField key = (JPasswordField) field(true);
        key.putClientProperty("JTextField.placeholderText", text.get("onlineOrganizerPlaceholder"));

        JTextField code = field(false);
        code.putClientProperty("JTextField.placeholderText", text.get("onlineCodePlaceholder"));

        var control = combo(Control.values(), option -> switch (option) {
            case RAPID_5_0 -> "5 + 0";
            case RAPID_10_5 -> "10 + 5";
            case RAPID_15_10 -> "15 + 10";
        });
        control.setSelectedItem(Control.RAPID_10_5);

        CardLayout tabsLayout = new CardLayout();
        JPanel tabCards = transparent(tabsLayout);

        // --- Create Room Tab ---
        JPanel createFields = transparent(new GridLayout(0, 2, 12, 10));
        addField(createFields, "displayName", createName);
        addField(createFields, "timeControl", control);
        addField(createFields, "onlineOrganizer", key);

        JButton createBtn = button(text.get("onlineCreate"), true, () -> {
            if (loading) return;
            try {
                String address = OnlineClient.normalizeServer(server.getText());
                var next = attempt != null && creatingAttempt && attempt.server().equals(address)
                        ? attempt : new OnlineClient.Seat(address, OnlineProtocol.roomId(), OnlineProtocol.seatToken());
                attempt = next;
                creatingAttempt = true;
                char[] input = key.getPassword();
                String organizerKey = new String(input);
                java.util.Arrays.fill(input, '\0');
                open(next, client.create(next, createName.getText(), (Control) control.getSelectedItem(), organizerKey),
                        () -> key.setText(""));
            } catch (IllegalArgumentException invalid) { showNotice("onlineInvalidServer", ERROR_TEXT); }
        });
        resumeCreate = button(text.get("resume"), false, () -> {
            if (!loading && lastSeat != null) open(lastSeat, client.sync(lastSeat), null);
        });
        JPanel createPanel = stack(createFields, Box.createVerticalStrut(14), row(createBtn, resumeCreate));

        // --- Join Room Tab ---
        JPanel joinFields = transparent(new GridLayout(0, 2, 12, 10));
        addField(joinFields, "displayName", joinName);
        addField(joinFields, "onlineCode", code);

        JButton joinBtn = button(text.get("onlineJoin"), true, () -> {
            if (loading) return;
            try {
                String room = code.getText().strip();
                if (!room.matches("[A-Za-z0-9_-]{12}")) {
                    showNotice("onlineInvalidCode", ERROR_TEXT);
                    return;
                }
                String address = OnlineClient.normalizeServer(server.getText());
                var next = attempt != null && !creatingAttempt && attempt.server().equals(address) && attempt.room().equals(room)
                        ? attempt : new OnlineClient.Seat(address, room, OnlineProtocol.seatToken());
                attempt = next;
                creatingAttempt = false;
                open(next, client.join(next, joinName.getText()), null);
            } catch (IllegalArgumentException invalid) { showNotice("onlineInvalidServer", ERROR_TEXT); }
        });
        resumeJoin = button(text.get("resume"), false, () -> {
            if (!loading && lastSeat != null) open(lastSeat, client.sync(lastSeat), null);
        });
        JPanel joinPanel = stack(joinFields, Box.createVerticalStrut(14), row(joinBtn, resumeJoin));

        tabCards.add(createPanel, TAB_CREATE);
        tabCards.add(joinPanel, TAB_JOIN);
        updateResumeButtons();

        // --- Tab Switcher ---
        RoyalButton[] tabButtons = new RoyalButton[2];
        tabButtons[0] = button(text.get("onlineTabCreate"), RoyalButton.Variant.PRIMARY, null, () -> {
            createName.setText(joinName.getText());
            tabsLayout.show(tabCards, TAB_CREATE);
            tabButtons[0].setVariant(RoyalButton.Variant.PRIMARY);
            tabButtons[1].setVariant(RoyalButton.Variant.SECONDARY);
            notice.setText(" ");
        });
        tabButtons[1] = button(text.get("onlineTabJoin"), RoyalButton.Variant.SECONDARY, null, () -> {
            joinName.setText(createName.getText());
            tabsLayout.show(tabCards, TAB_JOIN);
            tabButtons[0].setVariant(RoyalButton.Variant.SECONDARY);
            tabButtons[1].setVariant(RoyalButton.Variant.PRIMARY);
            notice.setText(" ");
        });
        JPanel tabBar = transparent(new GridLayout(1, 2, 8, 0));
        tabBar.add(tabButtons[0]);
        tabBar.add(tabButtons[1]);

        // --- Collapsible Server Settings ---
        JPanel serverRow = stack(label(text.get("onlineServer"), MUTED), Box.createVerticalStrut(4), server);
        serverRow.setVisible(false);
        RoyalButton serverToggle = button(text.get("onlineServerSettings") + " ▸", RoyalButton.Variant.GHOST, null, () -> {});
        serverToggle.addActionListener(event -> {
            boolean show = !serverRow.isVisible();
            serverRow.setVisible(show);
            serverToggle.setText(text.get("onlineServerSettings") + (show ? " ▾" : " ▸"));
            revalidate();
            repaint();
        });

        JPanel formCard = card(new BorderLayout(0, 14));
        formCard.setPreferredSize(new Dimension(520, formCard.getPreferredSize().height));
        formCard.add(stack(tabBar, Box.createVerticalStrut(14), divider(), Box.createVerticalStrut(14),
                tabCards, Box.createVerticalStrut(10), divider(), Box.createVerticalStrut(6),
                serverToggle, serverRow), BorderLayout.CENTER);

        JPanel group = stack(formCard, Box.createVerticalStrut(10), notice);
        JPanel center = transparent(new GridBagLayout());
        center.add(group);
        add(center, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private void addField(JPanel fields, String key, JComponent component) {
        JLabel label = label(text.get(key), MUTED);
        label.setLabelFor(component);
        fields.add(label);
        fields.add(component);
    }

    private void open(OnlineClient.Seat next, CompletableFuture<Snapshot> response, Runnable onSuccess) {
        loading = true;
        updateResumeButtons();
        showNotice("onlineConnecting", PALE);
        coldStartTimer.restart();
        background(response, (value, failure) -> {
            loading = false;
            coldStartTimer.stop();
            updateResumeButtons();
            if (failure != null) {
                var problem = asProblem(failure);
                if (problem != null && problem.status >= 400 && problem.status < 500) attempt = null;
                showError(failure);
                return;
            }
            if (onSuccess != null) onSuccess.run();
            attempt = null;
            seat = lastSeat = next;
            background(CompletableFuture.runAsync(() -> {
                try { seatFile.save(next); } catch (Exception e) { throw new CompletionException(e); }
            }), (unused, saveFailure) -> {
                if (saveFailure != null) showNotice("onlineSeatNotSaved", ERROR_TEXT);
            });
            match(value);
        });
    }

    private void match(Snapshot value) {
        removeAll();
        notice.setText(" ");
        outcomeAnnounced = value.outcome() != null;

        JPanel mast = transparent(new BorderLayout(10, 0));
        var copy = button(text.get("onlineCopy"), false, this::copyInviteCode);
        mast.add(heading(text.get("online"), row(label(seat.room(), PALE), copy)), BorderLayout.CENTER);
        add(mast, BorderLayout.NORTH);

        board = new ChessBoardPanel(frame.assets(), frame.prefs(), frame.prefs().theme(), this::canMove,
                options -> promote(this, frame.assets(), text, snapshot.seat()),
                move -> submit(Action.MOVE, move));
        board.setFocusMode(focus.isFocusMode());
        board.setFlipped(flip(frame.prefs(), value.seat()));
        add(board, BorderLayout.CENTER);

        // Prominent Waiting Banner (visible only while waiting for Black to join)
        JLabel codeDisplay = new JLabel(seat.room());
        codeDisplay.setFont(mono(Font.BOLD, 20f));
        codeDisplay.setForeground(PALE);
        JButton bannerCopy = button(text.get("onlineCopy"), true, this::copyInviteCode);
        JLabel waitHint = caption(text.get("onlineWaitingBanner"));
        waitingBanner = card(new BorderLayout(0, 6));
        waitingBanner.add(stack(eyebrow(text.get("onlineWaiting")), Box.createVerticalStrut(4),
                row(codeDisplay, bannerCopy), Box.createVerticalStrut(2), waitHint), BorderLayout.CENTER);

        whitePlayer.setFont(sans(Font.BOLD, 12.5f));
        blackPlayer.setFont(sans(Font.BOLD, 12.5f));
        JPanel clocks = stack(
                waitingBanner,
                Box.createVerticalStrut(6),
                whitePlayer, whiteClock,
                Box.createVerticalStrut(8),
                blackPlayer, blackClock,
                Box.createVerticalStrut(10),
                gameStatus);

        JPanel rail = card(new BorderLayout(0, 10));
        rail.setPreferredSize(new Dimension(270, 440));
        rail.add(clocks, BorderLayout.NORTH);
        moves = new JTextArea();
        moves.setEditable(false);
        moves.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 15));
        moves.setBackground(BG);
        moves.setForeground(IVORY);
        rail.add(scroll(moves), BorderLayout.CENTER);
        resign = button(text.get("resign"), false, () -> {
            if (frame.confirm("confirmResign")) submit(Action.RESIGN, null);
        });
        draw = button(text.get("offer"), false, () -> submit(snapshot.drawOfferedBy() == snapshot.seat().opposite()
                ? Action.ACCEPT_DRAW : Action.OFFER_DRAW, null));
        claim = button(text.get("claim"), false, () -> submit(Action.CLAIM_DRAW, null));
        var modeButton = button(text.get(focus.isFocusMode() ? "onlineClassic" : "onlineFocus"), false, () -> {});
        modeButton.addActionListener(event -> {
            var mode = focus.toggleMode();
            board.setFocusMode(focus.isFocusMode());
            frame.updateViewMode(mode);
            modeButton.setText(text.get(focus.isFocusMode() ? "onlineClassic" : "onlineFocus"));
        });
        backToLobby = button(text.get("onlineBackToLobby"), true, this::returnToLobby);
        backToLobby.setVisible(value.outcome() != null);

        JPanel controls = transparent(new GridLayout(0, 2, 5, 5));
        controls.add(button(text.get("flip"), false, board::flip));
        controls.add(modeButton);
        controls.add(draw);
        controls.add(claim);
        controls.add(resign);
        controls.add(button(text.get("onlineSync"), false, this::refresh));
        rail.add(stack(backToLobby, Box.createVerticalStrut(6), controls, Box.createVerticalStrut(8), notice),
                BorderLayout.SOUTH);
        add(rail, BorderLayout.EAST);

        accept(value);
        connect();
        timer.start();
        revalidate();
        repaint();
    }

    private void copyInviteCode() {
        if (seat == null) return;
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(seat.room()), null);
        showNotice("onlineCopied", SUCCESS);
    }

    private void accept(Snapshot value) {
        if (snapshot != null && (value.version() < snapshot.version()
                || (value.version() == snapshot.version() && value.serverTime() < snapshot.serverTime()))) {
            updateStatus();
            return;
        }
        Snapshot old = snapshot;
        snapshot = value;
        connected = true;
        lastSyncNano = System.nanoTime();
        nextPollNano = lastSyncNano + DurationNanos.seconds(socket == null ? 3 : 12);
        if (old == null || !old.fen().equals(value.fen()) || !old.moves().equals(value.moves())) {
            List<Ply> history = value.moves();
            Move last = history.isEmpty() ? null : Move.uci(history.get(history.size() - 1).uci());
            boolean singleMoveAdvance = old != null && history.size() == old.moves().size() + 1;
            board.update(Position.fromFen(value.fen()), last, singleMoveAdvance);
            if (singleMoveAdvance && value.outcome() == null && frame.prefs().sound()) {
                String san = history.get(history.size() - 1).san();
                String cue = san.contains("+") || san.contains("#") ? "check"
                        : san.contains("x") ? "capture" : "move";
                frame.sounds().play(cue);
            }
            StringBuilder notation = new StringBuilder();
            for (int i = 0; i < history.size(); i++) {
                if (i % 2 == 0) notation.append(i / 2 + 1).append(". ");
                notation.append(history.get(i).san()).append(i % 2 == 0 ? "  " : "\n");
            }
            moves.setText(notation.toString());
            moves.setCaretPosition(moves.getDocument().getLength());
        }
        updateStatus();
        if (value.outcome() != null && !outcomeAnnounced) {
            outcomeAnnounced = true;
            if (frame.prefs().sound()) frame.sounds().play("result");
            SwingUtilities.invokeLater(() -> announceOutcome(value));
        }
    }

    private void announceOutcome(Snapshot value) {
        if (closed || value.outcome() == null) return;
        Outcome outcome = value.outcome();
        boolean isDraw = outcome.winner() == null;
        boolean isVictory = !isDraw && outcome.winner() == value.seat();
        Color accent = isDraw ? new Color(0xD8DCE4) : isVictory ? PALE : DANGER_TEXT;
        String resultLabel = isDraw ? text.get("draw") : text.get(isVictory ? "victory" : "defeat");
        String reasonLabel = text.get("reason." + outcome.reason());
        String playersLine = text.get("onlinePlayersSummary",
                value.white() == null ? text.get("side.WHITE") : value.white(),
                value.black() == null ? text.get("side.BLACK") : value.black());
        String movesLine = text.get("moveCount", value.moves().size());
        boolean goLobby = RoyalDialog.onlineOutcome(this, text.get("onlineMatchOver"),
                resultLabel, reasonLabel, playersLine, movesLine, accent,
                text.get("onlineReviewBoard"), text.get("onlineBackToLobby"));
        if (goLobby && !closed) returnToLobby();
    }

    private void updateStatus() {
        boolean active = snapshot.playing() && connected && pending == null;
        resign.setEnabled(active);
        draw.setEnabled(active && snapshot.moves().size() >= 2 && snapshot.drawOfferedBy() != snapshot.seat());
        draw.setText(text.get(snapshot.drawOfferedBy() == snapshot.seat().opposite() ? "onlineAcceptDraw" : "offer"));
        claim.setEnabled(canMove());
        if (waitingBanner != null) waitingBanner.setVisible(snapshot.black() == null && snapshot.outcome() == null);
        if (backToLobby != null) backToLobby.setVisible(snapshot.outcome() != null);
        if (snapshot.outcome() != null) {
            boolean isDraw = snapshot.outcome().winner() == null;
            boolean isVictory = !isDraw && snapshot.outcome().winner() == snapshot.seat();
            String result = isDraw ? text.get("draw") : text.get(isVictory ? "victory" : "defeat");
            gameStatus.setText(result + " · " + text.get("reason." + snapshot.outcome().reason()));
            gameStatus.setForeground(isDraw ? IVORY : isVictory ? PALE : DANGER_TEXT);
        } else if (snapshot.black() == null) {
            gameStatus.setText(text.get("onlineWaiting"));
            gameStatus.setForeground(PALE);
        } else {
            gameStatus.setText(text.get(snapshot.turn() == snapshot.seat() ? "onlineYourTurn" : "onlineTheirTurn"));
            gameStatus.setForeground(snapshot.turn() == snapshot.seat() ? SUCCESS : MUTED);
        }
        paintClocks();
    }

    private boolean canMove() {
        return !closed && connected && pending == null && snapshot != null && snapshot.playing()
                && snapshot.turn() == snapshot.seat() && remaining(snapshot.seat()) > 0;
    }

    private long remaining(Side side) {
        long base = side == Side.WHITE ? snapshot.whiteMillis() : snapshot.blackMillis();
        long elapsed = (System.nanoTime() - lastSyncNano) / 1_000_000L;
        return snapshot.playing() && snapshot.turn() == side ? Math.max(0, base - elapsed) : base;
    }

    private void paintClocks() {
        String wName = snapshot.white() == null ? "…" : snapshot.white();
        String bName = snapshot.black() == null ? text.get("onlineWaiting") : snapshot.black();
        whitePlayer.setText(text.get("side.WHITE") + " · " + wName);
        blackPlayer.setText(text.get("side.BLACK") + " · " + bName);
        whitePlayer.setForeground(snapshot.turn() == Side.WHITE && snapshot.playing() ? PALE : MUTED);
        blackPlayer.setForeground(snapshot.turn() == Side.BLACK && snapshot.playing() ? PALE : MUTED);

        long wMs = remaining(Side.WHITE);
        long bMs = remaining(Side.BLACK);
        whiteClock.setText(clock(wMs));
        blackClock.setText(clock(bMs));
        whiteClock.setForeground(snapshot.playing() && snapshot.turn() == Side.WHITE
                ? (wMs < 30_000L ? WARN_CLOCK : PALE) : MUTED);
        blackClock.setForeground(snapshot.playing() && snapshot.turn() == Side.BLACK
                ? (bMs < 30_000L ? WARN_CLOCK : PALE) : MUTED);
        whiteClock.setToolTipText(wName);
        blackClock.setToolTipText(bName);
    }

    private void submit(Action action, Move move) {
        if (pending != null || !connected || !snapshot.playing() || (action == Action.MOVE && !canMove())) return;
        pending = new Command(UUID.randomUUID().toString(), snapshot.version(), action, move == null ? null : move.uci());
        sendPending();
    }

    private void sendPending() {
        if (pending == null || sending) return;
        sending = true;
        updateStatus();
        background(client.command(seat, pending), (state, failure) -> {
            sending = false;
            if (failure == null) {
                pending = null;
                notice.setText(" ");
                accept(state);
                board.clearPendingDrop();
            } else {
                var problem = asProblem(failure);
                if (problem != null && problem.status >= 400 && problem.status < 500) {
                    pending = null;
                    board.clearPendingDrop();
                    showError(failure);
                    refresh();
                } else {
                    connected = false;
                    nextPollNano = System.nanoTime() + DurationNanos.seconds(3);
                    showError(failure);
                }
                updateStatus();
            }
        });
    }

    private void refresh() {
        if (closed || snapshot == null) return;
        if (reading) { resyncNeeded = true; return; }
        if (pending != null && !sending) { sendPending(); return; }
        reading = true;
        background(client.sync(seat), (state, failure) -> {
            reading = false;
            if (failure == null) {
                boolean recovered = !connected;
                accept(state);
                if (recovered) notice.setText(" ");
            } else {
                connected = false;
                nextPollNano = System.nanoTime() + DurationNanos.seconds(4);
                showError(failure);
                updateStatus();
            }
            if (resyncNeeded) {
                resyncNeeded = false;
                if (failure == null) refresh();
            }
        });
    }

    private void connect() {
        if (closed || connecting || socket != null || snapshot.outcome() != null) return;
        connecting = true;
        client.events(seat, () -> SwingUtilities.invokeLater(this::refresh),
                ended -> SwingUtilities.invokeLater(() -> {
                    if (socket != ended) return;
                    socket = null;
                    nextSocketNano = System.nanoTime() + DurationNanos.seconds(5);
                    refresh();
                })).whenComplete((ws, failure) -> SwingUtilities.invokeLater(() -> {
            connecting = false;
            if (closed) { if (ws != null) ws.abort(); return; }
            if (failure == null && !ws.isInputClosed()) {
                socket = ws;
                refresh();
            } else nextSocketNano = System.nanoTime() + DurationNanos.seconds(6);
        }));
    }

    private void tick() {
        paintClocks();
        if (snapshot.outcome() != null && pending == null) {
            timer.stop();
            if (socket != null) { socket.abort(); socket = null; }
            return;
        }
        long now = System.nanoTime();
        if (socket == null && now >= nextSocketNano) connect();
        boolean clockReachedZero = snapshot.playing() && remaining(snapshot.turn()) == 0
                && now - lastSyncNano > DurationNanos.seconds(1);
        if (!reading && !sending && (now >= nextPollNano || (connected && clockReachedZero))) refresh();
    }

    private void showError(Throwable failure) {
        var problem = asProblem(failure);
        String code = problem == null ? "" : problem.code;
        String key = switch (code) {
            case "invalid_request", "organizer_key", "room_missing", "room_full", "seat_denied", "game_ended",
                    "stale", "illegal_move", "wrong_turn", "draw_unavailable", "incompatible" -> "onlineError." + code;
            default -> "onlineUnavailable";
        };
        showNotice(key, ERROR_TEXT);
    }

    private static OnlineClient.Failure asProblem(Throwable failure) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        return cause instanceof OnlineClient.Failure problem ? problem : null;
    }

    private void showNotice(String key, Color color) {
        notice.setText("<html>" + text.get(key) + "</html>");
        notice.setForeground(color);
    }

    private <T> void background(CompletableFuture<T> future, java.util.function.BiConsumer<T, Throwable> callback) {
        future.whenComplete((value, failure) -> SwingUtilities.invokeLater(() -> {
            if (!closed) callback.accept(value, failure);
        }));
    }

    private static final class DurationNanos {
        private DurationNanos() {}
        static long seconds(int seconds) { return seconds * 1_000_000_000L; }
    }
}
