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
- Use the private jiexiangtech-royal-chess repository and verify the remote before pushing. Preserve Git history and never force-push.
- Record real build, platform, launcher and CI results. Only publish version tags whose CI matrix has verified the artifacts.
- Keep Stockfish external to application packages. Retain third-party notices with distributed files.
