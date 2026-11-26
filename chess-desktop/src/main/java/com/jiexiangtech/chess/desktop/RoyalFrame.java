package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import org.slf4j.LoggerFactory;
import com.jiexiangtech.chess.infrastructure.ChessEngines;
import com.jiexiangtech.chess.application.*;
import com.jiexiangtech.chess.domain.*;
import static com.jiexiangtech.chess.desktop.RoyalStyle.*;
import static com.jiexiangtech.chess.desktop.ViewParts.*;

final class RoyalFrame extends JFrame {
    record Receipt(int before, int after) {}
    private record HomeData(ChessStore.Profile profile, List<ChessStore.Summary> history) {}
    private record LoginData(ChessStore.User user, Preferences preferences) {}
    private record ImportedFile(String raw, Pgn.Imported pgn) {}
    private record LoadedGame(MatchSnapshot snapshot, GameSession session) {}
    private enum Page { HOME, NEW, PUZZLES, HISTORY, LEADERBOARD, STATISTICS, ACHIEVEMENTS, SETTINGS }

    private final ChessStore store;
    private final Accounts accounts;
    private final AssetManager assets;
    private final BrandingManager branding;
    private final ChessEngines engines;
    private final SoundManager sounds = new SoundManager();
    private final UiJobs jobs = new UiJobs();
    private I18n text;
    private Preferences prefs;
    private ChessStore.User user;
    private ChessStore.Profile profile;
    private MatchView match;
    private ReplayView replay;
    private PuzzleView puzzle;
    private JLabel footer;
    private long generation;
    private String remembered;
    private String authLanguage;
    private Rectangle windowedBounds;
    private boolean closing;

