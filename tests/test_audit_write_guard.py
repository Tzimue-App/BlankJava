"""
Checks of .claude/hooks/audit-write-guard.sh (issues #122, #123) with simulated hook input.

Needs bash and jq on PATH, as the hook does. Run from the repository root:
    python -m pytest tests/
"""

import json
import os
import shutil
import subprocess
import tempfile
import unittest

from tests.test_claude_settings import DENIED_PATHS, ENV_VARIANTS, load_settings, path_regex

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
HOOKS = os.path.join(ROOT, ".claude", "hooks")
GUARD = os.path.join(HOOKS, "audit-write-guard.sh")
BASH = shutil.which("bash")
ROOT_FWD = ROOT.replace("\\", "/")


def run_guard(payload, guard=GUARD):
    """Runs the guard on a hook input (dict, or raw text); returns (exit code, stdout, stderr)."""
    data = payload if isinstance(payload, str) else json.dumps(payload)
    env = dict(os.environ, CLAUDE_PROJECT_DIR=ROOT)
    proc = subprocess.run(
        [BASH, guard], input=data, capture_output=True, text=True, encoding="utf-8", env=env, cwd=ROOT
    )
    return proc.returncode, proc.stdout, proc.stderr


def bash_input(command, cwd=ROOT_FWD):
    return {"tool_name": "Bash", "tool_input": {"command": command}, "cwd": cwd}


