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
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.slf4j.LoggerFactory;
import com.jiexiangtech.chess.application.*;
import com.jiexiangtech.chess.domain.*;
import com.jiexiangtech.chess.infrastructure.ChessEngines;
import static com.jiexiangtech.chess.desktop.RoyalStyle.*;
import static com.jiexiangtech.chess.desktop.ViewParts.*;

final class RoyalFrame extends JFrame {
    record Receipt(int before, int after) {}
    private record HomeData(ChessStore.Profile profile, List<ChessStore.Summary> history) {}
    private record LoginData(ChessStore.User user, Preferences preferences) {}
    private record ImportedFile(String raw, Pgn.Imported pgn) {}
    private record LoadedGame(MatchSnapshot snapshot, GameSession session) {}
    private enum Page { HOME, NEW, ONLINE, PUZZLES, HISTORY, LEADERBOARD, STATISTICS, ACHIEVEMENTS, SETTINGS }

    private static final List<String> ALL_ACHIEVEMENTS = List.of(
            "first-victory", "checkmate-master", "win-streak",
            "puzzle-master", "ai-challenger", "royal-champion");

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
    private OnlineView online;
    private JLabel footer;
    private long generation;
    private String remembered;
    private String authLanguage;
    private Rectangle windowedBounds;
    private boolean closing;
    private boolean sidebarCompact;
    private Page currentPage = Page.HOME;
    private Component currentContent;

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
        setSize(Math.min(1360, available.width), Math.min(880, available.height));
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { navigate(RoyalFrame.this::shutdown); }
        });
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                if (currentContent instanceof MatchView || currentContent instanceof PuzzleView || currentContent instanceof ReplayView || currentContent instanceof OnlineView) {
                    boolean wantCompact = getWidth() < 1180;
                    if (wantCompact != sidebarCompact) {
                        sidebarCompact = wantCompact;
                        mountShell();
                    }
                }
            }
        });
        auth(false);
    }

    I18n text() { return text; }
    Preferences prefs() { return prefs; }
    AssetManager assets() { return assets; }
    SoundManager sounds() { return sounds; }
    ChessEngines engines() { return engines; }
    BrandingManager branding() { return branding; }

    void setStatus(String message) { if (footer != null) footer.setText(message); }

    private String display() { return user.guest() ? text.get("guestName") : user.displayName(); }
    String displayLabel() { return display(); }
    int currentRating() { return profile == null ? 1200 : profile.rating(); }
    private String t(String key, Object... args) { return text.get(key, args); }

    String playerName(MatchOptions options, Side side) {
        if (options.mode() == MatchOptions.Mode.COMPUTER) {
            return options.human() == side ? display() : t("cpu", t("level." + options.level()));
        }
        return t("playerLocal", t("side." + side));
    }

    // =========================================================================
    // APPROVED LOGIN / REGISTRATION SCREEN (PRESERVED BASELINE)
    // =========================================================================

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

    // =========================================================================
    // NAVIGATION & APP SHELL
    // =========================================================================

    private void navigate(Runnable destination) {
        if (closing) return;
        if (match != null) match.leave(destination);
        else destination.run();
    }

    void home() { navigate(this::dashboard); }

    private long show(Page selected, Component content) {
        return show(selected, content, false);
    }

    private long show(Page selected, Component content, boolean compactSidebar) {
        ++generation;
        if (match != null && match != content) { match.close(); match = null; }
        if (replay != null && replay != content) { replay.close(); replay = null; }
        if (puzzle != content) puzzle = null;
        if (online != null && online != content) { online.close(); online = null; }
        this.currentPage = selected;
        this.currentContent = content;
        this.sidebarCompact = compactSidebar;
        mountShell();
        return generation;
    }

    private void mountShell() {
        boolean focusActive = currentContent instanceof MatchView mv && mv.isFocusMode();
        boolean cinematicBackdrop = currentContent instanceof MatchView || currentContent instanceof PuzzleView || currentContent instanceof ReplayView
                || currentContent instanceof OnlineView;
        String bgPath = focusActive ? BrandingManager.FOCUS_BG : cinematicBackdrop ? BrandingManager.GAMEPLAY_BG : null;
        JPanel root = new RoyalBackgroundPanel(new BorderLayout(),
                bgPath == null ? null : assets.brand(bgPath), 0, false);
        RoyalNavigationPanel nav = navigationPanel(currentPage, sidebarCompact);
        nav.setFocusDimmed(focusActive);
        root.add(nav, BorderLayout.WEST);
        JPanel main = transparent(new BorderLayout());
        main.add(currentContent, BorderLayout.CENTER);
        footer = caption(" ");
        footer.setBorder(new EmptyBorder(0, 28, 4, 12));
        if (!cinematicBackdrop) main.add(footer, BorderLayout.SOUTH);
        root.add(main, BorderLayout.CENTER);
        setContentPane(root);
        getRootPane().setDefaultButton(null);
        revalidate();
        repaint();
    }

    void refreshShellAtmosphere() {
        mountShell();
    }

    void updateViewMode(Preferences.ViewMode nextMode) {
        prefs = prefs.withViewMode(nextMode);
        refreshShellAtmosphere();
        if (user == null) return;
        long owner = user.id();
        Preferences toSave = prefs;
        jobs.submit(() -> { store.settings(owner, toSave); return toSave; },
                saved -> {}, failure -> LoggerFactory.getLogger(RoyalFrame.class).warn("Failed to persist view mode", failure));
    }

    private void toggleSidebar() {
        sidebarCompact = !sidebarCompact;
        mountShell();
    }

    private RoyalNavigationPanel navigationPanel(Page selected, boolean compact) {
        List<RoyalNavigationPanel.Item> items = List.of(
                new RoyalNavigationPanel.Item(Page.HOME.name(), t("home"), RoyalIcons.Glyph.HOME, () -> navigate(this::dashboard)),
                new RoyalNavigationPanel.Item(Page.NEW.name(), t("newGame"), RoyalIcons.Glyph.PLUS, () -> navigate(() -> newGame(MatchOptions.Mode.LOCAL))),
                new RoyalNavigationPanel.Item(Page.ONLINE.name(), t("online"), RoyalIcons.Glyph.PLAY, () -> navigate(this::online)),
                new RoyalNavigationPanel.Item(Page.PUZZLES.name(), t("puzzles"), RoyalIcons.Glyph.PUZZLES, () -> navigate(this::puzzleList)),
                new RoyalNavigationPanel.Item(Page.HISTORY.name(), t("history"), RoyalIcons.Glyph.HISTORY, () -> navigate(this::history)),
                new RoyalNavigationPanel.Item(Page.LEADERBOARD.name(), t("leaderboard"), RoyalIcons.Glyph.LEADERBOARD, () -> navigate(this::leaderboard)),
                new RoyalNavigationPanel.Item(Page.STATISTICS.name(), t("statistics"), RoyalIcons.Glyph.STATISTICS, () -> navigate(this::statistics)),
                new RoyalNavigationPanel.Item(Page.ACHIEVEMENTS.name(), t("achievements"), RoyalIcons.Glyph.ACHIEVEMENTS, () -> navigate(this::achievements)),
                new RoyalNavigationPanel.Item(Page.SETTINGS.name(), t("settings"), RoyalIcons.Glyph.SETTINGS, () -> navigate(this::settings))
        );
        return new RoyalNavigationPanel(branding, assets, items, selected.name(), display(),
                t("about"), t("signOut"), t("toggleSidebar"), compact,
                this::toggleSidebar, this::about, () -> navigate(this::signOut));
    }

    private void online() {
        if (online != null) return;
        OnlineView view = new OnlineView(this, user.id());
        show(Page.ONLINE, view, getWidth() < 1180);
        online = view;
    }

    private void signOut() {
        if (online != null) { online.close(); online = null; }
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

    // =========================================================================
    // HOME / DASHBOARD SCREEN
    // =========================================================================

    private void dashboard() {
        long owner = user.id();
        load(Page.HOME, "home", () -> new HomeData(store.profile(owner), store.history(owner)), data -> {
            profile = data.profile();
            JPanel page = padded();

            // Cinematic Hero Banner
            RoyalBackgroundPanel hero = new RoyalBackgroundPanel(new BorderLayout(),
                    assets.brand(BrandingManager.DASHBOARD), 20, true);
            hero.setPreferredSize(new Dimension(820, 274));
            hero.setMaximumSize(new Dimension(Integer.MAX_VALUE, 274));

            JPanel brandBadge = row(
                    branding.logo(BrandingManager.EMBLEM, 36, 26, BrandConstants.COMPANY),
                    eyebrow(BrandConstants.COMPANY + "  ·  " + BrandConstants.PRODUCT));
            JLabel welcomeTitle = title(t("welcome", display()), 40);
            JLabel subtitle = new JLabel(t("homeSubtitle"));
            subtitle.setForeground(new Color(0xD3CCBF));
            subtitle.setFont(sans(Font.PLAIN, 15f));

            JPanel ctaRow = transparent(new FlowLayout(FlowLayout.LEFT, 10, 0));
            RoyalButton quickBtn = button(t("quickPlay"), RoyalButton.Variant.PRIMARY, RoyalIcons.Glyph.NEW_GAME, this::quickPlay);
            quickBtn.setBorder(new EmptyBorder(10, 18, 10, 18));
            RoyalButton cpuBtn = button(t("computer"), RoyalButton.Variant.SECONDARY, RoyalIcons.Glyph.CPU,
                    () -> newGame(MatchOptions.Mode.COMPUTER));
            cpuBtn.setBorder(new EmptyBorder(10, 18, 10, 18));
            ctaRow.add(quickBtn);
            ctaRow.add(cpuBtn);
            data.history().stream().filter(item -> item.outcome() == null).findFirst().ifPresent(saved -> {
                RoyalButton resumeBtn = button(t("resume"), RoyalButton.Variant.SECONDARY, RoyalIcons.Glyph.PLAY,
                        () -> openSaved(saved, true));
                resumeBtn.setBorder(new EmptyBorder(10, 18, 10, 18));
                ctaRow.add(resumeBtn);
            });

            JPanel heroContent = stack(brandBadge, Box.createVerticalStrut(14), welcomeTitle,
                    Box.createVerticalStrut(6), subtitle, Box.createVerticalStrut(22), ctaRow);
            heroContent.setBorder(new EmptyBorder(28, 30, 26, 20));
            hero.add(heroContent, BorderLayout.WEST);

            // Summary Stat Cards
            JPanel metrics = metricGrid(profile, false);

            // Recent Games Section
            JPanel recentSection = transparent(new BorderLayout(0, 12));
            recentSection.add(heading(t("recent"), null,
                    button(t("history"), RoyalButton.Variant.SECONDARY, RoyalIcons.Glyph.HISTORY, this::history)),
                    BorderLayout.NORTH);

            JPanel gamesList = stack();
            if (data.history().isEmpty()) {
                RoyalCard emptyCard = new RoyalCard(new BorderLayout(16, 0));
                emptyCard.setBorder(new EmptyBorder(20, 22, 20, 22));
                emptyCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));
                JPanel iconWrap = transparent(new GridBagLayout());
                iconWrap.add(new JLabel(RoyalIcons.icon(RoyalIcons.Glyph.CROWN, 28, GOLD)));
                JPanel emptyText = stack(
                        title(t("emptyHistory"), 19),
                        Box.createVerticalStrut(4),
                        caption(t("emptyHistoryHint")));
                JPanel textWrap = transparent(new GridBagLayout());
                GridBagConstraints tc = new GridBagConstraints();
                tc.anchor = GridBagConstraints.WEST; tc.weightx = 1; tc.fill = GridBagConstraints.HORIZONTAL;
                textWrap.add(emptyText, tc);
                JPanel actionWrap = transparent(new GridBagLayout());
                RoyalButton cta = button(t("newGame"), RoyalButton.Variant.PRIMARY, RoyalIcons.Glyph.NEW_GAME,
                        () -> newGame(MatchOptions.Mode.COMPUTER));
                cta.setBorder(new EmptyBorder(9, 16, 9, 16));
                actionWrap.add(cta);
                emptyCard.add(iconWrap, BorderLayout.WEST);
                emptyCard.add(textWrap, BorderLayout.CENTER);
                emptyCard.add(actionWrap, BorderLayout.EAST);
                gamesList.add(emptyCard);
            } else {
                for (var saved : data.history().stream().limit(4).toList()) {
                    gamesList.add(gameSummaryCard(saved, false));
                    gap(gamesList, 8);
                }
            }
            recentSection.add(gamesList, BorderLayout.CENTER);

            JPanel body = stack(hero, Box.createVerticalStrut(18), metrics, Box.createVerticalStrut(22), recentSection);
            body.setBorder(new EmptyBorder(0, 0, 12, 4));
            JPanel bodyHolder = transparent(new BorderLayout());
            bodyHolder.add(body, BorderLayout.NORTH);
            page.add(scroll(bodyHolder), BorderLayout.CENTER);
            show(Page.HOME, page);
        });
    }

    private JPanel metricGrid(ChessStore.Profile player, boolean expanded) {
        JPanel metrics = transparent(new GridLayout(0, 4, 12, 12));
        metrics.add(metricCard("played", player.played(), RoyalIcons.Glyph.NEW_GAME, IVORY, false));
        metrics.add(metricCard("wins", player.wins(), RoyalIcons.Glyph.TROPHY, SUCCESS, false));
        metrics.add(metricCard("rating", player.rating(), RoyalIcons.Glyph.CROWN, PALE, true));
        int rate = player.played() == 0 ? 0 : (int) Math.round(100d * player.wins() / player.played());
        metrics.add(metricCard("winRate", rate + "%", RoyalIcons.Glyph.STATISTICS, IVORY, false));
        if (expanded) {
            metrics.add(metricCard("losses", player.losses(), RoyalIcons.Glyph.RESIGN, DANGER_TEXT, false));
            metrics.add(metricCard("draws", player.draws(), RoyalIcons.Glyph.DRAW, MUTED, false));
            metrics.add(metricCard("streak", player.streak(), RoyalIcons.Glyph.SPARK, PALE, false));
            metrics.add(metricCard("points", player.puzzlePoints(), RoyalIcons.Glyph.PUZZLES, PALE, false));
        }
        metrics.setMaximumSize(new Dimension(Integer.MAX_VALUE, expanded ? 248 : 118));
        return metrics;
    }

    private RoyalCard metricCard(String key, Object value, RoyalIcons.Glyph glyph, Color valueColor, boolean highlight) {
        RoyalCard card = new RoyalCard(new BorderLayout(0, 8));
        if (highlight) card.withGoldAccent();
        card.setBorder(new EmptyBorder(14, 18, 16, 18));

        JPanel top = transparent(new BorderLayout(8, 0));
        JLabel lbl = caption(t(key));
        lbl.setFont(sans(Font.BOLD, 12f));
        JLabel icon = new JLabel(RoyalIcons.icon(glyph, 17, highlight ? GOLD : MUTED));
        top.add(lbl, BorderLayout.WEST);
        top.add(icon, BorderLayout.EAST);

        JLabel val = title(String.valueOf(value), 31);
        val.setForeground(valueColor);
        card.add(top, BorderLayout.NORTH);
        card.add(val, BorderLayout.CENTER);
        return card;
    }

    private RoyalCard gameSummaryCard(ChessStore.Summary saved, boolean showExtraActions) {
        RoyalCard card = new RoyalCard(new BorderLayout(14, 0));
        card.setBorder(new EmptyBorder(13, 16, 13, 16));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));

        boolean vsCpu = saved.options().mode() == MatchOptions.Mode.COMPUTER;
        JPanel iconBadge = new RoyalPanel(new GridBagLayout(), new Color(0x231F18), new Color(0x161411), LINE_GOLD, 11);
        iconBadge.setPreferredSize(new Dimension(42, 42));
        iconBadge.setBorder(new EmptyBorder(0, 0, 0, 0));
        iconBadge.add(new JLabel(RoyalIcons.icon(vsCpu ? RoyalIcons.Glyph.CPU : RoyalIcons.Glyph.PLAYERS, 19, GOLD)));

        String modeText = t("mode." + saved.options().mode())
                + (vsCpu ? "  ·  " + t("level." + saved.options().level()) : "");
        JLabel titleLbl = new JLabel(modeText);
        titleLbl.setForeground(IVORY);
        titleLbl.setFont(sans(Font.BOLD, 14.5f));
        String meta = date(saved.updated()) + "  ·  " + t("halfMoves", saved.plies())
                + (saved.outcome() == null ? "" : "  ·  " + t("reason." + saved.outcome().reason()));
        JPanel info = stack(titleLbl, Box.createVerticalStrut(3), caption(meta));

        JPanel right = transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.add(outcomePill(saved.options(), saved.outcome()));
        if (saved.outcome() == null) {
            right.add(button(t("resume"), RoyalButton.Variant.PRIMARY, RoyalIcons.Glyph.PLAY, () -> openSaved(saved, true)));
        }
        right.add(button(t("replay"), RoyalButton.Variant.SECONDARY, RoyalIcons.Glyph.HISTORY, () -> openSaved(saved, false)));
        if (showExtraActions) {
            right.add(new RoyalIconButton(RoyalIcons.Glyph.EXPORT, t("export"),
                    () -> loadSaved(saved.id(), loaded -> exportSaved(loaded.snapshot(), loaded.session().game()))));
            right.add(new RoyalIconButton(RoyalIcons.Glyph.ABOUT, t("details"), () -> details(saved)));
        }

        card.add(iconBadge, BorderLayout.WEST);
        card.add(info, BorderLayout.CENTER);
        card.add(right, BorderLayout.EAST);
        return card;
    }

    private JLabel outcomePill(MatchOptions options, Outcome outcome) {
        String labelText = resultLabel(options, outcome);
        if (outcome == null) {
            return pill(labelText, new Color(212, 175, 55, 32), new Color(212, 175, 55, 130), PALE);
        }
        if (outcome.winner() == null) {
            return pill(labelText, new Color(150, 150, 165, 30), new Color(160, 160, 175, 110), new Color(0xD8D8E0));
        }
        boolean victory = options.mode() == MatchOptions.Mode.LOCAL || outcome.winner() == options.human();
        if (victory) {
            return pill(labelText, new Color(112, 227, 156, 26), new Color(112, 227, 156, 125), SUCCESS);
        }
        return pill(labelText, new Color(220, 75, 75, 30), new Color(220, 85, 85, 125), DANGER_TEXT);
    }

    private JPanel cardLabel(String message) {
        RoyalCard card = new RoyalCard(new BorderLayout());
        card.setBorder(new EmptyBorder(24, 22, 24, 22));
        card.add(label(message, MUTED), BorderLayout.CENTER);
        return card;
    }

    private void quickPlay() {
        startMatch(new MatchOptions(MatchOptions.Mode.LOCAL, Side.WHITE, MatchOptions.Level.MEDIUM, 0, 0, prefs.theme()));
    }

    private String[] availableThemes() {
        return ThemeManager.available(profile == null ? List.of() : profile.achievements());
    }

    // =========================================================================
    // NEW GAME SCREEN
    // =========================================================================

    private void newGame(MatchOptions.Mode preferred) {
        JPanel page = padded();
        page.add(heading(t("newGame"), t("newGameSubtitle"), null), BorderLayout.NORTH);

        AtomicReference<MatchOptions.Mode> selectedMode = new AtomicReference<>(preferred);
        AtomicReference<String> selectedSide = new AtomicReference<>("side.WHITE");
        AtomicReference<MatchOptions.Level> selectedLevel = new AtomicReference<>(MatchOptions.Level.MEDIUM);
        AtomicReference<String> selectedTheme = new AtomicReference<>(prefs.theme());

        var time = combo(new Integer[]{0, 1, 3, 5, 10, 15, 30, 60}, value -> value == 0 ? t("unlimited") : t("minutes", value));
        time.setSelectedItem(10);
        var increment = combo(new Integer[]{0, 1, 2, 3, 5, 10, 15, 30}, value -> t("seconds", value));
        increment.setSelectedItem(5);

        // Live summary labels
        JLabel sumMode = label("", IVORY), sumSide = label("", IVORY), sumLevel = label("", IVORY),
                sumClock = label("", PALE), sumTheme = label("", IVORY);

        // Mode Cards
        RoyalCard localCard = modeCard(t("mode.LOCAL"), t("modeLocalDesc"), RoyalIcons.Glyph.PLAYERS);
        RoyalCard cpuCard = modeCard(t("mode.COMPUTER"), t("modeComputerDesc"), RoyalIcons.Glyph.CPU);

        // Side segmented buttons
        RoyalButton whiteBtn = new RoyalButton(t("side.WHITE"), RoyalButton.Variant.SEGMENT, null, null);
        RoyalButton randomBtn = new RoyalButton(t("random"), RoyalButton.Variant.SEGMENT, RoyalIcons.Glyph.SPARK, null);
        RoyalButton blackBtn = new RoyalButton(t("side.BLACK"), RoyalButton.Variant.SEGMENT, null, null);

        // AI Level segmented buttons
        RoyalButton[] levelButtons = new RoyalButton[MatchOptions.Level.values().length];
        for (int i = 0; i < levelButtons.length; i++) {
            MatchOptions.Level lvl = MatchOptions.Level.values()[i];
            levelButtons[i] = new RoyalButton(t("level." + lvl), RoyalButton.Variant.SEGMENT, null, null);
        }

        // Time preset pills
        record Preset(int min, int inc, String label) {}
        List<Preset> presets = List.of(
                new Preset(0, 0, t("unlimited")),
                new Preset(3, 2, "3 + 2"),
                new Preset(5, 3, "5 + 3"),
                new Preset(10, 5, "10 + 5"),
                new Preset(15, 10, "15 + 10"),
                new Preset(30, 0, "30 + 0")
        );
        RoyalButton[] presetButtons = new RoyalButton[presets.size()];
        for (int i = 0; i < presets.size(); i++) {
            presetButtons[i] = new RoyalButton(presets.get(i).label(), RoyalButton.Variant.SEGMENT, null, null);
            presetButtons[i].setBorder(new EmptyBorder(7, 10, 7, 10));
        }

        // Theme cards
        String[] themes = availableThemes();
        java.util.Map<String, RoyalCard> themeCards = new java.util.LinkedHashMap<>();

        Runnable syncUi = () -> {
            boolean vsCpu = selectedMode.get() == MatchOptions.Mode.COMPUTER;
            localCard.setSelected(!vsCpu);
            cpuCard.setSelected(vsCpu);

            whiteBtn.setSelectedState(selectedSide.get().equals("side.WHITE"));
            randomBtn.setSelectedState(selectedSide.get().equals("random"));
            blackBtn.setSelectedState(selectedSide.get().equals("side.BLACK"));

            for (int i = 0; i < levelButtons.length; i++) {
                MatchOptions.Level lvl = MatchOptions.Level.values()[i];
                levelButtons[i].setEnabled(vsCpu);
                levelButtons[i].setSelectedState(vsCpu && selectedLevel.get() == lvl);
            }

            int mins = (Integer) time.getSelectedItem();
            int inc = (Integer) increment.getSelectedItem();
            increment.setEnabled(mins != 0);
            for (int i = 0; i < presets.size(); i++) {
                Preset p = presets.get(i);
                presetButtons[i].setSelectedState(p.min() == mins && (mins == 0 || p.inc() == inc));
            }
            themeCards.forEach((id, card) -> card.setSelected(id.equals(selectedTheme.get())));

            sumMode.setText(t("mode." + selectedMode.get()));
            sumSide.setText(t(selectedSide.get()));
            sumLevel.setText(vsCpu ? t("level." + selectedLevel.get()) : "—");
            sumClock.setText(mins == 0 ? t("unlimited") : mins + " min  +" + inc + " s");
            sumTheme.setText(t("theme." + selectedTheme.get()));
        };

        localCard.interactive(() -> { selectedMode.set(MatchOptions.Mode.LOCAL); syncUi.run(); });
        cpuCard.interactive(() -> { selectedMode.set(MatchOptions.Mode.COMPUTER); syncUi.run(); });
        whiteBtn.addActionListener(e -> { selectedSide.set("side.WHITE"); syncUi.run(); });
        randomBtn.addActionListener(e -> { selectedSide.set("random"); syncUi.run(); });
        blackBtn.addActionListener(e -> { selectedSide.set("side.BLACK"); syncUi.run(); });
        for (int i = 0; i < levelButtons.length; i++) {
            MatchOptions.Level lvl = MatchOptions.Level.values()[i];
            levelButtons[i].addActionListener(e -> { selectedLevel.set(lvl); syncUi.run(); });
        }
        for (int i = 0; i < presets.size(); i++) {
            Preset p = presets.get(i);
            presetButtons[i].addActionListener(e -> {
                time.setSelectedItem(p.min());
                increment.setSelectedItem(p.inc());
                syncUi.run();
            });
        }
        time.addActionListener(e -> syncUi.run());
        increment.addActionListener(e -> syncUi.run());

        // Left config column
        JPanel leftCol = stack();

        RoyalCard modeSection = new RoyalCard(new BorderLayout(0, 12));
        modeSection.add(eyebrow(t("mode")), BorderLayout.NORTH);
        JPanel modeRow = transparent(new GridLayout(1, 2, 12, 0));
        modeRow.add(localCard);
        modeRow.add(cpuCard);
        modeSection.add(modeRow, BorderLayout.CENTER);
        leftCol.add(modeSection);
        gap(leftCol, 12);

        RoyalCard sideAndAi = new RoyalCard(new GridLayout(2, 1, 0, 14));
        JPanel sideBlock = transparent(new BorderLayout(0, 8));
        sideBlock.add(eyebrow(t("side")), BorderLayout.NORTH);
        JPanel sideBtns = transparent(new GridLayout(1, 3, 8, 0));
        sideBtns.add(whiteBtn);
        sideBtns.add(randomBtn);
        sideBtns.add(blackBtn);
        sideBlock.add(sideBtns, BorderLayout.CENTER);

        JPanel aiBlock = transparent(new BorderLayout(0, 8));
        aiBlock.add(eyebrow(t("difficulty")), BorderLayout.NORTH);
        JPanel aiBtns = transparent(new GridLayout(1, 4, 8, 0));
        for (RoyalButton b : levelButtons) aiBtns.add(b);
        aiBlock.add(aiBtns, BorderLayout.CENTER);

        sideAndAi.add(sideBlock);
        sideAndAi.add(aiBlock);
        leftCol.add(sideAndAi);
        gap(leftCol, 12);

        RoyalCard clockSection = new RoyalCard(new BorderLayout(0, 12));
        clockSection.add(eyebrow(t("timeControl")), BorderLayout.NORTH);
        JPanel presetGrid = transparent(new GridLayout(1, presets.size(), 6, 0));
        for (RoyalButton pb : presetButtons) presetGrid.add(pb);
        JPanel customRow = transparent(new GridLayout(1, 2, 12, 0));
        customRow.add(labeledControl(t("timeControl"), time));
        customRow.add(labeledControl(t("increment"), increment));
        clockSection.add(stack(presetGrid, Box.createVerticalStrut(12), customRow), BorderLayout.CENTER);
        leftCol.add(clockSection);
        gap(leftCol, 12);

        RoyalCard themeSection = new RoyalCard(new BorderLayout(0, 10));
        themeSection.add(eyebrow(t("theme")), BorderLayout.NORTH);
        JPanel themeRow = transparent(new GridLayout(1, Math.max(2, themes.length), 10, 0));
        for (String id : themes) {
            RoyalCard tc = themeSwatchCard(id, () -> { selectedTheme.set(id); syncUi.run(); });
            themeCards.put(id, tc);
            themeRow.add(tc);
        }
        themeSection.add(themeRow, BorderLayout.CENTER);
        leftCol.add(themeSection);

        // Right match summary & Start CTA card
        RoyalCard summary = new RoyalCard(new BorderLayout(0, 16)).withGoldAccent();
        summary.setPreferredSize(new Dimension(296, 440));
        summary.setBorder(new EmptyBorder(20, 20, 20, 20));

        RoyalArtwork preview = new RoyalArtwork(assets, true);
        preview.setPreferredSize(new Dimension(250, 148));
        JPanel summaryRows = stack(
                eyebrow(t("matchSummary")),
                Box.createVerticalStrut(8),
                divider(),
                Box.createVerticalStrut(12),
                summaryPair(t("mode"), sumMode),
                Box.createVerticalStrut(8),
                summaryPair(t("side"), sumSide),
                Box.createVerticalStrut(8),
                summaryPair(t("difficulty"), sumLevel),
                Box.createVerticalStrut(8),
                summaryPair(t("timeControl"), sumClock),
                Box.createVerticalStrut(8),
                summaryPair(t("theme"), sumTheme));

        RoyalButton startBtn = button(t("start"), RoyalButton.Variant.PRIMARY, RoyalIcons.Glyph.NEW_GAME, () -> {
            Side human = switch (selectedSide.get()) {
                case "side.BLACK" -> Side.BLACK;
                case "random" -> ThreadLocalRandom.current().nextBoolean() ? Side.WHITE : Side.BLACK;
                default -> Side.WHITE;
            };
            long base = (Integer) time.getSelectedItem() * 60_000L;
            startMatch(new MatchOptions(selectedMode.get(), human, selectedLevel.get(), base,
                    base == 0 ? 0 : (Integer) increment.getSelectedItem() * 1000L, selectedTheme.get()));
        });
        startBtn.setBorder(new EmptyBorder(12, 18, 12, 18));
        startBtn.setFont(sans(Font.BOLD, 14.5f));

        summary.add(preview, BorderLayout.NORTH);
        summary.add(summaryRows, BorderLayout.CENTER);
        summary.add(startBtn, BorderLayout.SOUTH);

        JPanel rightWrap = transparent(new BorderLayout());
        rightWrap.add(summary, BorderLayout.NORTH);

        JPanel layout = transparent(new BorderLayout(18, 0));
        JPanel leftWrap = transparent(new BorderLayout());
        leftWrap.add(leftCol, BorderLayout.NORTH);
        layout.add(leftWrap, BorderLayout.CENTER);
        layout.add(rightWrap, BorderLayout.EAST);

        syncUi.run();
        page.add(scroll(layout), BorderLayout.CENTER);
        show(Page.NEW, page);
    }

    private RoyalCard modeCard(String name, String desc, RoyalIcons.Glyph glyph) {
        RoyalCard c = new RoyalCard(new BorderLayout(12, 0));
        c.setBorder(new EmptyBorder(14, 14, 14, 14));
        JLabel icon = new JLabel(RoyalIcons.icon(glyph, 22, GOLD));
        JLabel title = new JLabel(name);
        title.setForeground(IVORY);
        title.setFont(sans(Font.BOLD, 14f));
        c.add(icon, BorderLayout.WEST);
        c.add(stack(title, Box.createVerticalStrut(2), caption(desc)), BorderLayout.CENTER);
        return c;
    }

    private RoyalCard themeSwatchCard(String themeId, Runnable onSelect) {
        RoyalCard card = new RoyalCard(new BorderLayout(12, 0)).interactive(onSelect);
        card.setBorder(new EmptyBorder(11, 14, 11, 14));
        card.setPreferredSize(new Dimension(200, 60));
        JComponent swatch = new JComponent() {
            { setPreferredSize(new Dimension(36, 36)); }
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean slate = ThemeManager.SLATE.equals(themeId);
                g.setColor(slate ? new Color(0x9CB2BE) : new Color(0xDEC9A2));
                g.fillRect(2, 2, 16, 16);
                g.fillRect(18, 18, 16, 16);
                g.setColor(slate ? new Color(0x233849) : new Color(0x221B15));
                g.fillRect(18, 2, 16, 16);
                g.fillRect(2, 18, 16, 16);
                g.setColor(GOLD);
                g.drawRect(1, 1, 33, 33);
                g.dispose();
            }
        };
        JLabel name = new JLabel(t("theme." + themeId));
        name.setForeground(IVORY);
        name.setFont(sans(Font.BOLD, 13.5f));
        JPanel swWrap = transparent(new GridBagLayout());
        swWrap.add(swatch);
        card.add(swWrap, BorderLayout.WEST);
        card.add(name, BorderLayout.CENTER);
        return card;
    }

    private static JPanel labeledControl(String label, JComponent control) {
        JPanel p = transparent(new BorderLayout(0, 5));
        p.add(caption(label), BorderLayout.NORTH);
        p.add(control, BorderLayout.CENTER);
        return p;
    }

    private static JPanel summaryPair(String key, JLabel val) {
        JPanel row = transparent(new BorderLayout(10, 0));
        row.add(caption(key), BorderLayout.WEST);
        val.setFont(sans(Font.BOLD, 13f));
        row.add(val, BorderLayout.EAST);
        return row;
    }

    // =========================================================================
    // LIVE MATCH & RESULT SCREEN
    // =========================================================================

    private void startMatch(MatchOptions options) { live(GameSession.start(user.id(), options, System::nanoTime)); }

    private void live(GameSession session) {
        MatchView live = new MatchView(this, session);
        show(Page.NEW, live, getWidth() < 1180);
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

        Outcome outcome = saved.outcome();
        boolean isDraw = outcome.winner() == null;
        boolean isVictory = !isDraw && (saved.options().mode() == MatchOptions.Mode.LOCAL || outcome.winner() == saved.options().human());
        Color glow = isDraw ? new Color(180, 185, 200, 46)
                : isVictory ? new Color(212, 175, 55, 62)
                : new Color(210, 62, 58, 52);
        Color accent = isDraw ? new Color(0xD8DCE4) : isVictory ? PALE : DANGER_TEXT;
        RoyalIcons.Glyph badgeGlyph = isDraw ? RoyalIcons.Glyph.DRAW : isVictory ? RoyalIcons.Glyph.CROWN : RoyalIcons.Glyph.RESIGN;

        RoyalBackgroundPanel hero = new RoyalBackgroundPanel(new BorderLayout(),
                assets.brand(BrandingManager.DASHBOARD), 20, true, glow);
        hero.setPreferredSize(new Dimension(780, 290));

        JLabel reasonBadge = pill(t("reason." + outcome.reason()),
                new Color(20, 18, 22, 200), new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 140), accent);
        JLabel outcomeTitle = title(resultLabel(saved.options(), outcome), 54);
        outcomeTitle.setForeground(accent);

        JPanel badgeRow = row(new JLabel(RoyalIcons.icon(badgeGlyph, 28, accent)), reasonBadge);
        JPanel actions = transparent(new FlowLayout(FlowLayout.LEFT, 10, 0));
        RoyalButton again = button(t("again"), RoyalButton.Variant.PRIMARY, RoyalIcons.Glyph.NEW_GAME,
                () -> startMatch(saved.options()));
        again.setBorder(new EmptyBorder(10, 20, 10, 20));
        RoyalButton analysis = button(t("analysis"), RoyalButton.Variant.SECONDARY, RoyalIcons.Glyph.ANALYZE,
                () -> openReplay(game, saved, true));
        analysis.setBorder(new EmptyBorder(10, 18, 10, 18));
        RoyalButton backHome = button(t("home"), RoyalButton.Variant.SECONDARY, RoyalIcons.Glyph.HOME, this::dashboard);
        backHome.setBorder(new EmptyBorder(10, 18, 10, 18));
        actions.add(again);
        actions.add(analysis);
        actions.add(backHome);

        JPanel heroText = stack(badgeRow, Box.createVerticalStrut(10), outcomeTitle, Box.createVerticalStrut(24), actions);
        JPanel heroAnchor = transparent(new GridBagLayout());
        heroAnchor.setBorder(new EmptyBorder(24, 36, 24, 24));
        GridBagConstraints hc = new GridBagConstraints();
        hc.anchor = GridBagConstraints.WEST;
        heroAnchor.add(heroText, hc);
        hero.add(heroAnchor, BorderLayout.WEST);

        JPanel metrics = transparent(new GridLayout(1, 3, 14, 0));
        metrics.add(metricCard("gameTime", clock(saved.elapsedMillis()), RoyalIcons.Glyph.CLOCK, IVORY, false));
        metrics.add(metricCard("moves", t("halfMoves", game.plyCount()), RoyalIcons.Glyph.HISTORY, IVORY, false));
        String ratingVal = receipt == null ? "—" : receipt.before() == receipt.after()
                ? String.valueOf(receipt.after()) : receipt.before() + " → " + receipt.after();
        metrics.add(metricCard("rating", ratingVal, RoyalIcons.Glyph.CROWN, PALE, true));

        JPanel content = transparent(new BorderLayout(0, 18));
        content.add(hero, BorderLayout.CENTER);
        content.add(metrics, BorderLayout.SOUTH);
        page.add(content, BorderLayout.CENTER);
        if (receipt == null) {
            page.add(button(t("save"), RoyalButton.Variant.PRIMARY, RoyalIcons.Glyph.SAVE, () ->
                    save(saved, updated -> resultPage(saved, game, updated), failure -> error("saveError", failure))), BorderLayout.SOUTH);
        }
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

    // =========================================================================
    // GAME HISTORY SCREEN (PREMIUM CARD LIST)
    // =========================================================================

    private void history() {
        long owner = user.id();
        load(Page.HISTORY, "history", () -> store.history(owner), this::historyView);
    }

    private void historyView(List<ChessStore.Summary> data) {
        JPanel page = padded();
        page.add(heading(t("history"), t("historySubtitle"),
                button(t("import"), RoyalButton.Variant.SECONDARY, RoyalIcons.Glyph.IMPORT, this::importPgn)), BorderLayout.NORTH);

        RoyalTextField search = new RoyalTextField(t("search"), RoyalIcons.Glyph.SEARCH);
        var filter = combo(new String[]{"all", "inProgress", "victory", "defeat", "draw", "mode.LOCAL", "mode.COMPUTER"}, this::t);

        RoyalCard toolbar = new RoyalCard(new BorderLayout(12, 0));
        toolbar.setBorder(new EmptyBorder(12, 16, 12, 16));
        toolbar.add(search, BorderLayout.CENTER);
        toolbar.add(filter, BorderLayout.EAST);

        JPanel listPanel = stack();
        Runnable refresh = () -> {
            listPanel.removeAll();
            String query = search.getText().strip().toLowerCase(text.locale());
            String chosen = (String) filter.getSelectedItem();
            int count = 0;
            for (var game : data) {
                String kind = t("mode." + game.options().mode());
                String result = resultLabel(game.options(), game.outcome());
                String when = date(game.updated());
                String reason = game.outcome() == null ? "" : t("reason." + game.outcome().reason());
                if (!chosen.equals("all") && !t(chosen).equals(kind) && !t(chosen).equals(result)) continue;
                if (!(when + " " + kind + " " + result + " " + reason).toLowerCase(text.locale()).contains(query)) continue;
                listPanel.add(gameSummaryCard(game, true));
                gap(listPanel, 8);
                count++;
            }
            if (count == 0) {
                listPanel.add(cardLabel(t("emptyHistory")));
            }
            listPanel.revalidate();
            listPanel.repaint();
        };
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { refresh.run(); }
            @Override public void removeUpdate(DocumentEvent event) { refresh.run(); }
            @Override public void changedUpdate(DocumentEvent event) { refresh.run(); }
        });
        filter.addActionListener(event -> refresh.run());
        refresh.run();

        JPanel content = transparent(new BorderLayout(0, 14));
        content.add(toolbar, BorderLayout.NORTH);
        content.add(scroll(listPanel), BorderLayout.CENTER);
        page.add(content, BorderLayout.CENTER);
        show(Page.HISTORY, page);
    }

    private void details(ChessStore.Summary item) {
        String info = date(item.updated()) + "\n" + t("mode." + item.options().mode()) + "\n"
                + resultLabel(item.options(), item.outcome()) + "\n"
                + (item.outcome() == null ? "" : t("reason." + item.outcome().reason()) + "\n")
                + t("halfMoves", item.plies());
        List<String> actions = item.outcome() == null ? List.of(t("resume"), t("replay"), t("export"))
                : List.of(t("replay"), t("export"));
        String choice = RoyalDialog.choose(this, t("details"), info, actions, t("cancel"));
        if (choice == null) return;
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
        show(Page.HISTORY, view, true);
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
            show(Page.HISTORY, view, true);
            replay = view;
        }, failure -> { if (token == generation) error("fileError", failure); });
    }

    private static JFileChooser pgnChooser() {
        var chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PGN (*.pgn)", "pgn"));
        return chooser;
    }

    // =========================================================================
    // PUZZLES & PUZZLE HISTORY
    // =========================================================================

    void puzzles() { navigate(this::puzzleList); }

    private void puzzleList() {
        long owner = user.id();
        load(Page.PUZZLES, "puzzles", () -> store.profile(owner), data -> {
            profile = data;
            JPanel page = padded();
            AtomicReference<String> selectedFilter = new AtomicReference<>("all");

            JLabel ptsPill = pill(t("points") + ": " + data.puzzlePoints(),
                    new Color(212, 175, 55, 32), new Color(212, 175, 55, 130), PALE);
            page.add(heading(t("puzzles"), t("puzzlesSubtitle"),
                    row(ptsPill, button(t("puzzleHistory"), RoyalButton.Variant.SECONDARY, RoyalIcons.Glyph.HISTORY, this::puzzleHistory))),
                    BorderLayout.NORTH);

            String[] filters = {"all", "level.EASY", "level.MEDIUM", "level.HARD"};
            RoyalButton[] filterBtns = new RoyalButton[filters.length];
            JPanel filterBar = transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
            JPanel grid = transparent(new GridLayout(0, 2, 14, 14));

            Runnable draw = () -> {
                for (int i = 0; i < filters.length; i++) {
                    filterBtns[i].setSelectedState(filters[i].equals(selectedFilter.get()));
                }
                grid.removeAll();
                for (Puzzles.Puzzle entry : Puzzles.catalog()) {
                    if (!selectedFilter.get().equals("all") && !selectedFilter.get().equals("level." + entry.level())) continue;
                    grid.add(puzzleCard(entry));
                }
                grid.revalidate();
                grid.repaint();
            };

            for (int i = 0; i < filters.length; i++) {
                String key = filters[i];
                filterBtns[i] = new RoyalButton(t(key), RoyalButton.Variant.SEGMENT, null, () -> {
                    selectedFilter.set(key);
                    draw.run();
                });
                filterBar.add(filterBtns[i]);
            }
            draw.run();

            JPanel wrap = transparent(new BorderLayout(0, 14));
            wrap.add(filterBar, BorderLayout.NORTH);
            JPanel gridHolder = transparent(new BorderLayout());
            gridHolder.add(grid, BorderLayout.NORTH);
            wrap.add(scroll(gridHolder), BorderLayout.CENTER);
            page.add(wrap, BorderLayout.CENTER);
            show(Page.PUZZLES, page);
        });
    }

    private RoyalCard puzzleCard(Puzzles.Puzzle entry) {
        RoyalCard card = new RoyalCard(new BorderLayout(0, 12)).withGoldAccent();
        card.setBorder(new EmptyBorder(18, 20, 18, 20));
        Side turn = entry.game().position().turn();

        JPanel top = transparent(new BorderLayout(8, 0));
        top.add(pill(t("level." + entry.level()), new Color(212, 175, 55, 28), new Color(212, 175, 55, 115), PALE), BorderLayout.WEST);
        top.add(pill("+" + entry.points() + " pts", new Color(0x211E24), LINE_GOLD, IVORY), BorderLayout.EAST);

        JPanel middle = transparent(new BorderLayout(12, 0));
        JComponent pieceArt = new JComponent() {
            { setPreferredSize(new Dimension(44, 52)); }
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                assets.drawPieceFitted(g, Piece.of(turn, Kind.KNIGHT), 2, 2, getWidth() - 4, getHeight() - 4);
                g.dispose();
            }
        };
        middle.add(pieceArt, BorderLayout.WEST);
        middle.add(stack(
                title(t("puzzle." + entry.id()), 22),
                Box.createVerticalStrut(4),
                caption(t("mateOne") + "  ·  " + t("turn", t("side." + turn)))), BorderLayout.CENTER);

        RoyalButton solveBtn = button(t("startPuzzle"), RoyalButton.Variant.PRIMARY, RoyalIcons.Glyph.PUZZLES,
                () -> openPuzzle(entry));
        card.add(top, BorderLayout.NORTH);
        card.add(middle, BorderLayout.CENTER);
        card.add(solveBtn, BorderLayout.SOUTH);
        return card;
    }

    void openPuzzle(Puzzles.Puzzle selected) {
        var view = new PuzzleView(this, selected);
        show(Page.PUZZLES, view, true);
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
            page.add(heading(t("puzzleHistory"), null,
                    button(t("back"), RoyalButton.Variant.SECONDARY, RoyalIcons.Glyph.PREV, this::puzzleList)), BorderLayout.NORTH);
            JPanel list = stack();
            if (attempts.isEmpty()) {
                list.add(cardLabel(t("emptyPuzzleHistory")));
            } else {
                for (var attempt : attempts) {
                    RoyalCard card = new RoyalCard(new BorderLayout(12, 0));
                    card.setBorder(new EmptyBorder(13, 18, 13, 18));
                    card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 68));
                    JLabel name = new JLabel(t("puzzle." + attempt.puzzleId()));
                    name.setForeground(IVORY);
                    name.setFont(sans(Font.BOLD, 14.5f));
                    String sub = date(attempt.date()) + (attempt.hinted() ? "  ·  " + t("puzzleHint") : "");
                    card.add(stack(name, Box.createVerticalStrut(2), caption(sub)), BorderLayout.CENTER);
                    JPanel right = rowRight(
                            pill(t(attempt.solved() ? "puzzleSolved" : "puzzleMiss"),
                                    attempt.solved() ? new Color(112, 227, 156, 26) : new Color(220, 75, 75, 28),
                                    attempt.solved() ? new Color(112, 227, 156, 125) : new Color(220, 85, 85, 125),
                                    attempt.solved() ? SUCCESS : DANGER_TEXT),
                            pill("+" + attempt.points(), new Color(0x211E24), LINE_GOLD, PALE));
                    card.add(right, BorderLayout.EAST);
                    list.add(card);
                    gap(list, 8);
                }
            }
            page.add(scroll(list), BorderLayout.CENTER);
            show(Page.PUZZLES, page);
        });
    }

    // =========================================================================
    // LEADERBOARD, STATISTICS & ACHIEVEMENTS
    // =========================================================================

    private void leaderboard() {
        load(Page.LEADERBOARD, "leaderboard", store::leaderboard, players -> {
            JPanel page = padded();
            page.add(heading(t("leaderboard"), t("leaderboardSubtitle"), null), BorderLayout.NORTH);
            JPanel list = stack();
            if (players.isEmpty()) {
                list.add(cardLabel(t("noProfiles")));
            } else {
                int rank = 1;
                for (var player : players) {
                    RoyalCard card = new RoyalCard(new BorderLayout(16, 0));
                    if (rank == 1) card.withGoldAccent();
                    card.setBorder(new EmptyBorder(14, 18, 14, 18));
                    card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 74));

                    Color medalColor = rank == 1 ? GOLD : rank == 2 ? new Color(0xD2D6DE)
                            : rank == 3 ? new Color(0xCD8C52) : MUTED;
                    JLabel rankBadge = pill("#" + rank, new Color(0x201E24),
                            new Color(medalColor.getRed(), medalColor.getGreen(), medalColor.getBlue(), 145), medalColor);
                    rankBadge.setFont(mono(Font.BOLD, 14f));

                    int rate = player.played() == 0 ? 0 : (int) Math.round(100d * player.wins() / player.played());
                    JLabel name = new JLabel(player.user().displayName());
                    name.setForeground(IVORY);
                    name.setFont(sans(Font.BOLD, 15f));
                    JLabel sub = caption(t("played") + ": " + player.played() + "  ·  " + t("wins") + ": " + player.wins()
                            + "  ·  " + t("winRate") + ": " + rate + "%");

                    JPanel scoreCol = transparent(new BorderLayout(0, 2));
                    JLabel ratingVal = title(String.valueOf(player.rating()), 26);
                    ratingVal.setForeground(rank <= 3 ? PALE : IVORY);
                    ratingVal.setHorizontalAlignment(SwingConstants.RIGHT);
                    JLabel ratingLbl = caption(t("rating"));
                    ratingLbl.setHorizontalAlignment(SwingConstants.RIGHT);
                    scoreCol.add(ratingVal, BorderLayout.NORTH);
                    scoreCol.add(ratingLbl, BorderLayout.SOUTH);

                    card.add(rankBadge, BorderLayout.WEST);
                    card.add(stack(name, Box.createVerticalStrut(3), sub), BorderLayout.CENTER);
                    card.add(scoreCol, BorderLayout.EAST);
                    list.add(card);
                    gap(list, 8);
                    rank++;
                }
            }
            page.add(scroll(list), BorderLayout.CENTER);
            show(Page.LEADERBOARD, page);
        });
    }

    private void statistics() {
        long owner = user.id();
        load(Page.STATISTICS, "statistics", () -> store.profile(owner), player -> {
            profile = player;
            JPanel page = padded();
            page.add(heading(t("statistics"), t("statsOverview"), null), BorderLayout.NORTH);
            JPanel metrics = metricGrid(player, true);

            // Visual W/D/L Outcome Breakdown Card
            RoyalCard breakdown = new RoyalCard(new BorderLayout(0, 12)).withGoldAccent();
            breakdown.setBorder(new EmptyBorder(18, 20, 18, 20));
            breakdown.setMaximumSize(new Dimension(Integer.MAX_VALUE, 138));
            breakdown.add(eyebrow(t("statsBreakdown")), BorderLayout.NORTH);

            int total = Math.max(0, player.wins() + player.draws() + player.losses());
            JComponent bar = new JComponent() {
                { setPreferredSize(new Dimension(400, 22)); }
                @Override protected void paintComponent(Graphics graphics) {
                    Graphics2D g = (Graphics2D) graphics.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int w = getWidth(), h = 14, y = (getHeight() - h) / 2;
                    g.setColor(new Color(0x211F25));
                    g.fillRoundRect(0, y, w, h, h, h);
                    if (total > 0) {
                        int ww = (int) Math.round(w * (double) player.wins() / total);
                        int dw = (int) Math.round(w * (double) player.draws() / total);
                        int lw = w - ww - dw;
                        Shape old = g.getClip();
                        g.setClip(new java.awt.geom.RoundRectangle2D.Double(0, y, w, h, h, h));
                        g.setColor(SUCCESS); g.fillRect(0, y, ww, h);
                        g.setColor(new Color(0x9BA3B4)); g.fillRect(ww, y, dw, h);
                        g.setColor(DANGER_TEXT); g.fillRect(ww + dw, y, lw, h);
                        g.setClip(old);
                    }
                    g.setColor(LINE);
                    g.drawRoundRect(0, y, w - 1, h - 1, h, h);
                    g.dispose();
                }
            };
            JPanel legend = row(
                    legendDot(SUCCESS, t("wins") + ": " + player.wins()),
                    legendDot(new Color(0x9BA3B4), t("draws") + ": " + player.draws()),
                    legendDot(DANGER_TEXT, t("losses") + ": " + player.losses()));
            breakdown.add(bar, BorderLayout.CENTER);
            breakdown.add(legend, BorderLayout.SOUTH);

            JPanel container = stack(metrics, Box.createVerticalStrut(18), breakdown);
            page.add(scroll(container), BorderLayout.CENTER);
            show(Page.STATISTICS, page);
        });
    }

    private static JPanel legendDot(Color color, String text) {
        JPanel p = transparent(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JComponent dot = new JComponent() {
            { setPreferredSize(new Dimension(10, 10)); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fillOval(0, 0, 10, 10);
                g2.dispose();
            }
        };
        p.add(dot);
        p.add(caption(text));
        return p;
    }

    private void achievements() {
        long owner = user.id();
        load(Page.ACHIEVEMENTS, "achievements", () -> store.profile(owner), player -> {
            profile = player;
            JPanel page = padded();
            int unlockedCount = player.achievements().size();
            page.add(heading(t("achievements"), t("achievementsSubtitle"),
                    pill(unlockedCount + " / " + ALL_ACHIEVEMENTS.size() + "  " + t("unlocked"),
                            new Color(212, 175, 55, 32), new Color(212, 175, 55, 130), PALE)), BorderLayout.NORTH);

            JPanel grid = transparent(new GridLayout(0, 2, 14, 14));
            for (String badge : ALL_ACHIEVEMENTS) {
                boolean earned = player.achievements().contains(badge);
                RoyalCard card = new RoyalCard(new BorderLayout(14, 0));
                if (earned) card.withGoldAccent();
                card.setBorder(new EmptyBorder(16, 18, 16, 18));

                RoyalPanel emblem = new RoyalPanel(new GridBagLayout(),
                        earned ? new Color(0x2F2616) : new Color(0x18171B),
                        earned ? new Color(0x1B160D) : new Color(0x121115),
                        earned ? GOLD : LINE, 12);
                emblem.setPreferredSize(new Dimension(48, 48));
                emblem.setBorder(new EmptyBorder(0, 0, 0, 0));
                emblem.add(new JLabel(RoyalIcons.icon(earned ? RoyalIcons.Glyph.TROPHY : RoyalIcons.Glyph.LOCK,
                        22, earned ? GOLD : new Color(0x5B554C))));

                JLabel titleLbl = title(t("achievement." + badge), 19);
                titleLbl.setForeground(earned ? IVORY : new Color(0x8C867C));
                JLabel desc = caption(t("achievementDesc." + badge));
                JPanel rightCol = transparent(new BorderLayout());
                JLabel statePill = earned
                        ? pill(t("unlocked"), new Color(212, 175, 55, 34), new Color(212, 175, 55, 130), PALE)
                        : pill(t("locked"), new Color(0x1D1C20), LINE, MUTED);
                rightCol.add(statePill, BorderLayout.NORTH);
                if (badge.equals("puzzle-master")) {
                    JLabel reward = caption(t("theme.royal-slate"));
                    reward.setForeground(earned ? PALE : MUTED);
                    rightCol.add(reward, BorderLayout.SOUTH);
                }

                card.add(emblem, BorderLayout.WEST);
                card.add(stack(titleLbl, Box.createVerticalStrut(4), desc), BorderLayout.CENTER);
                card.add(rightCol, BorderLayout.EAST);
                grid.add(card);
            }

            JPanel holder = transparent(new BorderLayout());
            holder.add(grid, BorderLayout.NORTH);
            page.add(scroll(holder), BorderLayout.CENTER);
            show(Page.ACHIEVEMENTS, page);
        });
    }

    // =========================================================================
    // SETTINGS SCREEN
    // =========================================================================

    private void settings() {
        long owner = user.id();
        load(Page.SETTINGS, "settings", () -> store.profile(owner), player -> {
            profile = player;
            settingsForm();
        });
    }

    private void settingsForm() {
        JPanel page = padded();
        page.add(heading(t("settings"), t("settingsSubtitle"), null), BorderLayout.NORTH);

        JComboBox<String> language = combo(new String[]{"en", "vi"}, code -> t(code.equals("en") ? "english" : "vietnamese"));
        JComboBox<String> theme = combo(availableThemes(), id -> t("theme." + id));
        JComboBox<Preferences.Orientation> orientation = combo(Preferences.Orientation.values(), value -> t("orientation." + value));
        JComboBox<Preferences.ViewMode> viewMode = combo(Preferences.ViewMode.values(), value -> t("viewMode." + value));
        JComboBox<Preferences.GraphicsQuality> graphicsQuality = combo(Preferences.GraphicsQuality.values(), value -> t("quality." + value));
        RoyalToggle sound = new RoyalToggle(t("sound"), prefs.sound());
        RoyalToggle animation = new RoyalToggle(t("animation"), prefs.animation());
        RoyalToggle quality = new RoyalToggle(t("quality"), prefs.highQuality());
        RoyalToggle autosave = new RoyalToggle(t("autosave"), prefs.autosave());

        JSlider scaleSlider = new JSlider(70, 105, prefs.pieceScale());
        scaleSlider.setOpaque(false);
        JLabel scaleReadout = pill(prefs.pieceScale() + "%", new Color(212, 175, 55, 30), new Color(212, 175, 55, 120), PALE);
        scaleSlider.addChangeListener(e -> scaleReadout.setText(scaleSlider.getValue() + "%"));

        language.setSelectedItem(prefs.language());
        theme.setSelectedItem(prefs.theme());
        orientation.setSelectedItem(prefs.orientation());
        viewMode.setSelectedItem(prefs.viewMode());
        graphicsQuality.setSelectedItem(prefs.graphicsQuality());

        // Section 1: Board & Visuals
        RoyalCard appearance = new RoyalCard(new BorderLayout(0, 14)).withGoldAccent();
        appearance.setBorder(new EmptyBorder(18, 22, 20, 22));
        appearance.add(stack(eyebrow(t("settingsAppearance")), Box.createVerticalStrut(6), divider()), BorderLayout.NORTH);
        JPanel visualGrid = transparent(new GridLayout(0, 2, 18, 14));
        visualGrid.add(labeledControl(t("language"), language));
        visualGrid.add(labeledControl(t("theme"), theme));
        visualGrid.add(labeledControl(t("viewMode"), viewMode));
        visualGrid.add(labeledControl(t("graphicsQuality"), graphicsQuality));
        visualGrid.add(labeledControl(t("orientation"), orientation));
        JPanel scaleBox = transparent(new BorderLayout(10, 0));
        scaleBox.add(scaleSlider, BorderLayout.CENTER);
        JPanel scalePillWrap = transparent(new GridBagLayout());
        scalePillWrap.add(scaleReadout);
        scaleBox.add(scalePillWrap, BorderLayout.EAST);
        visualGrid.add(labeledControl(t("scale"), scaleBox));
        appearance.add(visualGrid, BorderLayout.CENTER);
        JPanel qualityRow = transparent(new FlowLayout(FlowLayout.LEFT, 0, 4));
        qualityRow.add(quality);
        appearance.add(qualityRow, BorderLayout.SOUTH);

        // Section 2: Gameplay & Behavior
        RoyalCard gameplay = new RoyalCard(new BorderLayout(0, 14));
        gameplay.setBorder(new EmptyBorder(18, 22, 20, 22));
        gameplay.add(stack(eyebrow(t("settingsGameplay")), Box.createVerticalStrut(6), divider()), BorderLayout.NORTH);
        JPanel toggles = transparent(new GridLayout(0, 1, 0, 12));
        toggles.add(sound);
        toggles.add(animation);
        toggles.add(autosave);
        gameplay.add(toggles, BorderLayout.CENTER);

        // Action Bar
        JPanel actions = transparent(new FlowLayout(FlowLayout.LEFT, 10, 0));
        RoyalButton applyBtn = button(t("apply"), RoyalButton.Variant.PRIMARY, RoyalIcons.Glyph.CHECK, () ->
                savePrefs(new Preferences((String) language.getSelectedItem(),
                        (String) theme.getSelectedItem(), sound.isSelected(), animation.isSelected(),
                        (Preferences.Orientation) orientation.getSelectedItem(), scaleSlider.getValue(),
                        quality.isSelected(), autosave.isSelected(),
                        (Preferences.ViewMode) viewMode.getSelectedItem(),
                        (Preferences.GraphicsQuality) graphicsQuality.getSelectedItem())));
        applyBtn.setBorder(new EmptyBorder(10, 22, 10, 22));
        RoyalButton resetBtn = button(t("reset"), RoyalButton.Variant.SECONDARY, RoyalIcons.Glyph.UNDO,
                () -> savePrefs(Preferences.defaults(authLanguage)));
        resetBtn.setBorder(new EmptyBorder(10, 18, 10, 18));
        actions.add(applyBtn);
        actions.add(resetBtn);

        JPanel sections = stack(appearance, Box.createVerticalStrut(14), gameplay, Box.createVerticalStrut(16), actions);
        sections.setMaximumSize(new Dimension(860, Integer.MAX_VALUE));
        JPanel container = transparent(new BorderLayout());
        container.add(sections, BorderLayout.NORTH);
        page.add(scroll(container), BorderLayout.CENTER);
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

    // =========================================================================
    // FORM HELPERS & DIALOGS
    // =========================================================================

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
        return RoyalDialog.confirm(this, getTitle(), t(key), t("confirm"), t("cancel"), false);
    }

    void error(String key, Throwable failure) {
        LoggerFactory.getLogger(RoyalFrame.class).error(key, failure);
        RoyalDialog.message(this, t("error"), t(key), t("close"), true);
    }

    private void about() {
        RoyalDialog.about(this, branding, text, engines.stockfishDetected());
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
        if (online != null) online.close();
        generation++;
        if (match != null) match.close();
        if (replay != null) replay.close();
        jobs.close();
        sounds.close();
        dispose();
    }

    // =========================================================================
    // VISUAL QA / SMOKE HOOKS (DRIVES THE REAL UI SCREENS)
    // =========================================================================

    void visualGuest() { enter(new LoginData(new ChessStore.User(ChessStore.GUEST_ID, "@guest", "Guest"), prefs)); }

    void visualMatch() {
        var game = GameSession.start(user.id(), new MatchOptions(MatchOptions.Mode.COMPUTER, Side.WHITE,
                MatchOptions.Level.HARD, 600_000, 5000, "royal"), System::nanoTime);
        game.resume();
        for (String move : "g1f3 g8f6 f3g5 f6g8 g5e4 a7a6".split(" ")) game.play(Move.uci(move));
        game.pause();
        live(game);
        if (match != null) {
            match.setFocusMode(false, false);
            match.boardPanel().selectSquare(Square.at(4, 3));
        }
    }

    void visualFocusMatch() {
        var game = GameSession.start(user.id(), new MatchOptions(MatchOptions.Mode.COMPUTER, Side.WHITE,
                MatchOptions.Level.HARD, 600_000, 5000, "royal"), System::nanoTime);
        game.resume();
        for (String move : "e2e4 d7d6".split(" ")) game.play(Move.uci(move));
        game.pause();
        live(game);
        if (match != null) {
            match.setFocusMode(true, false);
            match.boardPanel().selectSquare(Square.at(4, 3)); // e4 pawn selected with e5 legal target highlighted
        }
    }

    void visualNewGame() { newGame(MatchOptions.Mode.COMPUTER); }
    void visualPuzzles() { puzzleList(); }
    void visualPuzzlePlay() { openPuzzle(Puzzles.catalog().get(0)); }
    void visualHistory() { history(); }
    void visualLeaderboard() { leaderboard(); }
    void visualStatistics() { statistics(); }
    void visualAchievements() { achievements(); }
    void visualSettings() { settings(); }

    void visualResult() {
        var game = GameSession.start(user.id(), new MatchOptions(MatchOptions.Mode.COMPUTER, Side.WHITE,
                MatchOptions.Level.HARD, 600_000, 5000, "royal"), System::nanoTime);
        game.resume();
        for (String move : "f2f3 e7e5 g2g4 d8h4".split(" ")) game.play(Move.uci(move));
        // Build a victory sample using Scholar's mate for visual verification
        var winGame = GameSession.start(user.id(), new MatchOptions(MatchOptions.Mode.COMPUTER, Side.WHITE,
                MatchOptions.Level.MEDIUM, 600_000, 5000, "royal"), System::nanoTime);
        winGame.resume();
        for (String move : "e2e4 e7e5 f1c4 b8c6 d1h5 g8f6 h5f7".split(" ")) winGame.play(Move.uci(move));
        winGame.pause();
        resultPage(winGame.snapshot(), winGame.game(), new Receipt(1200, 1224));
    }

    JDialog visualAboutDialog() {
        return RoyalDialog.createAboutDialog(this, branding, text, engines.stockfishDetected());
    }
}
