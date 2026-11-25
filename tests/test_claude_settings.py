"""
Checks of .claude/settings.json (issue #118): valid JSON, the secret file deny rules kept.

Run from the repository root:
    python -m pytest tests/
"""

import json
import os
import re
import unittest

SETTINGS = os.path.join(os.path.dirname(__file__), "..", ".claude", "settings.json")
ANY_DEPTH = "//**/"


def load_settings():
    with open(SETTINGS, encoding="utf-8") as f:
        return json.load(f)


def name_regex(glob, ignore_case=True):
    """Regex of a file-name glob as Claude Code matches it (gitignore style).

    Case folded by default, as checked live on Windows; ignore_case=False models a
    case-sensitive matcher (Linux, macOS: not checked). A leading ``^`` or ``!`` in a
    class is escaped: Claude Code reads it literally, not as a negation (.claude/README.md).
    """
    out, i = "", 0
    while i < len(glob):
        c = glob[i]
        if c == "*":
            out += "[^/]*"
        elif c == "?":
            out += "[^/]"
        elif c == "[":
            end = glob.index("]", i + 1)
            body = glob[i + 1 : end].replace("--", "-\\-")
            if body[:1] in ("^", "!"):
                body = "\\" + body
            out += "[" + body + "]"
            i = end
        else:
            out += re.escape(c)
        i += 1
    return re.compile(out + r"\Z", re.IGNORECASE if ignore_case else 0)


class SettingsTest(unittest.TestCase):
    def setUp(self):
        self.permissions = load_settings()["permissions"]
        self.deny = self.permissions["deny"]

    def name_rules(self, tool, ignore_case=True):
        prefix = tool + "(" + ANY_DEPTH
        return [
            name_regex(rule[len(prefix) : -1], ignore_case)
            for rule in self.deny
            if rule.startswith(prefix) and "/" not in rule[len(prefix) : -1]
        ]

    def assert_name_denied(self, tool, name, ignore_case=True):
        self.assertTrue(
            any(r.match(name) for r in self.name_rules(tool, ignore_case)),
            f"{tool} {name!r} not denied (ignore_case={ignore_case})",
        )

    def assert_name_allowed(self, tool, name, ignore_case=True):
        self.assertFalse(
            any(r.match(name) for r in self.name_rules(tool, ignore_case)),
            f"{tool} {name!r} denied (ignore_case={ignore_case})",
        )

    def test_lists_are_present(self):
        for key in ("allow", "ask", "deny"):
            self.assertIsInstance(self.permissions[key], list)

    def test_allowed_commands_kept(self):
        for rule in ("Bash(git status*)", "Bash(git diff*)", "Bash(python -m pytest tests/*)"):
            self.assertIn(rule, self.permissions["allow"])

    def test_secret_files_denied_to_read_and_edit_at_any_depth(self):
        for tool in ("Read", "Edit"):
            for target in (".env", "infra/secrets/**", "*.pem", "*.key"):
                self.assertIn(f"{tool}({ANY_DEPTH}{target})", self.deny)

    def test_env_variants_denied(self):
        names = [
            ".env", ".env.", ".env.bak", ".env.local", ".env~", ".envrc",
            ".env - Copy", ".env (1)", ".env@bak", ".env]x", ".env[x", ".envé",
            ".env\tx", ".env\x01", ".env.\tx", ".env.ex\x1f", ".env.exampl\t",
            ".env.e2e", ".env.E2E", ".env.eXtra", ".env.EX", ".env.e", ".env.ex",
            ".env.exa", ".env.exam", ".env.examp", ".env.exampl", ".env.EXAMPL",
            ".env.exampl@", ".env.example.bak", ".env.EXAMPLE.bak", ".env.example~",
        ]
        for ignore_case in (True, False):
            for tool in ("Read", "Edit"):
                for name in names:
                    self.assert_name_denied(tool, name, ignore_case)

    def test_upper_case_env_denied_when_case_is_folded(self):
        for tool in ("Read", "Edit"):
            self.assert_name_denied(tool, ".ENV")

    def test_env_example_stays_readable_and_editable(self):
        for ignore_case in (True, False):
            for tool in ("Read", "Edit"):
                self.assert_name_allowed(tool, ".env.example", ignore_case)
                self.assert_name_allowed(tool, "env.txt", ignore_case)

    def test_model_reads_negated_class_literally(self):
        # Claude Code reads [^e] and [!e] literally, so such a rule would deny .env.example
        for mark in ("^", "!"):
            glob = f".env.[{mark}e]*"
            for ignore_case in (True, False):
                regex = name_regex(glob, ignore_case)
                self.assertTrue(regex.match(".env.example"), glob)
                self.assertTrue(regex.match(f".env.{mark}x"), glob)
                self.assertFalse(regex.match(".env.bak"), glob)

    def test_shell_commands_on_secret_files_denied(self):
        for rule in (
            "Bash(cat *.env*)",
            "Bash(cat *infra/secrets*)",
            "Bash(type *.env*)",
            "Bash(Get-Content *.env*)",
            "Bash(git *diff*--no-index*)",
            "PowerShell(Get-Content *.env*)",
            "PowerShell(cat *infra/secrets*)",
            "PowerShell(cp *.env*)",
        ):
            self.assertIn(rule, self.deny)

    def test_git_diff_no_index_denied_after_git_options(self):
        # A Bash rule's * matches any text, spaces included
        rules = [
            re.compile(".*".join(re.escape(p) for p in rule[len("Bash(") : -1].split("*")) + r"\Z")
            for rule in self.deny
            if rule.startswith("Bash(git ")
        ]
        for command in (
            "git diff --no-index backend/a backend/b",
            "git --no-pager diff --no-index backend/a backend/b",
            "git -C . diff --no-index backend/a backend/b",
        ):
            self.assertTrue(any(r.match(command) for r in rules), command)
        for command in ("git diff --stat", "git log -1", "git diff origin/v0.2.1-a...HEAD"):
            self.assertFalse(any(r.match(command) for r in rules), command)


if __name__ == "__main__":
    unittest.main()
