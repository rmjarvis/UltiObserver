#!/usr/bin/env python3
"""Generate phone and paired-watch documentation and Play Store screenshots."""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import time

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
ADB = Path.home() / "Library/Android/sdk/platform-tools/adb"
PACKAGE = "rmjarvis.ultiobserver"
TEST_PACKAGE = f"{PACKAGE}.test"
RUNNER = "androidx.test.runner.AndroidJUnitRunner"
PHONE_RUNNER = f"{PACKAGE}.UltiObserverTestRunner"
SEED_CLASS = f"{PACKAGE}.GenerateReleaseScreenshotArchive#generateArchive"
TEST_CLASS = f"{PACKAGE}.GenerateReleaseScreenshots#generateScreenshots"
WATCH_CLASS = f"{PACKAGE}.GenerateWatchReleaseScreenshots"
DEVICE_OUTPUT = f"/sdcard/Android/data/{PACKAGE}/files/release-screenshots"
NATIVE_SIZE = (1080, 2400)
PLAY_STORE_SIZE = (1080, 2160)
WATCH_SCREENSHOTS = {
    "WatchMainScreen.png": "watch-main-screen.png",
    "WatchTeamActions.png": "watch-team-actions.png",
    "WatchCardChoices.png": "watch-card-choices.png",
}

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
        help="Phone ADB serial. By default the running Pixel_7 AVD is selected.",
    )
    parser.add_argument("--watch-serial", help="Paired Wear_OS_Large_Round ADB serial.")
    parser.add_argument(
        "--phone-only", action="store_true", help="Capture only the phone screenshot set.",
    )
    parser.add_argument(
        "--skip-build", action="store_true", help="Reuse already-built screenshot APKs.",
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


def find_emulator(avd: str) -> str:
    devices = run([ADB, "devices"], capture_output=True).stdout.splitlines()[1:]
    serials = [line.split()[0] for line in devices if line.strip().endswith("device")]
    matches = []
    for serial in serials:
        name = adb(
            serial,
            "emu", "avd", "name",
            capture_output=True,
        ).stdout.splitlines()[0].strip()
        if name == avd:
            matches.append(serial)
    if len(matches) != 1:
        raise RuntimeError(f"Expected one running {avd} AVD, found {matches}.")
    return matches[0]


def verify_emulator(serial: str, avd: str) -> None:
    name = adb(
        serial,
        "emu", "avd", "name",
        capture_output=True,
    ).stdout.splitlines()[0].strip()
    if name != avd:
        raise RuntimeError(f"Expected {avd}, got {name} on {serial}.")


def build_and_install(serial: str, watch_serial: str | None, skip_build: bool) -> None:
    targets = [("app", serial)]
    if watch_serial is not None:
        targets.append(("wear", watch_serial))
    # Match the paired runner's workaround for stale incremental Android-test bytecode.
    compile_tasks = [
        f":{module}:{task}"
        for module, _ in targets
        for task in ("compileDebugKotlin", "compileDebugAndroidTestKotlin")
    ]
    assemble_tasks = [
        f":{module}:{task}"
        for module, _ in targets
        for task in ("assembleDebug", "assembleDebugAndroidTest")
    ]
    if not skip_build:
        run(["./gradlew", "-PincludeReleaseScreenshotTools=true", *compile_tasks, "--rerun-tasks"])
        run(["./gradlew", "-PincludeReleaseScreenshotTools=true", *assemble_tasks])
    for module, target in targets:
        adb(target, "install", "-r", f"{module}/build/outputs/apk/debug/{module}-debug.apk")
        adb(target, "install", "-r", f"{module}/build/outputs/apk/androidTest/debug/{module}-debug-androidTest.apk")
    if watch_serial is not None:
        adb(watch_serial, "shell", "pm", "grant", PACKAGE, "android.permission.POST_NOTIFICATIONS")
        adb(watch_serial, "shell", "rm", "-rf", DEVICE_OUTPUT)


def configure_emulator(serial: str) -> str:
    overlays = adb(serial, "shell", "cmd", "overlay", "list", "android", capture_output=True).stdout
    navigation = next(
        line.split()[1] for line in overlays.splitlines()
        if line.startswith("[x] com.android.internal.systemui.navbar.")
    )
    adb(serial, "shell", "cmd", "overlay", "enable-exclusive", "--category",
        "com.android.internal.systemui.navbar.gestural")
    adb(serial, "shell", "settings", "put", "system", "font_scale", "1.0")
    adb(serial, "shell", "cmd", "uimode", "night", "no")
    adb(serial, "shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb(serial, "shell", "wm", "dismiss-keyguard")
    return navigation


def reset_and_seed(serial: str) -> None:
    adb(serial, "shell", "pm", "clear", PACKAGE)
    adb(serial, "shell", "mkdir", "-p", DEVICE_OUTPUT)
    result = adb(
        serial,
        "shell",
        "am", "instrument", "-w", "-r",
        "-e", "class", SEED_CLASS,
        f"{TEST_PACKAGE}/{PHONE_RUNNER}",
        capture_output=True,
    )
    print(result.stdout)
    if "FAILURES!!!" in result.stdout or "OK (1 test)" not in result.stdout:
        raise RuntimeError("Release screenshot archive generation failed.")
    adb(serial, "shell", "cmd", "appops", "set", PACKAGE, "SCHEDULE_EXACT_ALARM", "deny")


def run_scenario(serial: str, watch_serial: str | None, staging: Path) -> None:
    command = [
        ADB, "-s", serial,
        "shell",
        "am",
        "instrument",
        "-w",
        "-r",
        "-e",
        "class",
        TEST_CLASS,
        "-e", "includeWatch", str(watch_serial is not None).lower(),
        f"{TEST_PACKAGE}/{PHONE_RUNNER}",
    ]
    stages = {"captureCardChoices", "captureMainScreen"}
    completed = set()
    log = staging / "phone-instrumentation.txt"
    with log.open("w") as output:
        process = subprocess.Popen(command, cwd=ROOT, stdout=output, stderr=subprocess.STDOUT)
        try:
            deadline = time.monotonic() + 600
            while process.poll() is None:
                if time.monotonic() > deadline:
                    raise TimeoutError("Phone screenshot narrative exceeded ten minutes.")
                if watch_serial is not None:
                    marker = subprocess.run(
                        [str(ADB), "-s", serial, "shell", "run-as", PACKAGE,
                         "cat", "files/release-watch-ready"],
                        text=True, capture_output=True, check=False,
                    ).stdout.strip()
                    if marker in stages and marker not in completed:
                        adb(watch_serial, "shell", "input", "keyevent", "KEYCODE_WAKEUP")
                        result = adb(
                            watch_serial, "shell", "am", "instrument", "-w", "-r",
                            "-e", "class", f"{WATCH_CLASS}#{marker}",
                            f"{TEST_PACKAGE}/{RUNNER}", capture_output=True,
                        )
                        print(result.stdout)
                        if "FAILURES!!!" in result.stdout or "OK (1 test)" not in result.stdout:
                            raise RuntimeError(f"Watch screenshot {marker} failed.")
                        completed.add(marker)
                        adb(serial, "shell", "run-as", PACKAGE, "touch", "files/release-watch-done")
                time.sleep(0.25)
        finally:
            if process.poll() is None:
                process.terminate()
                process.wait(timeout=10)
                adb(serial, "shell", "am", "force-stop", PACKAGE)
            print(log.read_text())
    if process.returncode != 0 or "FAILURES!!!" in log.read_text() or "OK (1 test)" not in log.read_text():
        raise RuntimeError("Screenshot instrumentation scenario failed.")
    if watch_serial is not None and completed != stages:
        raise RuntimeError(f"Missing watch stages: {stages - completed}")


def collect_and_validate(serial: str, staging: Path) -> None:
    adb(serial, "pull", DEVICE_OUTPUT, staging)
    captured = staging / "release-screenshots"
    top, bottom = map(int, (captured / "system-bars.txt").read_text().split(","))
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
                (0, top, NATIVE_SIZE[0], NATIVE_SIZE[1] - bottom),
            ).convert("RGB")
        app_viewport.save(path)
        if app_viewport.width != PLAY_STORE_SIZE[0] or app_viewport.height < PLAY_STORE_SIZE[1]:
            raise RuntimeError(f"{path.name} has unexpected cropped size {app_viewport.size}.")


def collect_watch(serial: str, staging: Path) -> None:
    destination = staging / "watch"
    destination.mkdir()
    adb(serial, "pull", DEVICE_OUTPUT, destination)
    captured = destination / "release-screenshots"
    if {path.name for path in captured.glob("*.png")} != set(WATCH_SCREENSHOTS):
        raise RuntimeError("Watch screenshot set does not match the expected captures.")
    for path in captured.glob("*.png"):
        with Image.open(path) as image:
            if image.width != image.height or image.width < 384:
                raise RuntimeError(f"Unexpected watch screenshot dimensions: {image.size}")
            opaque = image.convert("RGB")
        opaque.save(path)


def install_assets(staging: Path, include_watch: bool) -> None:
    captured = staging / "release-screenshots"
    docs = ROOT / "docs/screen-shots"
    store = ROOT / "screen_shots"
    for filename in DOC_SCREENSHOTS:
        shutil.copy2(captured / filename, docs / filename)
    for source, destination in PLAY_STORE_SCREENSHOTS.items():
        with Image.open(captured / source) as image:
            top_trim = (image.height - PLAY_STORE_SIZE[1]) // 2
            play_store_image = image.crop(
                (
                    0,
                    top_trim,
                    PLAY_STORE_SIZE[0],
                    top_trim + PLAY_STORE_SIZE[1],
                ),
            ).copy()
        play_store_image.save(store / destination)
    if include_watch:
        for source, destination in WATCH_SCREENSHOTS.items():
            image = staging / "watch/release-screenshots" / source
            shutil.copy2(image, docs / source)
            shutil.copy2(image, store / destination)


def main() -> int:
    args = parse_args()
    serial = args.serial or find_emulator("Pixel_7")
    verify_emulator(serial, "Pixel_7")
    watch_serial = None
    if not args.phone_only:
        watch_serial = args.watch_serial or find_emulator("Wear_OS_Large_Round")
        verify_emulator(watch_serial, "Wear_OS_Large_Round")
    build_and_install(serial, watch_serial, skip_build=args.skip_build)
    navigation = configure_emulator(serial)
    staging = Path(tempfile.mkdtemp(prefix="ultiobserver-release-screenshots-"))
    print(f"Captures and instrumentation log: {staging}", flush=True)
    try:
        reset_and_seed(serial)
        run_scenario(serial, watch_serial, staging)
        collect_and_validate(serial, staging)
        if watch_serial is not None:
            collect_watch(watch_serial, staging)
        if args.keep_staging:
            print(f"Validated captures kept at {staging}")
        else:
            install_assets(staging, include_watch=watch_serial is not None)
            shutil.rmtree(staging)
            print("Updated documentation and Play Store screenshot assets.")
    finally:
        adb(serial, "shell", "cmd", "appops", "set", PACKAGE, "SCHEDULE_EXACT_ALARM", "allow")
        adb(serial, "shell", "cmd", "overlay", "enable-exclusive", "--category", navigation)
    return 0


if __name__ == "__main__":
    sys.exit(main())
