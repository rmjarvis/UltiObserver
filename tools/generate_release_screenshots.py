#!/usr/bin/env python3
"""Generate the July 2026 documentation and Play Store screenshot narrative."""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
ADB = Path.home() / "Library/Android/sdk/platform-tools/adb"
PACKAGE = "rmjarvis.ultiobserver"
TEST_PACKAGE = f"{PACKAGE}.test"
RUNNER = "androidx.test.runner.AndroidJUnitRunner"
SEED_CLASS = f"{PACKAGE}.GenerateReleaseScreenshotArchive#generateArchive"
TEST_CLASS = f"{PACKAGE}.GenerateReleaseScreenshots#generateScreenshots"
DEVICE_OUTPUT = f"/sdcard/Android/data/{PACKAGE}/files/release-screenshots"
NATIVE_SIZE = (1080, 2400)
STATUS_BAR_HEIGHT = 132
NAVIGATION_BAR_TOP = 2337
DOC_SIZE = (1080, NAVIGATION_BAR_TOP - STATUS_BAR_HEIGHT)
PLAY_STORE_SIZE = (1080, 2160)
PLAY_STORE_TOP_TRIM = 28
PLAY_STORE_BOTTOM_TRIM = DOC_SIZE[1] - PLAY_STORE_SIZE[1] - PLAY_STORE_TOP_TRIM

DOC_SCREENSHOTS = (
    "AllArchiveGames.png",
    "ArchiveCategories.png",
    "CapAlertPermission.png",
    "CueSoundSettings.png",
    "EventLog.png",
    "FieldStartingPullTop.png",
    "FilterLevel.png",
    "FilteredSortedArchive.png",
    "GameInformationTop.png",
    "GameRules.png",
    "GameSummary.png",
    "HomePage.png",
    "LockedScreen.png",
    "OffenseSignalTimer.png",
    "Offsides.png",
    "Profile.png",
    "SavedSetupDrafts.png",
    "SettingsTop.png",
    "SetupTop.png",
    "ShareSummary.png",
    "ThirdCardPenalty.png",
    "TimeViolation.png",
    "TimeoutCountdown.png",
    "YellowCardPlayer.png",
    "YellowCardReason.png",
)

PLAY_STORE_SCREENSHOTS = {
    "HomePage.png": "home-current-game.png",
    "SetupTop.png": "game-setup.png",
    "FieldStartingPullTop.png": "field-starting-pull.png",
    "GameRules.png": "game-rules.png",
    "OffenseSignalTimer.png": "pull-timing-cue.png",
    "Offsides.png": "pull-violation.png",
    "YellowCardPlayer.png": "yellow-card-player.png",
    "GameSummary.png": "game-summary.png",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--serial",
        help="ADB serial. By default the running Pixel_8 AVD is selected.",
    )
    parser.add_argument(
        "--keep-staging",
        action="store_true",
        help="Keep the validated temporary captures instead of updating tracked assets.",
    )
    return parser.parse_args()


def run(
    command: list[str | os.PathLike[str]],
    *,
    capture_output: bool = False,
    check: bool = True,
) -> subprocess.CompletedProcess[str]:
    printable = " ".join(str(part) for part in command)
    print(f"+ {printable}", flush=True)
    return subprocess.run(
        [str(part) for part in command],
        cwd=ROOT,
        check=check,
        text=True,
        capture_output=capture_output,
    )


def adb(serial: str, *arguments: str, capture_output: bool = False) -> subprocess.CompletedProcess[str]:
    return run([ADB, "-s", serial, *arguments], capture_output=capture_output)


def find_pixel_8() -> str:
    devices = run([ADB, "devices"], capture_output=True).stdout.splitlines()[1:]
    serials = [line.split()[0] for line in devices if line.strip().endswith("device")]
    matches = []
    for serial in serials:
        name = adb(
            serial,
            "shell",
            "getprop",
            "ro.boot.qemu.avd_name",
            capture_output=True,
        ).stdout.strip()
        if name == "Pixel_8":
            matches.append(serial)
    if len(matches) != 1:
        raise RuntimeError(f"Expected one running Pixel_8 AVD, found {matches}.")
    return matches[0]


def verify_emulator(serial: str) -> None:
    name = adb(
        serial,
        "shell",
        "getprop",
        "ro.boot.qemu.avd_name",
        capture_output=True,
    ).stdout.strip()
    build_type = adb(
        serial,
        "shell",
        "getprop",
        "ro.build.type",
        capture_output=True,
    ).stdout.strip()
    api = adb(
        serial,
        "shell",
        "getprop",
        "ro.build.version.sdk",
        capture_output=True,
    ).stdout.strip()
    if name != "Pixel_8" or build_type not in {"userdebug", "eng"} or api != "35":
        raise RuntimeError(
            f"Expected Pixel_8 API 35 userdebug/eng; got {name=}, {api=}, {build_type=}.",
        )


