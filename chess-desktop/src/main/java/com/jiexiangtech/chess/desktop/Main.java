package com.jiexiangtech.chess.desktop;

import com.formdev.flatlaf.FlatDarkLaf;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.IntConsumer;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.slf4j.LoggerFactory;
import com.jiexiangtech.chess.application.*;
import com.jiexiangtech.chess.domain.*;
import com.jiexiangtech.chess.infrastructure.*;

public final class Main {
    private record Boot(SqliteChessStore store, AssetManager assets, ChessEngines engines, String remembered,
                        Preferences preferences) {}

    private Main() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--smoke")) {
            smoke();
            return;
        }
        boolean fullQa = args.length == 3 && args[0].equals("--visual-smoke") && args[2].equals("--all");
        Path screenshots = (args.length >= 2 && args[0].equals("--visual-smoke")) ? Path.of(args[1]) : null;
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("ChessMaster requires a graphical desktop. Use --smoke to validate the installation.");
        }
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac")) {
            System.setProperty("apple.awt.application.name", BrandConstants.PRODUCT);
        }
        BufferedImage art = null;
        try { art = AssetManager.read("assets/" + BrandingManager.SPLASH); }
        catch (java.io.IOException e) { LoggerFactory.getLogger(Main.class).warn("Unable to load splash artwork", e); }
        BufferedImage splash = art;
        SwingUtilities.invokeLater(() -> launch(screenshots, fullQa, splash));
    }

    private static String language() { return Locale.getDefault().getLanguage().equals("vi") ? "vi" : "en"; }

    private static Boot boot(IntConsumer stage) {
        stage.accept(1);
        var assets = new AssetManager();
        stage.accept(2);
        var store = new SqliteChessStore(DataPath.defaultDirectory().resolve("chessmaster.db"));
        stage.accept(3);
        var engines = new ChessEngines();
        if (Rules.legalMoves(Position.initial()).size() != 20) throw new IllegalStateException("Invalid chess engine");
        stage.accept(4);
        return new Boot(store, assets, engines, store.rememberedUsername(), store.settings(ChessStore.GUEST_ID, language()));
    }

    private static void launch(Path screenshots, boolean fullQa, BufferedImage artwork) {
        FlatDarkLaf.setup();
        RoyalStyle.install();
        I18n text = new I18n(language());
        SplashView splash = new SplashView(text, artwork);
        new SwingWorker<Boot, Integer>() {
            @Override protected Boot doInBackground() { return boot(stage -> publish(stage)); }

            @Override protected void process(java.util.List<Integer> stages) {
                for (int stage : stages) splash.step(stage);
            }

            @Override protected void done() {
                try {
                    Boot ready = get();
                    splash.step(5);
                    RoyalFrame frame = new RoyalFrame(ready.store(), new Pbkdf2Passwords(), ready.assets(), ready.engines(),
                            ready.remembered(), ready.preferences());
                    if (screenshots != null) saveImage(splash.snapshot(), screenshots, "splash");
                    splash.exit(() -> {
                        frame.setVisible(true);
                        if (!ready.assets().missing().isEmpty()) {
                            LoggerFactory.getLogger(Main.class).warn("Missing assets: {}", ready.assets().missing());
                            RoyalDialog.message(frame, text.get("error"),
                                    text.get("missingAssets") + "\n" + String.join("\n", ready.assets().missing()),
                                    text.get("close"), true);
                        }
                        if (screenshots != null) capture(frame, screenshots, fullQa);
                    });
                } catch (InterruptedException e) {
                    splash.dispose();
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    splash.dispose();
                    LoggerFactory.getLogger(Main.class).error("Startup failed", e);
                    JOptionPane.showMessageDialog(null, text.get("startupError"), text.get("error"), JOptionPane.ERROR_MESSAGE);
                    if (screenshots != null) System.exit(1);
                }
            }
        }.execute();
    }

    private static void smoke() {
        Boot boot = boot(stage -> {});
        if (!boot.assets().missing().isEmpty()) throw new IllegalStateException("Missing assets: " + boot.assets().missing());
        if (Rules.perft(Position.initial(), 3) != 8902) throw new IllegalStateException("Move generator verification failed");
        var store = boot.store();
        if (store.profile(ChessStore.GUEST_ID).user().id() != ChessStore.GUEST_ID || Puzzles.catalog().isEmpty()) {
            throw new IllegalStateException("Database initialization verification failed");
        }
        if (boot.engines().stockfishDetected()) {
            var result = StockfishEngineAdapter.discover().orElseThrow().analyze(
                    Position.initial(), java.time.Duration.ofSeconds(3));
            if (result.isEmpty() || !Rules.legalMoves(Position.initial()).contains(result.get().move())) {
                throw new IllegalStateException("Configured Stockfish did not return a legal move");
            }
            System.out.println("Stockfish UCI smoke OK: " + result.get().move().uci() + ", depth=" + result.get().depth());
        }
        System.out.println("ChessMaster smoke OK: assets, SQLite migrations/profile, puzzles, perft(3)=8902");
    }

    private static void capture(RoyalFrame frame, Path folder, boolean fullQa) {
        Dimension normalSize = frame.getSize();
        later(500, () -> {
            saveImage(frame, folder, "login");
            frame.visualGuest();
            later(750, () -> {
                saveImage(frame, folder, "dashboard");
                frame.visualFocusMatch();
                frame.setSize(1280, 748); // exact 16:9 content canvas matching 1024x576 reference ratio
                frame.validate();
                later(700, () -> {
                    saveImage(frame, folder, "royal-focus-actual");
                    frame.setSize(normalSize);
                    frame.validate();
                    frame.visualMatch();
                    later(600, () -> {
                        saveImage(frame, folder, "gameplay");
                        frame.setSize(frame.getMinimumSize());
                        frame.validate();
                        later(300, () -> {
                            saveImage(frame, folder, "gameplay-minimum");
                            if (!fullQa) {
                                frame.dispatchEvent(new java.awt.event.WindowEvent(frame, java.awt.event.WindowEvent.WINDOW_CLOSING));
                                return;
                            }
                            frame.setSize(normalSize);
                            frame.validate();
                            captureExtended(frame, folder);
                        });
                    });
                });
            });
        });
    }

    private static void captureExtended(RoyalFrame frame, Path folder) {
        frame.visualNewGame();
        later(450, () -> {
            saveImage(frame, folder, "new-game");
            frame.visualPuzzles();
            later(500, () -> {
                saveImage(frame, folder, "puzzles");
                frame.visualPuzzlePlay();
                later(400, () -> {
                    saveImage(frame, folder, "puzzle-play");
                    frame.visualHistory();
                    later(500, () -> {
                        saveImage(frame, folder, "history");
                        frame.visualLeaderboard();
                        later(500, () -> {
                            saveImage(frame, folder, "leaderboard");
                            frame.visualStatistics();
                            later(500, () -> {
                                saveImage(frame, folder, "statistics");
                                frame.visualAchievements();
                                later(500, () -> {
                                    saveImage(frame, folder, "achievements");
                                    frame.visualSettings();
                                    later(500, () -> {
                                        saveImage(frame, folder, "settings");
                                        frame.visualResult();
                                        later(450, () -> {
                                            saveImage(frame, folder, "result");
                                            JDialog about = frame.visualAboutDialog();
                                            about.setModal(false);
                                            about.setVisible(true);
                                            later(300, () -> {
                                                saveContainer(about.getContentPane(), folder, "about");
                                                about.dispose();
                                                frame.dispatchEvent(new java.awt.event.WindowEvent(frame, java.awt.event.WindowEvent.WINDOW_CLOSING));
                                            });
                                        });
                                    });
                                });
                            });
                        });
                    });
                });
            });
        });
    }

    private static void later(int ms, Runnable action) {
        Timer timer = new Timer(ms, event -> action.run());
        timer.setRepeats(false);
        timer.start();
    }

    private static void saveImage(RoyalFrame frame, Path folder, String name) {
        saveContainer(frame.getContentPane(), folder, name);
    }

    private static void saveContainer(Container content, Path folder, String name) {
        var image = new BufferedImage(Math.max(1, content.getWidth()), Math.max(1, content.getHeight()), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        content.printAll(graphics);
        graphics.dispose();
        saveImage(image, folder, name);
    }

    private static void saveImage(BufferedImage image, Path folder, String name) {
        try {
            Files.createDirectories(folder);
            ImageIO.write(image, "PNG", folder.resolve(name + ".png").toFile());
        } catch (Exception e) {
            LoggerFactory.getLogger(Main.class).error("Visual capture failed", e);
            System.exit(1);
        }
    }
}
