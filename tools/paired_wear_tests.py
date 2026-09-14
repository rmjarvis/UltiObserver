#!/usr/bin/env python3
"""Run matching phone/watch instrumentation narratives on one paired emulator set."""

from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from pathlib import Path
import shutil
import subprocess
import sys
import time


DEFAULT_ADB = Path("/Users/Mike/Library/Android/sdk/platform-tools/adb")
PACKAGE_NAME = "rmjarvis.ultiobserver"
PHONE_TEST_RUNNER = f"{PACKAGE_NAME}.test/{PACKAGE_NAME}.UltiObserverTestRunner"
WATCH_TEST_RUNNER = f"{PACKAGE_NAME}.test/androidx.test.runner.AndroidJUnitRunner"
PAIRED_TEST_CLASS = f"{PACKAGE_NAME}.TestWearPairedPhoneUi"
WATCH_ONLY_TEST_CLASSES = (
    f"{PACKAGE_NAME}.TestStateClient",
    f"{PACKAGE_NAME}.TestWatchStateUi",
)
READY_FILE = "files/paired-test-ready"
DEFAULT_NARRATIVES = (
    "goalAndUndo",
    "timeAndPullViolations",
    "timeoutAndMisconduct",
    "halftimeConfirmation",
    "gameWinningGoal",
    "playerCardEntryOnWatch",
    "playerCardPhoneHandoff",
    "timedGuidance",
    "noGuidance",
    "connectionRecovery",
)


@dataclass(frozen=True)
class EmulatorPair:
    """One validated phone/watch emulator pair."""

    phone_serial: str
    phone_avd: str
    watch_serial: str
    watch_avd: str
    label: str


def parse_args() -> argparse.Namespace:
    """Parse paired-emulator and optional narrative selections."""

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--pair",
        required=True,
        metavar="PHONE_SERIAL:PHONE_AVD:WATCH_SERIAL:WATCH_AVD:LABEL",
        help="Exact connected pair and stable coverage label.",
    )
    parser.add_argument(
        "--test",
        action="append",
        default=[],
        help="Paired test method to run. May be passed multiple times.",
    )
    parser.add_argument("--adb", type=Path, default=DEFAULT_ADB)
    parser.add_argument("--gradle", default="./gradlew")
    parser.add_argument(
        "--skip-build",
        action="store_true",
        help="Use the currently assembled app and test APKs.",
    )
    return parser.parse_args()


def parse_pair(text: str) -> EmulatorPair:
    """Parse one exact phone/watch pair specification."""

    parts = text.split(":")
    if len(parts) != 5 or any(not part.strip() for part in parts):
        raise ValueError(
            "Invalid --pair; expected "
            "PHONE_SERIAL:PHONE_AVD:WATCH_SERIAL:WATCH_AVD:LABEL."
        )
    return EmulatorPair(*(part.strip() for part in parts))


def run(
    command: list[str | Path],
    root: Path,
    *,
    capture: bool = False,
    check: bool = True,
) -> subprocess.CompletedProcess[str]:
    """Run one command and fail with its complete output when requested."""

    text_command = [str(part) for part in command]
    print(f"+ {' '.join(text_command)}", flush=True)
    return subprocess.run(
        text_command,
        cwd=root,
        check=check,
        text=True,
        capture_output=capture,
    )


def adb_command(adb: Path, serial: str, *arguments: str) -> list[str | Path]:
    """Build a serial-targeted ADB command."""

    return [adb, "-s", serial, *arguments]


def avd_name(adb: Path, serial: str, root: Path) -> str:
    """Read the running AVD name for one serial."""

    result = run(adb_command(adb, serial, "emu", "avd", "name"), root, capture=True)
    return result.stdout.splitlines()[0].strip()


