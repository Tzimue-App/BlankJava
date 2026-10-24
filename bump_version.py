#!/usr/bin/env python3
"""
bump_version.py — Milestones, version bumps and PR checks for this project.

See docs/technical_analysis.md §8.

Versions:
    - Project: root package.json "version", SemVer with pre-release counters:
      X.Y.Z-alpha.0 (milestone branch) → X.Y.Z-alpha.N (dev) → X.Y.Z-beta.N (test) → X.Y.Z (main).
    - Components: one SemVer each, in the version file listed in the root package.json
      "components" field (gradle.properties, package.json or an OpenAPI .yaml).

Branches:
    dev → vX.Y.Z-a (milestone) → <type>/<issue>-<slug> (one per issue)
    issue → vX.Y.Z-a → dev → test → main, each by pull request.

CHANGELOG.md [Unreleased] holds the release notes and one line per component:
    Bump-backend: 2     (0 = unchanged, 1 = major, 2 = minor, 3 = patch)

Usage:
    python bump_version.py --start-milestone 0.2.0          # Create milestone branch v0.2.0-a from dev
    python bump_version.py --check origin/dev --head-ref v0.2.0-a   # Validate a PR (CI)
    python bump_version.py --ci                             # Release after a merge into dev/test/main (CI)
    python bump_version.py                                  # Same, interactive
    python bump_version.py --dry-run                        # Preview without writing
"""

import re
import subprocess
import sys
import datetime
import os
import argparse
import json
import shutil

# Force UTF-8 encoding on standard streams to support emojis in Windows terminals
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')
if hasattr(sys.stderr, 'reconfigure'):
    sys.stderr.reconfigure(encoding='utf-8')

# ──────────────────────────────────────────────
# Configuration
# ──────────────────────────────────────────────

PACKAGE_JSON = "package.json"
CHANGELOG_FILE = "CHANGELOG.md"

VERSION_BLOCK_PATTERN = re.compile(
    r'## \[(v[\w\.\-]+)\] - (\d{4}-\d{2}-\d{2})\n\n(.*?)\n---',
    re.DOTALL
)

PROJECT_VERSION_PATTERN = re.compile(r"^v?(\d+\.\d+\.\d+)(?:-(a|b)\.(\d+))?$")
MILESTONE_BRANCH_PATTERN = re.compile(r"^v(\d+\.\d+\.\d+)-a$")
COMPONENT_BUMP_PATTERN = re.compile(r"^\s*bump-([\w\-]+)\s*:\s*\[?(\d)\]?", re.IGNORECASE | re.MULTILINE)
OPENAPI_VERSION_PATTERN = re.compile(
    r"(^info:[ \t]*\n(?:[ \t]+.*\n)*?[ \t]+version:[ \t]*[\"']?)([^\"'\s]+)", re.MULTILINE
)
COMPONENTS_LINE_PREFIX = "**Components:**"

SECTION_HEADERS = ["Features", "Patches", "Bug Fixes", "Deployment & Configuration", "ChangeLog"]

BUMP_LABELS = {0: "Unchanged", 1: "Major", 2: "Minor", 3: "Patch"}

# Release branch -> stage of the version it produces
BRANCH_STAGE_MAP = {
    "dev": "a",
    "test": "b",
    "main": None,
}

# Base branch -> head branches allowed to open a PR into it (None = issue branches)
BRANCH_FLOW = {
    "dev": "a milestone branch vX.Y.Z-a",
    "test": "dev",
    "main": "test",
}


def default_unreleased_template(components):
    """Returns a fresh [Unreleased] section with one bump line per component."""
    component_lines = "".join(f"Bump-{name}: 0\n" for name in components)
    return f"""## [Unreleased]

_Description: Write the summary here..._

<!--
One line per component: 0 = unchanged, 1 = major, 2 = minor, 3 = patch.
A component whose directory changed must be bumped. Over a milestone, keep the most
significant bump (a PR may raise a value, never lower it).
The project version comes from the milestone branch (vX.Y.Z-a).
-->
{component_lines}
### Features

### Patches

### Bug Fixes

### Deployment & Configuration

### ChangeLog

---
"""


