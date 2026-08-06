#!/usr/bin/env python3
"""Generate versioned persistence fixtures with each app version's own serializers."""

from __future__ import annotations

import argparse
import shutil
import subprocess
import tempfile
from dataclasses import dataclass
from pathlib import Path


repo_root = Path(__file__).resolve().parents[1]
default_output_dir = repo_root / "app/src/test/resources/persistence-fixtures"
generator_path = Path(
    "app/src/test/java/rmjarvis/ultiobserver/PersistenceFixtureGeneratorTool.kt"
)
generator_root = repo_root / "tools/persistence-fixtures"


@dataclass(frozen=True)
class ScenarioSource:
    scenario: str
    git_tag: str | None


persisted_scenarios = {
    "current": {
        "default-buckets": ScenarioSource("default-buckets", None),
        "setup-draft": ScenarioSource("setup-draft", None),
        "active-game": ScenarioSource("active-game", None),
        "complete-current-game": ScenarioSource("complete-current-game", None),
        "completed-archive": ScenarioSource("completed-archive", None),
    },
    "v1.2": {
        "default-buckets": ScenarioSource("default-buckets", "v1.2.0"),
        "setup-draft": ScenarioSource("setup-draft", "v1.2.0"),
        "active-game": ScenarioSource("active-game", "v1.2.0"),
        "complete-current-game": ScenarioSource("complete-current-game", "v1.2.0"),
        "complete-current-hard-cap": ScenarioSource("complete-current-hard-cap", "v1.2.0"),
        "complete-current-hard-cap-now": ScenarioSource(
            "complete-current-hard-cap-now",
            "v1.2.0",
        ),
        "complete-current-hard-cap-halftime": ScenarioSource(
            "complete-current-hard-cap-halftime",
            "v1.2.0",
        ),
        "complete-current-heat-level-3": ScenarioSource(
            "complete-current-heat-level-3",
            "v1.2.0",
        ),
        "complete-current-aqi-level-3": ScenarioSource(
            "complete-current-aqi-level-3",
            "v1.2.0",
        ),
        "completed-archive": ScenarioSource("completed-archive", "v1.2.0"),
    },
    "v1.1": {
        "default-buckets": ScenarioSource("default-buckets", "v1.1.0"),
        "setup-draft": ScenarioSource("setup-draft", "v1.1.0"),
        "active-game": ScenarioSource("active-game", "v1.1.0"),
        "complete-current-game": ScenarioSource("complete-current-game", "v1.1.0"),
        "completed-archive": ScenarioSource("completed-archive", "v1.1.0"),
    },
    "v1.0": {
        "default-buckets": ScenarioSource("default-buckets", "v1.0.1"),
        "setup-draft": ScenarioSource("setup-draft", "v1.0.1"),
        "active-game": ScenarioSource("active-game", "v1.0.1"),
        "completed-archive": ScenarioSource("completed-archive", "v1.0.1"),
        "setup-saved": ScenarioSource("setup-saved", "v1.0.1"),
        "complete-current-game": ScenarioSource("complete-current-game", "v1.0.1"),
        "timeout-countdown": ScenarioSource("timeout-countdown", "v1.0.1"),
        "completed-archive-1.0.0": ScenarioSource("completed-archive", "v1.0.0"),
    },
}


gradle_task_appendix = r'''

tasks.register<JavaExec>("generatePersistenceFixtures") {
    group = "verification"
    description = "Generates app-state persistence fixtures for migration tests."

    val fixtureScenario = providers.gradleProperty("fixtureScenario")
    val outputDir = providers.gradleProperty("fixtureOutputDir")
    dependsOn("compileDebugUnitTestKotlin")

    mainClass.set("rmjarvis.ultiobserver.PersistenceFixtureGeneratorToolKt")
    classpath = tasks.named<Test>("testDebugUnitTest").get().classpath
    args(fixtureScenario.get(), outputDir.get())
    workingDir = rootProject.projectDir
}
'''


