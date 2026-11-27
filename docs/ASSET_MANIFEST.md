# Royal Arena and Royal Luxury assets

Target paths are relative to assets/themes/royal. Original assets are preserved locally in
raw-assets. The current 12 sprites come from a transparent ImageGen atlas based on
the original Royal Arena Duel design. Each is isolated, proportionally resampled and
bottom-centered on a transparent 512 × 512 canvas. The grid and pieces are drawn separately
at runtime. Classic uses the carved frame from the original top-down reference.

| Source | Target | Source → output | Processing |
| --- | --- | --- | --- |
| `raw-assets/royal-arena-sprites.png` | `pieces/black/rook.png` | 1536 × 1024 → 512 × 512 | Atlas cell 1/1 |
| `raw-assets/royal-arena-sprites.png` | `pieces/black/knight.png` | 1536 × 1024 → 512 × 512 | Atlas cell 1/2 |
| `raw-assets/royal-arena-sprites.png` | `pieces/black/bishop.png` | 1536 × 1024 → 512 × 512 | Atlas cell 1/3 |
| `raw-assets/royal-arena-sprites.png` | `pieces/black/queen.png` | 1536 × 1024 → 512 × 512 | Atlas cell 1/4 |
| `raw-assets/royal-arena-sprites.png` | `pieces/black/king.png` | 1536 × 1024 → 512 × 512 | Atlas cell 1/5 |
| `raw-assets/royal-arena-sprites.png` | `pieces/black/pawn.png` | 1536 × 1024 → 512 × 512 | Atlas cell 1/6 |
| `raw-assets/royal-arena-sprites.png` | `pieces/white/rook.png` | 1536 × 1024 → 512 × 512 | Atlas cell 2/1 |
| `raw-assets/royal-arena-sprites.png` | `pieces/white/knight.png` | 1536 × 1024 → 512 × 512 | Atlas cell 2/2 |
| `raw-assets/royal-arena-sprites.png` | `pieces/white/bishop.png` | 1536 × 1024 → 512 × 512 | Atlas cell 2/3 |
| `raw-assets/royal-arena-sprites.png` | `pieces/white/queen.png` | 1536 × 1024 → 512 × 512 | Atlas cell 2/4 |
| `raw-assets/royal-arena-sprites.png` | `pieces/white/king.png` | 1536 × 1024 → 512 × 512 | Atlas cell 2/5 |
| `raw-assets/royal-arena-sprites.png` | `pieces/white/pawn.png` | 1536 × 1024 → 512 × 512 | Atlas cell 2/6 |
| `raw-assets/royal-interface-2.png` | `board/classic-frame.png` | 1586 × 992 → 828 × 842 | Bezel only. Baked coordinate glyphs removed. Playing area is transparent. |
| `raw-assets/royal-arena-marble.png` | `board/tile-light*.png, board/tile-dark*.png` | 1774 × 887 → 8 × (256 × 256) | Four cream marble and four dark onyx samples |
| `raw-assets/royal_chessboard.png` | `board/board.png` | 2048 × 2048 | Legacy decorative artwork retained |
| `raw-assets/royal_chessboard_grid.png` | `board/board-grid.png` | 1920 × 1920 | Legacy decorative artwork retained |

The original individual sprites, marble tiles and composite sheets are still in raw-assets.

## Arena scene and portraits

Targets in this section are relative to assets, except for the design reference.

| Source | Target | Source → output | Processing |
| --- | --- | --- | --- |
| `raw-assets/royal-arena-backdrop.png` | `backgrounds/royal-arena.jpg` | 1672 × 941 | Backdrop without board, pieces or UI, encoded as JPEG |
| `raw-assets/royal-arena-portraits.png` | `avatars/{knight,queen,sage,archer,sovereign,ranger}.png` | 1254 × 1254 → 6 × (256 × 256) | Center-cropped portraits, circular masks drawn by Swing |
| `raw-assets/royal-arena-duel-reference.png` | `docs/design/royal-focus-reference.png` | 1672 × 941 → 1024 × 576 | Owner-provided layout reference, not a runtime image |

## Corporate branding

All three supplied PNGs were opened and identified by their visible content.
Original filenames and source files are preserved. Target paths are relative to assets.

| Source | Role and output | Original → output | Check |
| --- | --- | --- | --- |
| `branding/logo.png` | `branding/company-logo.png` | 1254 × 1254 → 1000 × 991 | Transparent bilingual wordmark and emblem |
| `branding/logo.png` | `branding/company-emblem.png` | 1254 × 1254 → 720 × 450 | Transparent symbol cropped above wordmark |
| `branding/ PM-2.png` | `branding/game-logo.png` | 1254 × 1254 → 1200 × 1183 | Transparent full black-and-gold game logo |
| `branding/bannerPM-3.png` | `backgrounds/splash-background.png` | 1672 × 941 → 1672 × 941 | Opaque artwork with embedded branding and copyright |
| `branding/bannerPM-3.png` | `backgrounds/login-background.png` | 1672 × 941 → 1600 × 900 | Side crops blended with a dark background |
| `branding/bannerPM-3.png` | `backgrounds/dashboard-background.png` | 1672 × 941 → 1440 × 540 | Right-hand chess artwork and tapered backdrop |
| `branding/ PM-2.png` | `branding/app-icon.png`, `icons/app-icon-*.png` | 1254 × 1254 → 16, 32, 64, 128, 256, 512, 1024 square | King and gold curve cropped above lettering |
| Derived PNGs | `icons/app-icon.icns`, `icons/app-icon.ico` | 7 macOS frames, 5 Windows frames | Native icon containers with alpha |

The four cue sounds in assets/sounds are generated 22.05 kHz mono WAVs.
Typography uses system fonts. Royal Slate applies a cool tint to the existing marble tiles.
