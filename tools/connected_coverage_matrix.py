#!/usr/bin/env python3
"""Run Android UI coverage one device at a time and preserve each coverage file."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time


DEFAULT_ADB = Path("/Users/Mike/Library/Android/sdk/platform-tools/adb")
DEFAULT_CONNECTED_COVERAGE_DIR = Path(
    "app/build/outputs/code_coverage/debugAndroidTest/connected"
)
DEFAULT_PRESERVED_COVERAGE_DIR = Path(
    "app/build/outputs/code_coverage/debugAndroidTest/preserved-matrix"
)
PACKAGE_NAME = "rmjarvis.ultiobserver"
TEST_RUNNER = "rmjarvis.ultiobserver.test/androidx.test.runner.AndroidJUnitRunner"
EXACT_ALARM_MODES = {"allow", "deny", "skip"}
COVERAGE_MODES = {"direct", "gradle"}
JACOCO_CLASS_MISMATCH_TEXT = "Classes in bundle 'app' do not match with execution data"


@dataclass(frozen=True)
class MatrixDevice:
    """One connected device and its coverage-matrix role."""

    serial: str
    label: str
    exact_alarm_mode: str
    coverage_mode: str


def parse_args() -> argparse.Namespace:
    """Parse command-line arguments for the Android UI coverage matrix."""

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--device",
        action="append",
        default=[],
        metavar="SERIAL:LABEL[:allow|deny|skip]",
        help=(
            "Device to run, with a stable label, optional exact-alarm app-op, "
            "and optional coverage mode direct|gradle. "
            "Use one modern device with deny and another with allow for full app-shell coverage."
        ),
    )
    parser.add_argument(
        "--adb",
        type=Path,
        default=DEFAULT_ADB,
        help=f"adb executable, default: {DEFAULT_ADB}",
    )
    parser.add_argument(
        "--gradle",
        default="./gradlew",
        help="Gradle command, default: ./gradlew",
    )
    parser.add_argument(
        "--test-class",
        action="append",
        default=[],
        help="Optional instrumentation test class to run. May be passed multiple times.",
    )
    parser.add_argument(
        "--connected-coverage-dir",
        type=Path,
        default=DEFAULT_CONNECTED_COVERAGE_DIR,
        help=f"Gradle connected coverage directory, default: {DEFAULT_CONNECTED_COVERAGE_DIR}",
    )
    parser.add_argument(
        "--preserved-coverage-dir",
        type=Path,
        default=DEFAULT_PRESERVED_COVERAGE_DIR,
        help=f"Temporary preserved coverage directory, default: {DEFAULT_PRESERVED_COVERAGE_DIR}",
    )
    parser.add_argument(
        "--no-report",
        action="store_true",
        help="Skip the final filtered coverage report after preserving device coverage.",
    )
    parser.add_argument(
        "--no-coverage",
        action="store_true",
        help="Run instrumentation without collecting coverage; useful for platform behavior probes.",
    )
    parser.add_argument(
        "--keep-preserved",
        action="store_true",
        help=(
            "Do not clear the preserved coverage directory before this run. "
            "Use only when intentionally combining execution data from an earlier matching build."
        ),
    )
    parser.add_argument(
        "--report-only",
        action="store_true",
        help=(
            "Restore already-preserved same-build coverage files and regenerate the filtered "
            "coverage report without running any devices."
        ),
    )
    return parser.parse_args()


def parse_device(text: str) -> MatrixDevice:
    """Return a matrix device parsed from `SERIAL:LABEL[:APP_OP][:COVERAGE_MODE]`."""

    parts = text.split(":")
    if len(parts) not in {2, 3, 4}:
        raise ValueError(
            f"Invalid --device value {text!r}; "
            "expected SERIAL:LABEL[:APP_OP][:COVERAGE_MODE]."
        )

    serial = parts[0].strip()
    label = sanitize_label(parts[1].strip())
    exact_alarm_mode = parts[2].strip() if len(parts) == 3 else "skip"
    coverage_mode = "direct"
    if len(parts) == 4:
        exact_alarm_mode = parts[2].strip()
        coverage_mode = parts[3].strip()
    if not serial:
        raise ValueError(f"Invalid --device value {text!r}; serial is empty.")
    if not label:
        raise ValueError(f"Invalid --device value {text!r}; label is empty.")
    if exact_alarm_mode not in EXACT_ALARM_MODES:
        raise ValueError(
            f"Invalid exact-alarm mode {exact_alarm_mode!r}; "
            f"expected one of {sorted(EXACT_ALARM_MODES)}."
        )
    if coverage_mode not in COVERAGE_MODES:
        raise ValueError(
            f"Invalid coverage mode {coverage_mode!r}; "
            f"expected one of {sorted(COVERAGE_MODES)}."
        )
    if exact_alarm_mode != "skip" and coverage_mode == "gradle":
        raise ValueError(
            f"Invalid --device value {text!r}; exact-alarm {exact_alarm_mode!r} roles must "
            "use direct coverage mode. Gradle connected-test mode may reinstall or reset "
            "app-op state after the helper sets it."
        )
    return MatrixDevice(
        serial=serial,
        label=label,
        exact_alarm_mode=exact_alarm_mode,
        coverage_mode=coverage_mode,
    )


def sanitize_label(label: str) -> str:
    """Return a filesystem-safe coverage label."""

    return re.sub(r"[^A-Za-z0-9_.-]+", "_", label).strip("_")


def run(command: list[str], cwd: Path, env: dict[str, str] | None = None) -> None:
    """Run one command, echoing it first."""

    print(f"+ {' '.join(command)}", flush=True)
    subprocess.run(command, cwd=cwd, env=env, check=True)


def run_captured(command: list[str], cwd: Path, env: dict[str, str] | None = None) -> str:
    """Run one command, echoing and returning combined stdout/stderr."""

    print(f"+ {' '.join(command)}", flush=True)
    result = subprocess.run(
        command,
        cwd=cwd,
        env=env,
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    print(result.stdout, end="", flush=True)
    if result.returncode != 0:
        raise subprocess.CalledProcessError(result.returncode, command, output=result.stdout)
    return result.stdout


def prepare_coverage_build(args: argparse.Namespace, root: Path) -> None:
    """Build the app/test classes and unit-test coverage before connected instrumentation."""

    run(
        [
            args.gradle,
            "app:testDebugUnitTest",
            "app:compileDebugAndroidTestKotlin",
        ],
        cwd=root,
    )


def run_for_device(args: argparse.Namespace, device: MatrixDevice, root: Path) -> None:
    """Run instrumentation coverage on one device and preserve its execution data."""

    print(f"\n== {device.label} ({device.serial}) ==", flush=True)
    env = dict(os.environ, ANDROID_SERIAL=device.serial)

    if device.coverage_mode == "gradle":
        run_gradle_connected_coverage(args, device, root, env)
        return

    # Set the app-op after both APKs are installed.  Gradle's connected test task may reinstall
    # the app after external setup, so the script invokes instrumentation directly below.
    run([args.gradle, "app:installDebug", "app:installDebugAndroidTest"], cwd=root, env=env)
    clear_app_data(args.adb, device, root)
    set_exact_alarm_appop(args.adb, device, root)

    start_time = time.monotonic()
    coverage_file = None if args.no_coverage else f"/sdcard/Download/ultiobserver-{device.label}.ec"
    if coverage_file is not None:
        clear_device_coverage_file(args.adb, device, root, coverage_file)
    run_instrumentation(args.adb, device, root, args.test_class, coverage_file)
    elapsed = time.monotonic() - start_time
    print(f"{device.label}: instrumentation finished in {elapsed:.1f}s", flush=True)

    if coverage_file is not None:
        require_device_coverage_file(args.adb, device, root, coverage_file)
        preserve_device_coverage(
            adb=args.adb,
            preserved_coverage_dir=root / args.preserved_coverage_dir,
            device=device,
            coverage_file=coverage_file,
            root=root,
        )


def run_gradle_connected_coverage(
    args: argparse.Namespace,
    device: MatrixDevice,
    root: Path,
    env: dict[str, str],
) -> None:
    """Run connected coverage through Gradle for devices that cannot write direct coverage."""

    if device.exact_alarm_mode != "skip":
        run([args.gradle, "app:installDebug", "app:installDebugAndroidTest"], cwd=root, env=env)
        set_exact_alarm_appop(args.adb, device, root)

    if args.no_coverage:
        command = [args.gradle, "connectedDebugAndroidTest"]
    else:
        connected_coverage_dir = root / args.connected_coverage_dir
        if connected_coverage_dir.exists():
            shutil.rmtree(connected_coverage_dir)
        command = [args.gradle, "connectedDebugAndroidTest"]
    if args.test_class:
        command.append(
            "-Pandroid.testInstrumentationRunnerArguments.class=" + ",".join(args.test_class)
        )

    start_time = time.monotonic()
    run(command, cwd=root, env=env)
    elapsed = time.monotonic() - start_time
    print(f"{device.label}: Gradle instrumentation finished in {elapsed:.1f}s", flush=True)

    if not args.no_coverage:
        preserve_gradle_connected_coverage(
            connected_coverage_dir=root / args.connected_coverage_dir,
            preserved_coverage_dir=root / args.preserved_coverage_dir,
            device=device,
        )


def set_exact_alarm_appop(adb: Path, device: MatrixDevice, root: Path) -> None:
    """Set and verify the exact-alarm app-op for one device when requested."""

    if device.exact_alarm_mode == "skip":
        print(f"{device.label}: leaving exact-alarm app-op unchanged", flush=True)
        return

    command = [
        str(adb),
        "-s",
        device.serial,
        "shell",
        "cmd",
        "appops",
        "set",
        PACKAGE_NAME,
        "SCHEDULE_EXACT_ALARM",
        device.exact_alarm_mode,
    ]
    run(command, cwd=root)
    run(
        [
            str(adb),
            "-s",
            device.serial,
            "shell",
            "am",
            "force-stop",
            PACKAGE_NAME,
        ],
        cwd=root,
    )
    output = run_captured(
        [
            str(adb),
            "-s",
            device.serial,
            "shell",
            "cmd",
            "appops",
            "get",
            PACKAGE_NAME,
            "SCHEDULE_EXACT_ALARM",
        ],
        cwd=root,
    )
    expected = f"SCHEDULE_EXACT_ALARM: {device.exact_alarm_mode}"
    if expected not in output:
        raise RuntimeError(
            f"{device.label}: expected exact-alarm app-op {device.exact_alarm_mode!r}, "
            f"but Android reported:\n{output}"
        )


def clear_app_data(adb: Path, device: MatrixDevice, root: Path) -> None:
    """Reset persisted app state before one direct-instrumentation device run."""

    run(
        [
            str(adb),
            "-s",
            device.serial,
            "shell",
            "pm",
            "clear",
            PACKAGE_NAME,
        ],
        cwd=root,
    )


def clear_device_coverage_file(
    adb: Path,
    device: MatrixDevice,
    root: Path,
    coverage_file: str,
) -> None:
    """Delete the prior device-side coverage file so stale data cannot be preserved."""

    run(
        [
            str(adb),
            "-s",
            device.serial,
            "shell",
            "rm",
            "-f",
            coverage_file,
        ],
        cwd=root,
    )


def require_device_coverage_file(
    adb: Path,
    device: MatrixDevice,
    root: Path,
    coverage_file: str,
) -> None:
    """Require instrumentation to have created a new nonempty coverage file."""

    run(
        [
            str(adb),
            "-s",
            device.serial,
            "shell",
            "test",
            "-s",
            coverage_file,
        ],
        cwd=root,
    )


def run_instrumentation(
    adb: Path,
    device: MatrixDevice,
    root: Path,
    test_classes: list[str],
    coverage_file: str | None,
) -> None:
    """Run Android instrumentation with coverage enabled on one already-installed device."""

    command = [
        str(adb),
        "-s",
        device.serial,
        "shell",
        "am",
        "instrument",
        "-w",
    ]
    if coverage_file is not None:
        command.extend(
            [
                "-e",
                "coverage",
                "true",
                "-e",
                "coverageFile",
                coverage_file,
            ]
        )
    if device.exact_alarm_mode != "skip":
        command.extend(
            [
                "-e",
                "expectedExactAlarmMode",
                device.exact_alarm_mode,
            ]
        )
    if test_classes:
        command.extend(["-e", "class", ",".join(test_classes)])
    command.append(TEST_RUNNER)
    print(f"+ {' '.join(command)}", flush=True)
    result = subprocess.run(
        command,
        cwd=root,
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    print(result.stdout, end="", flush=True)
    if result.returncode != 0 or "FAILURES!!!" in result.stdout or "Error in " in result.stdout:
        raise subprocess.CalledProcessError(result.returncode, command, output=result.stdout)


def preserve_device_coverage(
    adb: Path,
    preserved_coverage_dir: Path,
    device: MatrixDevice,
    coverage_file: str,
    root: Path,
) -> None:
    """Pull one device coverage file into a stable device directory."""

    destination_dir = preserved_coverage_dir / device.label
    if destination_dir.exists():
        shutil.rmtree(destination_dir)
    destination_dir.mkdir(parents=True)

    run(
        [
            str(adb),
            "-s",
            device.serial,
            "pull",
            coverage_file,
            str(destination_dir / "coverage.ec"),
        ],
        cwd=root,
    )
    print(f"{device.label}: preserved 1 coverage file", flush=True)


def preserve_gradle_connected_coverage(
    connected_coverage_dir: Path,
    preserved_coverage_dir: Path,
    device: MatrixDevice,
) -> None:
    """Copy the one local coverage file produced by Gradle connected tests."""

    coverage_files = sorted(connected_coverage_dir.glob("**/coverage.ec"))
    if len(coverage_files) != 1:
        raise RuntimeError(
            f"Expected one Gradle coverage file for {device.label}, "
            f"found {len(coverage_files)} under {connected_coverage_dir}."
        )

    destination_dir = preserved_coverage_dir / device.label
    if destination_dir.exists():
        shutil.rmtree(destination_dir)
    destination_dir.mkdir(parents=True)
    shutil.copy2(coverage_files[0], destination_dir / "coverage.ec")
    print(f"{device.label}: preserved Gradle coverage file {coverage_files[0]}", flush=True)


def restore_preserved_coverage(preserved_coverage_dir: Path, connected_coverage_dir: Path) -> None:
    """Restore all preserved device coverage under Gradle's connected coverage tree."""

    if not preserved_coverage_dir.is_dir():
        raise FileNotFoundError(f"Preserved coverage directory not found: {preserved_coverage_dir}")

    if connected_coverage_dir.exists():
        shutil.rmtree(connected_coverage_dir)
    connected_coverage_dir.mkdir(parents=True)

    for device_dir in sorted(preserved_coverage_dir.iterdir()):
        if not device_dir.is_dir():
            continue
        destination_dir = connected_coverage_dir / device_dir.name
        shutil.copytree(device_dir, destination_dir)


