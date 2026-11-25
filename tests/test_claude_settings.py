"""
Checks of .claude/settings.json: valid JSON, the secret file deny rules kept (issue #118),
every spelling of a pull request merge denied (issue #119).

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


def path_regex(glob, ignore_case=True):
    """Regex of a whole-path glob: ``**`` is any number of folders, other parts as name_regex."""
    parts = glob.split("/")
    out = ""
    for i, part in enumerate(parts):
        last = i == len(parts) - 1
        if part == "**":
            out += ".*" if last else "(?:[^/]*/)*"
        else:
            out += name_regex(part, ignore_case).pattern[: -len(r"\Z")] + ("" if last else "/")
    return re.compile(out + r"\Z", re.IGNORECASE if ignore_case else 0)


def command_regex(glob, ignore_case=True):
    """Regex of a Bash or PowerShell rule body: ``*`` matches any text, spaces included (checked live for Bash).

    Single-line commands only: whether Claude Code's ``*`` crosses a newline was not checked
    (.claude/README.md, *Merge rules*), so ``.`` here does not match one.
    """
    return re.compile(
        ".*".join(re.escape(p) for p in glob.split("*")) + r"\Z",
        re.IGNORECASE if ignore_case else 0,
    )


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

    def rule_bodies(self, tool, prefix=""):
        start = tool + "(" + prefix
        return [rule[len(start) : -1] for rule in self.deny if rule.startswith(start)]

    def secret_globs(self, tool):
        return self.rule_bodies(tool, "//")

    def merge_globs(self, tool):
        return [body for body in self.rule_bodies(tool) if body.startswith("*gh*")]

    def merge_rules(self, tool, ignore_case):
        return [command_regex(glob, ignore_case) for glob in self.merge_globs(tool)]

    def path_denied(self, tool, path, ignore_case=True):
        return any(path_regex(glob, ignore_case).match(path) for glob in self.secret_globs(tool))

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

    def test_read_and_edit_deny_the_same_secret_files(self):
        self.assertTrue(self.secret_globs("Read"))
        self.assertEqual(self.secret_globs("Read"), self.secret_globs("Edit"))

    def test_secret_paths_denied_at_any_depth(self):
        denied = [
            "C:/x/infra/secrets/a.txt",
            "/home/u/p/infra/secrets/sub/b",
            "/infra/secrets/jwt-private.pem",
            "C:/x/y/z/infra/secrets/deep/er/c",
            "C:/x/.env",
            "/home/u/p/backend/.env.E2E",
            "C:/x/keys/jwt.pem",
            "/home/u/p/a/b/server.key",
        ]
        allowed = [
            "C:/x/infra/README.md",
            "/home/u/p/infra/secrets-old/a",
            "/home/u/p/infra/secret/a",
            "C:/x/.env.example",
            "/home/u/p/backend/.env.example",
        ]
        for ignore_case in (True, False):
            for tool in ("Read", "Edit"):
                for path in denied:
                    self.assertTrue(self.path_denied(tool, path, ignore_case), f"{tool} {path}")
                for path in allowed:
                    self.assertFalse(self.path_denied(tool, path, ignore_case), f"{tool} {path}")

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
        commands = {
            "Bash": (
                "cat", "type", "head", "tail", "less", "more", "Get-Content",
                "grep", "sed", "awk", "cp", "base64",
            ),
            "PowerShell": (
                "Get-Content", "gc", "cat", "type", "Select-String", "sls",
                "Copy-Item", "cp", "copy", "cpi", "more",
            ),
        }
        targets = (".env", "infra/secrets", "infra\\secrets", ".pem", ".key")
        for tool, names in commands.items():
            for command in names:
                for target in targets:
                    with self.subTest(tool=tool, command=command, target=target):
                        self.assertIn(f"{tool}({command} *{target}*)", self.deny)
        self.assertIn("Bash(git *diff*--no-index*)", self.deny)

    def test_git_diff_no_index_denied_after_git_options(self):
        globs = [body for body in self.rule_bodies("Bash") if body.startswith("git ")]
        self.assertIn("git *diff*--no-index*", globs)
        rules = [command_regex(glob, ignore_case=False) for glob in globs]
        for command in (
            "git diff --no-index backend/a backend/b",
            "git --no-pager diff --no-index backend/a backend/b",
            "git -C . diff --no-index backend/a backend/b",
        ):
            self.assertTrue(any(r.match(command) for r in rules), command)
        for command in (
            "git diff --stat",
            "git log -1",
            "git diff origin/v0.2.1-a...HEAD",
        ):
            self.assertFalse(any(r.match(command) for r in rules), command)

    def test_every_spelling_of_a_pull_request_merge_denied(self):
        commands = [
            "gh pr merge",
            "gh pr merge 12",
            "gh pr merge 12 --squash --delete-branch",
            "gh pr merge>out.txt 12",
            "gh pr merge\"\" 12",
            "gh pr merge\t12",
            "gh pr merge --auto --squash 12",
            "gh pr merge 12 -R owner/repo",
            "gh -R owner/repo pr merge 12",
            "gh pr -R owner/repo merge 12",
            "gh pr --repo owner/repo merge 12",
            "gh pr --repo=owner/repo merge --auto 12",
            "GH_REPO=owner/repo gh pr merge 12",
            "gh.exe pr merge 12",
            "& gh pr merge 12",
            "gh api repos/owner/repo/pulls/12/merge -X PUT",
            "gh api -X PUT repos/owner/repo/pulls/12/merge",
            "gh api --method PUT /repos/{owner}/{repo}/pulls/12/merge -f merge_method=squash",
            "gh api repos/owner/repo/merges -f base=main -f head=feat/x",
            "gh api graphql -f query='mutation { mergePullRequest(input: {pullRequestId: \"x\"}) { clientMutationId } }'",
            "gh api graphql -f query='mutation { enablePullRequestAutoMerge(input: {pullRequestId: \"x\"}) { clientMutationId } }'",
            "gh api graphql -f query='mutation { mergeBranch(input: {repositoryId: \"x\", base: \"main\", head: \"f\"}) { clientMutationId } }'",
            "gh api graphql -f query='mutation { enqueuePullRequest(input: {pullRequestId: \"x\"}) { clientMutationId } }'",
        ]
        # Case-sensitive is the strict direction for a deny: a case-folded matcher denies at least as much
        for tool in ("Bash", "PowerShell"):
            rules = self.merge_rules(tool, ignore_case=False)
            for command in commands:
                with self.subTest(tool=tool, command=command):
                    self.assertTrue(any(r.match(command) for r in rules))

    def test_upper_case_gh_denied_when_case_is_folded(self):
        # Windows runs GH.exe for `GH`; denied only if the matcher folds case (not checked for Bash)
        for tool in ("Bash", "PowerShell"):
            rules = self.merge_rules(tool, ignore_case=True)
            for command in ("GH pr merge 12", "GH pr -R owner/repo merge 12"):
                with self.subTest(tool=tool, command=command):
                    self.assertTrue(any(r.match(command) for r in rules))

    def test_other_commands_mentioning_merge_stay_allowed(self):
        commands = [
            "git merge origin/v0.2.1-a",
            "git log --merges",
            "git log --merges -1 --oneline",
            "gh pr view 12",
            "gh pr view 12 --json state",
            "gh pr view 12 --json state,mergedAt,mergeable,mergeStateStatus",
            "gh pr -R o/r view 1 --json mergedAt",
            "gh pr --repo o/r view 1 --json mergedAt",
            "gh pr checks 12",
            "gh pr ready 12",
            "gh pr list --base feat/14-x --state merged --search \"57 in:body\" --json number,body",
            "gh pr list --state merged --search \"57 in:body\" --limit 100 --json number,title,body,baseRefName",
            "gh issue close 57 --comment \"Merged into feat/14-x by #60 (/implement-issue).\"",
            "gh pr comment 60 --body-file round.md",
            "gh pr comment 60 --body \"F1 blocks the merge: no\"",
            "gh run list --workflow sub-issue-automerge.yml",
            "gh run view 123 --log",
            "gh api repos/{owner}/{repo}/statuses/abc -f state=success -f context=agent-review",
            "gh api repos/owner/repo/pulls/12 --jq .merged",
            "gh api \"repos/owner/repo/pulls?state=closed\" --jq '.[].merged_at'",
            "gh api graphql -f query='{repository(owner:\"o\",name:\"r\"){pullRequest(number:12){mergeStateStatus mergeable}}}'",
            "gh pr create --base v0.2.1-a --head chore/119-deny-pr-merge-variants --title \"x\"",
            "python bump_version.py --check origin/v0.2.1-a --head-ref chore/119-deny-pr-merge-variants",
            "git push -u origin chore/119-deny-pr-merge-variants",
        ]
        # Every Bash / PowerShell deny rule, secret file rules included, not only the merge rules
        for tool in ("Bash", "PowerShell"):
            rules = [command_regex(glob, ignore_case=True) for glob in self.rule_bodies(tool)]
            self.assertTrue(self.merge_globs(tool), tool)
            for command in commands:
                with self.subTest(tool=tool, command=command):
                    self.assertFalse(any(r.match(command) for r in rules))

    def test_bash_and_powershell_deny_the_same_merge_rules(self):
        self.assertTrue(self.merge_globs("Bash"))
        self.assertEqual(self.merge_globs("Bash"), self.merge_globs("PowerShell"))


if __name__ == "__main__":
    unittest.main()
