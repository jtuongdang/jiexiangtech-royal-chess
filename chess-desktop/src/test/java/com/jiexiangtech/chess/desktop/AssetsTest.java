package com.jiexiangtech.chess.desktop;

import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import com.jiexiangtech.chess.application.Preferences;
import com.jiexiangtech.chess.domain.*;
import static org.junit.jupiter.api.Assertions.*;

class AssetsTest {
    @Test void loadsEveryBoardAndTransparentSpriteFromClassloader() {
        var assets = new AssetManager();
        assertEquals(java.util.List.of(), assets.missing());
        assertEquals(2048, assets.board().getWidth());
        assertEquals(256, assets.tile(true).getWidth());
        assertEquals(256, assets.tile(false).getHeight());
        for (Side side : Side.values()) {
            for (Kind kind : Kind.values()) {
                var image = assets.piece(Piece.of(side, kind));
                assertEquals(512, image.getWidth());
                assertEquals(512, image.getHeight());
                assertTrue(image.getColorModel().hasAlpha());
                assertEquals(0, image.getRGB(0, 0) >>> 24);
                assertSame(image, assets.piece(Piece.of(side, kind)));
            }
        }
    }

    @Test void classicBoardUsesReferenceBezelAndPieceRatios() {
        var assets = new AssetManager();
        var frame = assets.classicFrame();
        assertEquals(828, frame.getWidth());
        assertEquals(842, frame.getHeight());
        assertEquals(0, frame.getRGB(414, 421) >>> 24);
        assertNotEquals(0, frame.getRGB(414, 10) >>> 24);
        for (boolean light : new boolean[]{false, true}) {
            for (int i = 1; i < 4; i++) {
                assertEquals(256, assets.tile(light, i).getWidth());
                assertNotSame(assets.tile(light, 0), assets.tile(light, i));
            }
        }

        for (int size : new int[]{450, 650, 920, 1300}) {
            var board = BoardCoordinateMapper.fit(size + 12, size, false);
            int span = board.tile() * 8;
            assertTrue(board.x() - board.leftBezel() >= 0);
            assertTrue(board.y() + span + board.bottomBezel() <= size);
            assertEquals(68.0 / 713, board.leftBezel() / (double) span, .5 / span);
            assertEquals(69.0 / 730, board.bottomBezel() / (double) span, .5 / span);
            var cell = board.bounds(Square.parse("e4"));
            assertEquals(cell.width, cell.height);
            var knight = RoyalPieceRenderer.classicBounds(Piece.of(Side.WHITE, Kind.KNIGHT), cell, 97, false);
            assertEquals(67.0 * 8 / 713, knight.width / (double) cell.width, .5 / cell.width);
            assertEquals(77.0 * 8 / 730, knight.height / (double) cell.height, .5 / cell.height);
            assertEquals(.928, (knight.y + knight.height - cell.y) / (double) cell.height, .5 / cell.height);
        }
    }

    @Test void corporateArtworkAndAllIconSizesArePackaged() throws Exception {
        var assets = new AssetManager();
        assertEquals(1672, assets.brand(BrandingManager.SPLASH).getWidth());
        assertEquals(941, assets.brand(BrandingManager.SPLASH).getHeight());
        assertTrue(assets.brand(BrandingManager.GAME).getColorModel().hasAlpha());
        assertTrue(assets.brand(BrandingManager.COMPANY).getColorModel().hasAlpha());
        for (int size : new int[]{16, 32, 64, 128, 256, 512, 1024}) {
            var icon = AssetManager.read("assets/icons/app-icon-" + size + ".png");
            assertEquals(size, icon.getWidth());
            assertEquals(size, icon.getHeight());
            assertEquals(0, icon.getRGB(0, 0) >>> 24);
        }
        try (var mac = getClass().getResourceAsStream("/assets/icons/app-icon.icns");
             var win = getClass().getResourceAsStream("/assets/icons/app-icon.ico")) {
            assertArrayEquals(new byte[]{'i','c','n','s'}, mac.readNBytes(4));
            assertArrayEquals(new byte[]{0,0,1,0}, win.readNBytes(4));
        }
    }

