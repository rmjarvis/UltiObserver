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
ALLOWED_COMMENT_LOOKBACK_LINES = 5
DEFENSIVE_GUARD_COMMENT = re.compile(r"\bdefensive\b.*\bguard\b", re.IGNORECASE)
EXHAUSTIVE_WHEN_COMMENT = re.compile(r"\bno else branch\b", re.IGNORECASE)
CALLBACK_NAME_PATTERN = r"(?:on[A-Za-z]\w*|performHaptic)"
CALLBACK_LAMBDA_OPENER = re.compile(
    rf"^{CALLBACK_NAME_PATTERN}\s*=\s*\{{\s*"
    r"(?:(?:[A-Za-z_]\w*\s*,\s*)*[A-Za-z_]\w*\s*->\s*)?$"
)
NO_OP_CALLBACK_LAMBDA = re.compile(
    rf"^{CALLBACK_NAME_PATTERN}\s*=\s*\{{\}},?$"
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
        "--exclude-ui",
        action="store_true",
        help="Omit UI source files from the report, useful for JVM-only coverage passes.",
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


def is_ui_source_file(path: Path) -> bool:
    """Return whether a source path is a UI/platform entry-point file."""

    name = path.name
    return (
        name.endswith("_UI.kt")
        or name in {
            "MainActivity.kt",
            "MainActivityPreview.kt",
            "TimingAlertAudio.kt",
            "TimingAlertForegroundService.kt",
            "UiComponents.kt",
        }
        or "/ui/theme/" in path.as_posix()
    )


def line_allowed_reason(
    source_lines: list[str],
    line_number: int,
    coverage_by_line: dict[int, LineCounters],
    counters: LineCounters,
) -> str | None:
    """Return the explicit marker or narrow source pattern allowing this miss."""

    return pointer_input_coroutine_epilogue_reason(
        source_lines,
        line_number,
        counters,
    ) or defensive_guard_reason(
        source_lines,
        line_number,
    ) or exhaustive_when_without_else_reason(
        source_lines,
        line_number,
    ) or version_migration_bucket_constructor_reason(
        source_lines,
        line_number,
        counters,
    ) or callback_lambda_scaffold_reason(
        source_lines,
        line_number,
        coverage_by_line,
        counters,
    ) or composable_restart_epilogue_reason(
        source_lines,
        line_number,
        counters,
    ) or composable_declaration_scaffold_reason(
        source_lines,
        line_number,
        coverage_by_line,
        counters,
    ) or compose_canvas_lambda_scaffold_reason(
        source_lines,
        line_number,
        coverage_by_line,
        counters,
    ) or compose_remember_lambda_scaffold_reason(
        source_lines,
        line_number,
        coverage_by_line,
        counters,
    ) or launched_effect_scaffold_reason(
        source_lines,
        line_number,
        coverage_by_line,
        counters,
    ) or resource_cleanup_scaffold_reason(
        source_lines,
        line_number,
        counters,
    )


def pointer_input_coroutine_epilogue_reason(
    source_lines: list[str],
    line_number: int,
    counters: LineCounters,
) -> str | None:
    """
    Return a reason for Kotlin's unreachable pointer-input coroutine epilogue.

    A long-running `pointerInput` detector, such as the live-screen unlock slider,
    normally exits because Compose cancels/restarts its coroutine.  The Kotlin compiler
    still emits a theoretical normal-return epilogue after the suspending detector call:

        .pointerInput(trackWidthPx) {
            detectDragGestures(...)
        },

    JaCoCo maps that epilogue to two source locations: the `detectDragGestures(` call
    line and the closing brace of the enclosing `pointerInput` block.  A user cannot
    deliberately exercise that normal-return path because Compose owns the coroutine
    lifecycle.

    The source shape is the guardrail.  This rule only accepts a `detectDragGestures(`
    call line or the matching `pointerInput` closing line, and only with the no-branch
    instruction-miss profiles seen for this generated epilogue.  Real drag callback
    misses stay actionable because their source lines sit inside the detector lambda,
    not on the detector call or enclosing `pointerInput` close.
    """

    line_index = line_number - 1
    statement = source_lines[line_index].strip()
    if statement == "detectDragGestures(":
        if counters.missed_instructions != 1 or counters.covered_instructions == 0:
            return None
        if counters.missed_branches != 0 or counters.covered_branches != 0:
            return None
        if nearest_enclosing_pointer_input_index(source_lines, line_index) is None:
            return None
        return "pointerInput coroutine call epilogue"

    if statement not in {"}", "},"}:
        return None

    if counters.missed_instructions != 1 or counters.covered_instructions != 0:
        return None
    if counters.missed_branches != 0 or counters.covered_branches != 0:
        return None

    opening_index = matching_opening_brace_index(source_lines, line_index)
    if opening_index is None or "pointerInput(" not in source_lines[opening_index]:
        return None
    if not block_contains_detect_drag_gestures(source_lines, opening_index, line_index):
        return None

    return "pointerInput coroutine closing epilogue"


def block_contains_detect_drag_gestures(
    source_lines: list[str],
    opening_index: int,
    closing_index: int,
) -> bool:
    """Return whether the block contains a direct `detectDragGestures(` call."""

    for index in range(opening_index + 1, closing_index):
        if source_lines[index].strip() == "detectDragGestures(":
            return True
    return False


def defensive_guard_reason(source_lines: list[str], line_number: int) -> str | None:
    """
    Return a reason for a documented defensive UI timing guard.

    A few UI flows intentionally keep tiny guard branches for awkward timing windows:
    recomposition, restored prompt state, or other Compose lifecycle edges that are
    difficult to drive deterministically through a realistic emulator story.  Those are
    allowed only when a nearby comment explicitly describes the code as a defensive
    guard and the missed line falls in the guarded statement/block.

    Model/JVM code should not use this escape hatch; those impossible states should
    usually fail loudly or be covered directly.  The filter itself stays source-local:
    it does not accept an arbitrary line merely because some earlier comment says
    "defensive guard".
    """

    line_index = line_number - 1
    comment_index = nearby_comment_index_before(source_lines, line_index, DEFENSIVE_GUARD_COMMENT)
    if comment_index is None:
        return None

    statement_index = next_code_line_after(source_lines, comment_index)
    if statement_index is None:
        return None

    allowed_range = defensive_guard_range(source_lines, statement_index)
    if allowed_range is None or line_index not in allowed_range:
        return None

    return "documented defensive guard"


def exhaustive_when_without_else_reason(source_lines: list[str], line_number: int) -> str | None:
    """
    Return a reason for Kotlin's synthetic default on a documented exhaustive `when`.

    For nullable enums we sometimes write every real value plus `null` and deliberately
    omit `else`:

        // No else branch: every SetupEditor value plus null is handled
        when (activeEditor) {
            ...
            null -> Unit
        }

    Kotlin can still emit a synthetic default branch as a runtime safety net for enum
    binaries that do not match the source the compiler saw.  That branch is not a user
    pathway in this app.  This recognizer accepts only the `when (` line immediately
    documented by the "No else branch" comment.
    """

    line_index = line_number - 1
    if not source_lines[line_index].strip().startswith("when ("):
        return None

    comment_index = nearby_comment_index_before(source_lines, line_index, EXHAUSTIVE_WHEN_COMMENT)
    if comment_index is None:
        return None
    if next_code_line_after(source_lines, comment_index) != line_index:
        return None

    return "documented exhaustive when without else"


def version_migration_bucket_constructor_reason(
    source_lines: list[str],
    line_number: int,
    counters: LineCounters,
) -> str | None:
    """Return a reason for nullable migration-table bucket slots.

    Each persistence-version step can leave an individual bucket unchanged by using a
    null converter.  The current migration table does not yet exercise every nullable
    bucket slot, but the nullability is an intentional part of the migration design.
    Keep this filter limited to the exact constructor-property shape and bytecode profile.
    """

    line_index = line_number - 1
    statement = source_lines[line_index].strip()
    if not statement.endswith("BucketMigration?,"):
        return None
    context_start = max(0, line_index - 10)
    if not any("private class VersionMigration(" in line for line in source_lines[context_start:line_index]):
        return None
    if counters.missed_instructions != 3 or counters.covered_instructions != 3:
        return None
    if counters.missed_branches != 0 or counters.covered_branches != 0:
        return None
    return "version migration nullable bucket slot"


def nearby_comment_index_before(
    source_lines: list[str],
    line_index: int,
    pattern: re.Pattern[str],
) -> int | None:
    """Return the nearest preceding comment line matching `pattern` within the allowed lookback."""

    first_line_index = max(0, line_index - ALLOWED_COMMENT_LOOKBACK_LINES)
    for comment_index in range(line_index, first_line_index - 1, -1):
        stripped = source_lines[comment_index].strip()
        if not stripped.startswith("//"):
            continue
        if pattern.search(stripped):
            return comment_index
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
    """
    Return a reason for a covered named-callback body with missed lambda wrapper branches.

    Compose and Kotlin generate branchy adapter code around callback parameters such as:

        onConfirm = { jerseyNumber ->
            recordYellowCard(jerseyNumber)
        }

    JaCoCo can report a branch miss on the `onConfirm = { ...` opener even when the
    body line that records the card was covered by a real UI action.  This recognizer
    intentionally accepts only named callback openers (`onClick`, `onConfirm`,
    `onValueChange`, etc., plus the very specific `performHaptic` callback used by
    timing-alert plumbing).  It does not accept one-line lambdas such as
    `onClick = { doThing() }`, because then the body and wrapper share one source line
    and the script cannot prove that `doThing()` actually ran.

    The body check is the important guardrail: if any executable line inside the lambda
    still has missed instructions or missed branches, the opener stays actionable.

    The exception is a named no-op callback lambda, so lines like
    `onDismissGameOverPrompt = {},` are accepted with the no-branch instruction-only
    profile because there is no function body to check coverage for.
    """

    line_index = line_number - 1
    source = source_lines[line_index].strip()
    if NO_OP_CALLBACK_LAMBDA.fullmatch(source) is not None:
        if counters.missed_branches != 0 or counters.covered_branches != 0:
            return None
        if counters.missed_instructions == 0 or counters.covered_instructions == 0:
            return None
        return "UI no-op callback lambda scaffold"

    if counters.missed_branches == 0 or counters.covered_branches == 0:
        return None

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
    """
    Return a reason for Compose restart-scope bookkeeping on a Composable close brace.

    The Compose compiler rewrites each `@Composable` function into a restartable group.
    At the end of the generated method it records an `updateScope { ... }` callback so
    Compose can re-run the function later if state read by that group changes:

        @Composable
        private fun SetupScreen(...) {
            ...
        }

    JaCoCo sometimes maps a tiny piece of that generated restart epilogue to the final
    `}` of the function.  A user cannot deliberately exercise this close-brace bytecode
    through app behavior; the meaningful coverage is whether the body of the Composable
    rendered and its callbacks ran.  This rule is narrow: it only allows a lone `}` that
    closes a real `@Composable` function and has the characteristic tiny miss profile
    we have seen from the generated restart-scope epilogue.  A composable with explicit
    early `return` statements may have one generated restart epilogue per exit path
    mapped to the same closing brace, so the miss count should be one plus the number
    of unlabeled returns in the function body.
    """

    if counters.missed_instructions != counters.missed_branches:
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

    expected_misses = 1 + unlabeled_return_count(source_lines, opening_index, line_index)
    if counters.missed_instructions != expected_misses:
        return None

    return "Compose restart-scope epilogue"


def composable_declaration_scaffold_reason(
    source_lines: list[str],
    line_number: int,
    coverage_by_line: dict[int, LineCounters],
    counters: LineCounters,
) -> str | None:
    """
    Return a reason for Compose declaration prologue/default/restart-skip scaffolding.

    Compose rewrites a declaration like:

        @Composable
        private fun HomeActions(
            onStartNewGame: () -> Unit,
            modifier: Modifier = Modifier,
        ) {
            Column(...)
        }

    into a method with hidden `Composer`, changed-flags, and default-flags parameters.
    Before the body runs, generated code calls `composer.changed(...)` for parameters,
    checks `composer.shouldExecute(...)`, and either runs the body or calls
    `composer.skipToGroupEnd()`.  After the body it records restart information via
    `endRestartGroup()?.updateScope { ... }`.

    JaCoCo maps parts of that generated machinery back to source lines that are not
    themselves user behavior:

    * The function opener `) {` can receive the `shouldExecute` / skip branch.
      Example pattern: `HomeActions(...) { ... }` or `GameListRow(...) { ... }`.

    * Defaulted parameters can receive generated default-mask branches.
      Example pattern: `modifier: Modifier = Modifier,` or `enabled: Boolean = true,`.
      Tests commonly cover either "caller supplied a value" or "Kotlin/Compose used
      the default", but not every generated mask path.

    * In some cases, such as `SmallActionButton`, JaCoCo maps restart/skip code all
      the way back to the `@Composable` annotation line.  The annotation is not
      executable app logic; it is just where the compiler-associated bookkeeping lands.

    This recognizer only applies when the line belongs to a real `@Composable fun`
    declaration.  For the annotation-line case, it also requires evidence that the
    function body has covered executable code, so an unrendered Composable is not hidden
    merely because it has an annotation.
    """

    line_index = line_number - 1
    source = source_lines[line_index].strip()
    opening_index = composable_declaration_opening_index(source_lines, line_index)
    if opening_index is None:
        return None

    if source == ") {" and counters.covered_instructions > 0 and counters.covered_branches > 0:
        return "Compose function prologue scaffold"

    if composable_default_parameter_source(source) and counters.covered_instructions > 0:
        return "Compose default-parameter scaffold"

    if (
        source == "@Composable" and
        counters.covered_instructions > 0 and
        composable_body_has_covered_code(source_lines, opening_index, coverage_by_line)
    ):
        return "Compose restart-skip scaffold"

    return None


def composable_declaration_opening_index(source_lines: list[str], line_index: int) -> int | None:
    """Return the function-opening line if this line belongs to a Composable declaration."""

    for index in range(line_index, min(len(source_lines), line_index + 60)):
        stripped = source_lines[index].strip()
        if not stripped:
            return None
        if "{" not in stripped:
            continue
        if opens_composable_function(source_lines, index):
            return index
        return None
    return None


def composable_default_parameter_source(source: str) -> bool:
    """Return whether a source line is a defaulted function parameter."""

    return ":" in source and " = " in source and source.endswith(",")


def composable_body_has_covered_code(
    source_lines: list[str],
    opening_index: int,
    coverage_by_line: dict[int, LineCounters],
) -> bool:
    """Return whether a Composable body has any covered executable line."""

    end_index = block_end_index(source_lines, opening_index)
    for index in range(opening_index + 1, end_index):
        counters = coverage_by_line.get(index + 1)
        if counters is None:
            continue
        if counters.covered_instructions > 0 or counters.covered_branches > 0:
            return True
    return False


def compose_canvas_lambda_scaffold_reason(
    source_lines: list[str],
    line_number: int,
    coverage_by_line: dict[int, LineCounters],
    counters: LineCounters,
) -> str | None:
    """
    Return a reason for Compose Canvas draw-lambda wrapper branches.

    A Canvas call has a draw lambda:

        Canvas(
            modifier = Modifier
                .width(28.dp)
                .height(48.dp),
        ) {
            val centerX = size.width / 2f
            ...
        }

    Compose wraps that draw lambda in generated callback machinery.  JaCoCo may report
    a missed branch on the `) {` opener even when the drawing statements inside the
    lambda have all run.  This is not the same as a Composable function prologue: the
    line opens the Canvas draw callback, not a function declaration.

    The rule is intentionally specific to nearby `Canvas(` calls and it requires the
    draw-lambda body to be covered.  If the arrow/path drawing lines themselves are
    missed, this recognizer will not hide the opener.
    """

    line_index = line_number - 1
    if source_lines[line_index].strip() != ") {":
        return None
    if counters.covered_instructions == 0 or counters.covered_branches == 0:
        return None

    for index in range(line_index - 1, max(-1, line_index - 8), -1):
        stripped = source_lines[index].strip()
        if stripped.startswith("Canvas("):
            if lambda_body_is_covered(source_lines, line_index, coverage_by_line):
                return "Compose Canvas lambda scaffold"
            return None
        if stripped.endswith(") {") or stripped == "}":
            return None
    return None


def compose_remember_lambda_scaffold_reason(
    source_lines: list[str],
    line_number: int,
    coverage_by_line: dict[int, LineCounters],
    counters: LineCounters,
) -> str | None:
    """
    Return a reason for Compose `remember` cache-wrapper branches.

    A remembered value such as:

        val capStatus = remember(now, state) {
            state.computeNextCapStatus(now)
        }

    is compiled into cache bookkeeping: compare the keys, check the remembered slot,
    either reuse the existing value or execute the lambda and store a new value.  UI
    tests naturally prove the app behavior by rendering the screen and covering the
    lambda body, but they do not need to force every cache-hit/cache-miss branch of
    Compose's runtime implementation.

    This recognizer only accepts ordinary or delegated `val`/`var` remember opener
    lines and only when the remembered lambda body is covered.  In the example above,
    the `state.computeNextCapStatus(now)` line still has to run; otherwise the remember
    opener remains actionable.
    """

    line_index = line_number - 1
    source = source_lines[line_index].strip()
    if not (
        source.startswith(("val ", "var "))
        and (" = remember(" in source or " by remember(" in source)
        and source.endswith("{")
    ):
        return None
    if counters.covered_instructions == 0 or counters.covered_branches == 0:
        return None
    if not lambda_body_is_covered(source_lines, line_index, coverage_by_line):
        return None
    return "Compose remember cache scaffold"


def launched_effect_scaffold_reason(
    source_lines: list[str],
    line_number: int,
    coverage_by_line: dict[int, LineCounters],
    counters: LineCounters,
) -> str | None:
    """
    Return a reason for `LaunchedEffect` coroutine state-machine scaffolding.

    A `LaunchedEffect` body is compiled as a `SuspendLambda`.  Even when the effect
    body itself is ordinary straight-line UI behavior, Kotlin emits a coroutine state
    machine with an impossible "resumed before invoke" guard.  JaCoCo maps that guard
    back to the source opener:

        LaunchedEffect(state.phase, readOnlySummary) {
            val previousPhase = previouslyObservedPhase
            ...
        }

    or to the opening line of the same block when the keys are split across lines:

        LaunchedEffect(
            state,
            now,
            readOnlySummary,
        ) {
            val suppressAutoLock = suppressNextAutoLock
            ...
        }

    The app cannot reach that generated error path through user behavior; the useful
    coverage is on the effect body lines.  The Kotlin/Compose generated scaffolding
    around a `LaunchedEffect` opener has shown two counter profiles in this codebase:
    sometimes JaCoCo reports missed guard instructions and branches, and sometimes
    those guard instructions are covered while only generated branches remain missed.

    This recognizer is intentionally narrower than "anything named LaunchedEffect":
    it only accepts the line that opens the lambda body, verifies that line belongs to
    a `LaunchedEffect` call, requires one of the observed generated-scaffold counter
    profiles, and requires covered executable code inside the effect block.  If the
    body line that changes state, opens a prompt, or calls the model is missed, that
    body line remains actionable.
    """

    line_index = line_number - 1
    if not line_opens_launched_effect_body(source_lines, line_index):
        return None
    observed_launched_effect_scaffold_profiles = {
        # Generated coroutine guard branches mapped to the effect opener.
        (4, 3),
        # Same generated scaffolding when the guard instructions are covered but branch counters remain.
        (0, 2),
    }
    if (
        counters.missed_instructions,
        counters.missed_branches,
    ) not in observed_launched_effect_scaffold_profiles:
        return None
    if counters.covered_instructions == 0 or counters.covered_branches == 0:
        return None
    if not composable_body_has_covered_code(source_lines, line_index, coverage_by_line):
        return None
    return "Compose LaunchedEffect coroutine scaffold"


def line_opens_launched_effect_body(source_lines: list[str], line_index: int) -> bool:
    """Return whether a source line opens the lambda body of a `LaunchedEffect` call."""

    source = source_lines[line_index].strip()
    if not source.endswith("{"):
        return False
    if source.startswith("LaunchedEffect("):
        return True
    if source != ") {":
        return False

    for index in range(line_index - 1, max(-1, line_index - 20), -1):
        stripped = source_lines[index].strip()
        if stripped.startswith("LaunchedEffect("):
            return True
        if stripped.endswith("{") or stripped == "}":
            return False
    return False


def nearest_enclosing_pointer_input_index(source_lines: list[str], line_index: int) -> int | None:
    """Return the closest preceding source line that opens an enclosing `pointerInput` block."""

    for index in range(line_index - 1, max(-1, line_index - 20), -1):
        code = source_lines[index].split("//", 1)[0]
        if "pointerInput(" not in code:
            continue
        if block_end_index(source_lines, index) >= line_index:
            return index
    return None


def resource_cleanup_scaffold_reason(
    source_lines: list[str],
    line_number: int,
    counters: LineCounters,
) -> str | None:
    """
    Return a reason for Kotlin/JDK cleanup bytecode from `use {}`.

    Kotlin's `use` is the closeable-resource helper behind code such as:

        tmpFile.outputStream().use { output ->
            output.write(bytes)
            output.fd.sync()
        }

    The normal app behavior is opening the stream, writing the bytes, syncing, and
    closing the stream.  Kotlin/JDK also emits exceptional cleanup paths, including
    suppression logic for cases such as "the body threw and close also threw".  JaCoCo
    can map those cleanup instructions back to the `.use {` line even though our tests
    already covered the real write path.

    This rule only accepts partially covered `.use {` lines with no branch counters.
    If opening the resource never ran, or if JaCoCo reports branch behavior on that
    line, the miss remains actionable.
    """

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

    for index in range(function_index, opening_index):
        if "{" in source_lines[index].split("//", 1)[0]:
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


def unlabeled_return_count(
    source_lines: list[str],
    opening_index: int,
    closing_index: int,
) -> int:
    """Return the count of explicit unlabeled returns that exit the outer function body."""

    count = 0
    nested_function_depth = 0
    pending_nested_function = False
    for index in range(opening_index + 1, closing_index):
        code = source_lines[index].split("//", 1)[0]
        if nested_function_depth > 0:
            nested_function_depth += code.count("{") - code.count("}")
            continue

        if pending_nested_function:
            if "{" in code:
                nested_function_depth = code.count("{") - code.count("}")
                pending_nested_function = False
            continue

        if re.search(r"\bfun\b", code):
            pending_nested_function = "{" not in code
            if not pending_nested_function:
                nested_function_depth = code.count("{") - code.count("}")
            continue

        count += len(re.findall(r"\breturn\b(?!@)", code))
    return count


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
    if args.exclude_ui:
        misses = [line for line in misses if not is_ui_source_file(line.path)]
    actionable = [line for line in misses if line.allowed_reason is None]
    ignored = [line for line in misses if line.allowed_reason is not None]

    print_lines("Actionable missed lines/branches:", actionable)
    ignored_title = "Ignored non-actionable misses" if args.exclude_ui else "Ignored UI misses"
    print(f"\n{ignored_title}: {len(ignored)}")
    if args.show_ignored:
        print_lines(f"{ignored_title}:", ignored)

    if args.strict and actionable:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
