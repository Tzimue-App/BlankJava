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

    # Review round 1 of #123

    def assert_all(self, blocked=(), allowed=(), cwd=ROOT_FWD):
        for command in blocked:
            with self.subTest(blocked=command):
                self.assert_blocked(bash_input(command, cwd))
        for command in allowed:
            with self.subTest(allowed=command):
                self.assert_allowed(bash_input(command, cwd))

    def test_r1_1_path_glued_to_a_short_option_is_checked(self):
        self.assert_all(
            blocked=("date -f.env", "date -fbackend/.env", "head -c5x.key", "tail -n1infra/secrets/k"),
            allowed=("head -n5 .claude/settings.json", "git log -n3"),
        )

    def test_r1_2_windows_short_names_are_blocked_outside_git(self):
        self.assert_all(
            blocked=("head ENV~1", "ls infra/SECRET~1", "jq -R . ENV~1", "wc 'backend\\ENV~1'"),
            allowed=("git diff HEAD~1 -- .claude/settings.json", "git log HEAD~3..HEAD", "ls ~/.claude"),
        )

    def test_r1_3_win32_name_normalisation_is_blocked(self):
        self.assert_all(
            blocked=(
                "head keys/x.pem.",
                "ls infra/secrets./jwt-private.pem.",
                "head 'x.pem::$DATA'",
                "ls 'infra/secrets::$INDEX_ALLOCATION/k'",
                "head 'x.pem '",
                "head 'infra/secrets /k'",
                "head x.pem:stream",
                "head 'backend/.env. '",
            ),
            allowed=("git show HEAD:.claude/settings.json", "head .env.example", "head .claude/README.md"),
        )

    def test_r1_4_git_diff_outside_the_project_is_blocked(self):
        self.assert_all(
            blocked=(
                "git diff /etc/hosts .claude/settings.json",
                "git diff ../outside.txt .claude/settings.json",
                "git diff C:/Windows/win.ini .claude/settings.json",
                "git diff -- '~/x.txt' .claude/settings.json",
            ),
            allowed=(
                "git diff HEAD~1 -- .claude/settings.json",
                "git diff a..b",
                "git diff -- " + ROOT_FWD + "/README.md",
            ),
        )
        self.assert_blocked(bash_input("git diff ../../x.txt y.txt", ROOT_FWD + "/infra"))
        self.assert_allowed(bash_input("git diff x.txt", ROOT_FWD + "/infra"))

    def test_r1_5_recursive_and_ignored_listings_are_blocked(self):
        self.assert_all(
            blocked=(
                "ls -R infra",
                "ls -laR infra",
                "ls --recursive infra",
                "ls --recur infra",
                "git status --ignored",
                "git status --ignored=matching",
                "git ls-files --ignored --others --exclude-standard",
                "git ls-files -oi --exclude-standard",
            ),
            allowed=("ls -la infra", "git status --short", "git ls-files -o --exclude-standard"),
        )

    def test_r1_6_jq_filter_is_not_read_as_a_path(self):
        self.assert_all(
            blocked=(
                "jq '.x' .env",
                "jq -f .env .claude/settings.json",
                "jq . -f .env",
                "jq -rf .env .claude/settings.json",
                "jq --slurpfile a .env '.env' .claude/settings.json",
                "jq --unknown .env .claude/settings.json",
                "jq -- .env .claude/settings.json",
                "jq -Z .env .claude/settings.json",
            ),
            allowed=(
                "jq '.env' .claude/settings.json",
                "jq -r '.key' .claude/settings.json",
                "jq --arg n v '.env' .claude/settings.json",
                "jq -n --indent 2 '.env'",
                "jq --raw-output .env .claude/settings.json",
            ),
        )

    def test_r1_8_an_unexpected_error_blocks(self):
        with tempfile.TemporaryDirectory() as tmp:
            hooks = os.path.join(tmp, ".claude", "hooks")
            shutil.copytree(HOOKS, hooks)
            shutil.copy(os.path.join(ROOT, ".claude", "settings.json"), os.path.join(tmp, ".claude"))
            with open(os.path.join(hooks, "lib.sh"), "a", encoding="utf-8", newline="\n") as f:
                f.write("require_jq() { exit 1; }\n")
            self.assert_blocked(bash_input("git status"), os.path.join(hooks, "audit-write-guard.sh"))

    def test_r1_9_long_commands_are_blocked(self):
        self.assert_blocked(bash_input("ls " + "a" * 5000))
        self.assert_allowed(bash_input("ls " + "a" * 4093))

    # Review round 2 of #123

    def test_r2_1_git_diff_two_arguments_and_drive_relative_paths_are_blocked(self):
        self.assert_all(
            blocked=(
                "git diff ENV~1 C:README.md",
                "git diff C:../../x.txt .claude/settings.json",
                "git diff a b",
                "git diff --stat a b",
                "git diff -- x y",
                "git diff HEAD -- C:x.txt",
            ),
            allowed=(
                "git diff a..b",
                "git diff a b --",
                "git diff HEAD~1 -- .claude/settings.json",
                "git diff --stat",
                "git diff origin/v0.2.1-a...HEAD --stat",
            ),
        )

    def test_r2_2_git_diff_dot_dot_segment_is_blocked(self):
        self.assert_all(
            blocked=(
                "git diff ../BlankJava-123/ENV~1 README.md",
                "git diff HEAD -- ../BlankJava-123/README.md",
                "git diff HEAD -- 'docs\\..\\README.md'",
            ),
            allowed=("git diff HEAD -- docs/README.md",),
        )

    def test_r2_3_git_diff_words_after_double_dash_are_paths(self):
        self.assert_all(
            blocked=(
                "git diff -- -a/../../../.ssh/id_ed25519 README.md",
                "git diff HEAD -- -a/../../x README.md",
                "git diff HEAD -- /etc/hosts",
            ),
            allowed=("git diff HEAD -- -a.txt README.md",),
        )

    def test_r2_4_git_ls_files_others_needs_exclude_standard(self):
        self.assert_all(
            blocked=("git ls-files -o", "git ls-files --others", "git ls-files -co", "git ls-files --oth"),
            allowed=("git ls-files -o --exclude-standard", "git ls-files --others --exclude-standard", "git ls-files"),
        )

    def test_r2_5_prefixes_of_ignored_are_blocked(self):
        self.assert_all(
            blocked=(
                "git ls-files --ig -o --exclude-standard",
                "git status --i",
                "git status --ignor",
                "git status --ignored=matching",
                "git ls-files --ignore",
            ),
            allowed=("git status --short", "git status -s"),
        )

    def test_r2_6_git_pathspec_globs_and_magic_are_blocked(self):
        self.assert_all(
            blocked=(
                "git log -p -- 'infra/secr*'",
                "git log -p -- 'infra/secret?/x'",
                "git log -p -- 'infra/[s]ecrets'",
                "git show HEAD -- ':(icase)INFRA/SECRETS'",
                "git log -p -- ':/infra'",
                "git log -p -- ':!README.md'",
                "git log -p -- ':^README.md'",
            ),
            allowed=("git show HEAD:.claude/settings.json", "git log -p -- README.md", "git log --format='%h %s' -3"),
        )

    def test_r2_7_ignore_options_of_other_git_commands_are_allowed(self):
        self.assert_all(
            allowed=("git diff --ignore-space-change HEAD~1", "git log -p --ignore-all-space -1"),
        )

    def test_r2_8_jq_keyword_check_only_on_the_filter(self):
        self.assert_all(
            blocked=(
                "jq 'import \"x\" as $x; .' f.json",
                "jq -f prog.jq docs/include/x.json",
                "jq --unknown . docs/include/x.json",
            ),
            allowed=("jq . docs/include/x.json", "jq -r .a docs/import/x.json"),
        )

    # Review round 3 of #123

    def test_r3_4_wc_files0_from_is_blocked(self):
        self.assert_all(
            blocked=(
                "wc --files0-from=list.txt",
                "wc --files0-from list.txt",
                "wc -l --files0 list.txt",
                "wc --f=list.txt",
            ),
            allowed=("wc -l .claude/settings.json", "wc --lines .claude/settings.json"),
        )

    def test_r3_2_long_short_options_are_blocked_before_the_timeout(self):
        # The first case took 18 s, past the 10 s hook timeout, and a timed-out hook lets the call through.
        self.assert_all(
            blocked=(
                "head -" + "q" * 1500 + " .env",
                "ls -" + "a" * 65,
                " ".join(["ls"] + ["-" + "a" * 20] * 4),
            ),
            allowed=("head -n 50 README.md", "ls -" + "a" * 63, "ls -la -h -t"),
        )

    def test_r3_6_git_status_ignore_submodules_is_allowed(self):
        self.assert_all(
            blocked=("git status --ignored", "git status --ig", "git status --i", "git ls-files --ignored"),
            allowed=(
                "git status --ignore-submodules",
                "git status --ignore-submodules=all",
            ),
        )

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