def main() -> None:
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=default_output_dir,
        help="Directory that will receive version/scenario fixture trees.",
    )
    parser.add_argument(
        "--version",
        choices=("all", *persisted_scenarios.keys()),
        default="all",
        help="Fixture set to generate.",
    )
    parser.add_argument(
        "--keep-workdirs",
        action="store_true",
        help="Keep temporary work directories for debugging failed generator runs.",
    )
    args = parser.parse_args()

    output_dir = args.output_dir.resolve()
    versions = list(persisted_scenarios.keys()) if args.version == "all" else [args.version]

    temp_root = Path(tempfile.mkdtemp(prefix="ultiobserver-fixtures-"))
    created_workdirs = []

    try:
        for version in versions:
            created_workdirs.extend(
                generate_version_fixtures(
                    version=version,
                    output_dir=output_dir / version,
                    temp_root=temp_root,
                )
            )
    finally:
        if args.keep_workdirs:
            print("Temporary work directories kept:")
            for _, workdir in created_workdirs:
                print(f"  {workdir}")
        else:
            remove_tagged_worktrees(created_workdirs)
            shutil.rmtree(temp_root, ignore_errors=True)


def generate_version_fixtures(
    version: str,
    output_dir: Path,
    temp_root: Path,
):
    """Generate every declared scenario for one persistence fixture version."""
    created_workdirs = []
    workdirs = {}
    shutil.rmtree(output_dir, ignore_errors=True)
    output_dir.mkdir(parents=True)
    for output_name, scenario in persisted_scenarios[version].items():
        workdir = workdirs.get(scenario.git_tag)
        if workdir is None:
            git_tag, workdir = create_workdir(
                git_tag=scenario.git_tag,
                temp_root=temp_root,
            )
            workdirs[git_tag] = workdir
            created_workdirs.append((git_tag, workdir))
        inject_generator(workdir, generator_source_for(version))
        run_fixture_task(
            workdir=workdir,
            scenario=scenario.scenario,
            output_dir=output_dir / output_name,
        )
    return created_workdirs


def generator_source_for(version: str) -> Path:
    """Return the Kotlin generator source file for one fixture version."""
    return generator_root / version / "PersistenceFixtureGeneratorTool.kt"


def create_workdir(
    git_tag: str | None,
    temp_root: Path,
):
    """Create a temporary source tree for the current repo or one historical tag."""
    if git_tag is None:
        workdir = temp_root / "current-worktree-copy"
        copy_current_worktree(workdir)
    else:
        workdir = temp_root / f"{git_tag}-worktree"
        run(["git", "worktree", "add", "--detach", str(workdir), git_tag], cwd=repo_root)
        copy_local_files(workdir)
    return git_tag, workdir


def remove_tagged_worktrees(workdirs) -> None:
    for git_tag, workdir in workdirs:
        if git_tag is not None:
            run(["git", "worktree", "remove", "--force", str(workdir)], cwd=repo_root)


def copy_current_worktree(workdir: Path) -> None:
    workdir.mkdir(parents=True)
    for path in (
        "build.gradle.kts",
        "settings.gradle.kts",
        "gradle.properties",
        "gradlew",
        "gradlew.bat",
        "gradle",
        "app/build.gradle.kts",
        "app/proguard-rules.pro",
        "app/src",
    ):
        copy_project_path(path, workdir)
    copy_local_files(workdir)


def copy_project_path(path: str, workdir: Path) -> None:
    source = repo_root / path
    target = workdir / path
    target.parent.mkdir(parents=True, exist_ok=True)
    if source.is_dir():
        shutil.copytree(source, target)
    else:
        shutil.copy2(source, target)


def copy_local_files(workdir: Path) -> None:
    for path in ("local.properties", "app/google-services.json"):
        source = repo_root / path
        target = workdir / path
        if source.is_file():
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)


def inject_generator(workdir: Path, generator: Path) -> None:
    generator_file = workdir / generator_path
    generator_file.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(generator, generator_file)

    build_file = workdir / "app/build.gradle.kts"
    if "generatePersistenceFixtures" not in build_file.read_text(encoding="utf-8"):
        with build_file.open("a", encoding="utf-8") as output:
            output.write(gradle_task_appendix)


def run_fixture_task(workdir: Path, scenario: str, output_dir: Path) -> None:
    output_dir.parent.mkdir(parents=True, exist_ok=True)
    run(
        [
            "./gradlew",
            "generatePersistenceFixtures",
            f"-PfixtureScenario={scenario}",
            f"-PfixtureOutputDir={output_dir}",
        ],
        cwd=workdir,
    )


def run(command: list[str], cwd: Path) -> None:
    print(f"$ {' '.join(command)}")
    subprocess.run(command, cwd=cwd, check=True)


if __name__ == "__main__":
    main()
