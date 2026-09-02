param(
    [ValidateSet("start", "stop", "restart")]
    [string]$Command = "start",

    [ValidateSet("FRESH")]
    [string]$Mode,

    [ValidateSet("h2", "registration", "token-generator", "reverse-proxy", "bff", "nidam", "spa")]
    [string[]]$Exclude = @()
)

if ($PSVersionTable.PSVersion.Major -lt 7) {
    Write-Host "❌ PowerShell 7+ is required to run Nidam Dev scripts." -ForegroundColor Red
    Write-Host "👉 Install from: https://aka.ms/powershell"
    exit 1
}

Set-Location $PSScriptRoot
$Root = Split-Path -Parent $PSScriptRoot

$hasWt = Get-Command wt.exe -ErrorAction SilentlyContinue

$EffectiveExclude = @()

function Initialize-Exclusions {
    $EffectiveExclude = @($Exclude)

    if ($Mode -eq "FRESH") {
        $EffectiveExclude += "nidam", "spa", "h2"

        Write-Host ""
        Write-Host "╔═══════════════════════════════════════════════════════════════════════════════════════════════════════╗" -ForegroundColor Yellow
        Write-Host "║ ⚠️  FRESH MODE                                                                                        ║" -ForegroundColor Yellow
        Write-Host "╠═══════════════════════════════════════════════════════════════════════════════════════════════════════╣" -ForegroundColor Yellow
        Write-Host "║ Skipping nidam, spa and internal H2 database.                                                         ║" -ForegroundColor Yellow
        Write-Host "║                                                                                                       ║" -ForegroundColor Yellow
        Write-Host "║ FRESH mode requires an external database to be configured and running.                                ║" -ForegroundColor Yellow
        Write-Host "║ Configure these properties in application.yml:                                                        ║" -ForegroundColor Yellow
        Write-Host "║                                                                                                       ║" -ForegroundColor Yellow
        Write-Host "║   users-db-url    users-db-user   users-db-password                                                   ║" -ForegroundColor Yellow
        Write-Host "║                                                                                                       ║" -ForegroundColor Yellow
        Write-Host "║ Read the documentation for more information                                                           ║" -ForegroundColor Yellow
        Write-Host "║ To Remove this message from appearing again remove lines from 31 to 45 in nidam-dev.ps1               ║" -ForegroundColor Yellow
        Write-Host "╚═══════════════════════════════════════════════════════════════════════════════════════════════════════╝" -ForegroundColor Yellow
        Write-Host ""
    }
    return $EffectiveExclude
}

function Handle-StartFailure {
    param ([bool]$Started)

    if (-not $Started) {
        Write-Host "❌ Nidam did not start." -ForegroundColor Red
        Stop-Nidam | Out-Null
        return $false
    }

    return $true
}

function Test-PortListening {
    param ([int]$Port)
    return (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue) -ne $null
}

function Start-DBTab {
    param (
        [int]$Index,
        [string]$Name,
        [string]$Directory,
        [string]$Command,
        [int]$Port
    )

    if (Test-PortListening $Port) {
        Write-Host "$Name already running."
        return $true
    }

    Write-Host "[$Index/$TotalServices] Starting $Name…"
    if ($hasWt) {
        wt.exe -w 0 nt --title "$Name" -d "$Directory" pwsh -NoProfile -Command "$Command"
    } else {
        Start-Process pwsh -WorkingDirectory $Directory -ArgumentList ("-NoProfile", "-Command", $Command)
    }

    return [bool](SetupH2 -Port $Port)
}

function SetupH2 {
    param ([int]$Port)
    $TimeoutSeconds = 60
    $timeout = [DateTime]::Now.AddSeconds($TimeoutSeconds)

    while ([DateTime]::Now -lt $timeout) {
        if (Test-PortListening $Port) {
            break
        }
        Start-Sleep -Milliseconds 100
    }

    if (-not (Test-PortListening $Port)) {
        Write-Host "❌ H2 failed to become ready within $TimeoutSeconds seconds." -ForegroundColor Red
        return $false
    }

    $h2Jar = Join-Path $PSScriptRoot "h2-2.4.240.jar"
    $dbUrl = "jdbc:h2:tcp://localhost:$Port/identity_hub"

    $result = java -cp $h2Jar org.h2.tools.Shell -url $dbUrl -user sa -password "" -sql "SELECT COUNT(*) FROM INFORMATION_SCHEMA.USERS WHERE USER_NAME = 'NIDAM';" 2>&1
    $lines = $result -split "`n"
    $nidamcount = [int]$lines[1].Trim()
    # Write-Host "Parsed count = $nidamcount"
    if ($nidamcount -eq "1") {
        Write-Host "H2 Database already exists. Skipping setup."
        return $true
    }
    Write-Host "H2 Database does not exist. Creating..."
    java -cp $h2Jar org.h2.tools.Shell -url $dbUrl -user sa -password "" -sql (Get-Content (Join-Path $PSScriptRoot "init.sql") -Raw)
    Write-Host "User created. username: 'nidam', password: 'gF2mshbI819AV2L3'"
    Write-Host "DB setup completed."
    return $true
}