# ──────────────────────────────────────────────
# Helpers
# ──────────────────────────────────────────────

def run_command(cmd, abort_on_error=True):
    """Runs a shell command and returns stdout. Aborts on error by default."""
    result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    if result.returncode != 0 and abort_on_error:
        print(f"❌ Command failed: {cmd}")
        print(result.stderr.strip())
        sys.exit(1)
    return result.stdout.strip()


def command_succeeds(cmd):
    """Runs a shell command and returns True if it exited with 0."""
    return subprocess.run(cmd, shell=True, capture_output=True, text=True).returncode == 0


def get_current_branch():
    """Returns the current Git branch name."""
    return run_command("git rev-parse --abbrev-ref HEAD")


def read_file(path):
    """Reads a file and returns its content."""
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


def write_file(path, content):
    """Writes content to a file."""
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)


def read_package_json(path=PACKAGE_JSON):
    """Parses a package.json file."""
    try:
        return json.loads(read_file(path))
    except (OSError, ValueError) as e:
        print(f"❌ Error reading {path}: {e}")
        sys.exit(1)


def get_project_name():
    """Returns the project name from the root package.json."""
    return read_package_json().get("name", "project")


def get_components():
    """Returns {component_name: version_file} from the root package.json."""
    return read_package_json().get("components", {})


def branch_name(ref):
    """Returns the branch name of a ref like origin/dev or refs/heads/dev."""
    for prefix in ("refs/heads/", "refs/remotes/origin/", "origin/"):
        if ref.startswith(prefix):
            return ref[len(prefix):]
    return ref


# ──────────────────────────────────────────────
# Version reading and writing
# ──────────────────────────────────────────────

def parse_project_version(version):
    """Returns (base, stage, counter) for X.Y.Z, X.Y.Z-alpha.N or X.Y.Z-beta.N."""
    match = PROJECT_VERSION_PATTERN.match(version or "")
    if not match:
        print(f"❌ Unrecognised project version: {version} (expected X.Y.Z, X.Y.Z-alpha.N or X.Y.Z-beta.N)")
        sys.exit(1)
    base, stage, counter = match.groups()
    return base, stage, int(counter) if counter is not None else None


def get_current_version():
    """Reads the project version (without 'v') from the root package.json."""
    version = read_package_json().get("version")
    if not version:
        print(f"❌ Could not find version in {PACKAGE_JSON}")
        sys.exit(1)
    return version.lstrip("v")


def update_package_json_version(path, new_version):
    """Replaces the top-level "version" of a package.json, keeping its formatting."""
    content = read_file(path)
    new_content, count = re.subn(
        r'("version"\s*:\s*")[^"]*(")', rf'\g<1>{new_version}\g<2>', content, count=1
    )
    if count == 0:
        print(f"❌ No \"version\" field in {path}")
        sys.exit(1)
    write_file(path, new_content)

    # Keep package-lock.json consistent, as `npm version` would
    lock_path = os.path.join(os.path.dirname(path), "package-lock.json")
    if os.path.exists(lock_path):
        lock = json.loads(read_file(lock_path))
        lock["version"] = new_version
        if "" in lock.get("packages", {}):
            lock["packages"][""]["version"] = new_version
        write_file(lock_path, json.dumps(lock, indent=2, ensure_ascii=False) + "\n")
        return [path, lock_path]
    return [path]


def read_component_version(version_file):
    """Reads a component version from gradle.properties, package.json or an OpenAPI file."""
    if version_file.endswith("package.json"):
        return read_package_json(version_file).get("version")
    content = read_file(version_file)
    if version_file.endswith((".yaml", ".yml")):
        match = OPENAPI_VERSION_PATTERN.search(content)
        return match.group(2) if match else None
    match = re.search(r"^version\s*=\s*(\S+)\s*$", content, re.MULTILINE)
    return match.group(1) if match else None


