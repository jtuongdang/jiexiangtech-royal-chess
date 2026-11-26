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
                }
            }
        });
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
