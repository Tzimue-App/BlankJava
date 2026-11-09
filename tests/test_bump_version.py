"""
Regression tests for bump_version.py (release tooling).

Run from the repository root:
    python -m unittest discover -s tests -t . -v
"""

import contextlib
import io
import json
import os
import tempfile
import time
import unittest

import bump_version as bv


def write(path, content):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


class OpenApiVersionTest(unittest.TestCase):
    """info.version of api/openapi.yaml (code scanning alert #1, issue #9)."""

    SAMPLES = {
        "plain": ("openapi: 3.1.0\ninfo:\n  title: BlankJava API\n  version: 0.1.0\npaths: {}\n", "0.1.0"),
        "single quotes": ("openapi: 3.1.0\ninfo:\n  title: X\n  version: '1.2.3'\n", "1.2.3"),
        "double quotes": ("openapi: 3.1.0\ninfo:\n  title: X\n  version: \"2.0.0\"\n", "2.0.0"),
        "tabs": ("info:\n\ttitle: X\n\tversion: 0.0.1\n", "0.0.1"),
        "multi-line description first": (
            "openapi: 3.1.0\ninfo:\n  title: X\n  description: |\n    Line one\n    Line two\n  version: 3.4.5\n",
            "3.4.5",
        ),
    }

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.path = os.path.join(self.tmp.name, "openapi.yaml")

    def tearDown(self):
        self.tmp.cleanup()

    def test_reads_the_version_of_realistic_documents(self):
        for name, (content, expected) in self.SAMPLES.items():
            with self.subTest(name):
                write(self.path, content)
                self.assertEqual(bv.read_component_version(self.path), expected)

    def test_writes_only_the_version_and_keeps_the_rest(self):
        for name, (content, old) in self.SAMPLES.items():
            with self.subTest(name):
                write(self.path, content)
                self.assertEqual(bv.write_component_version(self.path, "9.9.9"), [self.path])
                self.assertEqual(bv.read_component_version(self.path), "9.9.9")
                self.assertEqual(read(self.path), content.replace(old, "9.9.9", 1))

    def test_no_version_in_info_returns_none(self):
        write(self.path, "info:\n  title: X\nservers:\n  - url: /api\n")
        self.assertIsNone(bv.read_component_version(self.path))

    def test_pathological_input_is_linear(self):
        # Input from the CodeQL alert: "info:\n" then whitespace-only lines and no "version:".
        # The previous pattern needed ~0.4 s for 22 lines and doubled every extra line.
        attack = "info:\n" + "\t\t\n" * 50_000
        start = time.perf_counter()
        self.assertIsNone(bv.OPENAPI_VERSION_PATTERN.search(attack))
        self.assertLess(time.perf_counter() - start, 1.0)

    def test_pathological_input_with_version_at_the_end(self):
        content = "info:\n" + "  \t \n" * 50_000 + "  version: 1.0.0\n"
        start = time.perf_counter()
        match = bv.OPENAPI_VERSION_PATTERN.search(content)
        self.assertLess(time.perf_counter() - start, 1.0)
        self.assertEqual(match.group(2), "1.0.0")


class OtherVersionFilesTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()

    def tearDown(self):
        self.tmp.cleanup()

    def test_gradle_properties(self):
        path = os.path.join(self.tmp.name, "gradle.properties")
        write(path, "group=com.blankjava\nversion=0.1.0\norg.gradle.jvmargs=-Xmx1g\n")
        self.assertEqual(bv.read_component_version(path), "0.1.0")
        bv.write_component_version(path, "0.2.0")
        self.assertEqual(read(path), "group=com.blankjava\nversion=0.2.0\norg.gradle.jvmargs=-Xmx1g\n")

    def test_package_json_and_lock_file(self):
        path = os.path.join(self.tmp.name, "package.json")
        lock = os.path.join(self.tmp.name, "package-lock.json")
        write(path, '{\n  "name": "frontend",\n  "version": "0.1.0",\n  "private": true\n}\n')
        write(lock, json.dumps({"name": "frontend", "version": "0.1.0", "lockfileVersion": 3,
                                "packages": {"": {"name": "frontend", "version": "0.1.0"}}}, indent=2) + "\n")
        self.assertEqual(bv.read_component_version(path), "0.1.0")
        self.assertEqual(sorted(bv.write_component_version(path, "0.2.0")), sorted([path, lock]))
        self.assertIn('"version": "0.2.0"', read(path))
        lock_data = json.loads(read(lock))
        self.assertEqual(lock_data["version"], "0.2.0")
        self.assertEqual(lock_data["packages"][""]["version"], "0.2.0")