@unittest.skipUnless(BASH and shutil.which("jq"), "the guard needs bash and jq")
class AuditGuardTest(unittest.TestCase):
    def assert_allowed(self, payload):
        code, out, err = run_guard(payload)
        self.assertEqual(code, 0, f"{payload}: {err}")
        decision = json.loads(out)["hookSpecificOutput"]["permissionDecision"]
        self.assertEqual(decision, "allow", payload)

    def assert_blocked(self, payload, guard=GUARD):
        code, out, err = run_guard(payload, guard)
        self.assertEqual(code, 2, f"{payload}: exit {code}, {out}")
        self.assertIn("audit guard:", err)

    # Acceptance criteria of #123

    def test_secret_files_named_in_bash_are_blocked(self):
        for command in (
            "jq . .env",
            "git show HEAD:infra/secrets/jwt-private.pem",
            "ls infra/secrets",
        ):
            with self.subTest(command=command):
                self.assert_blocked(bash_input(command))

    def test_jq_filter_with_a_quoted_pipe_is_allowed(self):
        self.assert_allowed(bash_input("jq '.permissions.allow | length' .claude/settings.json"))

    # Variants of the secret path check

    def test_secret_path_variants_are_blocked(self):
        for command in (
            "ls infra/secrets/",
            "ls ./infra/./secrets",
            "ls 'infra'/sec'rets'",
            "ls 'infra\\secrets'",
            "head backend/../.ENV",
            "head ./.env.local",
            "tail -n 1 backend/.env.e2e",
            "wc -c server.key",
            "ls " + ROOT_FWD + "/infra/secrets/jwt-private.pem",
            "git show :.env",
            "git diff HEAD -- keys/a.pem",
            "git log -p -- '*.pem'",
            "git show 'HEAD:infra/secrets'",
            "jq -n --rawfile k infra/secrets/k.txt '$k'",
            "jq --arg x 1 . --slurpfile=a.key",
            "git log --oneline | head -1 .env",
        ):
            with self.subTest(command=command):
                self.assert_blocked(bash_input(command))

    def test_every_path_denied_to_read_is_blocked_in_bash(self):
        globs = [
            rule[len("Read(//") : -1]
            for rule in load_settings()["permissions"]["deny"]
            if rule.startswith("Read(//")
        ]
        corpus = DENIED_PATHS + ["/home/u/p/backend/" + name for name in ENV_VARIANTS + [".ENV"]]
        denied = [p for p in corpus if any(path_regex(g).match(p) for g in globs)]
        self.assertEqual(len(denied), len(corpus))
        for path in denied:
            with self.subTest(path=path):
                self.assert_blocked(bash_input(f"ls '{path}'"))

    def test_relative_path_is_resolved_from_the_hook_cwd(self):
        self.assert_blocked(bash_input("ls secrets", cwd=ROOT_FWD + "/infra"))
        self.assert_blocked(bash_input("ls ../.env", cwd=ROOT_FWD + "/backend"))
        self.assert_allowed(bash_input("ls secrets", cwd=ROOT_FWD + "/backend"))

    def test_non_secret_paths_stay_allowed(self):
        for command in (
            "head .env.example",
            "ls infra",
            "ls infra/secrets-old",
            "git diff origin/v0.2.1-a...HEAD --stat",
            "git show HEAD:.claude/settings.json",
            "git log --format='%h %s' -3",
            "jq '.a > 1 and .b != \"x;y&z\"' .claude/settings.json",
            "jq 'select(.x) | \"\\(.a)\"' .claude/settings.json",
            'jq ".hooks" .claude/settings.json',
            "ls ~/.claude",
        ):
            with self.subTest(command=command):
                self.assert_allowed(bash_input(command))

    def test_shell_expansion_and_jq_modules_are_blocked(self):
        for command in (
            "head .en?",
            "ls infra/secret*",
            "ls infra/secre[t]s",
            "head .e{n,}v",
            'head "$HOME"',
            'head "a\\b"',
            "head 'unterminated",
            "ls |",
            "| ls",
            "ls | | wc",
            "git diff --no-index a b",
            "jq 'import \"infra/x\" as $x; .' f.json",
            "jq 'include \"x\"; .' f.json",
            "jq -L infra . f.json",
        ):
            with self.subTest(command=command):
                self.assert_blocked(bash_input(command))

    def test_missing_deny_rules_block_every_bash_command(self):
        with tempfile.TemporaryDirectory() as tmp:
            hooks = os.path.join(tmp, ".claude", "hooks")
            shutil.copytree(HOOKS, hooks)
            guard = os.path.join(hooks, "audit-write-guard.sh")
            self.assert_blocked(bash_input("git status"), guard)
            with open(os.path.join(tmp, ".claude", "settings.json"), "w", encoding="utf-8") as f:
                json.dump({"permissions": {"deny": ["Edit(//**/.env)"]}}, f)
            self.assert_blocked(bash_input("git status"), guard)

    # Non-regression: the cases of #122

    def test_cases_allowed_by_122(self):
        for payload in (
            {"tool_name": "Write", "tool_input": {"file_path": ROOT + "/.audits/claude-env/report.md"}},
            {"tool_name": "Edit", "tool_input": {"file_path": ".audits/claude-env/report.md"}},
            bash_input("git status"),
            bash_input("git log --oneline | head -5"),
            bash_input("git branch --show-current"),
            bash_input("claude --version"),
            {"tool_name": "Agent", "tool_input": {"subagent_type": "claude-env-analyst"}},
        ):
            with self.subTest(payload=payload):
                self.assert_allowed(payload)

    def test_cases_blocked_by_122(self):
        for payload in (
            {"tool_name": "Write", "tool_input": {"file_path": ROOT + "/src/x.txt"}},
            {"tool_name": "Write", "tool_input": {"file_path": ".audits/claude-env/../../x.txt"}},
            {"tool_name": "Write", "tool_input": {}},
            bash_input("rm -rf build"),
            bash_input("ls > out.txt"),
            bash_input("ls < in.txt"),
            bash_input("ls && ls"),
            bash_input("ls || ls"),
            bash_input("ls; ls"),
            bash_input("ls &"),
            bash_input("ls $(pwd)"),
            bash_input("ls `pwd`"),
            bash_input("ls\nls"),
            bash_input("curl https://example.test"),
            bash_input("sed -i s/a/b/ f.txt"),
            bash_input("git branch -D main"),
            bash_input("git -c core.pager=x log"),
            bash_input("git push"),
            bash_input("git diff --output=x.txt"),
            bash_input("claude --help"),
            bash_input(""),
            {"tool_name": "Agent", "tool_input": {"subagent_type": "general-purpose"}},
            {"tool_name": "Task", "tool_input": {}},
            "not json",
            {"tool_input": {"command": "ls"}},
        ):
            with self.subTest(payload=payload):
                self.assert_blocked(payload)

    def test_other_tools_are_left_to_the_permission_rules(self):
        code, out, _ = run_guard({"tool_name": "Read", "tool_input": {"file_path": "x"}})
        self.assertEqual((code, out), (0, ""))


if __name__ == "__main__":
    unittest.main()
