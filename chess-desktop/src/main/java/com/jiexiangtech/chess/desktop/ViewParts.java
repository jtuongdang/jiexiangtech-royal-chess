package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.util.ArrayList;
import java.util.function.Function;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import com.jiexiangtech.chess.application.Preferences;
import com.jiexiangtech.chess.domain.*;
import static com.jiexiangtech.chess.desktop.RoyalStyle.*;

final class ViewParts {
    private ViewParts() {}

    static JPanel row(Component... children) {
        JPanel row = transparent(new FlowLayout(FlowLayout.LEFT, 7, 3));
        for (Component child : children) row.add(child);
        return row;
    }

    static JPanel stack(Component... children) {
        JPanel panel = transparent(null);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        for (Component child : children) {
            if (child instanceof JComponent component) component.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(child);
        }
        return panel;
    }

    static void gap(JPanel panel, int size) { panel.add(Box.createVerticalStrut(size)); }

    static JPanel heading(String text, Component actions) {
        JPanel header = transparent(new BorderLayout(12, 0));
        header.add(title(text, 32), BorderLayout.CENTER);
        if (actions != null) header.add(actions, BorderLayout.EAST);
        return header;
    }

    static Kind promote(Component parent, I18n i18n) {
        Kind[] kinds = {Kind.QUEEN, Kind.ROOK, Kind.BISHOP, Kind.KNIGHT};
        String[] labels = new String[kinds.length];
        for (int i = 0; i < kinds.length; i++) labels[i] = i18n.get("kind." + kinds[i]);
        int result = JOptionPane.showOptionDialog(parent, i18n.get("promotion"), i18n.get("promotion"),
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, labels, labels[0]);
        return result < 0 ? null : kinds[result];
    }

    static boolean flip(Preferences prefs, Side side) {
        return switch (prefs.orientation()) {
            case BLACK -> true;
            case WHITE -> false;
            case AUTO -> side == Side.BLACK;
        };
    }

    static String clock(long millis) {
        long sec = millis / 1000 + (millis % 1000 == 0 ? 0 : 1);
        return sec >= 3600 ? "%d:%02d:%02d".formatted(sec / 3600, sec / 60 % 60, sec % 60) :
                "%02d:%02d".formatted(sec / 60, sec % 60);
    }

    static <T> JComboBox<T> combo(T[] values, Function<T, String> labeler) {
        JComboBox<T> combo = new JComboBox<>(values);
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override @SuppressWarnings("unchecked")
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                return super.getListCellRendererComponent(list, value == null ? "" : labeler.apply((T) value), index, selected, focus);
            }
        });
        return combo;
    }

    static class Moves extends AbstractTableModel {
        private record Row(int number, String white, String black) {}
        private final I18n i18n;
        private final ArrayList<Row> rows = new ArrayList<>();

        Moves(I18n i18n) { this.i18n = i18n; }

        void update(Game game, int count) {
            rows.clear();
            var moves = game.moves();
            for (int i = 0; i < count; i++) {
                var before = game.positionAt(i);
                String san = moves.get(i).san();
                if (before.turn() == Side.WHITE) {
                    rows.add(new Row(before.fullmove(), san, ""));
                } else if (rows.isEmpty()) {
                    rows.add(new Row(before.fullmove(), "", san));
                } else {
                    var row = rows.remove(rows.size() - 1);
                    rows.add(new Row(row.number(), row.white(), san));
                }
            }
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return 3; }
        @Override public String getColumnName(int column) {
            return column == 0 ? "#" : i18n.get(column == 1 ? "side.WHITE" : "side.BLACK");
        }
        @Override public Object getValueAt(int row, int column) {
            var data = rows.get(row);
            return switch (column) { case 0 -> data.number(); case 1 -> data.white(); default -> data.black(); };
        }

        JTable table() {
            JTable table = new JTable(this);
            table.setRowHeight(29);
            table.getColumnModel().getColumn(0).setMaxWidth(52);
            table.setFocusable(false);
            table.setRowSelectionAllowed(false);
            table.setShowGrid(false);
            table.setFillsViewportHeight(true);
            table.getTableHeader().setReorderingAllowed(false);
            return table;
        }
    }

    static final class Captures extends JPanel {
        private final AssetManager assets;
        private final JPanel white, black;
        private final I18n i18n;
        private final java.util.Map<Piece, ImageIcon> icons = new java.util.HashMap<>();

        Captures(AssetManager assets, I18n i18n) {
            super(new GridLayout(2, 1));
            this.assets = assets;
            this.i18n = i18n;
            setOpaque(false);
            white = row();
            black = row();
            add(tray(white));
            add(tray(black));
        }

        private static JScrollPane tray(JPanel pieces) {
            JScrollPane strip = scroll(pieces);
            strip.setOpaque(false);
            strip.getViewport().setOpaque(false);
            strip.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
            strip.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            strip.setPreferredSize(new Dimension(200, 31));
            strip.setMinimumSize(new Dimension(128, 31));
            return strip;
        }

        void update(Game game, int count) {
            white.removeAll();
            black.removeAll();
            white.add(label(i18n.get("side.WHITE"), MUTED));
            black.add(label(i18n.get("side.BLACK"), MUTED));
            for (var piece : game.moves().subList(0, count).stream().map(Game.Ply::captured).filter(java.util.Objects::nonNull).toList()) {
                JPanel pane = piece.side() == Side.BLACK ? white : black;
                var image = assets.piece(piece);
                JLabel icon = image == null ? new JLabel(piece.kind().name())
                        : new JLabel(icons.computeIfAbsent(piece,
                                key -> new ImageIcon(image.getScaledInstance(22, 22, Image.SCALE_SMOOTH))));
                icon.setToolTipText(i18n.get("kind." + piece.kind()));
                pane.add(icon);
            }
            revalidate();
            repaint();
        }
    }

    static JPanel padded() {
        JPanel panel = transparent(new BorderLayout(0, 16));
        panel.setBorder(new EmptyBorder(23, 25, 22, 25));
        return panel;
    }
}