class ProjectVersionTest(unittest.TestCase):
    def test_accepts_only_the_three_release_forms(self):
        self.assertEqual(bv.parse_project_version("0.1.7"), ("0.1.7", None))
        self.assertEqual(bv.parse_project_version("0.1.7-a"), ("0.1.7", "a"))
        self.assertEqual(bv.parse_project_version("v0.1.7-b"), ("0.1.7", "b"))

    def test_rejects_other_forms(self):
        for version in ("0.1.7-alpha.1", "0.1.7-beta.1", "0.1.7-c", "0.1", "latest"):
            with self.subTest(version), contextlib.redirect_stdout(io.StringIO()):
                with self.assertRaises(SystemExit):
                    bv.parse_project_version(version)

    def test_bumps(self):
        self.assertEqual(bv.bump_base_version("0.1.7", 1), "1.0.0")
        self.assertEqual(bv.bump_base_version("0.1.7", 2), "0.2.0")
        self.assertEqual(bv.bump_base_version("0.1.7", 3), "0.1.8")
        self.assertEqual(bv.bump_type_between("0.1.7", "0.2.0"), 2)
        self.assertEqual(bv.bump_type_between("0.1.7", "0.1.7"), 0)
        self.assertIsNone(bv.bump_type_between("0.1.7", "0.3.0"))


