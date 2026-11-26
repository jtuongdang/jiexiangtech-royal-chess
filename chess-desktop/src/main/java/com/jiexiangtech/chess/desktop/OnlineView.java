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
    private final RoyalFrame frame;
    private final I18n text;
    private final OnlineClient client = new OnlineClient();
    private final OnlineSeatFile seatFile;
    private final FocusModeController focus;
    private final Timer timer = new Timer(200, event -> tick());
    private final JLabel notice = label(" ", MUTED);
    private final JLabel gameStatus = label(" ", PALE);
    private final JLabel whiteClock = title("—", 26);
    private final JLabel blackClock = title("—", 26);
    private OnlineClient.Seat seat;
    private OnlineClient.Seat lastSeat;
    private OnlineClient.Seat attempt;
    private boolean creatingAttempt;
    private Snapshot snapshot;
    private Command pending;
    private ChessBoardPanel board;
    private JTextArea moves;
    private JButton resign, draw, claim, resume;
    private WebSocket socket;
    private long lastSyncNano, nextPollNano, nextSocketNano;
    private boolean closed, loading, reading, sending, connecting, connected;
    private boolean resyncNeeded;

    OnlineView(RoyalFrame frame, long localProfile) {
        super(new BorderLayout(12, 10));
        this.frame = frame;
        text = frame.text();
        seatFile = new OnlineSeatFile(localProfile);
        focus = new FocusModeController(frame.prefs().viewMode());
        setOpaque(false);
        setBorder(new EmptyBorder(16, 18, 16, 18));
        lobby();
        background(CompletableFuture.supplyAsync(() -> {
            try { return seatFile.read(); } catch (Exception ignored) { return null; }
        }), (saved, failure) -> {
            lastSeat = saved;
            if (resume != null) resume.setEnabled(saved != null && !loading);
        });
    }

    void close() {
        closed = true;
        timer.stop();
        if (socket != null) socket.abort();
    }

    private void lobby() {
        removeAll();
        add(heading(text.get("online"), null), BorderLayout.NORTH);
        var fields = card(new GridLayout(0, 2, 12, 10));
        var server = field(false);
        var name = field(false);
        var code = field(false);
        var key = (JPasswordField) field(true);
        var control = combo(Control.values(), option -> switch (option) {
            case RAPID_5_0 -> "5 + 0";
            case RAPID_10_5 -> "10 + 5";
            case RAPID_15_10 -> "15 + 10";
        });
        control.setSelectedItem(Control.RAPID_10_5);
        String defaultServer = System.getProperty("royal.serverUrl", System.getenv("CHESS_SERVER_URL"));
        server.setText(defaultServer == null ? "http://localhost:8080" : defaultServer);
        name.setText(frame.displayLabel());
        addField(fields, "onlineServer", server);
        addField(fields, "displayName", name);
        addField(fields, "timeControl", control);
        addField(fields, "onlineOrganizer", key);
        addField(fields, "onlineCode", code);

        JButton create = button(text.get("onlineCreate"), true, () -> {
            if (loading) return;
            try {
                String address = OnlineClient.normalizeServer(server.getText());
                var next = attempt != null && creatingAttempt && attempt.server().equals(address)
                        ? attempt : new OnlineClient.Seat(address, OnlineProtocol.roomId(), OnlineProtocol.seatToken());
                attempt = next;
                creatingAttempt = true;
                char[] input = key.getPassword();
                try {
                    open(next, client.create(next, name.getText(), (Control) control.getSelectedItem(), new String(input)));
                } finally {
                    java.util.Arrays.fill(input, '\0');
                    key.setText("");
                }
            } catch (IllegalArgumentException invalid) { showNotice("onlineInvalidServer", ERROR_TEXT); }
        });
        JButton join = button(text.get("onlineJoin"), false, () -> {
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
                open(next, client.join(next, name.getText()));
            } catch (IllegalArgumentException invalid) { showNotice("onlineInvalidServer", ERROR_TEXT); }
        });
        resume = button(text.get("resume"), false, () -> {
            if (!loading && lastSeat != null) open(lastSeat, client.sync(lastSeat));
        });
        resume.setEnabled(lastSeat != null);
        JPanel group = stack(fields, Box.createVerticalStrut(12), row(create, join, resume),
                Box.createVerticalStrut(5), notice);
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

    private void open(OnlineClient.Seat next, CompletableFuture<Snapshot> response) {
        loading = true;
        showNotice("onlineConnecting", PALE);
        background(response, (value, failure) -> {
            loading = false;
            if (failure != null) {
                var problem = asProblem(failure);
                if (problem != null && problem.status >= 400 && problem.status < 500) attempt = null;
                showError(failure);
                return;
            }
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
        JPanel mast = transparent(new BorderLayout(10, 0));
        var copy = button(text.get("onlineCopy"), false, () -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(seat.room()), null);
            showNotice("onlineCopied", SUCCESS);
        });
        mast.add(heading(text.get("online"), row(label(seat.room(), PALE), copy)), BorderLayout.CENTER);
        add(mast, BorderLayout.NORTH);

        board = new ChessBoardPanel(frame.assets(), frame.prefs(), frame.prefs().theme(), this::canMove,
                options -> promote(this, frame.assets(), text, snapshot.seat()),
                move -> submit(Action.MOVE, move));
        board.setFocusMode(focus.isFocusMode());
        board.setFlipped(flip(frame.prefs(), value.seat()));
        add(board, BorderLayout.CENTER);

        JPanel rail = card(new BorderLayout(0, 10));
        rail.setPreferredSize(new Dimension(250, 440));
        rail.add(stack(whiteClock, Box.createVerticalStrut(8), blackClock, Box.createVerticalStrut(12), gameStatus),
                BorderLayout.NORTH);
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
        JPanel controls = transparent(new GridLayout(0, 2, 5, 5));
        controls.add(button(text.get("flip"), false, board::flip));
        controls.add(modeButton);
        controls.add(draw);
        controls.add(claim);
        controls.add(resign);
        controls.add(button(text.get("onlineSync"), false, this::refresh));
        rail.add(stack(controls, Box.createVerticalStrut(8), notice), BorderLayout.SOUTH);
        add(rail, BorderLayout.EAST);

        accept(value);
        connect();
        timer.start();
        revalidate();
        repaint();
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
            board.update(Position.fromFen(value.fen()), last, old != null && history.size() == old.moves().size() + 1);
            StringBuilder notation = new StringBuilder();
            for (int i = 0; i < history.size(); i++) {
                if (i % 2 == 0) notation.append(i / 2 + 1).append(". ");
                notation.append(history.get(i).san()).append(i % 2 == 0 ? "  " : "\n");
            }
            moves.setText(notation.toString());
            moves.setCaretPosition(moves.getDocument().getLength());
        }
        updateStatus();
    }

    private void updateStatus() {
        boolean active = snapshot.playing() && connected && pending == null;
        resign.setEnabled(active);
        draw.setEnabled(active && snapshot.moves().size() >= 2 && snapshot.drawOfferedBy() != snapshot.seat());
        draw.setText(text.get(snapshot.drawOfferedBy() == snapshot.seat().opposite() ? "onlineAcceptDraw" : "offer"));
        claim.setEnabled(canMove());
        if (snapshot.outcome() != null) {
            String result = snapshot.outcome().winner() == null ? text.get("draw")
                    : text.get(snapshot.outcome().winner() == snapshot.seat() ? "victory" : "defeat");
            gameStatus.setText(result + " · " + text.get("reason." + snapshot.outcome().reason()));
        } else if (snapshot.black() == null) gameStatus.setText(text.get("onlineWaiting"));
        else gameStatus.setText(text.get(snapshot.turn() == snapshot.seat() ? "onlineYourTurn" : "onlineTheirTurn"));
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
        whiteClock.setText(text.get("side.WHITE") + "  " + clock(remaining(Side.WHITE)));
        blackClock.setText(text.get("side.BLACK") + "  " + clock(remaining(Side.BLACK)));
        whiteClock.setForeground(snapshot.turn() == Side.WHITE ? PALE : MUTED);
        blackClock.setForeground(snapshot.turn() == Side.BLACK ? PALE : MUTED);
        whiteClock.setToolTipText(snapshot.white());
        blackClock.setToolTipText(snapshot.black());
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
            } else {
                var problem = asProblem(failure);
                if (problem != null && problem.status >= 400 && problem.status < 500) {
                    pending = null;
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
