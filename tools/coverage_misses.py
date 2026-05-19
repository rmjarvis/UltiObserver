#!/usr/bin/env python3
"""List JaCoCo source lines that are still actionable after allowed-miss filtering."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET


DEFAULT_REPORT = Path("app/build/reports/jacoco/filtered/filteredCoverageReport.xml")
DEFAULT_SOURCE_ROOT = Path("app/src/main/java")
ALLOWED_MISS_COMMENTS = (
    (re.compile(r"\bnot user-reachable\b", re.IGNORECASE), "documented unreachable coroutine epilogue"),
    (re.compile(r"\bdefensive\b.*\bguard\b", re.IGNORECASE), "documented defensive guard"),
    (re.compile(r"\bno else branch\b", re.IGNORECASE), "documented exhaustive when without else"),
)
ALLOWED_COMMENT_LOOKBACK_LINES = 5
CALLBACK_NAME_PATTERN = r"(?:on[A-Za-z]\w*|performHaptic)"
CALLBACK_LAMBDA_OPENER = re.compile(
    rf"^{CALLBACK_NAME_PATTERN}\s*=\s*\{{\s*"
    r"(?:(?:[A-Za-z_]\w*\s*,\s*)*[A-Za-z_]\w*\s*->\s*)?$"
)


@dataclass(frozen=True)
class LineCounters:
    """JaCoCo counters for one source line."""

    missed_instructions: int
    covered_instructions: int
    missed_branches: int
    covered_branches: int


@dataclass(frozen=True)
class MissedLine:
    """Coverage counters and source text for one JaCoCo source line."""

    path: Path
    number: int
    missed_instructions: int
    covered_instructions: int
    missed_branches: int
    covered_branches: int
    source: str
    allowed_reason: str | None


def parse_args() -> argparse.Namespace:
    """Parse command-line options for the coverage miss report."""

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--report",
        type=Path,
        default=DEFAULT_REPORT,
        help=f"JaCoCo XML report path, default: {DEFAULT_REPORT}",
    )
    parser.add_argument(
        "--source-root",
        type=Path,
        default=DEFAULT_SOURCE_ROOT,
        help=f"Kotlin source root, default: {DEFAULT_SOURCE_ROOT}",
    )
    parser.add_argument(
        "--show-ignored",
        action="store_true",
        help="Print allowed UI misses after the actionable misses.",
    )
    parser.add_argument(
        "--path",
        action="append",
        default=[],
        help="Only print misses whose source path contains this text. May be passed multiple times.",
    )
    parser.add_argument(
        "--strict",
        action="store_true",
        help="Exit with status 1 when actionable misses remain.",
    )
    return parser.parse_args()


def line_allowed_reason(
    source_lines: list[str],
    line_number: int,
    coverage_by_line: dict[int, LineCounters],
    counters: LineCounters,
) -> str | None:
    """Return the explicit marker or narrow source pattern allowing this miss."""

    documented_reason = documented_allowed_miss_reason(source_lines, line_number)
    if documented_reason is not None:
        return documented_reason

    return callback_lambda_scaffold_reason(
        source_lines,
        line_number,
        coverage_by_line,
        counters,
    ) or composable_restart_epilogue_reason(
        source_lines,
        line_number,
        counters,
    ) or resource_cleanup_scaffold_reason(
        source_lines,
        line_number,
        counters,
    )


def documented_allowed_miss_reason(source_lines: list[str], line_number: int) -> str | None:
    """Return the reason from a nearby explicit comment that marks an allowed miss."""

    line_index = line_number - 1
    first_line_index = max(0, line_index - ALLOWED_COMMENT_LOOKBACK_LINES)
    for comment_index in range(line_index, first_line_index - 1, -1):
        source = source_lines[comment_index]
        stripped = source.strip()
        if not stripped.startswith("//"):
            continue
        for pattern, reason in ALLOWED_MISS_COMMENTS:
            if pattern.search(stripped):
                allowed_range = documented_allowed_range(source_lines, comment_index, reason)
                if allowed_range is not None and line_index in allowed_range:
                    return reason
    return None


def documented_allowed_range(
    source_lines: list[str],
    comment_index: int,
    reason: str,
) -> range | None:
    """Return the narrow source-line range authorized by an allowed-miss comment."""

    statement_index = next_code_line_after(source_lines, comment_index)
    if statement_index is None:
        return None

    statement = source_lines[statement_index].strip()
    if reason == "documented unreachable coroutine epilogue":
        if statement in {"}", "},", ")", "),"}:
            return range(statement_index, statement_index + 1)
        return None

    if reason == "documented defensive guard":
        return defensive_guard_range(source_lines, statement_index)

    if reason == "documented exhaustive when without else":
        if statement.startswith("when ("):
            return range(statement_index, statement_index + 1)
        return None

    return None


def next_code_line_after(source_lines: list[str], line_index: int) -> int | None:
    """Return the first nonblank, non-comment source line after a comment."""

    for index in range(line_index + 1, len(source_lines)):
        stripped = source_lines[index].strip()
        if not stripped or stripped.startswith("//"):
            continue
        return index
    return None


def defensive_guard_range(source_lines: list[str], statement_index: int) -> range | None:
    """Return the statement or block range covered by a defensive-guard comment."""

    statement = source_lines[statement_index].strip()
    if statement.startswith("if "):
        return range(statement_index, block_end_index(source_lines, statement_index) + 1)

    if "?: return" in statement or statement.startswith("return"):
        return range(statement_index, statement_index + 1)

    next_index = next_code_line_after(source_lines, statement_index)
    if statement.startswith("val ") and next_index is not None:
        next_statement = source_lines[next_index].strip()
        if next_statement.startswith("if "):
            return range(statement_index, block_end_index(source_lines, next_index) + 1)

    return None


def block_end_index(source_lines: list[str], start_index: int) -> int:
    """Return the end line for a simple braced Kotlin block."""

    depth = 0
    saw_open_brace = False
    for index in range(start_index, len(source_lines)):
        code = source_lines[index].split("//", 1)[0]
        depth += code.count("{")
        saw_open_brace = saw_open_brace or "{" in code
        depth -= code.count("}")
        if saw_open_brace and depth <= 0:
            return index
        if not saw_open_brace:
            return start_index
    return start_index


def callback_lambda_scaffold_reason(
    source_lines: list[str],
    line_number: int,
    coverage_by_line: dict[int, LineCounters],
    counters: LineCounters,
) -> str | None:
    """Return a reason for a covered callback body with missed lambda wrapper branches."""

    if counters.missed_branches == 0 or counters.covered_branches == 0:
        return None

    line_index = line_number - 1
    source = source_lines[line_index].strip()
    if CALLBACK_LAMBDA_OPENER.fullmatch(source) is None:
        return None

    if not lambda_body_is_covered(source_lines, line_index, coverage_by_line):
        return None

    return "UI callback lambda scaffold"


def composable_restart_epilogue_reason(
    source_lines: list[str],
    line_number: int,
    counters: LineCounters,
) -> str | None:
    """Return a reason for Compose restart-scope bookkeeping on a function close."""

    if counters.missed_instructions != 1 or counters.missed_branches != 1:
        return None
    if counters.covered_instructions == 0 or counters.covered_branches == 0:
        return None

    line_index = line_number - 1
    if source_lines[line_index].strip() != "}":
        return None

    opening_index = matching_opening_brace_index(source_lines, line_index)
    if opening_index is None:
        return None
    if not opens_composable_function(source_lines, opening_index):
        return None

    return "Compose restart-scope epilogue"


def resource_cleanup_scaffold_reason(
    source_lines: list[str],
    line_number: int,
    counters: LineCounters,
) -> str | None:
    """Return a reason for Kotlin/JDK cleanup bytecode from `use {}`."""

    source = source_lines[line_number - 1].strip()
    if ".use {" not in source:
        return None
    if counters.covered_instructions == 0:
        return None
    if counters.missed_branches != 0 or counters.covered_branches != 0:
        return None
    return "Kotlin resource-cleanup scaffold"


def lambda_body_is_covered(
    source_lines: list[str],
    opener_index: int,
    coverage_by_line: dict[int, LineCounters],
) -> bool:
    """Return whether all executable lambda-body lines are covered."""

    end_index = block_end_index(source_lines, opener_index)
    if end_index <= opener_index:
        return False

    saw_covered_body_line = False
    for index in range(opener_index + 1, end_index):
        source = source_lines[index].strip()
        if not meaningful_lambda_body_source(source):
            continue

        counters = coverage_by_line.get(index + 1)
        if counters is None:
            continue

        if counters.missed_instructions > 0 or counters.missed_branches > 0:
            return False
        if counters.covered_instructions > 0 or counters.covered_branches > 0:
            saw_covered_body_line = True

    return saw_covered_body_line


def matching_opening_brace_index(source_lines: list[str], closing_index: int) -> int | None:
    """Return the source index of the brace matched by a closing-brace line."""

    depth = 1
    for index in range(closing_index - 1, -1, -1):
        code = source_lines[index].split("//", 1)[0]
        depth += code.count("}")
        depth -= code.count("{")
        if depth == 0:
            return index
    return None


def opens_composable_function(source_lines: list[str], opening_index: int) -> bool:
    """Return whether an opening brace starts a function annotated with @Composable."""

    function_index = None
    for index in range(opening_index, max(-1, opening_index - 80), -1):
        if re.search(r"\bfun\b", source_lines[index]):
            function_index = index
            break
        if source_lines[index].strip() == "":
            return False

    if function_index is None:
        return False

    for index in range(function_index - 1, max(-1, function_index - 10), -1):
        stripped = source_lines[index].strip()
        if stripped == "":
            continue
        if stripped.startswith("@"):
            if stripped.startswith("@Composable"):
                return True
            continue
        return False

    return False


def meaningful_lambda_body_source(source: str) -> bool:
    """Return whether a lambda body source line should prove behavior coverage."""

    return bool(source) and not source.startswith("//") and source not in {"}", "},", ")", "),"}


def read_source(source_root: Path, package_name: str, source_name: str) -> tuple[Path, list[str]]:
    """Read the Kotlin source file referenced by one JaCoCo package/sourcefile pair."""

    path = source_root / package_name / source_name
    if not path.exists():
        return path, []
    return path, path.read_text().splitlines()


def collect_misses(report: Path, source_root: Path) -> list[MissedLine]:
    """Return all source lines with missed instructions or branches."""

    tree = ET.parse(report)
    root = tree.getroot()
    misses: list[MissedLine] = []

    for package in root.findall("package"):
        package_name = package.attrib["name"]
        for sourcefile in package.findall("sourcefile"):
            source_name = sourcefile.attrib["name"]
            source_path, source_lines = read_source(source_root, package_name, source_name)
            coverage_by_line = {
                int(line.attrib["nr"]): LineCounters(
                    missed_instructions=int(line.attrib["mi"]),
                    covered_instructions=int(line.attrib["ci"]),
                    missed_branches=int(line.attrib["mb"]),
                    covered_branches=int(line.attrib["cb"]),
                )
                for line in sourcefile.findall("line")
            }

            for line in sourcefile.findall("line"):
                number = int(line.attrib["nr"])
                counters = coverage_by_line[number]
                if counters.missed_instructions == 0 and counters.missed_branches == 0:
                    continue

                source = source_lines[number - 1].strip() if number <= len(source_lines) else ""
                allowed_reason = (
                    line_allowed_reason(
                        source_lines,
                        number,
                        coverage_by_line,
                        counters,
                    )
                    if source_lines
                    else None
                )
                misses.append(
                    MissedLine(
                        path=source_path,
                        number=number,
                        missed_instructions=counters.missed_instructions,
                        covered_instructions=counters.covered_instructions,
                        missed_branches=counters.missed_branches,
                        covered_branches=counters.covered_branches,
                        source=source,
                        allowed_reason=allowed_reason,
                    )
                )

    return misses


def print_lines(title: str, lines: list[MissedLine]) -> None:
    """Print one group of missed coverage lines."""

    print(title)
    if not lines:
        print("  none")
        return

    for line in lines:
        print(
            "  "
            f"{line.path}:{line.number}: "
            f"mi={line.missed_instructions} ci={line.covered_instructions} "
            f"mb={line.missed_branches} cb={line.covered_branches}"
        )
        suffix = f" [{line.allowed_reason}]" if line.allowed_reason else ""
        print(f"    {line.source}{suffix}")


def main() -> int:
    """Run the coverage miss classifier."""

    args = parse_args()
    if not args.report.exists():
        print(f"Coverage report not found: {args.report}", file=sys.stderr)
        return 2

    misses = collect_misses(args.report, args.source_root)
    if args.path:
        misses = [
            line
            for line in misses
            if any(path_filter in str(line.path) for path_filter in args.path)
        ]
    actionable = [line for line in misses if line.allowed_reason is None]
    ignored = [line for line in misses if line.allowed_reason is not None]

    print_lines("Actionable missed lines/branches:", actionable)
    print(f"\nIgnored UI misses: {len(ignored)}")
    if args.show_ignored:
        print_lines("Ignored UI missed lines/branches:", ignored)

    if args.strict and actionable:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