class ReleaseDryRunTest(unittest.TestCase):
    """`python bump_version.py --dry-run` on an issue or milestone branch (issue #2)."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.cwd = os.getcwd()
        os.chdir(self.tmp.name)
        os.mkdir("api")
        write("package.json", json.dumps({"name": "blankjava", "version": "0.1.0-a",
                                          "components": {"api": "api/openapi.yaml"}}, indent=2) + "\n")
        write("api/openapi.yaml", "openapi: 3.1.1\ninfo:\n  title: X\n  version: 0.0.0\npaths: {}\n")
        write("CHANGELOG.md", "# Changelog\n\n## [Unreleased]\n\nBump-api: 2\n\n### Features\n- Contract\n\n---\n")
        self.real_branch = bv.get_current_branch
        bv.get_current_branch = lambda: "chore/2-api-contract-skeleton"

    def tearDown(self):
        bv.get_current_branch = self.real_branch
        os.chdir(self.cwd)
        self.tmp.cleanup()

    def test_previews_a_merge_into_dev_and_lists_component_versions(self):
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            bv.release(ci=True, dry_run=True)
        text = out.getvalue()
        self.assertIn("previewing a merge into dev", text)
        self.assertIn("api: 0.0.0", text)
        self.assertIn("api: 0.0.0 → 0.1.0 (Minor)", text)
        self.assertIn("New version: 0.1.0-a", text)
        # Nothing written
        self.assertIn("version: 0.0.0", read("api/openapi.yaml"))

    def test_without_dry_run_an_issue_branch_is_refused(self):
        with contextlib.redirect_stdout(io.StringIO()), self.assertRaises(SystemExit):
            bv.release(ci=True, dry_run=False)


class ChangelogBumpsTest(unittest.TestCase):
    CHANGELOG = (
        "# Changelog\n\n## [Unreleased]\n\n<!--\nBump-api: 1 (example in the help comment)\n-->\n"
        "Bump-api: 2\nBump-backend: 0\n\n### Features\n\n---\n"
    )

    def test_reads_bumps_and_ignores_the_help_comment(self):
        self.assertEqual(bv.extract_component_bumps(self.CHANGELOG), {"api": 2, "backend": 0})

    def test_a_bump_cannot_be_lowered_over_a_milestone(self):
        components = {"api": "api/openapi.yaml"}
        with contextlib.redirect_stdout(io.StringIO()):
            errors = bv.validate_bumps({"api": 3}, components, base_bumps={"api": 2})
        self.assertTrue(any("cannot be lowered" in e for e in errors), errors)


class BackMergeTest(unittest.TestCase):
    """
    Back-merges into dev keep dev's project version (issue #54), on a real repository with a
    bare 'origin'. Before the fix, test → dev brought 0.1.0-b into dev, and merging dev into
    the open milestone branch v0.2.0-a replaced its version.
    """

    GIT_ENV = {"GIT_CONFIG_NOSYSTEM": "1", "GIT_CONFIG_GLOBAL": os.devnull, "GIT_AUTHOR_NAME": "t",
               "GIT_AUTHOR_EMAIL": "t@example.test", "GIT_COMMITTER_NAME": "t",
               "GIT_COMMITTER_EMAIL": "t@example.test"}

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.cwd = os.getcwd()
        # Isolated from the machine's Git configuration (autocrlf, signing, default branch…)
        self.saved_env = {k: os.environ.get(k) for k in self.GIT_ENV}
        os.environ.update(self.GIT_ENV)
        self.real_which = bv.shutil.which
        bv.shutil.which = lambda name: None  # never open a real pull request
        origin = os.path.join(self.tmp.name, "origin.git")
        bv.run_command(f'git init -q --bare "{origin}"')
        os.chdir(self.tmp.name)
        bv.run_command(f'git clone -q "{origin}" work')
        os.chdir("work")
        # dev released v0.1.0-a, test was promoted from it
        self.commit("dev", version="0.1.0-a", changelog="## [v0.1.0-a]\n", new_branch=True)
        bv.run_command("git branch test")
        bv.run_command("git push -q origin refs/heads/dev refs/heads/test")

    def tearDown(self):
        bv.shutil.which = self.real_which
        os.chdir(self.cwd)
        for key, value in self.saved_env.items():
            if value is None:
                os.environ.pop(key, None)
            else:
                os.environ[key] = value
        self.tmp.cleanup()

    def commit(self, branch, version=None, changelog=None, other=None, new_branch=False):
        bv.run_command(f"git checkout -q {'-b ' if new_branch else ''}{branch}")
        if version:
            write("package.json", json.dumps({"name": "x", "version": version}, indent=2) + "\n")
        if changelog:
            write("CHANGELOG.md", changelog)
        if other:
            write("other.txt", other)
        bv.run_command("git add -A")
        bv.run_command(f'git commit -q -m "{branch} {version or other or "changelog"}"')

    def release_test(self, other=None):
        """test releases v0.1.0-b, then back-merges into dev, as bump_version.py does."""
        self.commit("test", version="0.1.0-b", changelog="## [v0.1.0-b]\n## [v0.1.0-a]\n", other=other)
        bv.run_command("git push -q origin refs/heads/test")
        with contextlib.redirect_stdout(io.StringIO()):
            bv.back_merge("test", "dev", "v0.1.0-b", keep_version=True)

    def origin_file(self, branch, path):
        bv.run_command("git fetch -q origin")
        return bv.run_command(f"git show origin/{branch}:{path}")

    def origin_version(self, branch):
        return json.loads(self.origin_file(branch, "package.json"))["version"]

    def test_dev_keeps_its_version_and_gets_the_rest(self):
        self.release_test()
        self.assertEqual(self.origin_version("dev"), "0.1.0-a")
        self.assertIn("v0.1.0-b", self.origin_file("dev", "CHANGELOG.md"))

    def test_a_conflict_on_the_version_only_is_resolved_for_dev(self):
        # dev moved on (the next milestone was merged) before test released
        self.commit("dev", version="0.2.0-a")
        bv.run_command("git push -q origin refs/heads/dev")
        self.release_test()
        self.assertEqual(self.origin_version("dev"), "0.2.0-a")
        self.assertIn("v0.1.0-b", self.origin_file("dev", "CHANGELOG.md"))

    def test_an_open_milestone_merges_dev_without_losing_its_version(self):
        self.commit("v0.2.0-a", version="0.2.0-a", new_branch=True)
        self.release_test()
        bv.run_command("git checkout -q v0.2.0-a")
        bv.run_command("git merge -q --no-edit origin/dev")
        self.assertEqual(bv.get_current_version(), "0.2.0-a")
        self.assertIn("v0.1.0-b", read("CHANGELOG.md"))

    def test_any_other_conflict_is_left_to_a_pull_request(self):
        self.commit("dev", other="dev side")
        bv.run_command("git push -q origin refs/heads/dev")
        dev_before = bv.run_command("git rev-parse origin/dev")
        self.release_test(other="test side")
        bv.run_command("git fetch -q origin")
        self.assertEqual(bv.run_command("git rev-parse origin/dev"), dev_before)
        self.assertTrue(bv.run_command("git ls-remote --heads origin refs/heads/backmerge/v0.1.0-b-into-dev"))
        self.assertFalse(bv.command_succeeds("git rev-parse -q --verify MERGE_HEAD"))

    def test_main_into_test_still_takes_the_stable_version(self):
        self.commit("main", version="0.1.0", new_branch=True)
        bv.run_command("git push -q origin refs/heads/main")
        with contextlib.redirect_stdout(io.StringIO()):
            bv.back_merge("main", "test", "v0.1.0")
        self.assertEqual(self.origin_version("test"), "0.1.0")


if __name__ == "__main__":
    unittest.main()