def validate_pair(adb: Path, pair: EmulatorPair, root: Path) -> None:
    """Refuse to install unless both dynamic serials match the requested AVDs."""

    actual_phone = avd_name(adb, pair.phone_serial, root)
    actual_watch = avd_name(adb, pair.watch_serial, root)
    if actual_phone != pair.phone_avd:
        raise ValueError(
            f"{pair.phone_serial} is {actual_phone!r}, expected {pair.phone_avd!r}."
        )
    if actual_watch != pair.watch_avd:
        raise ValueError(
            f"{pair.watch_serial} is {actual_watch!r}, expected {pair.watch_avd!r}."
        )


def build_apks(args: argparse.Namespace, root: Path) -> None:
    """Recompile paired-test code, then assemble all target APKs once."""

    # AGP's incremental compilation has occasionally left stale production or test bytecode in an
    # otherwise successfully assembled APK. Force the Kotlin compilation tasks for both halves of
    # the paired test before the normal build.
    run(
        [
            args.gradle,
            "app:compileDebugKotlin",
            "app:compileDebugAndroidTestKotlin",
            "wear:compileDebugKotlin",
            "wear:compileDebugAndroidTestKotlin",
            "--rerun-tasks",
        ],
        root,
    )

    run(
        [
            args.gradle,
            "app:assembleDebug",
            "app:assembleDebugAndroidTest",
            "wear:assembleDebug",
            "wear:assembleDebugAndroidTest",
        ],
        root,
    )


def install_apks(adb: Path, pair: EmulatorPair, root: Path) -> None:
    """Install the phone build on the phone and the Wear build on the watch."""

    installs = (
        (
            pair.phone_serial,
            root / "app/build/outputs/apk/debug/app-debug.apk",
            root / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk",
        ),
        (
            pair.watch_serial,
            root / "wear/build/outputs/apk/debug/wear-debug.apk",
            root / "wear/build/outputs/apk/androidTest/debug/wear-debug-androidTest.apk",
        ),
    )
    for serial, app_apk, test_apk in installs:
        run(adb_command(adb, serial, "install", "-r", "-t", "-d", str(app_apk)), root)
        run(adb_command(adb, serial, "install", "-r", "-t", "-d", str(test_apk)), root)
        run(adb_command(adb, serial, "shell", "pm", "clear", PACKAGE_NAME), root)


def instrumentation_arguments(
    test_name: str,
    coverage_file: str,
) -> list[str]:
    """Build arguments shared by phone and watch instrumentation runs."""

    return [
        "shell",
        "am",
        "instrument",
        "-w",
        "-r",
        "-e",
        "class",
        test_name,
        "-e",
        "coverage",
        "true",
        "-e",
        "coverageFile",
        coverage_file,
    ]


def instrumentation_command(
    adb: Path,
    serial: str,
    test_name: str,
    coverage_file: str,
    test_runner: str,
) -> list[str | Path]:
    """Build one direct instrumentation command with an explicit coverage destination."""

    return adb_command(
        adb,
        serial,
        *instrumentation_arguments(test_name, coverage_file),
        test_runner,
    )


def paired_phone_instrumentation_command(
    adb: Path,
    serial: str,
    test_name: str,
    coverage_file: str,
) -> list[str | Path]:
    """Select only phone-side partners for one coordinated paired-Wear run."""

    return adb_command(
        adb,
        serial,
        *instrumentation_arguments(test_name, coverage_file),
        "-e",
        "pairedWearOnly",
        "true",
        PHONE_TEST_RUNNER,
    )


def clear_ready_file(adb: Path, phone_serial: str, root: Path) -> None:
    """Remove the prior narrative marker from the phone app's private storage."""

    run(
        adb_command(
            adb,
            phone_serial,
            "shell",
            "run-as",
            PACKAGE_NAME,
            "rm",
            "-f",
            READY_FILE,
        ),
        root,
    )


