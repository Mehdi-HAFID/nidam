#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

pwsh_path="$SCRIPT_DIR/PowerShell/pwsh"
target_script="$SCRIPT_DIR/nidam-ha.ps1"

if [[ ! -x "$pwsh_path" ]]; then
    printf 'PowerShell executable not found or not executable: %s\n' "$pwsh_path" >&2
    exit 127
fi

if [[ ! -f "$target_script" ]]; then
    printf 'Target script not found: %s\n' "$target_script" >&2
    exit 1
fi

exec "$pwsh_path" -NoProfile -File "$target_script" "$@"
