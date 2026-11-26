"""Inspect staged Mac packages and match their application JAR to the tested CI JAR."""

import hashlib
import io
from pathlib import Path
import plistlib
import subprocess
import sys
import tempfile
from xml.etree import ElementTree
from zipfile import ZipFile


APP = "ChessMaster Royal.app"
CONTENTS = f"{APP}/Contents"
LAUNCHER = f"{CONTENTS}/MacOS/ChessMaster Royal"
RUNTIME = f"{CONTENTS}/runtime/Contents/Home/lib/libjava.dylib"
PLIST = f"{CONTENTS}/Info.plist"
ARM64_MACHO = bytes.fromhex("cffaedfe0c000001")


def jar_entries(jar):
    with ZipFile(jar) as archive:
        entries = [entry for entry in archive.infolist() if not entry.is_dir()]
        result = {entry.filename: hashlib.sha256(archive.read(entry)).digest() for entry in entries}
        if len(result) != len(entries):
            raise ValueError("JAR contains duplicate file entries")
        return result


def verify_package(read, version, expected_entries):
    plist = plistlib.loads(read(PLIST))
    if plist["CFBundleShortVersionString"] != version or plist["CFBundleIdentifier"] != "com.jiexiangtech.chess":
        raise ValueError("Wrong macOS application identity or version")
    for binary in (LAUNCHER, RUNTIME):
        if not read(binary).startswith(ARM64_MACHO):
            raise ValueError(f"Expected an Apple Silicon binary at {binary}")
    payload = io.BytesIO(read(f"{CONTENTS}/app/royal-chess-{version}-all.jar"))
    if jar_entries(payload) != expected_entries:
        raise ValueError("The packaged application differs from the JAR tested by CI")


def main():
    if sys.argv[1:] == ["--version"]:
        pom = Path(__file__).resolve().parent.parent / "pom.xml"
        print(ElementTree.parse(pom).getroot().findtext("{http://maven.apache.org/POM/4.0.0}version"))
        return

    version, jar, packages = sys.argv[1:]
    expected_entries = jar_entries(jar)
    packages = Path(packages).resolve()
    with ZipFile(packages / "royal-chess-macos-arm64.zip") as archive:
        if not archive.getinfo(LAUNCHER).external_attr & (0o100 << 16):
            raise ValueError("The macOS ZIP launcher is missing its execute permission")
        verify_package(archive.read, version, expected_entries)

    with tempfile.TemporaryDirectory() as unpacked:
        volume = "ChessMaster Royal"
        required = (PLIST, LAUNCHER, RUNTIME, f"{CONTENTS}/app/royal-chess-{version}-all.jar")
        subprocess.run(["7z", "x", "-y", "-bso0", "-bsp0", f"-o{unpacked}",
                        str(packages / "royal-chess-macos-arm64.dmg"),
                        *(f"-i!{volume}/{name}" for name in required)], check=True)
        apps = list(Path(unpacked).rglob(APP))
        if len(apps) != 1:
            raise ValueError("The DMG must contain one ChessMaster application")
        verify_package(lambda name: (apps[0].parent / name).read_bytes(), version, expected_entries)
    print("Verified the Apple Silicon DMG, ZIP, runtime, application version and every bundled JAR entry.")


if __name__ == "__main__":
    main()