def write_component_version(version_file, new_version):
    """Writes a component version. Returns the list of files changed."""
    if version_file.endswith("package.json"):
        return update_package_json_version(version_file, new_version)
    content = read_file(version_file)
    if version_file.endswith((".yaml", ".yml")):
        new_content, count = OPENAPI_VERSION_PATTERN.subn(rf"\g<1>{new_version}", content, count=1)
    else:
        new_content, count = re.subn(
            r"^(version\s*=\s*)\S+", rf"\g<1>{new_version}", content, count=1, flags=re.MULTILINE
        )
    if count == 0:
        print(f"❌ No version found in {version_file}")
        sys.exit(1)
    write_file(version_file, new_content)
    return [version_file]


def get_component_versions(components):
    """Returns {component_name: version} for the components whose version file exists."""
    versions = {}
    for name, version_file in components.items():
        if os.path.exists(version_file):
            versions[name] = read_component_version(version_file)
    return versions


def format_components_line(versions, changed=()):
    """Returns the changelog line listing component versions."""
    if not versions:
        return ""
    parts = [
        f"{name} `{version}`" + (" (updated)" if name in changed else "")
        for name, version in versions.items()
    ]
    return f"{COMPONENTS_LINE_PREFIX} " + " · ".join(parts)


# ──────────────────────────────────────────────
# Bump extraction from CHANGELOG
# ──────────────────────────────────────────────

def extract_component_bumps(changelog_content):
    """Returns {component: bump} from the [Unreleased] section of a CHANGELOG content."""
    match = re.search(r"## \[Unreleased\](.*?)(?=\n## |\n---\s*$|$)", changelog_content, re.DOTALL)
    if not match:
        return None
    # Ignore the help comment
    unreleased = re.sub(r"<!--.*?-->", "", match.group(1), flags=re.DOTALL)
    return {name.lower(): int(value) for name, value in COMPONENT_BUMP_PATTERN.findall(unreleased)}


def get_component_bumps():
    """Returns {component: bump} from the working tree CHANGELOG.md."""
    bumps = extract_component_bumps(read_file(CHANGELOG_FILE))
    if bumps is None:
        print("❌ [Unreleased] section not found in CHANGELOG.md")
        sys.exit(1)
    return bumps


# ──────────────────────────────────────────────
# Version calculation
# ──────────────────────────────────────────────

def parse_base(version_str):
    """Returns [X, Y, Z] from a version string."""
    match = re.search(r"(\d+)\.(\d+)\.(\d+)", version_str or "")
    if not match:
        print(f"❌ Unrecognised version format: {version_str}")
        sys.exit(1)
    return [int(match.group(1)), int(match.group(2)), int(match.group(3))]


def bump_base_version(version_str, bump_type):
    """Applies a SemVer bump to the X.Y.Z base of a version and returns X.Y.Z."""
    parts = parse_base(version_str)

    if bump_type == 1:
        parts = [parts[0] + 1, 0, 0]
    elif bump_type == 2:
        parts = [parts[0], parts[1] + 1, 0]
    elif bump_type == 3:
        parts = [parts[0], parts[1], parts[2] + 1]
    else:
        print(f"❌ Invalid bump type: {bump_type}. Must be 1, 2 or 3.")
        sys.exit(1)

    return f"{parts[0]}.{parts[1]}.{parts[2]}"


def bump_type_between(previous_base, target_base):
    """Returns 1/2/3 if target is the next major/minor/patch of previous, 0 if equal, None otherwise."""
    if previous_base == target_base:
        return 0
    for bump in (1, 2, 3):
        if bump_base_version(previous_base, bump) == target_base:
            return bump
    return None


def next_counter(base, stage):
    """Returns 1 + the highest N among existing tags vBASE-STAGE.N."""
    tags = run_command(f'git tag --list "v{base}-{stage}.*"', abort_on_error=False).splitlines()
    counters = [int(m.group(1)) for t in tags if (m := re.match(rf"^v{re.escape(base)}-{stage}\.(\d+)$", t))]
    return max(counters, default=0) + 1