    @Test void arenaBackdropAndAllBundledPortraitsArePackaged() {
        var assets = new AssetManager();
        assertNotNull(assets.brand(BrandingManager.ARENA_BG));
        for (var avatar : com.jiexiangtech.chess.online.RoyalAvatar.values()) {
            if (avatar == com.jiexiangtech.chess.online.RoyalAvatar.EMBLEM) continue;
            assertEquals(256, assets.avatar(avatar).getWidth());
            assertEquals(256, assets.avatar(avatar).getHeight());
        }
    }

    @Test void coordinatesRoundTripAndPaintAnEightByEightBoardAtTwoSizes() throws Exception {
        var assets = new AssetManager();
        SwingUtilities.invokeAndWait(() -> {
            for (int size : new int[]{470, 660}) {
                for (boolean flipped : new boolean[]{false, true}) {
                    var mapper = BoardCoordinateMapper.fit(size + 12, size, flipped);
                    for (int square = 0; square < 64; square++) {
                        var area = mapper.bounds(square);
                        assertEquals(square, mapper.squareAt((int) area.getCenterX(), (int) area.getCenterY()));
                    }
                    assertEquals(-1, mapper.squareAt(0, 0));
                    var board = new ChessBoardPanel(assets, Preferences.defaults("en"), "royal", () -> false, x -> null, m -> {});
                    board.setSize(size + 12, size);
                    board.setFlipped(flipped);
                    board.update(Position.initial(), null, false);
                    var image = new BufferedImage(size + 12, size, BufferedImage.TYPE_INT_ARGB);
                    var graphics = image.createGraphics();
                    board.paint(graphics);
                    graphics.dispose();
                    assertNotEquals(0, image.getRGB(mapper.x() + 2, mapper.y() + 2) >>> 24);

                    // Verify 2.5D Royal Focus perspective homography round-trips all 64 squares & renders cleanly
                    var pMapper = PerspectiveCoordinateMapper.fit(1080, 620, flipped);
                    for (int square = 0; square < 64; square++) {
                        var center = pMapper.squareCenter(square);
                        assertEquals(square, pMapper.boardSquareAt(center.x, center.y));
                    }
                    assertEquals(-1, pMapper.boardSquareAt(0, 0));
                    board.setFocusMode(true);
                    board.setSize(1080, 620);
                    var focusImg = new BufferedImage(1080, 620, BufferedImage.TYPE_INT_ARGB);
                    var fg = focusImg.createGraphics();
                    board.paint(fg);
                    fg.dispose();
                    var mid = pMapper.squareCenter(Square.at(4, 3));
                    assertNotEquals(0, focusImg.getRGB((int) mid.x, (int) mid.y) >>> 24);
                }
            }
        });
    }

    @Test void royalFocus25DSupportsClickToMoveOnRank2PawnsAndStepForwardTargets() throws Exception {
        var assets = new AssetManager();
        SwingUtilities.invokeAndWait(() -> {
            java.util.List<Move> played = new java.util.ArrayList<>();
            var board = new ChessBoardPanel(assets, Preferences.defaults("en"), "royal", () -> true, x -> null, played::add);
            board.setFocusMode(true);
            board.setSize(1080, 620);
            Position pos = Position.initial();
            board.update(pos, null, false);
            var mapper = PerspectiveCoordinateMapper.fit(1080, 620, false);

            // Click d2 pawn (even though d1 Queen stands in front of it) -> then click d3 (1 step ahead of d2)
            var d2 = mapper.squareCenter(Square.at(3, 1));
            var d3 = mapper.squareCenter(Square.at(3, 2));
            clickAt(board, (int) Math.round(d2.x), (int) Math.round(d2.y));
            clickAt(board, (int) Math.round(d3.x), (int) Math.round(d3.y));
            assertEquals(1, played.size());
            assertEquals(Move.uci("d2d3"), played.get(0));
        });
    }

