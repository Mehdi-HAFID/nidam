#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
pwsh_path="$SCRIPT_DIR/PowerShell/pwsh"

if [[ ! -x "$pwsh_path" ]]; then
    printf 'PowerShell executable not found or not executable: %s\n' "$pwsh_path" >&2
    exit 127
fi

# Reconstruct the arguments as PowerShell expressions.
forward_args=()

for arg in "$@"; do
    if [[ "$arg" =~ [[:space:]] ]]; then
        # Preserve spaces by using a PowerShell single-quoted string.
        # Double embedded single quotes to escape them.
        arg=${arg//\'/\'\'}
        forward_args+=("'$arg'")
    else
        # Keep expression-style arguments unquoted, including comma-separated arrays.
        forward_args+=("$arg")
    fi
done

arg_string=''
if ((${#forward_args[@]} > 0)); then
    printf -v arg_string '%s ' "${forward_args[@]}"
    arg_string=${arg_string% }
fi

export NIDAM_TARGET_SCRIPT="$SCRIPT_DIR/nidam.ps1"

command_text='& $env:NIDAM_TARGET_SCRIPT'
if [[ -n "$arg_string" ]]; then
    command_text+=" $arg_string"
fi

exec "$pwsh_path" -NoProfile -Command "$command_text"