def stable_tag_exists(base):
    """Returns True if the stable tag vBASE exists."""
    return bool(run_command(f"git tag --list v{base}", abort_on_error=False))


def previous_release_base(ref="HEAD"):
    """Returns the X.Y.Z base of the latest version tag reachable from ref, or 0.0.0."""
    tag = run_command(f'git describe --tags --abbrev=0 --match "v[0-9]*" {ref}', abort_on_error=False)
    return ".".join(map(str, parse_base(tag))) if tag else "0.0.0"


def validate_bumps(component_bumps, components, changed_components=(), base_bumps=None, project_bump=None):
    """Returns a list of error messages for the component bump directives."""
    errors = []
    for name in components:
        if name not in component_bumps:
            errors.append(f"'Bump-{name}' line is missing in the [Unreleased] section.")

    for name, bump in component_bumps.items():
        if name not in components:
            errors.append(f"'Bump-{name}' refers to an unknown component (known: {', '.join(components)}).")
        elif bump not in (0, 1, 2, 3):
            errors.append(f"'Bump-{name}: {bump}' is invalid. Must be 0, 1, 2 or 3.")
        elif bump and not os.path.exists(components[name]):
            errors.append(f"'Bump-{name}: {bump}' but {components[name]} does not exist yet.")

    for name in changed_components:
        if not component_bumps.get(name):
            errors.append(f"{name} changed in this PR: set 'Bump-{name}: 1|2|3' in CHANGELOG.md.")

    # Over a milestone a bump may only become more significant (smaller number)
    for name, base_bump in (base_bumps or {}).items():
        bump = component_bumps.get(name, 0)
        if base_bump and (not bump or bump > base_bump):
            errors.append(
                f"'Bump-{name}' was {base_bump} ({BUMP_LABELS[base_bump]}) on the base branch and "
                f"cannot be lowered to {bump} ({BUMP_LABELS.get(bump, '?')})."
            )

    # A component change is never more significant than the project change it ships in
    effective = [b for b in component_bumps.values() if b in (1, 2, 3)]
    if project_bump and effective and project_bump > min(effective):
        errors.append(
            f"The milestone is a {BUMP_LABELS[project_bump]} release but a component declares a "
            f"{BUMP_LABELS[min(effective)]} bump: target a {BUMP_LABELS[min(effective)].lower()} "
            f"milestone or lower the component bump."
        )
    return errors


# ──────────────────────────────────────────────
# CHANGELOG updates
# ──────────────────────────────────────────────

def get_stage_name(version):
    """Returns the human-readable stage name."""
    _, stage, _ = parse_project_version(version)
    return {"alpha": "Alpha", "beta": "Beta"}.get(stage, "Stable")


def get_status_alert(version):
    """Returns the Markdown alert for the release notes."""
    _, stage, _ = parse_project_version(version)
    if stage == "alpha":
        return "> [!WARNING]\n> **Status: Alpha.** Development build for internal integration testing."
    if stage == "beta":
        return "> [!IMPORTANT]\n> **Status: Beta.** Stabilised test build."
    return "> [!TIP]\n> **Status: Stable.** Completed milestone release."


def build_version_block(tag, components_line, notes):
    """Builds a frozen changelog block for a version tag."""
    today = datetime.date.today().isoformat()
    version_block = f"## [{tag}] - {today}\n\n"
    version_block += f"# {get_project_name()} - {get_stage_name(tag)} {tag}\n\n"
    version_block += f"{get_status_alert(tag)}\n\n"
    if components_line:
        version_block += f"{components_line}\n\n"
    if notes:
        version_block += f"{notes}\n\n"
    version_block += "---"
    return version_block


def ensure_unreleased_section(components):
    """Adds a fresh [Unreleased] section at the top of CHANGELOG.md if it has none."""
    content = read_file(CHANGELOG_FILE)
    if "## [Unreleased]" in content:
        return False
    body = re.sub(r"^# Changelog\s*", "", content)
    write_file(CHANGELOG_FILE, "# Changelog\n\n" + default_unreleased_template(components) + "\n" + body)
    return True


