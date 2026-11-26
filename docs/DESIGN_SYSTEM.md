# Design system

Royal Luxury combines ivory and onyx marble, gold frames and the supplied chess pieces.
JIEXIANGTECH branding is centralized in BrandConstants and BrandingManager. AssetManager
loads images once from the classpath for both IDE and bundled launchers. ThemeManager
applies the Royal Slate treatment to the original tile textures.

| Role | Color |
| --- | --- |
| Window | #0D0D0D |
| Surface | #171717 |
| Raised controls | #202020 |
| Gold | #D4AF37 |
| Light gold | #F2D78B |
| Ivory | #F5F0E4 |
| Text | #F5F5F5 |
| Muted text | #A3A3A3 |
| Error / success text | #FF7777 / #70E39C |

Georgia headings and system sans-serif controls provide hierarchy. FlatLaf supplies
HiDPI-aware controls, hover and focus states, and scrollbars. Java2D paints the board
frame, tiles, pieces, shadows, highlights and animation. The board uses a single integer
cell size and reverses the coordinate mapping when flipped.

The splash preserves the wordmarks embedded in the supplied image. Its footer shows the
active startup phase and build version. The login and registration forms use both supplied
logos and a chess-art backdrop. Global navigation carries the product mark and About
shows the full company name, version and copyright. The app icon uses only the king
and gold curve at small sizes.

The desktop minimum is 1020 × 690 window pixels. Layout uses BorderLayout, GridBagLayout,
GridLayout and scroll views. Visible motion can be disabled in Settings. ResourceBundle
covers English and Vietnamese for the application interface.
