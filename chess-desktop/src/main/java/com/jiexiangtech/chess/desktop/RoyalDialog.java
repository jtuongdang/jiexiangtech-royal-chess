package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import com.jiexiangtech.chess.domain.Kind;
import com.jiexiangtech.chess.domain.Piece;
import com.jiexiangtech.chess.domain.Side;
import com.jiexiangtech.chess.online.RoyalAvatar;
import static com.jiexiangtech.chess.desktop.RoyalStyle.*;

final class RoyalDialog extends JDialog {
    private RoyalDialog(Window owner, String title) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private void buildShell(String title, Component body, Component footer) {
        JPanel shell = new JPanel(new BorderLayout(0, 14)) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                g.setPaint(new GradientPaint(0, 0, new Color(0x1B191F), 0, h, new Color(0x100F13)));
                g.fillRoundRect(0, 0, w, h, 20, 20);
                // Top gold sheen line
                g.setPaint(new GradientPaint(24, 0, new Color(212, 175, 55, 25), w / 2f, 0,
                        new Color(242, 215, 139, 195), true));
                g.fillRect(24, 1, Math.max(0, w - 48), 2);
                g.setColor(LINE_GOLD);
                g.drawRoundRect(0, 0, w - 1, h - 1, 20, 20);
                g.dispose();
            }
        };
        shell.setOpaque(false);
        shell.setBorder(new EmptyBorder(22, 26, 20, 26));

        JPanel head = transparent(new BorderLayout(0, 10));
        head.add(title(title, 24), BorderLayout.NORTH);
        head.add(divider(), BorderLayout.SOUTH);
        shell.add(head, BorderLayout.NORTH);
        shell.add(body, BorderLayout.CENTER);
        if (footer != null) shell.add(footer, BorderLayout.SOUTH);

        setContentPane(shell);
        pack();
        setLocationRelativeTo(getOwner());
    }

    static boolean confirm(Component parent, String title, String message, String confirmLabel, String cancelLabel, boolean destructive) {
        AtomicReference<Boolean> accepted = new AtomicReference<>(false);
        Window owner = SwingUtilities.getWindowAncestor(parent);
        RoyalDialog dialog = new RoyalDialog(owner, title);
        JLabel text = new JLabel("<html><div style='width:340px;'>" + escape(message) + "</div></html>");
        text.setForeground(IVORY);
        text.setFont(sans(Font.PLAIN, 14.5f));
        JPanel buttons = transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(new RoyalButton(cancelLabel, RoyalButton.Variant.SECONDARY, null, dialog::dispose));
        buttons.add(new RoyalButton(confirmLabel, destructive ? RoyalButton.Variant.DANGER : RoyalButton.Variant.PRIMARY, null, () -> {
            accepted.set(true);
            dialog.dispose();
        }));
        dialog.buildShell(title, text, buttons);
        dialog.setVisible(true);
        return Boolean.TRUE.equals(accepted.get());
    }

    static void message(Component parent, String title, String message, String okLabel, boolean error) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        RoyalDialog dialog = new RoyalDialog(owner, title);
        JLabel text = new JLabel("<html><div style='width:340px;'>" + escape(message) + "</div></html>");
        text.setForeground(error ? ERROR_TEXT : IVORY);
        text.setFont(sans(Font.PLAIN, 14.5f));
        JPanel buttons = transparent(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.add(new RoyalButton(okLabel, RoyalButton.Variant.PRIMARY, null, dialog::dispose));
        dialog.buildShell(title, text, buttons);
        dialog.setVisible(true);
    }

    static JDialog avatarPicker(RoyalFrame parent, RoyalAvatar current,
                                java.util.function.Consumer<RoyalAvatar> save) {
        var text = parent.text();
        String heading = text.get("avatarChoose");
        RoyalDialog dialog = new RoyalDialog(parent, heading);
        var selected = new AtomicReference<>(current);
        var group = new ButtonGroup();
        JPanel portraits = transparent(new GridLayout(0, 3, 10, 10));
        for (RoyalAvatar avatar : RoyalAvatar.values()) {
            JToggleButton choice = new JToggleButton(text.get("avatar." + avatar.id()),
                    new AvatarIcon(parent.assets(), avatar, Side.WHITE, 78));
            choice.setVerticalTextPosition(SwingConstants.BOTTOM);
            choice.setHorizontalTextPosition(SwingConstants.CENTER);
            choice.setMargin(new Insets(6, 8, 8, 8));
            choice.setSelected(avatar == current);
            choice.addActionListener(event -> selected.set(avatar));
            group.add(choice);
            portraits.add(choice);
        }
        JPanel buttons = transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(new RoyalButton(text.get("cancel"), RoyalButton.Variant.SECONDARY, null, dialog::dispose));
        var confirm = new RoyalButton(text.get("save"), RoyalButton.Variant.PRIMARY, null, () -> {
            dialog.dispose();
            save.accept(selected.get());
        });
        buttons.add(confirm);
        dialog.buildShell(heading, portraits, buttons);
        dialog.getRootPane().setDefaultButton(confirm);
        return dialog;
    }

    static boolean onlineOutcome(Component parent, String heading, String resultLabel, String reasonLabel,
                                 String playersLine, String movesLine, Color accent,
                                 String reviewLabel, String lobbyLabel) {
        AtomicReference<Boolean> goLobby = new AtomicReference<>(false);
        Window owner = SwingUtilities.getWindowAncestor(parent);
        RoyalDialog dialog = new RoyalDialog(owner, heading);

        JLabel badge = pill(reasonLabel, new Color(20, 18, 22, 220),
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 150), accent);
        JLabel hero = title(resultLabel, 34);
        hero.setForeground(accent);
        JLabel players = label(playersLine, IVORY);
        players.setFont(sans(Font.PLAIN, 14f));
        JLabel moves = label(movesLine, MUTED);
        moves.setFont(sans(Font.PLAIN, 13f));

        JPanel body = transparent(null);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        for (JComponent c : new JComponent[]{badge, hero, players, moves}) c.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(badge);
        body.add(Box.createVerticalStrut(10));
        body.add(hero);
        body.add(Box.createVerticalStrut(10));
        body.add(players);
        body.add(Box.createVerticalStrut(4));
        body.add(moves);
        body.setPreferredSize(new Dimension(360, body.getPreferredSize().height + 6));

        JPanel buttons = transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(new RoyalButton(reviewLabel, RoyalButton.Variant.SECONDARY, null, dialog::dispose));
        buttons.add(new RoyalButton(lobbyLabel, RoyalButton.Variant.PRIMARY, null, () -> {
            goLobby.set(true);
            dialog.dispose();
        }));
        dialog.buildShell(heading, body, buttons);
        dialog.setVisible(true);
        return Boolean.TRUE.equals(goLobby.get());
    }

    static String choose(Component parent, String title, String subtitle, List<String> options, String cancelLabel) {
        AtomicReference<String> picked = new AtomicReference<>();
        Window owner = SwingUtilities.getWindowAncestor(parent);
        RoyalDialog dialog = new RoyalDialog(owner, title);
        JPanel content = transparent(new BorderLayout(0, 12));
        if (subtitle != null && !subtitle.isBlank()) {
            JLabel sub = new JLabel("<html><div style='width:330px;'>" + escape(subtitle) + "</div></html>");
            sub.setForeground(MUTED);
            sub.setFont(sans(Font.PLAIN, 13.5f));
            content.add(sub, BorderLayout.NORTH);
        }
        JPanel list = transparent(new GridLayout(0, 1, 0, 8));
        for (int i = 0; i < options.size(); i++) {
            String option = options.get(i);
            RoyalButton btn = new RoyalButton(option, i == 0 ? RoyalButton.Variant.PRIMARY : RoyalButton.Variant.SECONDARY, null, () -> {
                picked.set(option);
                dialog.dispose();
            });
            btn.setPreferredSize(new Dimension(320, 40));
            list.add(btn);
        }
        content.add(list, BorderLayout.CENTER);
        JPanel footer = transparent(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.add(new RoyalButton(cancelLabel, RoyalButton.Variant.GHOST, null, dialog::dispose));
        dialog.buildShell(title, content, footer);
        dialog.setVisible(true);
        return picked.get();
    }

    static Kind promote(Component parent, AssetManager assets, I18n i18n, Side side) {
        AtomicReference<Kind> chosen = new AtomicReference<>();
        Window owner = SwingUtilities.getWindowAncestor(parent);
        RoyalDialog dialog = new RoyalDialog(owner, i18n.get("promotion"));
        Kind[] kinds = {Kind.QUEEN, Kind.ROOK, Kind.BISHOP, Kind.KNIGHT};
        JPanel grid = transparent(new GridLayout(1, 4, 10, 0));
        for (Kind kind : kinds) {
            RoyalCard card = new RoyalCard(new BorderLayout(0, 8)).interactive(() -> {
                chosen.set(kind);
                dialog.dispose();
            });
            card.setBorder(new EmptyBorder(12, 12, 10, 12));
            card.setPreferredSize(new Dimension(92, 114));
            JComponent pieceArt = new JComponent() {
                @Override protected void paintComponent(Graphics graphics) {
                    Graphics2D g = (Graphics2D) graphics.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    if (side == Side.BLACK) {
                        g.setPaint(new RadialGradientPaint(getWidth() / 2f, getHeight() / 2f, Math.max(1f, getWidth() * 0.5f),
                                new float[]{0f, 1f}, new Color[]{new Color(212, 175, 55, 52), new Color(0, 0, 0, 0)}));
                        g.fillRect(0, 0, getWidth(), getHeight());
                    }
                    assets.drawPieceFitted(g, Piece.of(side, kind), 4, 2, getWidth() - 8, getHeight() - 4);
                    g.dispose();
                }
            };
            JLabel lbl = new JLabel(i18n.get("kind." + kind), SwingConstants.CENTER);
            lbl.setForeground(PALE);
            lbl.setFont(sans(Font.BOLD, 12.5f));
            card.add(pieceArt, BorderLayout.CENTER);
            card.add(lbl, BorderLayout.SOUTH);
            grid.add(card);
        }
        JPanel footer = transparent(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.add(new RoyalButton(i18n.get("cancel"), RoyalButton.Variant.GHOST, null, dialog::dispose));
        dialog.buildShell(i18n.get("promotion"), grid, footer);
        dialog.setVisible(true);
        return chosen.get();
    }

    static JDialog createAboutDialog(Window owner, BrandingManager branding, I18n i18n, boolean stockfishDetected) {
        RoyalDialog dialog = new RoyalDialog(owner, i18n.get("about"));
        JPanel body = transparent(new BorderLayout(0, 16));
        body.setPreferredSize(new Dimension(472, 412));

        JPanel logos = transparent(new GridBagLayout());
        GridBagConstraints lc = new GridBagConstraints();
        lc.gridx = 0; lc.gridy = 0; lc.insets = new Insets(0, 0, 8, 0);
        logos.add(branding.logo(BrandingManager.GAME, 260, 138, BrandConstants.PRODUCT), lc);
        lc.gridy = 1; lc.insets = new Insets(0, 0, 0, 0);
        logos.add(branding.logo(BrandingManager.EMBLEM, 74, 48, BrandConstants.COMPANY), lc);
        body.add(logos, BorderLayout.NORTH);

        RoyalPanel meta = new RoyalPanel(new GridLayout(0, 1, 0, 9), new Color(0x161519), new Color(0x111014), LINE, 12);
        meta.setBorder(new EmptyBorder(14, 18, 14, 18));
        meta.add(infoRow(i18n.get("aboutProduct"), BrandConstants.PRODUCT, IVORY));
        meta.add(infoRow(i18n.get("aboutCompany"), BrandConstants.COMPANY + " (" + BrandConstants.COMPANY_CHINESE + ")", PALE));
        meta.add(infoRow(i18n.get("aboutVersion"), "v" + BrandConstants.version(), IVORY));
        meta.add(infoRow(i18n.get("aboutRepo"), BrandConstants.REPOSITORY, MUTED));
        body.add(meta, BorderLayout.CENTER);

        JPanel footInfo = transparent(new BorderLayout(0, 6));
        JLabel engine = new JLabel(i18n.get(stockfishDetected ? "stockfishDetected" : "stockfishInternal"), SwingConstants.CENTER);
        engine.setForeground(stockfishDetected ? SUCCESS : MUTED);
        engine.setFont(sans(Font.PLAIN, 12f));
        JLabel copy = new JLabel(BrandConstants.COPYRIGHT, SwingConstants.CENTER);
        copy.setForeground(MUTED);
        copy.setFont(sans(Font.PLAIN, 11.5f));
        footInfo.add(engine, BorderLayout.NORTH);
        footInfo.add(copy, BorderLayout.SOUTH);
        body.add(footInfo, BorderLayout.SOUTH);

        JPanel footer = transparent(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.add(new RoyalButton(i18n.get("close"), RoyalButton.Variant.PRIMARY, null, dialog::dispose));
        dialog.buildShell(i18n.get("about"), body, footer);
        return dialog;
    }

    static void about(Component parent, BrandingManager branding, I18n i18n, boolean stockfishDetected) {
        createAboutDialog(SwingUtilities.getWindowAncestor(parent), branding, i18n, stockfishDetected).setVisible(true);
    }

    private static JPanel infoRow(String label, String value, Color valueColor) {
        JPanel row = transparent(new BorderLayout(12, 0));
        JLabel l = new JLabel(label);
        l.setForeground(MUTED);
        l.setFont(sans(Font.PLAIN, 12.5f));
        JLabel v = new JLabel(value);
        v.setForeground(valueColor);
        v.setFont(sans(Font.BOLD, 12.5f));
        row.add(l, BorderLayout.WEST);
        row.add(v, BorderLayout.EAST);
        return row;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br/>");
    }
}