def freeze_unreleased(tag, components_line):
    """
    Freezes the [Unreleased] section into a dated version block,
    and inserts a fresh [Unreleased] template.
    """
    content = read_file(CHANGELOG_FILE)

    match = re.search(r"## \[Unreleased\](.*?)(\n---)", content, re.DOTALL)
    if not match:
        print("❌ [Unreleased] section not found for the update.")
        sys.exit(1)

    raw_notes = match.group(1).strip()

    # Clean: remove HTML comments, bump directives, description placeholder
    clean_notes = re.sub(r"<!--.*?-->", "", raw_notes, flags=re.DOTALL)
    clean_notes = re.sub(r"^\s*bump(-[\w\-]+)?\s*:.*$", "", clean_notes, flags=re.IGNORECASE | re.MULTILINE)
    clean_notes = re.sub(r"_Description:.*?_", "", clean_notes).strip()

    # Remove empty sections (### Header with nothing below, including the last one)
    clean_notes = re.sub(r"###[^\S\n]+\w[^\n]*\s*(?=###|\Z)", "", clean_notes).strip()
    # Collapse the blank lines left by the removed directives
    clean_notes = re.sub(r"\n{3,}", "\n\n", clean_notes)

    version_block = build_version_block(tag, components_line, clean_notes)

    new_changelog = "# Changelog\n\n"
    new_changelog += default_unreleased_template(get_components()) + "\n"
    new_changelog += version_block
    new_changelog += content[match.end():]

    write_file(CHANGELOG_FILE, new_changelog)


def parse_sections(block_body):
    """Extracts {section_name: [lines]} from the body of a version block."""
    sections = {}
    matches = re.findall(r'### (.+?)\n(.*?)(?=\n### |\Z)', block_body, re.DOTALL)
    for header, content in matches:
        header = header.strip()
        lines = [l.strip() for l in content.strip().split('\n') if l.strip()]
        if lines:
            sections.setdefault(header, []).extend(lines)
    return sections


def aggregate_alpha_blocks(tag, stop_stages):
    """
    Inserts at the top a block for `tag` that merges every Alpha block written since
    the last block whose stage is in stop_stages (beta/stable for a beta, stable for a
    stable release). Beta blocks in between are skipped: they repeat their alphas.
    The original blocks stay untouched below (history is preserved).
    """
    content = read_file(CHANGELOG_FILE)

    consumed_blocks = []
    for b in VERSION_BLOCK_PATTERN.finditer(content):
        stage = parse_project_version(b.group(1))[1]
        if stage in stop_stages:
            break
        if stage == "alpha":
            consumed_blocks.append(b)

    # Merge the sections, oldest to newest (chronological order)
    merged_sections = {}
    updated_components = set()
    for b in reversed(consumed_blocks):
        for header, lines in parse_sections(b.group(3)).items():
            merged_sections.setdefault(header, [])
            for line in lines:
                if line not in merged_sections[header]:
                    merged_sections[header].append(line)
        components_match = re.search(rf"^{re.escape(COMPONENTS_LINE_PREFIX)}(.*)$", b.group(3), re.MULTILINE)
        if components_match:
            updated_components.update(re.findall(r"([\w\-]+) `[^`]+` \(updated\)", components_match.group(1)))

    body_parts = []
    for header in SECTION_HEADERS:
        if merged_sections.get(header):
            body_parts.append(f"### {header}")
            body_parts.extend(merged_sections[header])
            body_parts.append("")
    notes_body = "\n".join(body_parts).strip()

    # Promotions do not change component versions: list the current ones
    components_line = format_components_line(get_component_versions(get_components()), updated_components)
    version_block = build_version_block(tag, components_line, notes_body)

    first_block = VERSION_BLOCK_PATTERN.search(content)
    insert_at = first_block.start() if first_block else len(content)
    new_content = content[:insert_at] + version_block + "\n\n" + content[insert_at:]
    write_file(CHANGELOG_FILE, new_content)


