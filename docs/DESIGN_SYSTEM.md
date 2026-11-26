# Design system

Royal Luxury combines obsidian surfaces, ivory and onyx marble, metallic gold accents, and the
chess artwork redrawn from the supplied Classic board reference. The approved Splash and Login screens establish the visual
baseline for the entire desktop client.

JIEXIANGTECH (杰祥科技) branding is centralized in `BrandConstants` and `BrandingManager`.
`AssetManager` loads images once from the classpath for both IDE and bundled launchers, and
computes visible alpha bounds (`alpha > 16`) for all 12 piece sprites so pieces render at full
reference-matched size without transparent canvas shrinkage. `ThemeManager` applies the Royal Slate
treatment to the marble tile textures.

## Color tokens

| Role | Color |
| --- | --- |
| Window canvas | `#0B0B0D` |
| Surface | `#141316` |
| Elevated surface | `#1B191E` |
| Raised controls | `#232026` |
| Metallic gold | `#D4AF37` / `#9E7823` |
| Champagne gold | `#F2D78B` |
| Ivory | `#F5F0E4` |
| Primary text | `#F5F5F5` |
| Muted text | `#9E988E` |
| Hairline borders | `#362E22` / `#6E552B` |
| Destructive surface / text | `#3B171B` / `#FFA0A0` |
| Error / success text | `#FF7777` / `#70E39C` |

## Reusable component system

- `RoyalStyle`: centralized palette, typography (`Georgia` serif display, system sans UI, tabular monospaced clocks), pills, dividers, and FlatLaf overrides (including metallic gold slider and scrollbar theming).
- `RoyalIcons`: antialiased Java2D vector icon library for navigation, gameplay controls, replay transport, and status medallions.
- `RoyalButton` & `RoyalIconButton`: hierarchical button system (`PRIMARY`, `SECONDARY`, `GHOST`, `DANGER`, `SEGMENT`) with metallic gold gradients, specular borders, hover states, and focus rings.
- `RoyalPanel`, `RoyalCard`, & `RoyalBackgroundPanel`: elevated dark obsidian containers with subtle layered shadows, gold accent bars, interactive hover/selection states, and cinematic vignette backdrops.
- `RoyalNavigationPanel`: collapsible left sidebar with active gold indicator bar, full mode on lobby screens (`220px`) and auto-minimized icon rail (`66px`) during live Gameplay, Puzzles, and Replay so the chessboard dominates the viewport.
- `RoyalPlayerCard` & `RoyalGameClock`: live player banners featuring piece avatars, side/role pills, active-turn gold illumination, check badges, and large tabular clocks with low-time warning styling.
- `RoyalMoveHistory` & `RoyalCapturedTray`: custom-rendered move notation list with alternating dark bands, active ply pill, and click-to-seek support; captured pieces tray displaying content-trimmed piece sprites sorted by material value with material advantage delta.
- `RoyalTextField`, `RoyalToggle`, & `RoyalDialog`: rounded search/input fields, custom gold pill switches, and undecorated obsidian-gold modal dialogs (including visual piece-card promotion picker and branded About dialog).

## Board craftsmanship and responsiveness

- `BoardCoordinateMapper` fits an 8×8 square grid and the asymmetric gold bezel measured from [Interface-2](design/ROYAL_CLASSIC_BOARD.md).
- Classic pieces follow measured per-kind and per-side bounds. The renderer paints the frame, marble squares, pieces, coordinates, shadows and highlights as separate layers. Royal Focus uses the same sprites with its perspective camera and dynamic depth ordering.
- Minimum supported window size remains `1020 × 690` pixels, verified alongside standard desktop and fullscreen layouts on macOS Retina displays. English and Vietnamese resource bundles maintain full key parity.