def wait_until_phone_ready(
    adb: Path,
    pair: EmulatorPair,
    narrative: str,
    phone_process: subprocess.Popen[str],
    root: Path,
) -> None:
    """Wait until the phone fixture has published the narrative's starting state."""

    deadline = time.monotonic() + 60.0
    command = adb_command(
        adb,
        pair.phone_serial,
        "shell",
        "run-as",
        PACKAGE_NAME,
        "cat",
        READY_FILE,
    )
    while time.monotonic() < deadline:
        if phone_process.poll() is not None:
            output, _ = phone_process.communicate()
            raise RuntimeError(f"Phone fixture stopped before readiness:\n{output}")
        result = subprocess.run(
            [str(part) for part in command],
            cwd=root,
            text=True,
            capture_output=True,
            check=False,
        )
        if result.returncode == 0 and result.stdout.strip() == narrative:
            return
        time.sleep(0.25)
    raise TimeoutError(f"Phone fixture did not become ready for {narrative}.")


def assert_instrumentation_passed(side: str, output: str, returncode: int) -> None:
    """Recognize both shell failures and JUnit failures in instrumentation output."""

    print(output, end="" if output.endswith("\n") else "\n", flush=True)
    if returncode != 0 or "FAILURES!!!" in output or "INSTRUMENTATION_FAILED" in output:
        raise RuntimeError(f"{side} instrumentation failed with exit code {returncode}.")


def pull_coverage(
    adb: Path,
    serial: str,
    remote_file: str,
    destination: Path,
    root: Path,
) -> None:
    """Preserve one newly written execution file under its owning module."""

    destination.parent.mkdir(parents=True, exist_ok=True)
    run(adb_command(adb, serial, "pull", remote_file, str(destination)), root)
    if not destination.is_file() or destination.stat().st_size == 0:
        raise RuntimeError(f"Missing or empty coverage file: {destination}")


def preserve_failed_coverage(
    adb: Path,
    pair: EmulatorPair,
    narrative: str,
    phone_remote: str,
    watch_remote: str,
    root: Path,
) -> None:
    """Best-effort preservation of execution data emitted by a failed narrative."""

    for module, serial, remote in (
        ("app", pair.phone_serial, phone_remote),
        ("wear", pair.watch_serial, watch_remote),
    ):
        destination = root / (
            f"{module}/build/outputs/code_coverage/debugAndroidTest/connected/"
            f"paired-{pair.label}/{narrative}-failed/coverage.ec"
        )
        destination.parent.mkdir(parents=True, exist_ok=True)
        result = run(
            adb_command(adb, serial, "pull", remote, str(destination)),
            root,
            capture=True,
            check=False,
        )
        if result.returncode == 0:
            print(result.stdout, end="", flush=True)


def run_narrative(
    adb: Path,
    pair: EmulatorPair,
    narrative: str,
    root: Path,
) -> None:
    """Run one matching phone/watch method and preserve both execution files."""

    phone_remote = f"/sdcard/Download/ultiobserver-{pair.label}-{narrative}-phone.ec"
    watch_remote = f"/sdcard/Download/ultiobserver-{pair.label}-{narrative}-watch.ec"
    for serial, remote in (
        (pair.phone_serial, phone_remote),
        (pair.watch_serial, watch_remote),
    ):
        run(adb_command(adb, serial, "shell", "rm", "-f", remote), root)
    clear_ready_file(adb, pair.phone_serial, root)

    phone_command = paired_phone_instrumentation_command(
        adb,
        pair.phone_serial,
        f"{PAIRED_TEST_CLASS}#{narrative}",
        phone_remote,
    )
    print(f"+ {' '.join(str(part) for part in phone_command)}", flush=True)
    phone_process = subprocess.Popen(
        [str(part) for part in phone_command],
        cwd=root,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )
    try:
        wait_until_phone_ready(adb, pair, narrative, phone_process, root)
        with ThreadPoolExecutor(max_workers=1) as executor:
            if narrative == "connectionRecovery":
                recovery = executor.submit(coordinate_recovery, adb, pair, root)
            watch_result = run(
                instrumentation_command(
                    adb,
                    pair.watch_serial,
                    f"{PAIRED_TEST_CLASS}#{narrative}",
                    watch_remote,
                    WATCH_TEST_RUNNER,
                ),
                root,
                capture=True,
                check=False,
            )
            if narrative == "connectionRecovery":
                recovery.result()
        assert_instrumentation_passed("Watch", watch_result.stdout, watch_result.returncode)
        phone_output, _ = phone_process.communicate(timeout=30.0)
        assert_instrumentation_passed("Phone", phone_output, phone_process.returncode)
    except Exception:
        if phone_process.poll() is None:
            phone_process.terminate()
            phone_process.communicate(timeout=5.0)
        preserve_failed_coverage(
            adb,
            pair,
            narrative,
            phone_remote,
            watch_remote,
            root,
        )
        raise

    phone_destination = root / (
        "app/build/outputs/code_coverage/debugAndroidTest/connected/"
        f"paired-{pair.label}/{narrative}/coverage.ec"
    )
    watch_destination = root / (
        "wear/build/outputs/code_coverage/debugAndroidTest/connected/"
        f"paired-{pair.label}/{narrative}/coverage.ec"
    )
    for destination in (phone_destination, watch_destination):
        if destination.exists():
            destination.unlink()
    pull_coverage(adb, pair.phone_serial, phone_remote, phone_destination, root)
    pull_coverage(adb, pair.watch_serial, watch_remote, watch_destination, root)


