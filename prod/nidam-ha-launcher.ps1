$pwshPath = Join-Path $PSScriptRoot "PowerShell\pwsh.exe"
$targetScript = Join-Path $PSScriptRoot "nidam-ha.ps1"

& $pwshPath -NoProfile -ExecutionPolicy Bypass -File $targetScript @args

exit $LASTEXITCODE