function Stop-H2 {
    param ([int]$Port)
    #    Write-Host "Stopping H2..."
    if (-not (Test-PortListening $Port)) {
        return
    }
    java -cp h2-2.4.240.jar org.h2.tools.Server -tcpShutdown tcp://localhost:$Port -tcpPassword "shutdown-secret"
    Write-Host "H2 stopped."
}

function Wait-ForService{
    param (
        [string]$Name,
        [int]$Port,
        [string]$ContextPath = ""
    )
    $regUrl = "http://localhost:$Port$ContextPath/actuator/health/readiness"
    #    Write-Host "⏳ Waiting for $Name readiness..."
    $TimeoutSeconds = 60
    $timeout = [DateTime]::Now.AddSeconds($TimeoutSeconds)
    #while ($true) {
    while ([DateTime]::Now -lt $timeout) {
        try {
            $health = Invoke-RestMethod -Uri $regUrl -Method Get -TimeoutSec 2

            if ($health.status -eq "UP") {
                return $true
            }
        } catch {
            # ignore until service is up
        }
        Start-Sleep -Milliseconds 100
    }
    Write-Host "❌ $Name failed to become ready within $TimeoutSeconds seconds." -ForegroundColor Red
    return $false
}

function Start-SpringServiceTab {
    param (
        [int]$Index,
        [string]$Name,
        [string]$Directory,
        [string]$Command,
        [int]$Port,
        [string]$ContextPath = ""
    )

    if (Test-PortListening $Port) {
        Write-Host "$Name already running."
        return $true
    }

    Write-Host "[$Index/$TotalServices] Starting $Name…"

    $env:SPRING_OUTPUT_ANSI_ENABLED = "ALWAYS"
    if ($hasWt) {
        wt.exe -w 0 nt --title "$Name" -d "$Directory" pwsh -Command "$Command"
    } else {
        Start-Process pwsh -WorkingDirectory $Directory -ArgumentList ("-NoProfile", "-Command", $Command)
    }
    $env:SPRING_OUTPUT_ANSI_ENABLED = ""
    if (-not (Wait-ForService -Name $Name -Port $Port -ContextPath $ContextPath)) {
        return $false
    }
    return $true
}

function Stop-SpringService{
    param (
        [string]$Name,
        [int]$Port,
        [string]$ContextPath = ""
    )

    try {
        $response = Invoke-RestMethod -Method Post -Uri "http://localhost:$Port$ContextPath/actuator/shutdown" -TimeoutSec 1 -ContentType "application/json" -Body "{}"
        Write-Host "$Name shutdown."
    } catch{
        Write-Host "Shutdown request failed or $Name already stopped."
    }
    Start-Sleep -Milliseconds 100
}

function Start-Spa {
    param (
        [int]$Index,
        [string]$Name,
        [string]$Directory,
        [string]$Command,
        [int]$Port
    )
    if (Test-PortListening $Port) {
        Write-Host "$Name already running."
        return $true
    }
    Write-Host "[$Index/$TotalServices] Starting $Name…"

    if ($hasWt) {
        wt.exe -w 0 nt --title "$Name" -d "$Directory" pwsh -Command "$Command"
    } else {
        Start-Process pwsh -WorkingDirectory $Directory -ArgumentList ("-NoProfile", "-Command", "$Command")
    }
    return [bool](Wait-ForSpa -Name $Name -Port $Port)
}

function Wait-ForSpa {
    param (
        [string]$Name,
        [int]$Port
    )
    $TimeoutSeconds = 60
    $timeout = [DateTime]::Now.AddSeconds($TimeoutSeconds)
    while ([DateTime]::Now -lt $timeout) {
        if (Test-PortListening $Port) {
            return $true
        }
        #        Write-Host "$Name not listening yet"
        Start-Sleep -Milliseconds 100
    }
    Write-Host "❌ $Name failed to become ready within $TimeoutSeconds seconds." -ForegroundColor Red
    return $false
}

function Stop-ProcessByPort {
    param ([int]$Port)

    $connections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    foreach ($conn in $connections) {
        $pidNumber = $conn.OwningProcess
        if ($pidNumber) {
            #            Write-Host "Stopping process on port $Port (PID $pidNumber)..."
            Stop-Process -Id $pidNumber -Force
            Write-Host "SPA shutdown."
        }
    }
}

