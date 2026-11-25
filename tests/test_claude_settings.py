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


def name_regex(glob):
    """Regex of a file-name glob as Claude Code matches it (gitignore style, case folded)."""
    out, i = "", 0
    while i < len(glob):
        c = glob[i]
        if c == "*":
            out += "[^/]*"
        elif c == "?":
            out += "[^/]"
        elif c == "[":
            end = glob.index("]", i + 1)
            out += "[" + glob[i + 1 : end].replace("--", "-\\-") + "]"
            i = end
        else:
            out += re.escape(c)
        i += 1
    return re.compile(out + r"\Z", re.IGNORECASE)


class SettingsTest(unittest.TestCase):
    def setUp(self):
        self.permissions = load_settings()["permissions"]
        self.deny = self.permissions["deny"]

    def name_rules(self, tool):
        prefix = tool + "(" + ANY_DEPTH
        return [
            name_regex(rule[len(prefix) : -1])
            for rule in self.deny
            if rule.startswith(prefix) and "/" not in rule[len(prefix) : -1]
        ]

    def assert_name_denied(self, tool, name):
        self.assertTrue(
            any(r.match(name) for r in self.name_rules(tool)), f"{tool} {name!r} not denied"
        )

    def assert_name_allowed(self, tool, name):
        self.assertFalse(
            any(r.match(name) for r in self.name_rules(tool)), f"{tool} {name!r} denied"
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
            ".env", ".ENV", ".env.", ".env.bak", ".env.local", ".env~", ".envrc",
            ".env - Copy", ".env (1)", ".env@bak", ".env]x", ".env[x", ".envé",
            ".env.E2E", ".env.eXtra", ".env.e", ".env.ex", ".env.exa", ".env.exam",
            ".env.examp", ".env.exampl", ".env.exampl@", ".env.example.bak", ".env.example~",
        ]
        for tool in ("Read", "Edit"):
            for name in names:
                self.assert_name_denied(tool, name)

    def test_env_example_stays_readable_and_editable(self):
        for tool in ("Read", "Edit"):
            self.assert_name_allowed(tool, ".env.example")
            self.assert_name_allowed(tool, "env.txt")

    def test_shell_commands_on_secret_files_denied(self):
        for rule in (
            "Bash(cat *.env*)",
            "Bash(cat *infra/secrets*)",
            "Bash(type *.env*)",
            "Bash(Get-Content *.env*)",
            "Bash(git diff*--no-index*)",
            "PowerShell(Get-Content *.env*)",
            "PowerShell(cat *infra/secrets*)",
            "PowerShell(cp *.env*)",
        ):
            self.assertIn(rule, self.deny)


if __name__ == "__main__":
    unittest.main()
