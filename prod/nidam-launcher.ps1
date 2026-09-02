$pwshPath = Join-Path $PSScriptRoot "PowerShell\pwsh.exe"
$targetScript = Join-Path $PSScriptRoot "nidam.ps1"

# Reconstruct the arguments: join arrays with commas, quote strings with spaces
$forwardArgs = foreach ($arg in $args) {
    if ($arg -is [array]) {
        $arg -join ','
    } else {
        if ($arg -match '\s') { "`"$arg`"" } else { $arg }
    }
}

$argString = $forwardArgs -join ' '

& $pwshPath -NoProfile -ExecutionPolicy Bypass -Command "& '$targetScript' $argString"

exit $LASTEXITCODE