    @Test void moveAnimationKeepsTheOriginalPiecesAndSettlesAtBothEndpoints() {
        var initial = Position.initial();
        var shortMove = MoveAnimation.from(initial, Move.uci("e2e3"), null);
        var longMove = MoveAnimation.from(initial, Move.uci("e2e4"), null);
        var release = new java.awt.Point(123, 456);
        var dropped = MoveAnimation.from(initial, Move.uci("e2e4"), release);
        assertEquals(release, dropped.release());
        assertTrue(dropped.durationMillis() < shortMove.durationMillis());
        assertTrue(shortMove.durationMillis() < longMove.durationMillis());
        assertEquals(0, MoveAnimation.ease(0));
        assertEquals(0.5, MoveAnimation.ease(0.5));
        assertEquals(1, MoveAnimation.ease(1));
        assertTrue(MoveAnimation.ease(0.01) < 0.001);
        assertTrue(1 - MoveAnimation.ease(0.99) < 0.001);

        var castle = MoveAnimation.from(Position.fromFen("4k3/8/8/8/8/8/8/4K2R w K - 0 1"),
                Move.uci("e1g1"), null);
        assertEquals(Move.uci("h1f1"), castle.rook());
        var enPassant = MoveAnimation.from(Position.fromFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2"),
                Move.uci("e5d6"), null);
        assertEquals(Square.parse("d5"), enPassant.capturedSquare());
        assertEquals(Piece.of(Side.BLACK, Kind.PAWN), enPassant.captured());
        assertEquals(1f, MoveAnimation.captureAlpha(0.38));
        assertEquals(0f, MoveAnimation.captureAlpha(1));
        var promotion = MoveAnimation.from(Position.fromFen("4k3/P7/8/8/8/8/8/4K3 w - - 0 1"),
                Move.uci("a7a8q"), null);
        assertEquals(Piece.of(Side.WHITE, Kind.PAWN), promotion.piece());
    }

    @Test void bothRenderersPaintAnIntermediateMoveAndTheCorrectFinalPosition() throws Exception {
        var assets = new AssetManager();
        var preferences = Preferences.defaults("en");
        var before = Position.initial();
        var move = Move.uci("e2e4");
        var after = before.afterGeneratedMove(move);
        var pieces = new RoyalPieceRenderer(assets);
        SwingUtilities.invokeAndWait(() -> {
            for (BoardRenderer renderer : java.util.List.of(new Classic2DBoardRenderer(assets, pieces),
                    new RoyalFocusBoardRenderer(assets, pieces))) {
                var motion = MoveAnimation.from(before, move, null);
                int[] start = paintMove(renderer, after, move, preferences, motion, 0);
                int[] middle = paintMove(renderer, after, move, preferences, motion, 0.5);
                int[] beforeLanding = paintMove(renderer, after, move, preferences, motion, 0.8);
                int[] landed = paintMove(renderer, after, move, preferences, null, 1);
                assertFalse(java.util.Arrays.equals(start, middle));
                assertFalse(java.util.Arrays.equals(middle, beforeLanding));
                assertFalse(java.util.Arrays.equals(beforeLanding, landed));
                assertArrayEquals(landed, paintMove(renderer, after, move, preferences, null, 1));
            }
        });
    }

    private static int[] paintMove(BoardRenderer renderer, Position position, Move move, Preferences preferences,
                                   MoveAnimation motion, double progress) {
        var image = new BufferedImage(650, 580, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        renderer.paint(g, new BoardRenderer.RenderState(650, 580, position, false, "royal", preferences,
                move, null, -1, -1, java.util.List.of(), false, false, null, null, null,
                motion, progress, 0.5f, false, 1, null, null, RoyalStyle.PALE, 0));
        g.dispose();
        return image.getRGB(0, 0, 650, 580, null, 0, 650);
    }

    private static void clickAt(ChessBoardPanel board, int x, int y) {
        long now = System.currentTimeMillis();
        board.dispatchEvent(new java.awt.event.MouseEvent(board, java.awt.event.MouseEvent.MOUSE_PRESSED,
                now, java.awt.event.InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false, java.awt.event.MouseEvent.BUTTON1));
        board.dispatchEvent(new java.awt.event.MouseEvent(board, java.awt.event.MouseEvent.MOUSE_RELEASED,
                now + 10, 0, x, y, 1, false, java.awt.event.MouseEvent.BUTTON1));
    }

    @Test void vietnameseBundleContainsEveryEnglishKey() throws Exception {
        var english = new java.util.Properties();
        var vietnamese = new java.util.Properties();
        try (var en = getClass().getResourceAsStream("/i18n/Messages.properties");
             var vi = getClass().getResourceAsStream("/i18n/Messages_vi.properties")) {
            english.load(new java.io.InputStreamReader(en, java.nio.charset.StandardCharsets.UTF_8));
            vietnamese.load(new java.io.InputStreamReader(vi, java.nio.charset.StandardCharsets.UTF_8));
        }
        assertEquals(english.keySet(), vietnamese.keySet());
        assertEquals("Ván mới", new I18n("vi").get("newGame"));
    }
}