def build_and_install(serial: str) -> None:
    environment = os.environ.copy()
    environment["ANDROID_SERIAL"] = serial
    command = ["./gradlew", "assembleDebug", "assembleDebugAndroidTest"]
    print(f"+ {' '.join(command)}", flush=True)
    subprocess.run(command, cwd=ROOT, env=environment, check=True)
    adb(serial, "install", "-r", "app/build/outputs/apk/debug/app-debug.apk")
    adb(
        serial,
        "install",
        "-r",
        "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk",
    )


def configure_emulator(serial: str) -> None:
    adb(serial, "root")
    adb(serial, "wait-for-device")
    adb(serial, "shell", "settings", "put", "global", "auto_time", "0")
    adb(serial, "shell", "settings", "put", "global", "auto_time_zone", "0")
    adb(serial, "shell", "setprop", "persist.sys.timezone", "America/New_York")
    adb(serial, "shell", "settings", "put", "system", "font_scale", "1.0")
    adb(serial, "shell", "cmd", "uimode", "night", "no")
    adb(serial, "shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb(serial, "shell", "wm", "dismiss-keyguard")


def reset_and_seed(serial: str) -> None:
    adb(serial, "shell", "pm", "clear", PACKAGE)
    adb(serial, "shell", "mkdir", "-p", DEVICE_OUTPUT)
    result = adb(
        serial,
        "shell",
        "am", "instrument", "-w", "-r",
        "-e", "class", SEED_CLASS,
        f"{TEST_PACKAGE}/{RUNNER}",
        capture_output=True,
    )
    print(result.stdout)
    if "FAILURES!!!" in result.stdout or "OK (1 test)" not in result.stdout:
        raise RuntimeError("Release screenshot archive generation failed.")
    adb(serial, "shell", "cmd", "appops", "set", PACKAGE, "SCHEDULE_EXACT_ALARM", "deny")


def run_scenario(serial: str) -> None:
    result = adb(
        serial,
        "shell",
        "am",
        "instrument",
        "-w",
        "-r",
        "-e",
        "class",
        TEST_CLASS,
        f"{TEST_PACKAGE}/{RUNNER}",
        capture_output=True,
    )
    print(result.stdout)
    if "FAILURES!!!" in result.stdout or "OK (1 test)" not in result.stdout:
        raise RuntimeError("Screenshot instrumentation scenario failed.")


def collect_and_validate(serial: str, staging: Path) -> None:
    adb(serial, "pull", DEVICE_OUTPUT, staging)
    captured = staging / "release-screenshots"
    actual = {path.name for path in captured.glob("*.png")}
    expected = set(DOC_SCREENSHOTS)
    if actual != expected:
        raise RuntimeError(
            f"Screenshot set mismatch. Missing: {sorted(expected - actual)}; "
            f"unexpected: {sorted(actual - expected)}",
        )
    for path in sorted(captured.glob("*.png")):
        with Image.open(path) as image:
            if image.size != NATIVE_SIZE:
                raise RuntimeError(f"{path.name} has unexpected dimensions {image.size}.")
            if image.mode not in {"RGB", "RGBA"}:
                raise RuntimeError(f"{path.name} has unexpected mode {image.mode}.")
            app_viewport = image.crop(
                (0, STATUS_BAR_HEIGHT, NATIVE_SIZE[0], NAVIGATION_BAR_TOP),
            ).copy()
        app_viewport.save(path)
        if app_viewport.size != DOC_SIZE:
            raise RuntimeError(f"{path.name} has unexpected cropped size {app_viewport.size}.")


def install_assets(staging: Path) -> None:
    captured = staging / "release-screenshots"
    docs = ROOT / "docs/screen-shots"
    store = ROOT / "screen_shots"
    for filename in DOC_SCREENSHOTS:
        shutil.copy2(captured / filename, docs / filename)
    for source, destination in PLAY_STORE_SCREENSHOTS.items():
        with Image.open(captured / source) as image:
            play_store_image = image.crop(
                (
                    0,
                    PLAY_STORE_TOP_TRIM,
                    PLAY_STORE_SIZE[0],
                    DOC_SIZE[1] - PLAY_STORE_BOTTOM_TRIM,
                ),
            ).copy()
        play_store_image.save(store / destination)


def restore_emulator(serial: str) -> None:
    adb(serial, "shell", "settings", "put", "global", "auto_time", "1")
    adb(serial, "shell", "settings", "put", "global", "auto_time_zone", "1")


def main() -> int:
    args = parse_args()
    serial = args.serial or find_pixel_8()
    verify_emulator(serial)
    build_and_install(serial)
    configure_emulator(serial)
    staging = Path(tempfile.mkdtemp(prefix="ultiobserver-release-screenshots-"))
    try:
        reset_and_seed(serial)
        run_scenario(serial)
        collect_and_validate(serial, staging)
        if args.keep_staging:
            print(f"Validated captures kept at {staging}")
        else:
            install_assets(staging)
            shutil.rmtree(staging)
            print("Updated documentation and Play Store screenshot assets.")
    finally:
        restore_emulator(serial)
    return 0


if __name__ == "__main__":
    sys.exit(main())
