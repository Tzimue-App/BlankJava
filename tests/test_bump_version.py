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


if __name__ == "__main__":
    unittest.main()