# ──────────────────────────────────────────────
# Git operations
# ──────────────────────────────────────────────

def git_commit(message, files):
    """Stages the given files and commits them."""
    run_command("git add " + " ".join(files))
    run_command(f'git commit -m "{message}"')


def git_tag(tag):
    """Creates an annotated tag, refusing to reuse an existing one."""
    if run_command(f"git tag --list {tag}"):
        print(f"❌ Tag {tag} already exists. Did the previous bump fail half-way?")
        sys.exit(1)
    run_command(f'git tag -a {tag} -m "Release {tag}"')


def git_push(branch, tag=None):
    """Pushes the branch (and the tag) to origin."""
    run_command(f"git push origin {branch}")
    if tag:
        run_command(f"git push origin {tag}")


def back_merge(source, target, tag):
    """
    Merges source into target. On conflict, does not force anything: pushes a
    backmerge/<tag>-into-<target> branch and opens a pull request for a human.
    """
    print(f"🔄 Back-merge {source} → {target}...")
    run_command(f"git checkout {target}")
    run_command(f"git pull --ff-only origin {target}")
    if command_succeeds(f'git merge {source} -m "chore: back-merge {source} into {target} [bump_version]"'):
        run_command(f"git push origin {target}")
        print(f"   ✅ Back-merge {source} → {target} done")
    else:
        run_command("git merge --abort", abort_on_error=False)
        branch = f"backmerge/{tag}-into-{target}"
        run_command(f"git push origin {source}:refs/heads/{branch}")
        print(f"⚠️  Back-merge {source} → {target} conflicts. Pushed {branch} for manual resolution.")
        opened = shutil.which("gh") and command_succeeds(
            f'gh pr create --base {target} --head {branch} '
            f'--title "chore: back-merge {tag} into {target}" '
            f'--body "Automatic back-merge of {source} into {target} after {tag} conflicted. '
            f'Resolve the conflicts (keep {target} versions in package.json and the newest blocks in CHANGELOG.md) and merge."'
        )
        if opened:
            print("   ✅ Back-merge pull request opened")
        else:
            print(f"::warning::Back-merge conflict: open a pull request from {branch} into {target} by hand.")
    run_command(f"git checkout {source}")


# ──────────────────────────────────────────────
# Commands
# ──────────────────────────────────────────────

def start_milestone(target, dry_run, push=True):
    """Creates the milestone branch vTARGET-a from dev with the project at TARGET-alpha.0."""
    target = target.lstrip("v")
    if not re.match(r"^\d+\.\d+\.\d+$", target):
        print(f"❌ Milestone version must be X.Y.Z, got {target}")
        sys.exit(1)

    branch = get_current_branch()
    if branch != "dev" and not dry_run:
        print(f"❌ Milestones start from 'dev'. Current branch: {branch}")
        sys.exit(1)

    current_base = parse_project_version(get_current_version())[0]
    bump = bump_type_between(current_base, target)
    if bump is None or (bump == 0 and stable_tag_exists(target)):
        allowed = [bump_base_version(current_base, b) for b in (1, 2, 3)]
        if not stable_tag_exists(current_base):
            allowed.append(f"{current_base} (fix round)")
        print(f"❌ v{target} does not follow {current_base}. Allowed: {', '.join(allowed)}")
        sys.exit(1)

    milestone_branch = f"v{target}-a"
    if run_command(f"git branch --list {milestone_branch}") or \
            run_command(f"git ls-remote --heads origin {milestone_branch}", abort_on_error=False):
        print(f"❌ Branch {milestone_branch} already exists.")
        sys.exit(1)

    kind = "fix round" if bump == 0 else BUMP_LABELS[bump].lower()
    print(f"🏁 Milestone v{target} ({kind}) → branch {milestone_branch}, project {target}-alpha.0")
    if dry_run:
        print("\n🔍 Dry-run mode — no changes applied.")
        return

    run_command(f"git checkout -b {milestone_branch}")
    files = update_package_json_version(PACKAGE_JSON, f"{target}-alpha.0")
    if ensure_unreleased_section(get_components()):
        files.append(CHANGELOG_FILE)
    git_commit(f"chore(release): start milestone v{target} [bump_version]", files)
    if push:
        run_command(f"git push -u origin {milestone_branch}")
    print(f"✅ Branch {milestone_branch} ready. Create one issue branch per issue from it.")


