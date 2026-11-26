# Royal Luxury assets

The 18 PNG sources were inspected visually on a contact sheet. The 12 individual
transparent pieces were selected over the two composite reference sheets.
Piece bounds use nonzero alpha. All sprites have proportional scaling and a
centered bottom anchor at y=488 on a transparent 512 × 512 canvas.

Target paths below are relative to assets/themes/royal.

| Source | Target | Source → output | Check |
| --- | --- | --- | --- |
| `raw-assets/imgchess/check_while/ChatGPT Image Nov 26, 2026 at 03_45_18 PM-1.png` | `pieces/white/rook.png` | 1254 × 1254 → 512 × 512 | RGBA, visual identity verified |
| `raw-assets/imgchess/check_while/ChatGPT Image Nov 26, 2026 at 03_45_20 PM-2.png` | `pieces/white/knight.png` | 1254 × 1254 → 512 × 512 | RGBA, visual identity verified |
| `raw-assets/imgchess/check_while/ChatGPT Image Nov 26, 2026 at 03_45_21 PM-3.png` | `pieces/white/bishop.png` | 1254 × 1254 → 512 × 512 | RGBA, visual identity verified |
| `raw-assets/imgchess/check_while/ChatGPT Image Nov 26, 2026 at 03_45_22 PM-4.png` | `pieces/white/queen.png` | 1254 × 1254 → 512 × 512 | RGBA, visual identity verified |
| `raw-assets/imgchess/check_while/ChatGPT Image Nov 26, 2026 at 03_45_24 PM-5.png` | `pieces/white/king.png` | 1254 × 1254 → 512 × 512 | RGBA, visual identity verified |
| `raw-assets/imgchess/check_while/ChatGPT Image Nov 26, 2026 at 03_45_25 PM-6.png` | `pieces/white/pawn.png` | 1254 × 1254 → 512 × 512 | RGBA, visual identity verified |
| `raw-assets/imgchess/check_black/ChatGPT Image Nov 26, 2026 at 03_41_24 PM-1.png` | `pieces/black/rook.png` | 1254 × 1254 → 512 × 512 | RGBA, visual identity verified |
| `raw-assets/imgchess/check_black/ChatGPT Image Nov 26, 2026 at 03_41_25 PM-2.png` | `pieces/black/knight.png` | 1254 × 1254 → 512 × 512 | RGBA, visual identity verified |
| `raw-assets/imgchess/check_black/ChatGPT Image Nov 26, 2026 at 03_41_27 PM-3.png` | `pieces/black/bishop.png` | 1254 × 1254 → 512 × 512 | RGBA, visual identity verified |
| `raw-assets/imgchess/check_black/ChatGPT Image Nov 26, 2026 at 03_41_28 PM-4.png` | `pieces/black/queen.png` | 1254 × 1254 → 512 × 512 | RGBA, visual identity verified |
| `raw-assets/imgchess/check_black/ChatGPT Image Nov 26, 2026 at 03_41_29 PM-5.png` | `pieces/black/king.png` | 1254 × 1254 → 512 × 512 | RGBA, visual identity verified |
| `raw-assets/imgchess/check_black/ChatGPT Image Nov 26, 2026 at 03_41_30 PM-6.png` | `pieces/black/pawn.png` | 1254 × 1254 → 512 × 512 | RGBA, visual identity verified |
| `raw-assets/royal_chessboard.png` | `board/board.png` | 2048 × 2048 → 2048 × 2048 | Copied byte for byte |
| `raw-assets/royal_chessboard_grid.png` | `board/board-grid.png` | 1920 × 1920 → 1920 × 1920 | Copied byte for byte |
| `raw-assets/royal_tile_light.png` | `board/tile-light.png` | 256 × 256 → 256 × 256 | Copied byte for byte |
| `raw-assets/royal_tile_dark.png` | `board/tile-dark.png` | 256 × 256 → 256 × 256 | Copied byte for byte |
| `raw-assets/table_chess/Luxe Marble Chess Asset Collection-2.png` | `—` | 1536 × 1024 → — | Composite reference retained |
| `raw-assets/table_chess/Luxury Ivory Chess Asset Sheet.png` | `—` | 1536 × 1024 → — | Composite reference retained |

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
