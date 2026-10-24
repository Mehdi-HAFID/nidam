[CmdletBinding()]
param (
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet('token-generator', 'bff', 'registration')]
    [string]$Target
)

$ErrorActionPreference = 'Stop'

# Define current working directory
$baseDir = $PSScriptRoot
if (-not $baseDir) { $baseDir = Get-Location }

# Define items to include based on target
if ($Target -eq 'token-generator') {
    $zipName = "nidam-token-generator-ha.zip"
    $itemsToInclude = @(
        "jdbc",
        "key",
        "runtime",
        "token-generator-ui",
        "configuration.yml",
        "token-generator-2.0.0.jar"
    )
}
if ($Target -eq 'bff') {
    $zipName = "nidam-bff-ha.zip"
    $itemsToInclude = @(
        "runtime",
        "configuration.yml",
        "bff-2.0.0.jar"
    )
}
if ($Target -eq 'registration') {
    $zipName = "nidam-registration-ha.zip"
    $itemsToInclude = @(
        "jdbc",
        "runtime",
        "configuration.yml",
        "registration-2.0.0.jar"
    )
}

$destinationZip = Join-Path $baseDir $zipName

# Resolve full paths and verify file existence
$validPaths = @()
foreach ($item in $itemsToInclude) {
    $fullPath = Join-Path $baseDir $item
    if (Test-Path $fullPath) {
        $validPaths += $fullPath
    } else {
        Write-Warning "Skipping missing item: $item"
    }
}

if ($validPaths.Count -eq 0) {
    Write-Error "No valid files or folders found to bundle."
    exit 1
}

# Remove existing zip if present
if (Test-Path $destinationZip) {
    Remove-Item $destinationZip -Force
}

# Create Zip Archive
Write-Host "Creating $zipName for $Target..." -ForegroundColor Cyan
Compress-Archive -LiteralPath $validPaths -DestinationPath $destinationZip -CompressionLevel Optimal

Write-Host "Successfully packaged: $destinationZip" -ForegroundColor Green