def check(base_ref, head_ref=None):
    """Validates the branch flow and the bump directives of a pull request."""
    base = branch_name(base_ref)
    head = branch_name(head_ref) if head_ref else None
    errors = []

    # 1. Branch flow
    if head and not head.startswith("backmerge/"):
        if base == "dev" and not MILESTONE_BRANCH_PATTERN.match(head):
            errors.append(f"PRs into dev must come from {BRANCH_FLOW['dev']}, not '{head}'.")
        elif base in ("test", "main") and head != BRANCH_FLOW[base]:
            errors.append(f"PRs into {base} must come from '{BRANCH_FLOW[base]}', not '{head}'.")
        elif MILESTONE_BRANCH_PATTERN.match(base) and (head in BRANCH_STAGE_MAP or MILESTONE_BRANCH_PATTERN.match(head)):
            errors.append(f"PRs into {base} must come from an issue branch (<type>/<issue>-<slug>), not '{head}'.")
    print(f"📌 {head or '?'} → {base}")

    # Promotions and back-merges carry versions that are already applied
    if base in ("test", "main") or (head or "").startswith("backmerge/"):
        print("ℹ️  Promotion or back-merge: version directives are not checked.")
    else:
        components = get_components()
        changed_files = run_command(f"git diff --name-only {base_ref}...HEAD").splitlines()
        changed_components = sorted(
            name for name, version_file in components.items()
            if any(f.startswith(os.path.dirname(version_file) + "/") for f in changed_files)
        )
        component_bumps = get_component_bumps()
        base_changelog = run_command(f"git show {base_ref}:{CHANGELOG_FILE}", abort_on_error=False)
        base_bumps = extract_component_bumps(base_changelog) or {}

        # 2. Project version of the milestone
        version = get_current_version()
        target, stage, counter = parse_project_version(version)
        project_bump = None
        if stage != "alpha" or counter != 0:
            errors.append(f"Project version is {version}; a milestone branch must carry X.Y.Z-alpha.0 "
                          f"(use --start-milestone).")
        else:
            branch_for_version = head if base == "dev" else base
            branch_match = MILESTONE_BRANCH_PATTERN.match(branch_for_version or "")
            if branch_match and branch_match.group(1) != target:
                errors.append(f"Project version {version} does not match milestone branch {branch_for_version}.")
            project_bump = bump_type_between(previous_release_base(base_ref), target) or None

        print(f"📋 Milestone: v{target} ({BUMP_LABELS.get(project_bump, 'fix round')})")
        print(f"📋 Changed components: {', '.join(changed_components) or 'none'}")
        for name in components:
            print(f"   Bump-{name}: {BUMP_LABELS.get(component_bumps.get(name), 'missing')}")

        # 3. Component bumps
        errors += validate_bumps(component_bumps, components, changed_components, base_bumps, project_bump)
        for name, bump in component_bumps.items():
            if bump and name in components and name not in changed_components and not base_bumps.get(name):
                print(f"⚠️  Bump-{name} is set but nothing changed under {os.path.dirname(components[name])}/.")

    if errors:
        for error in errors:
            print(f"❌ {error}")
            print(f"::error file={CHANGELOG_FILE}::{error}")
        sys.exit(1)
    print("✅ Branch flow and version directives are valid.")