def coordinate_recovery(adb: Path, pair: EmulatorPair, root: Path) -> None:
    """Relay recovery-test barriers while both instrumentation processes remain running."""

    for source, target, stage in (
        (pair.watch_serial, pair.phone_serial, "disconnect"),
        (pair.phone_serial, pair.watch_serial, "disabled"),
        (pair.watch_serial, pair.phone_serial, "restore"),
        (pair.phone_serial, pair.watch_serial, "restored"),
    ):
        marker = f"files/paired-recovery-{stage}"
        deadline = time.monotonic() + 60.0
        while time.monotonic() < deadline:
            result = subprocess.run(
                adb_command(adb, source, "shell", "run-as", PACKAGE_NAME, "test", "-f", marker),
                cwd=root, capture_output=True, check=False,
            )
            if result.returncode == 0:
                run(adb_command(adb, target, "shell", "run-as", PACKAGE_NAME, "touch", marker), root)
                break
            time.sleep(0.25)
        else:
            raise TimeoutError(f"Recovery test did not reach {stage}.")


def run_watch_only_tests(adb: Path, pair: EmulatorPair, root: Path) -> None:
    """Run deterministic watch-only UI tests once for this watch shape."""

    remote = f"/sdcard/Download/ultiobserver-{pair.label}-watch-only.ec"
    destination = root / (
        "wear/build/outputs/code_coverage/debugAndroidTest/connected/"
        f"paired-{pair.label}/watch-only/coverage.ec"
    )
    run(adb_command(adb, pair.watch_serial, "shell", "rm", "-f", remote), root)
    result = run(
        instrumentation_command(
            adb,
            pair.watch_serial,
            ",".join(WATCH_ONLY_TEST_CLASSES),
            remote,
            WATCH_TEST_RUNNER,
        ),
        root,
        capture=True,
        check=False,
    )
    assert_instrumentation_passed("Watch-only", result.stdout, result.returncode)
    if destination.exists():
        destination.unlink()
    pull_coverage(adb, pair.watch_serial, remote, destination, root)


def main() -> int:
    """Run the selected narratives on one exact pair."""

    args = parse_args()
    root = Path(__file__).resolve().parent.parent
    try:
        pair = parse_pair(args.pair)
        validate_pair(args.adb, pair, root)
        if not args.skip_build:
            build_apks(args, root)
        install_apks(args.adb, pair, root)
        if not args.test:
            print(f"\n== {pair.label}: watch-only UI tests ==", flush=True)
            run_watch_only_tests(args.adb, pair, root)
        for narrative in args.test or DEFAULT_NARRATIVES:
            print(f"\n== {pair.label}: {narrative} ==", flush=True)
            run_narrative(args.adb, pair, narrative, root)
    except (ValueError, RuntimeError, TimeoutError, subprocess.CalledProcessError) as error:
        print(error, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