def reset_preserved_coverage(preserved_coverage_dir: Path) -> None:
    """Remove preserved coverage from earlier builds so stale execution data cannot leak in."""

    if preserved_coverage_dir.exists():
        shutil.rmtree(preserved_coverage_dir)
    preserved_coverage_dir.mkdir(parents=True)


def generate_filtered_report(args: argparse.Namespace, root: Path) -> None:
    """Generate the filtered coverage report and fail if JaCoCo rejects stale execution data."""

    output = run_captured(
        [args.gradle, "app:filteredCoverageReport", "-x", "connectedDebugAndroidTest"],
        cwd=root,
    )
    if JACOCO_CLASS_MISMATCH_TEXT in output:
        raise RuntimeError(
            "JaCoCo reported class/execution-data mismatch. "
            "Discard stale preserved coverage and rerun the matrix from the same build."
        )


def main() -> int:
    """Run the Android UI coverage matrix."""

    args = parse_args()
    root = Path.cwd()
    try:
        devices = [parse_device(device) for device in args.device]
    except ValueError as error:
        print(error, file=sys.stderr)
        return 2

    if not devices and not args.report_only:
        print("Pass at least one --device SERIAL:LABEL[:MODE], or use --report-only.", file=sys.stderr)
        return 2

    if not args.no_coverage:
        preserved_coverage_dir = root / args.preserved_coverage_dir
        if args.report_only:
            if not preserved_coverage_dir.is_dir():
                print(
                    f"Preserved coverage directory not found: {preserved_coverage_dir}",
                    file=sys.stderr,
                )
                return 2
        elif args.keep_preserved:
            preserved_coverage_dir.mkdir(parents=True, exist_ok=True)
        else:
            reset_preserved_coverage(preserved_coverage_dir)
        prepare_coverage_build(args, root)

    for device in devices:
        run_for_device(args, device, root)

    if args.no_coverage:
        return 0

    restore_preserved_coverage(
        preserved_coverage_dir=root / args.preserved_coverage_dir,
        connected_coverage_dir=root / args.connected_coverage_dir,
    )
    print("Restored preserved coverage into the Gradle connected coverage tree.", flush=True)

    if not args.no_report:
        generate_filtered_report(args, root)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
