# ChessMaster – Royal Edition

- Preserve all originals in raw-assets. Regenerate runtime assets with scripts/PrepareAssets.java and update the manifest when sources change.
- Keep dependencies from desktop to application and infrastructure, infrastructure to application, and application to domain. The domain must run without Swing or JDBC.
- Keep rendering side-effect free. Run database work and chess searches off the Swing event dispatch thread.
- Store snapshots and move history atomically. Restore a game by replaying legal moves from its initial FEN.
- Put user-facing UI text in both resource bundles. Keep game rules in the domain.
- Run mvn clean verify before delivery. Record observed test and packaging results in docs/TEST_REPORT.md and pending work in docs/IMPLEMENTATION_STATUS.md.

## Corporate identity and delivery

- Use ChessMaster – Royal Edition and JIEXIANGTECH (杰祥科技). Centralize runtime identity in BrandConstants and BrandingManager.
- Keep the Maven reactor at com.jiexiangtech:royal-chess and Java packages at com.jiexiangtech.chess.
- Preserve originals under branding and raw-assets. Commit processed assets and documented application screenshots. Generate branding variants with scripts/PrepareBranding.java after board assets when updating the manifest.
- Keep the existing `jtuongdang/jiexiangtech-royal-chess` GitHub repository public so desktop releases remain downloadable. Verify the remote before pushing. Preserve Git history and never force-push.
- Keep the Docker Hub server repository private. Run the online server and MongoDB integration test before publishing a server image. Keep Atlas connection strings, organizer keys and deployment credentials in service secret settings.
- Record real build, platform, launcher and CI results. Only publish version tags whose CI matrix has verified the artifacts.
- Keep Stockfish external to application packages. Retain third-party notices with distributed files.

## Royal Focus Mode 2.5D Graphics Rules

- Keep all 2.5D perspective rendering, homography projection, texture warping, lighting, shadows, and reflections strictly inside `chess-desktop` (Presentation Layer); `chess-domain`, `chess-application`, and `chess-infrastructure` must remain free of Swing and Java2D.
- Preserve both **Classic 2D** and **Royal Focus 2.5D** gameplay modes with live in-match switching (`FocusModeController`) and SQLite-persisted `ViewMode` / `GraphicsQuality` preferences.
- Always render all 64 squares and 12 Royal Luxury piece sprites dynamically via `PerspectiveTextureRenderer` and `RoyalPieceRenderer` (Retina-aware sprite caching and offscreen warped-board caching); never substitute static mockup screenshots for interactive boards.
- Keep `docs/design/ROYAL_FOCUS_MODE.md`, `docs/design/royal-focus-reference.png`, `docs/screenshots/royal-focus-actual.png`, and `docs/screenshots/royal-focus-comparison.png` updated whenever Focus Mode geometry or visuals change.
