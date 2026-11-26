param(
    [ValidateSet("app-image", "msi")][string]$Type = "app-image",
    [string]$Destination = "dist"
)
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
$jar = Get-ChildItem "chess-desktop/target/royal-chess-*-all.jar" | Select-Object -First 1
if (-not $jar) { throw "Build with mvn clean verify before packaging." }
$version = $jar.Name -replace '^royal-chess-(.+)-all\.jar$', '$1'
$inputDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ("royal-package-" + [guid]::NewGuid())
$null = New-Item -ItemType Directory -Path $inputDirectory
try {
    Copy-Item $jar.FullName $inputDirectory
    $jpackage = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin/jpackage.exe" } else { "jpackage" }
    [string[]]$options = @(
        "--type", $Type, "--input", $inputDirectory, "--dest", $Destination,
        "--name", "ChessMasterRoyal", "--app-version", $version, "--main-jar", $jar.Name,
        "--main-class", "com.jiexiangtech.chess.desktop.Main",
        "--add-modules", "java.desktop,java.net.http,java.sql,java.management,java.naming,jdk.crypto.ec,jdk.unsupported",
        "--vendor", "JIEXIANGTECH", "--copyright", "© 2026 JIEXIANGTECH. All rights reserved.",
        "--icon", "chess-desktop/src/main/resources/assets/icons/app-icon.ico"
    )
    if ($Type -eq "msi") { $options += @("--win-menu", "--win-shortcut", "--win-dir-chooser", "--license-file", "LICENSE") }
    & $jpackage @options
    if ($LASTEXITCODE -ne 0) { throw "jpackage failed with exit code $LASTEXITCODE" }
} finally {
    Remove-Item (Join-Path $inputDirectory $jar.Name) -ErrorAction SilentlyContinue
    Remove-Item $inputDirectory
}