    RoyalFrame(ChessStore store, Passwords passwords, AssetManager assets, ChessEngines engines,
               String remembered, Preferences initialPreferences) {
        super(BrandConstants.PRODUCT);
        this.store = store;
        this.accounts = new Accounts(store, passwords);
        this.assets = assets;
        this.branding = new BrandingManager(assets);
        this.engines = engines;
        branding.apply(this);
        this.remembered = remembered;
        authLanguage = initialPreferences.language();
        prefs = initialPreferences;
        text = new I18n(authLanguage);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1020, 690));
        Rectangle available = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(1330, available.width), Math.min(865, available.height));
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { navigate(RoyalFrame.this::shutdown); }
        });
        auth(false);
    }

    I18n text() { return text; }
    Preferences prefs() { return prefs; }
    AssetManager assets() { return assets; }
    SoundManager sounds() { return sounds; }
    ChessEngines engines() { return engines; }

    void setStatus(String message) { if (footer != null) footer.setText(message); }

    private String display() { return user.guest() ? text.get("guestName") : user.displayName(); }
    private String t(String key, Object... args) { return text.get(key, args); }

    String playerName(MatchOptions options, Side side) {
        if (options.mode() == MatchOptions.Mode.COMPUTER) {
            return options.human() == side ? display() : t("cpu", t("level." + options.level()));
        }
        return t("playerLocal", t("side." + side));
    }

    private void auth(boolean registering) {
        long token = ++generation;
        JPanel root = branding.background(BrandingManager.LOGIN, new GridLayout(1, 2, 28, 0));
        root.setBorder(new EmptyBorder(25, 36, 25, 48));
        JPanel illustration = transparent(new GridBagLayout());
        JPanel brand = transparent(new BorderLayout(0, 24));
        brand.add(branding.logo(BrandingManager.COMPANY, 238, 190, BrandConstants.COMPANY + " " + BrandConstants.COMPANY_CHINESE),
                BorderLayout.NORTH);
        brand.add(branding.logo(BrandingManager.GAME, 510, 288, BrandConstants.PRODUCT), BorderLayout.CENTER);
        GridBagConstraints art = new GridBagConstraints();
        art.fill = GridBagConstraints.HORIZONTAL;
        art.weightx = 1;
        illustration.add(brand, art);
        root.add(illustration);

        JPanel right = transparent(new GridBagLayout());
        JPanel form = translucentCard(new GridBagLayout());
        form.setBorder(new EmptyBorder(22, 29, 19, 29));
        GridBagConstraints f = fieldConstraints();
        JTextField username = field(false), displayName = field(false);
        JPasswordField password = (JPasswordField) field(true), confirmation = (JPasswordField) field(true);
        username.setText(registering ? "" : remembered);
        JCheckBox remember = new JCheckBox(t("remember"), !remembered.isBlank());
        JLabel message = label(" ", ERROR_TEXT);
        message.setPreferredSize(new Dimension(325, 38));
        addFieldRow(form, f, title(t(registering ? "register" : "login"), 34));
        addInput(form, f, t("username"), username);
        if (registering) addInput(form, f, t("displayName"), displayName);
        addInput(form, f, t("password"), password);
        if (registering) addInput(form, f, t("confirmation"), confirmation);
        JCheckBox reveal = new JCheckBox(t("showPassword"));
        reveal.setOpaque(false);
        remember.setOpaque(false);
        char passwordMask = password.getEchoChar(), confirmationMask = confirmation.getEchoChar();
        reveal.addActionListener(event -> {
            password.setEchoChar(reveal.isSelected() ? (char) 0 : passwordMask);
            confirmation.setEchoChar(reveal.isSelected() ? (char) 0 : confirmationMask);
            reveal.setText(t(reveal.isSelected() ? "hidePassword" : "showPassword"));
        });
        addFieldRow(form, f, registering ? row(reveal) : row(remember, reveal));
        addFieldRow(form, f, message);
        JButton submit = button(t(registering ? "register" : "login"), true, () -> {});
        for (var listener : submit.getActionListeners()) submit.removeActionListener(listener);
        submit.addActionListener(event -> {
            submit.setEnabled(false);
            message.setText(" ");
            char[] secret = password.getPassword();
            char[] repeated = confirmation.getPassword();
            password.setText("");
            confirmation.setText("");
            String name = username.getText(), shown = displayName.getText();
            boolean keep = remember.isSelected();
            String language = authLanguage;
            jobs.submit(() -> {
                var authenticated = registering ? accounts.register(name, shown, secret, repeated)
                        : accounts.signIn(name, secret, keep).orElse(null);
                return authenticated == null ? null : new LoginData(authenticated, store.settings(authenticated.id(), language));
            }, data -> {
                if (token != generation) return;
                submit.setEnabled(true);
                if (data == null) message.setText(t("authFail"));
                else {
                    if (!registering) remembered = keep ? data.user().username() : "";
                    enter(data);
                }
            }, failure -> {
                if (token != generation) return;
                submit.setEnabled(true);
                String msg = failure instanceof Accounts.Invalid invalid ? "invalid." + invalid.problem()
                        : failure instanceof ChessStore.DuplicateUsernameException ? "authDuplicate" : "storeError";
                message.setText("<html>" + t(msg) + "</html>");
                if (msg.equals("storeError")) LoggerFactory.getLogger(RoyalFrame.class).error("Account operation failed", failure);
            });
        });
        addFieldRow(form, f, submit);
        JButton switchMode = button(t(registering ? "login" : "register"), false, () -> auth(!registering));
        JButton guest = button(t("guest"), false, () -> {
            String language = authLanguage;
            jobs.submit(() -> new LoginData(new ChessStore.User(ChessStore.GUEST_ID, "@guest", "Guest"),
                        store.settings(ChessStore.GUEST_ID, language)),
                    data -> { if (token == generation) enter(data); },
                    failure -> { if (token == generation) error("storeError", failure); });
        });
        addFieldRow(form, f, row(switchMode, guest));
        addFieldRow(form, f, row(button(t("languageEnglish"), authLanguage.equals("en"), () -> authLanguage("en", registering)),
                button(t("languageVietnamese"), authLanguage.equals("vi"), () -> authLanguage("vi", registering))));
        JPanel authColumn = stack(form, Box.createVerticalStrut(11), label(BrandConstants.COPYRIGHT, MUTED));
        right.add(authColumn, new GridBagConstraints());
        root.add(right);
        setContentPane(root);
        getRootPane().setDefaultButton(submit);
        revalidate();
        repaint();
        username.requestFocusInWindow();
    }

    private void authLanguage(String language, boolean registering) {
        authLanguage = language;
        text = new I18n(language);
        auth(registering);
    }

    private void enter(LoginData data) {
        user = data.user();
        prefs = data.preferences();
        text = new I18n(prefs.language());
        dashboard();
    }

    private void navigate(Runnable destination) {
        if (closing) return;
        if (match != null) match.leave(destination);
        else destination.run();
    }

    void home() { navigate(this::dashboard); }

    private long show(Page selected, Component content) {
        ++generation;
        if (match != null && match != content) { match.close(); match = null; }
        if (replay != null && replay != content) { replay.close(); replay = null; }
        if (puzzle != content) puzzle = null;
        JPanel root = new JPanel(new BorderLayout());
        root.add(sidebar(selected), BorderLayout.WEST);
        JPanel main = transparent(new BorderLayout());
        main.add(content, BorderLayout.CENTER);
        footer = label(" ", MUTED);
        footer.setBorder(new EmptyBorder(0, 25, 5, 10));
        main.add(footer, BorderLayout.SOUTH);
        root.add(main, BorderLayout.CENTER);
        setContentPane(root);
        getRootPane().setDefaultButton(null);
        revalidate();
        repaint();
        return generation;
    }

    private JPanel sidebar(Page selected) {
        JPanel sidebar = new JPanel(new BorderLayout(0, 18));
        sidebar.setBackground(SURFACE);
        sidebar.setPreferredSize(new Dimension(194, 480));
        sidebar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, LINE),
                new EmptyBorder(20, 13, 14, 13)));
        sidebar.add(branding.logo(BrandingManager.GAME, 166, 96, BrandConstants.PRODUCT), BorderLayout.NORTH);
        JPanel menu = transparent(new GridLayout(0, 1, 0, 7));
        sidebarEntry(menu, selected, Page.HOME, "home", this::dashboard);
        sidebarEntry(menu, selected, Page.NEW, "newGame", () -> newGame(MatchOptions.Mode.LOCAL));
        sidebarEntry(menu, selected, Page.PUZZLES, "puzzles", this::puzzleList);
        sidebarEntry(menu, selected, Page.HISTORY, "history", this::history);
        sidebarEntry(menu, selected, Page.LEADERBOARD, "leaderboard", this::leaderboard);
        sidebarEntry(menu, selected, Page.STATISTICS, "statistics", this::statistics);
        sidebarEntry(menu, selected, Page.ACHIEVEMENTS, "achievements", this::achievements);
        sidebarEntry(menu, selected, Page.SETTINGS, "settings", this::settings);
        var nav = transparent(new BorderLayout());
        nav.add(menu, BorderLayout.NORTH);
        sidebar.add(nav, BorderLayout.CENTER);
        var name = label(display(), PALE);
        name.setToolTipText(display());
        JPanel accountActions = transparent(new GridLayout(0, 1, 0, 5));
        accountActions.add(button(t("about"), false, this::about));
        accountActions.add(button(t("signOut"), false, () -> navigate(this::signOut)));
        sidebar.add(stack(name, Box.createVerticalStrut(7), accountActions), BorderLayout.SOUTH);
        return sidebar;
    }

    private void sidebarEntry(JPanel menu, Page selected, Page item, String key, Runnable action) {
        var entry = button(t(key), item == selected, () -> navigate(action));
        entry.setHorizontalAlignment(SwingConstants.LEFT);
        menu.add(entry);
    }

    private void signOut() {
        if (match != null) { match.close(); match = null; }
        if (replay != null) { replay.close(); replay = null; }
        puzzle = null;
        profile = null;
        user = null;
        prefs = Preferences.defaults(authLanguage);
        text = new I18n(authLanguage);
        auth(false);
    }

    private JPanel loading(String key) {
        JPanel panel = padded();
        panel.add(heading(t(key), null), BorderLayout.NORTH);
        panel.add(label(t("loading"), MUTED), BorderLayout.CENTER);
        return panel;
    }

    private <T> void load(Page page, String key, Callable<T> supplier, Consumer<T> callback) {
        long token = show(page, loading(key));
        jobs.submit(supplier, data -> { if (token == generation) callback.accept(data); },
                e -> { if (token == generation) error("storeError", e); });
    }

    private void dashboard() {
        long owner = user.id();
        load(Page.HOME, "home", () -> new HomeData(store.profile(owner), store.history(owner)), data -> {
            profile = data.profile();
            JPanel page = padded();
            var hero = branding.background(BrandingManager.DASHBOARD, new BorderLayout());
            hero.setPreferredSize(new Dimension(750, 248));
            JPanel heroText = stack(branding.logo(BrandingManager.COMPANY, 128, 93, BrandConstants.COMPANY), Box.createVerticalStrut(9),
                    title(t("welcome", display()), 39), Box.createVerticalStrut(21),
                    row(button(t("quickPlay"), true, this::quickPlay), button(t("computer"), false, () -> newGame(MatchOptions.Mode.COMPUTER))));
            heroText.setBorder(new EmptyBorder(38, 23, 15, 8));
            hero.add(heroText, BorderLayout.WEST);

            JPanel metrics = metricGrid(profile, false);
            JPanel section = transparent(new BorderLayout(0, 11));
            section.add(heading(t("recent"), button(t("history"), false, this::history)), BorderLayout.NORTH);
            var games = stack();
            if (data.history().isEmpty()) games.add(cardLabel(t("emptyHistory")));
            for (var saved : data.history().stream().limit(4).toList()) {
                JPanel item = card(new BorderLayout(10, 0));
                item.add(stack(label(t("mode." + saved.options().mode()), TEXT),
                        label(date(saved.updated()) + "  ·  " + resultLabel(saved.options(), saved.outcome()), MUTED)), BorderLayout.CENTER);
                item.add(button(t(saved.outcome() == null ? "resume" : "replay"), false, () -> openSaved(saved, saved.outcome() == null)),
                        BorderLayout.EAST);
                games.add(item);
                gap(games, 7);
            }
            section.add(games, BorderLayout.CENTER);
            data.history().stream().filter(item -> item.outcome() == null).findFirst().ifPresent(saved ->
                    heroText.add(row(button(t("resume"), false, () -> openSaved(saved, true)))));
            JPanel body = stack(hero, Box.createVerticalStrut(13), metrics, Box.createVerticalStrut(17), section);
            page.add(scroll(body), BorderLayout.CENTER);
            show(Page.HOME, page);
        });
    }

    private JPanel metricGrid(ChessStore.Profile player, boolean expanded) {
        JPanel metrics = transparent(new GridLayout(0, 4, 9, 9));
        metrics.add(metric("played", player.played()));
        metrics.add(metric("wins", player.wins()));
        metrics.add(metric("rating", player.rating()));
        int rate = player.played() == 0 ? 0 : (int) Math.round(100d * player.wins() / player.played());
        metrics.add(metric("winRate", rate + "%"));
        if (expanded) {
            metrics.add(metric("losses", player.losses()));
            metrics.add(metric("draws", player.draws()));
            metrics.add(metric("streak", player.streak()));
            metrics.add(metric("points", player.puzzlePoints()));
        }
        metrics.setMaximumSize(new Dimension(Integer.MAX_VALUE, expanded ? 230 : 112));
        return metrics;
    }

    private JPanel metric(String key, Object value) {
        var card = card(new BorderLayout(0, 5));
        card.add(label(t(key), MUTED), BorderLayout.NORTH);
        card.add(title(String.valueOf(value), 30), BorderLayout.CENTER);
        return card;
    }

    private JPanel cardLabel(String message) {
        JPanel card = card(new BorderLayout());
        card.add(label(message, MUTED));
        return card;
    }

    private void quickPlay() {
        startMatch(new MatchOptions(MatchOptions.Mode.LOCAL, Side.WHITE, MatchOptions.Level.MEDIUM, 0, 0, prefs.theme()));
    }

    private String[] availableThemes() {
        return ThemeManager.available(profile == null ? List.of() : profile.achievements());
    }

    private void newGame(MatchOptions.Mode preferred) {
        JPanel page = padded();
        page.add(heading(t("newGame"), null), BorderLayout.NORTH);
        JPanel form = card(new GridBagLayout());
        GridBagConstraints f = fieldConstraints();
        var mode = combo(MatchOptions.Mode.values(), value -> t("mode." + value));
        mode.setSelectedItem(preferred);
        var color = combo(new String[]{"side.WHITE", "side.BLACK", "random"}, this::t);
        var level = combo(MatchOptions.Level.values(), value -> t("level." + value));
        level.setSelectedItem(MatchOptions.Level.MEDIUM);
        var time = combo(new Integer[]{0, 1, 3, 5, 10, 15, 30, 60}, value -> value == 0 ? t("unlimited") : t("minutes", value));
        time.setSelectedItem(10);
        var increment = combo(new Integer[]{0, 1, 2, 3, 5, 10, 15, 30}, value -> t("seconds", value));
        increment.setSelectedItem(5);
        var theme = combo(availableThemes(), id -> t("theme." + id));
        theme.setSelectedItem(prefs.theme());
        addInput(form, f, t("mode"), mode);
        addInput(form, f, t("side"), color);
        addInput(form, f, t("difficulty"), level);
        addInput(form, f, t("timeControl"), time);
        addInput(form, f, t("increment"), increment);
        addInput(form, f, t("theme"), theme);
        Runnable adjust = () -> {
            boolean vsComputer = mode.getSelectedItem() == MatchOptions.Mode.COMPUTER;
            level.setEnabled(vsComputer);
            increment.setEnabled((Integer) time.getSelectedItem() != 0);
        };
        mode.addActionListener(event -> adjust.run());
        time.addActionListener(event -> adjust.run());
        adjust.run();
        addFieldRow(form, f, button(t("start"), true, () -> {
            Side human = switch ((String) color.getSelectedItem()) {
                case "side.BLACK" -> Side.BLACK;
                case "random" -> ThreadLocalRandom.current().nextBoolean() ? Side.WHITE : Side.BLACK;
                default -> Side.WHITE;
            };
            long base = (Integer) time.getSelectedItem() * 60_000L;
            startMatch(new MatchOptions((MatchOptions.Mode) mode.getSelectedItem(), human,
                    (MatchOptions.Level) level.getSelectedItem(), base,
                    base == 0 ? 0 : (Integer) increment.getSelectedItem() * 1000L, (String) theme.getSelectedItem()));
        }));
        JPanel anchor = transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        anchor.add(form);
        page.add(scroll(anchor), BorderLayout.CENTER);
        show(Page.NEW, page);
    }

    private void startMatch(MatchOptions options) { live(GameSession.start(user.id(), options, System::nanoTime)); }

    private void live(GameSession session) {
        MatchView live = new MatchView(this, session);
        show(Page.NEW, live);
        match = live;
    }

    void save(MatchSnapshot snapshot, Consumer<Receipt> success, Consumer<Exception> failure) {
        jobs.submit(() -> {
            int before = snapshot.outcome() == null ? 0 : store.profile(snapshot.owner()).rating();
            store.save(snapshot);
            int after = snapshot.outcome() == null ? 0 : store.profile(snapshot.owner()).rating();
            return new Receipt(before, after);
        }, success, failure);
    }

    void result(MatchView view, MatchSnapshot saved, Receipt receipt) {
        if (match != view) return;
        resultPage(saved, view.session().game(), receipt);
    }

    private void resultPage(MatchSnapshot saved, Game game, Receipt receipt) {
        JPanel page = padded();
        page.add(heading(t("result"), null), BorderLayout.NORTH);
        var banner = new RoyalArtwork(assets, true);
        banner.setLayout(new BorderLayout());
        JPanel result = stack(label(t("reason." + saved.outcome().reason()), PALE), Box.createVerticalStrut(8),
                title(resultLabel(saved.options(), saved.outcome()), 54), Box.createVerticalStrut(17),
                row(button(t("again"), true, () -> startMatch(saved.options())),
                        button(t("analysis"), false, () -> openReplay(game, saved, true)),
                        button(t("home"), false, this::dashboard)));
        result.setBorder(new EmptyBorder(35, 26, 22, 24));
        banner.add(result, BorderLayout.CENTER);
        banner.setPreferredSize(new Dimension(750, 237));
        JPanel metrics = transparent(new GridLayout(1, 3, 10, 0));
        metrics.add(metric("gameTime", clock(saved.elapsedMillis())));
        metrics.add(metric("moves", t("halfMoves", game.plyCount())));
        metrics.add(metric("rating", receipt == null ? "—" : receipt.before() == receipt.after()
                ? String.valueOf(receipt.after()) : receipt.before() + " → " + receipt.after()));
        JPanel content = transparent(new BorderLayout(0, 15));
        content.add(banner, BorderLayout.CENTER);
        content.add(metrics, BorderLayout.SOUTH);
        page.add(content, BorderLayout.CENTER);
        if (receipt == null) page.add(button(t("save"), true, () ->
                save(saved, updated -> resultPage(saved, game, updated), failure -> error("saveError", failure))), BorderLayout.SOUTH);
        show(Page.NEW, page);
    }

    private String resultLabel(MatchOptions options, Outcome outcome) {
        if (outcome == null) return t("inProgress");
        if (outcome.winner() == null) return t("draw");
        return options.mode() == MatchOptions.Mode.LOCAL ? t("winner", t("side." + outcome.winner()))
                : t(outcome.winner() == options.human() ? "victory" : "defeat");
    }

    private String date(Instant when) {
        return DateTimeFormatter.ofPattern("dd MMM yyyy  HH:mm", text.locale()).withZone(ZoneId.systemDefault()).format(when);
    }

    private void history() {
        long owner = user.id();
        load(Page.HISTORY, "history", () -> store.history(owner), this::historyTable);
    }

    private void historyTable(List<ChessStore.Summary> data) {
        JPanel page = padded();
        var search = field(false);
        search.putClientProperty("JTextField.placeholderText", t("search"));
        search.getAccessibleContext().setAccessibleName(t("search"));
        var filter = combo(new String[]{"all", "inProgress", "victory", "defeat", "draw", "mode.LOCAL", "mode.COMPUTER"}, this::t);
        page.add(heading(t("history"), row(button(t("import"), false, this::importPgn))), BorderLayout.NORTH);
        JTable table = readonlyTable("date", "mode", "result", "moves");
        var model = (DefaultTableModel) table.getModel();
        List<ChessStore.Summary> visible = new java.util.ArrayList<>();
        Runnable refresh = () -> {
            model.setRowCount(0);
            visible.clear();
            String query = search.getText().strip().toLowerCase(text.locale());
            String chosen = (String) filter.getSelectedItem();
            for (var game : data) {
                String kind = t("mode." + game.options().mode());
                String result = resultLabel(game.options(), game.outcome());
                String when = date(game.updated());
                String reason = game.outcome() == null ? "" : t("reason." + game.outcome().reason());
                if (!chosen.equals("all") && !t(chosen).equals(kind) && !t(chosen).equals(result)) continue;
                if (!(when + " " + kind + " " + result + " " + reason).toLowerCase(text.locale()).contains(query)) continue;
                visible.add(game);
                model.addRow(new Object[]{when, kind, result, game.plies()});
            }
        };
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { refresh.run(); }
            @Override public void removeUpdate(DocumentEvent event) { refresh.run(); }
            @Override public void changedUpdate(DocumentEvent event) { refresh.run(); }
        });
        filter.addActionListener(event -> refresh.run());
        Runnable select = () -> {
            int row = table.getSelectedRow();
            if (row >= 0) details(visible.get(row));
        };
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { if (e.getClickCount() == 2) select.run(); }
        });
        JButton details = button(t("details"), false, select);
        JButton resume = button(t("resume"), true, () -> openSaved(visible.get(table.getSelectedRow()), true));
        JButton replayButton = button(t("replay"), false, () -> openSaved(visible.get(table.getSelectedRow()), false));
        details.setEnabled(false);
        resume.setEnabled(false);
        replayButton.setEnabled(false);
        table.getSelectionModel().addListSelectionListener(event -> {
            int row = table.getSelectedRow();
            boolean selected = row >= 0 && row < visible.size();
            details.setEnabled(selected);
            replayButton.setEnabled(selected);
            resume.setEnabled(selected && visible.get(row).outcome() == null);
        });
        refresh.run();
        var wrapper = card(new BorderLayout(0, 10));
        wrapper.add(row(search, filter), BorderLayout.NORTH);
        wrapper.add(scroll(table), BorderLayout.CENTER);
        wrapper.add(row(details, resume, replayButton), BorderLayout.SOUTH);
        page.add(wrapper, BorderLayout.CENTER);
        show(Page.HISTORY, page);
    }

    private void details(ChessStore.Summary item) {
        String details = date(item.updated()) + "\n" + t("mode." + item.options().mode()) + "\n"
                + resultLabel(item.options(), item.outcome()) + "\n"
                + (item.outcome() == null ? "" : t("reason." + item.outcome().reason()) + "\n") + t("halfMoves", item.plies());
        Object[] actions = item.outcome() == null ? new Object[]{t("resume"), t("replay"), t("export"), t("cancel")}
                : new Object[]{t("replay"), t("export"), t("cancel")};
        Object choice = JOptionPane.showInputDialog(this, details, t("details"), JOptionPane.PLAIN_MESSAGE, null, actions, actions[0]);
        if (choice == null || choice.equals(t("cancel"))) return;
        if (choice.equals(t("export"))) loadSaved(item.id(), loaded -> exportSaved(loaded.snapshot(), loaded.session().game()));
        else openSaved(item, choice.equals(t("resume")));
    }

    private void openSaved(ChessStore.Summary summary, boolean resume) {
        loadSaved(summary.id(), loaded -> {
            if (resume && loaded.snapshot().outcome() == null) live(loaded.session());
            else openReplay(loaded.session().game(), loaded.snapshot(), false);
        });
    }

    private void loadSaved(UUID id, Consumer<LoadedGame> action) {
        long token = generation, owner = user.id();
        jobs.submit(() -> {
            MatchSnapshot saved = store.load(owner, id).orElseThrow();
            return new LoadedGame(saved, GameSession.restore(saved, System::nanoTime));
        }, loaded -> { if (token == generation) action.accept(loaded); },
                failure -> { if (token == generation) error("storeError", failure); });
    }

    private void openReplay(Game game, MatchSnapshot saved, boolean end) {
        String white = playerName(saved.options(), Side.WHITE), black = playerName(saved.options(), Side.BLACK);
        ReplayView view = new ReplayView(this, game, saved.options().human(), saved.options().theme(), white, black,
                this::history, () -> exportSaved(saved, game), end);
        show(Page.HISTORY, view);
        replay = view;
    }

    private void exportSaved(MatchSnapshot saved, Game game) {
        String date = DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneId.systemDefault()).format(saved.started());
        exportPgn(Pgn.export(game, BrandConstants.PRODUCT, playerName(saved.options(), Side.WHITE), playerName(saved.options(), Side.BLACK), date));
    }

    private void exportPgn(String pgn) {
        var chooser = pgnChooser();
        chooser.setDialogTitle(t("export"));
        chooser.setSelectedFile(new java.io.File("chessmaster.pgn"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        var path = chooser.getSelectedFile().toPath();
        if (Files.exists(path) && !confirm("overwrite")) return;
        jobs.submit(() -> Files.writeString(path, pgn, StandardCharsets.UTF_8),
                result -> setStatus(t("fileDone")), failure -> error("fileError", failure));
    }

    private void importPgn() {
        var chooser = pgnChooser();
        chooser.setDialogTitle(t("import"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        var path = chooser.getSelectedFile().toPath();
        long token = generation;
        jobs.submit(() -> {
            if (Files.size(path) > 2_000_000) throw new IllegalArgumentException("PGN exceeds 2 MB");
            String raw = Files.readString(path).replaceFirst("^\uFEFF", "");
            return new ImportedFile(raw, Pgn.parse(raw));
        }, result -> {
            if (token != generation) return;
            var imported = result.pgn();
            var view = new ReplayView(this, imported.game(), Side.WHITE, prefs.theme(),
                    imported.tags().getOrDefault("White", t("side.WHITE")), imported.tags().getOrDefault("Black", t("side.BLACK")),
                    this::history, () -> exportPgn(result.raw()), false);
            show(Page.HISTORY, view);
            replay = view;
        }, failure -> { if (token == generation) error("fileError", failure); });
    }

    private static JFileChooser pgnChooser() {
        var chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PGN (*.pgn)", "pgn"));
        return chooser;
    }

    void puzzles() { navigate(this::puzzleList); }

    private void puzzleList() {
        long owner = user.id();
        load(Page.PUZZLES, "puzzles", () -> store.profile(owner), data -> {
            profile = data;
            JPanel page = padded();
            var filter = combo(new String[]{"all", "level.EASY", "level.MEDIUM", "level.HARD"}, this::t);
            page.add(heading(t("puzzles"), row(filter, button(t("puzzleHistory"), false, this::puzzleHistory))), BorderLayout.NORTH);
            JPanel list = stack();
            Runnable draw = () -> {
                list.removeAll();
                for (Puzzles.Puzzle entry : Puzzles.catalog()) {
                    if (!filter.getSelectedItem().equals("all") && !filter.getSelectedItem().equals("level." + entry.level())) continue;
                    JPanel card = card(new BorderLayout(12, 0));
                    card.add(stack(title(t("puzzle." + entry.id()), 22), label(t("level." + entry.level()) + "  ·  +" + entry.points(), PALE)),
                            BorderLayout.CENTER);
                    card.add(button(t("startPuzzle"), false, () -> openPuzzle(entry)), BorderLayout.EAST);
                    list.add(card);
                    gap(list, 10);
                }
                list.revalidate();
                list.repaint();
            };
            filter.addActionListener(event -> draw.run());
            draw.run();
            page.add(scroll(list), BorderLayout.CENTER);
            show(Page.PUZZLES, page);
        });
    }

    void openPuzzle(Puzzles.Puzzle selected) {
        var view = new PuzzleView(this, selected);
        show(Page.PUZZLES, view);
        puzzle = view;
    }

    void recordPuzzle(PuzzleView view, Puzzles.Puzzle selected, boolean solved, boolean hinted) {
        long owner = user.id();
        jobs.submit(() -> store.recordPuzzle(owner, selected.id(), solved, hinted), points -> {
            if (view == puzzle) view.reward(points);
        }, failure -> { if (view == puzzle) error("storeError", failure); });
    }

    private void puzzleHistory() {
        long owner = user.id();
        load(Page.PUZZLES, "puzzleHistory", () -> store.puzzleHistory(owner), attempts -> {
            JPanel page = padded();
            page.add(heading(t("puzzleHistory"), button(t("back"), false, this::puzzleList)), BorderLayout.NORTH);
            var table = readonlyTable("date", "puzzles", "result", "points");
            var model = (DefaultTableModel) table.getModel();
            for (var attempt : attempts) {
                String result = t(attempt.solved() ? "puzzleSolved" : "puzzleMiss");
                if (attempt.hinted()) result += " · " + t("puzzleHint");
                model.addRow(new Object[]{date(attempt.date()), t("puzzle." + attempt.puzzleId()), result, attempt.points()});
            }
            page.add(attempts.isEmpty() ? cardLabel(t("emptyPuzzleHistory")) : scroll(table), BorderLayout.CENTER);
            show(Page.PUZZLES, page);
        });
    }

    private void leaderboard() {
        load(Page.LEADERBOARD, "leaderboard", store::leaderboard, players -> {
            JPanel page = padded();
            page.add(heading(t("leaderboard"), null), BorderLayout.NORTH);
            JTable table = readonlyTable("displayName", "rating", "wins", "played");
            var model = (DefaultTableModel) table.getModel();
            for (var player : players) model.addRow(new Object[]{player.user().displayName(), player.rating(), player.wins(), player.played()});
            page.add(players.isEmpty() ? cardLabel(t("noProfiles")) : scroll(table), BorderLayout.CENTER);
            show(Page.LEADERBOARD, page);
        });
    }

    private void statistics() {
        long owner = user.id();
        load(Page.STATISTICS, "statistics", () -> store.profile(owner), player -> {
            profile = player;
            JPanel page = padded();
            page.add(heading(t("statistics"), null), BorderLayout.NORTH);
            JPanel metrics = metricGrid(player, true);
            var container = stack(metrics);
            page.add(scroll(container), BorderLayout.CENTER);
            show(Page.STATISTICS, page);
        });
    }

    private void achievements() {
        long owner = user.id();
        load(Page.ACHIEVEMENTS, "achievements", () -> store.profile(owner), player -> {
            profile = player;
            JPanel page = padded();
            page.add(heading(t("achievements"), null), BorderLayout.NORTH);
            var list = stack();
            if (player.achievements().isEmpty()) list.add(cardLabel(t("noAchievements")));
            for (String badge : player.achievements()) {
                JPanel card = card(new BorderLayout());
                card.add(title(t("achievement." + badge), 22), BorderLayout.CENTER);
                if (badge.equals("puzzle-master")) card.add(label(t("theme.royal-slate"), PALE), BorderLayout.EAST);
                list.add(card);
                gap(list, 8);
            }
            page.add(scroll(list), BorderLayout.CENTER);
            show(Page.ACHIEVEMENTS, page);
        });
    }

    private void settings() {
        long owner = user.id();
        load(Page.SETTINGS, "settings", () -> store.profile(owner), player -> {
            profile = player;
            settingsForm();
        });
    }

    private void settingsForm() {
        JPanel page = padded();
        page.add(heading(t("settings"), null), BorderLayout.NORTH);
        JPanel form = card(new GridBagLayout());
        GridBagConstraints f = fieldConstraints();
        JComboBox<String> language = combo(new String[]{"en", "vi"}, code -> t(code.equals("en") ? "english" : "vietnamese"));
        JComboBox<String> theme = combo(availableThemes(), id -> t("theme." + id));
        JComboBox<Preferences.Orientation> orientation = combo(Preferences.Orientation.values(), value -> t("orientation." + value));
        JCheckBox sound = new JCheckBox(t("sound")), animation = new JCheckBox(t("animation")),
                quality = new JCheckBox(t("quality")), autosave = new JCheckBox(t("autosave"));
        var scale = new JSpinner(new SpinnerNumberModel(prefs.pieceScale(), 70, 105, 1));
        Runnable read = () -> {
            language.setSelectedItem(prefs.language());
            theme.setSelectedItem(prefs.theme());
            orientation.setSelectedItem(prefs.orientation());
            sound.setSelected(prefs.sound());
            animation.setSelected(prefs.animation());
            quality.setSelected(prefs.highQuality());
            autosave.setSelected(prefs.autosave());
            scale.setValue(prefs.pieceScale());
        };
        read.run();
        addInput(form, f, t("language"), language);
        addInput(form, f, t("theme"), theme);
        addInput(form, f, t("orientation"), orientation);
        addInput(form, f, t("scale"), scale);
        for (var checkbox : new JCheckBox[]{sound, animation, quality, autosave}) addFieldRow(form, f, checkbox);
        addFieldRow(form, f, row(button(t("apply"), true, () -> savePrefs(new Preferences((String) language.getSelectedItem(),
                        (String) theme.getSelectedItem(), sound.isSelected(), animation.isSelected(),
                        (Preferences.Orientation) orientation.getSelectedItem(), (Integer) scale.getValue(),
                        quality.isSelected(), autosave.isSelected()))),
                button(t("reset"), false, () -> savePrefs(Preferences.defaults(authLanguage)))));
        JPanel anchor = transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        anchor.add(form);
        page.add(scroll(anchor), BorderLayout.CENTER);
        show(Page.SETTINGS, page);
    }

    private void savePrefs(Preferences next) {
        long token = generation, owner = user.id();
        jobs.submit(() -> { store.settings(owner, next); return next; }, saved -> {
            if (generation != token) return;
            prefs = saved;
            authLanguage = saved.language();
            text = new I18n(saved.language());
            settingsForm();
            setStatus(t("preferencesSaved"));
        }, failure -> { if (generation == token) error("storeError", failure); });
    }

    private JTable readonlyTable(String... keys) {
        String[] headers = java.util.Arrays.stream(keys).map(this::t).toArray(String[]::new);
        JTable table = new JTable(new DefaultTableModel(headers, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        });
        table.setRowHeight(34);
        table.setShowGrid(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        return table;
    }

    private static GridBagConstraints fieldConstraints() {
        GridBagConstraints f = new GridBagConstraints();
        f.gridx = 0;
        f.gridy = 0;
        f.weightx = 1;
        f.fill = GridBagConstraints.HORIZONTAL;
        f.anchor = GridBagConstraints.WEST;
        f.insets = new Insets(3, 0, 4, 0);
        return f;
    }

    private static void addInput(JPanel panel, GridBagConstraints f, String title, JComponent input) {
        JLabel label = label(title, MUTED);
        label.setLabelFor(input);
        addFieldRow(panel, f, label);
        addFieldRow(panel, f, input);
    }

    private static void addFieldRow(JPanel panel, GridBagConstraints f, Component child) {
        panel.add(child, f);
        f.gridy++;
    }

    boolean confirm(String key) {
        return JOptionPane.showOptionDialog(this, t(key), getTitle(), JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, new Object[]{t("confirm"), t("cancel")}, t("cancel")) == 0;
    }

    void error(String key, Throwable failure) {
        LoggerFactory.getLogger(RoyalFrame.class).error(key, failure);
        JOptionPane.showMessageDialog(this, t(key), t("error"), JOptionPane.ERROR_MESSAGE);
    }

    private void about() {
        JPanel info = stack(
                branding.logo(BrandingManager.GAME, 330, 187, BrandConstants.PRODUCT),
                branding.logo(BrandingManager.EMBLEM, 92, 59, BrandConstants.COMPANY),
                Box.createVerticalStrut(8),
                title(BrandConstants.PRODUCT, 24),
                label(t("version", BrandConstants.version()), PALE),
                label(t("developed", BrandConstants.COMPANY, BrandConstants.COMPANY_CHINESE), IVORY),
                label(BrandConstants.COPYRIGHT, MUTED),
                label(t(engines.stockfishDetected() ? "stockfishDetected" : "stockfishInternal"), MUTED));
        JOptionPane.showMessageDialog(this, info, t("about"), JOptionPane.PLAIN_MESSAGE);
    }

    void toggleFullscreen() {
        GraphicsDevice device = getGraphicsConfiguration().getDevice();
        if (windowedBounds != null) {
            if (device.getFullScreenWindow() == this) device.setFullScreenWindow(null);
            setBounds(windowedBounds);
            windowedBounds = null;
        } else {
            windowedBounds = getBounds();
            if (device.isFullScreenSupported()) device.setFullScreenWindow(this);
            else setBounds(getGraphicsConfiguration().getBounds());
        }
    }

    private void shutdown() {
        closing = true;
        generation++;
        if (match != null) match.close();
        if (replay != null) replay.close();
        jobs.close();
        sounds.close();
        dispose();
    }

    // The visual smoke path drives the same frame, views and page methods as the desktop.
    void visualGuest() { enter(new LoginData(new ChessStore.User(ChessStore.GUEST_ID, "@guest", "Guest"), prefs)); }
    void visualMatch() {
        var game = GameSession.start(user.id(), new MatchOptions(MatchOptions.Mode.LOCAL, Side.WHITE,
                MatchOptions.Level.MEDIUM, 600_000, 5000, "royal"), System::nanoTime);
        game.resume();
        for (String move : "e2e4 e7e5 g1f3 b8c6 f1b5 a7a6".split(" ")) game.play(Move.uci(move));
        game.pause();
        live(game);
    }
}
