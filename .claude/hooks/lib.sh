#!/usr/bin/env bash
# Shared helpers for the Claude Code hooks. Needs bash and jq (Git for Windows + jq on Windows).

# require_jq <exit code when jq is missing>: a guard fails closed (2), a convenience fails open (1).
require_jq() {
  command -v jq >/dev/null 2>&1 || { echo "hook: jq is required (https://jqlang.org)" >&2; exit "${1:-1}"; }
}

# Project root, with forward slashes (CLAUDE_PROJECT_DIR is a Windows path under Git Bash).
project_root() {
  local root="${CLAUDE_PROJECT_DIR:-$(pwd)}"
  root="$(printf '%s' "$root" | tr '\\' '/')"
  printf '%s' "${root%/}"
}

# Path of the file the tool targets, relative to the project root when inside it.
# Windows paths are converted, then ".." segments are resolved (cygpath, else realpath -m, else
# a path containing ".." is kept as is and left to the caller).
relative_target() {
  local file="$1" root
  root="$(project_root)"
  file="$(printf '%s' "$file" | tr '\\' '/')"
  if command -v cygpath >/dev/null 2>&1; then
    file="$(cygpath -m "$file" 2>/dev/null || printf '%s' "$file")"
    root="$(cygpath -m "$root" 2>/dev/null || printf '%s' "$root")"
  elif command -v realpath >/dev/null 2>&1; then
    file="$(realpath -m -- "$file" 2>/dev/null || printf '%s' "$file")"
    root="$(realpath -m -- "$root" 2>/dev/null || printf '%s' "$root")"
  fi
  case "$file" in
    "$root"/*) file="${file#"$root"/}" ;;
  esac
  printf '%s' "$file"
}