function Test-Excluded {
    param ([string]$Name)

    return $EffectiveExclude -contains $Name
}

$TotalServices = 0

function Start-Nidam {
    Write-Output "🚀 Starting Nidam Dev Env..."

    $services = @("h2", "registration", "token-generator", "reverse-proxy", "bff", "nidam", "spa") | Where-Object { $_ -notin $EffectiveExclude }

    $TotalServices = $services.Count
    $index = 0

    if (-not (Test-Excluded "h2")) {
        $index++
        $started = Start-DBTab -Index $index -Name "H2 db" -Directory $PSScriptRoot -Command "java -cp h2-2.4.240.jar org.h2.tools.Server -tcp -tcpAllowOthers -ifNotExists -baseDir ./db -tcpPassword 'shutdown-secret'" -Port 9092
        if (-not (Handle-StartFailure $started)) {
            return
        }
    }

    if (-not (Test-Excluded "registration")) {
        $index++
        $started = Start-SpringServiceTab -Index $index -Name "Registration" -Directory (Join-Path $Root "registration") -Command "mvn spring-boot:run '-Dspring-boot.run.profiles=dev'" -Port 4000
        if (-not (Handle-StartFailure $started)) {
            return
        }
    }

    if (-not (Test-Excluded "token-generator")) {
        $index++
        $started = Start-SpringServiceTab -Index $index -Name "Token-Generator" -Directory (Join-Path $Root "token-generator") -Command "mvn spring-boot:run '-Dspring-boot.run.profiles=dev'" -Port 4002 -ContextPath "/auth"
        if (-not (Handle-StartFailure $started)) {
            return
        }
    }

    if (-not (Test-Excluded "reverse-proxy")) {
        $index++
        $started = Start-SpringServiceTab -Index $index -Name "Reverse-Proxy" -Directory (Join-Path $Root "reverse-proxy") -Command "mvn spring-boot:run '-Dspring-boot.run.profiles=dev'" -Port 7080
        if (-not (Handle-StartFailure $started)) {
            return
        }
    }

    if (-not (Test-Excluded "bff")) {
        $index++
        $started = Start-SpringServiceTab -Index $index -Name "BFF" -Directory (Join-Path $Root "bff") -Command "mvn spring-boot:run '-Dspring-boot.run.profiles=dev'" -Port 7081
        if (-not (Handle-StartFailure $started)) {
            return
        }
    }

    if (-not (Test-Excluded "nidam")) {
        $index++
        $started = Start-SpringServiceTab -Index $index -Name "Nidam" -Directory (Join-Path $Root "nidam") -Command "mvn spring-boot:run '-Dspring-boot.run.profiles=dev'" -Port 4003
        if (-not (Handle-StartFailure $started)) {
            return
        }
    }

    if (-not (Test-Excluded "spa")) {
        $index++
        $started = Start-Spa -Index $index -Name "SPA" -Directory (Join-Path $Root "nidam-spa") -Command "npm start" -Port 4001
        if (-not (Handle-StartFailure $started)) {
            return
        }
    }

    Write-Host ""
    Write-Host "✅ Nidam started."

    if (-not (Test-Excluded "spa") -and -not (Test-Excluded "reverse-proxy")) {
        Start-Process "http://localhost:7080/react-ui"
    }
}

function Stop-Nidam {
    Write-Host "Stopping Nidam services..."

    if (-not (Test-Excluded "spa")) {
        Stop-ProcessByPort -Port 4001
    }

    if (-not (Test-Excluded "nidam")) {
        Stop-SpringService -Name "Nidam" -Port 4003
    }

    if (-not (Test-Excluded "bff")) {
        Stop-SpringService -Name "BFF" -Port 7081
    }

    if (-not (Test-Excluded "reverse-proxy")) {
        Stop-SpringService -Name "Reverse-Proxy" -Port 7080
    }

    if (-not (Test-Excluded "token-generator")) {
        Stop-SpringService -Name "Token-Generator" -Port 4002 -ContextPath "/auth"
    }

    if (-not (Test-Excluded "registration")) {
        Stop-SpringService -Name "Registration" -Port 4000
    }

    if (-not (Test-Excluded "h2")) {
        Stop-H2 -Port 9092
    }

    Write-Host "✅ Nidam stopped."
}

function Restart-Nidam {
    Stop-Nidam
    Start-Sleep 2
    Start-Nidam
}

# -----------------------------------
# ENTRY POINT
# -----------------------------------

$EffectiveExclude = Initialize-Exclusions

switch ($Command) {
    "start"   { Start-Nidam }
    "stop"    { Stop-Nidam }
    "restart" { Restart-Nidam }
}