def release(ci, dry_run):
    """Releases after a merge into dev (alpha), test (beta) or main (stable)."""
    print(f"🚀 {get_project_name()} — Release")
    print("=" * 40)

    branch = get_current_branch()
    if branch not in BRANCH_STAGE_MAP:
        print(f"❌ Releases happen on dev, test or main. Current branch: {branch}")
        sys.exit(1)
    print(f"📌 Branch: {branch}")

    components = get_components()
    current = get_current_version()
    base, stage, counter = parse_project_version(current)
    print(f"📦 Current version: {current}")

    # What was merged decides what is released; anything else is a no-op
    expected_stage = {"dev": ("alpha", 0), "test": ("alpha", None), "main": ("beta", None)}[branch]
    if stage != expected_stage[0] or (expected_stage[1] is not None and counter != expected_stage[1]) \
            or (branch == "test" and counter == 0):
        print(f"ℹ️  {current} is not a version to release from {branch} — nothing to do.")
        return

    component_updates = {}
    new_stage = BRANCH_STAGE_MAP[branch]
    if new_stage:
        new_version = f"{base}-{new_stage}.{next_counter(base, new_stage)}"
    else:
        new_version = base
    tag = f"v{new_version}"

    if branch == "dev":
        component_bumps = get_component_bumps()
        errors = validate_bumps(component_bumps, components)
        if errors:
            for error in errors:
                print(f"❌ {error}")
            sys.exit(1)
        for name, bump in component_bumps.items():
            if bump:
                old = read_component_version(components[name])
                component_updates[name] = bump_base_version(old, bump)
                print(f"   {name}: {old} → {component_updates[name]} ({BUMP_LABELS[bump]})")

    print(f"🆕 New version: {new_version} ({get_stage_name(new_version)})")

    if dry_run:
        print("\n🔍 Dry-run mode — no changes applied.")
        return

    if not ci:
        confirm = input(f"\n Apply {current} → {new_version}? (y/N): ").strip().lower()
        if confirm != "y":
            print("❌ Cancelled by user.")
            sys.exit(0)

    print("\n📝 Updating files...")
    changed_files = update_package_json_version(PACKAGE_JSON, new_version)
    for name, version in component_updates.items():
        changed_files += write_component_version(components[name], version)
    if branch == "dev":
        freeze_unreleased(tag, format_components_line(get_component_versions(components), component_updates))
    elif branch == "test":
        aggregate_alpha_blocks(tag, stop_stages=("beta", None))
    else:
        aggregate_alpha_blocks(tag, stop_stages=(None,))
    changed_files.append(CHANGELOG_FILE)
    for path in changed_files:
        print(f"   ✅ {path}")

    print("\n🏷️  Commit and tag...")
    git_commit(f"chore(release): {tag} [bump_version]", changed_files)
    git_tag(tag)
    print(f"   ✅ Tag {tag} created")

    print("\n📤 Pushing to origin...")
    git_push(branch, tag)
    print(f"   ✅ Pushed to {branch}")

    if branch == "test":
        back_merge("test", "dev", tag)
    elif branch == "main":
        back_merge("main", "test", tag)
        back_merge("main", "dev", tag)

    print(f"\n✅ Done: {tag}")


# ──────────────────────────────────────────────
# Main
# ──────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="Milestones, version bumps and PR checks")
    parser.add_argument("--ci", action="store_true", help="Run in CI mode (non-interactive)")
    parser.add_argument("--dry-run", action="store_true", help="Preview changes without modifying files")
    parser.add_argument("--start-milestone", metavar="X.Y.Z", help="Create the milestone branch vX.Y.Z-a from dev")
    parser.add_argument("--no-push", action="store_true", help="With --start-milestone: do not push the branch")
    parser.add_argument("--check", metavar="BASE_REF", help="Validate a PR against BASE_REF (e.g. origin/dev)")
    parser.add_argument("--head-ref", metavar="BRANCH", help="With --check: the PR head branch, for the branch-flow rules")
    args = parser.parse_args()

    if args.start_milestone:
        start_milestone(args.start_milestone, args.dry_run, push=not args.no_push)
    elif args.check:
        check(args.check, args.head_ref)
    else:
        release(args.ci, args.dry_run)


if __name__ == "__main__":
    main()
