param(
    [ValidateSet("start", "stop", "restart")]
    [string]$Command = "start"
)

if ($PSVersionTable.PSVersion.Major -lt 7) {
    Write-Host "❌ PowerShell 7+ is required to run Nidam Dev scripts." -ForegroundColor Red
    Write-Host "👉 Install from: https://aka.ms/powershell"
    exit 1
}

Set-Location $PSScriptRoot
$Root = Split-Path -Parent $PSScriptRoot

$hasWt = Get-Command wt.exe -ErrorAction SilentlyContinue

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
        Write-Output "$Name already running."
        return
    }

    Write-Host "[$Index/7] Starting $Name…"
    if ($hasWt) {
        wt.exe -w 0 nt --title "$Name" -d "$Directory" pwsh -NoProfile -Command "$Command"
    } else {
        Start-Process pwsh -WorkingDirectory $Directory -ArgumentList ("-NoProfile", "-Command", $Command)
    }
}

function SetupH2 {
    while (-not ((Get-NetTCPConnection -State Listen -LocalPort 9092 -ErrorAction SilentlyContinue) -ne $null )) {
        # Write-Host "Waiting for H2"
        Start-Sleep -Milliseconds 100
    }
    $h2Jar = Join-Path $PSScriptRoot "h2-2.4.240.jar"
    $dbUrl = "jdbc:h2:tcp://localhost:9092/identity_hub"

    $result = java -cp $h2Jar org.h2.tools.Shell -url $dbUrl -user sa -password "" -sql "SELECT COUNT(*) FROM INFORMATION_SCHEMA.USERS WHERE USER_NAME = 'NIDAM';" 2>&1
    $lines = $result -split "`n"
    $nidamcount = [int]$lines[1].Trim()
    # Write-Host "Parsed count = $nidamcount"
    if ($nidamcount -eq "1") {
        Write-Host "H2 Database already exists. Skipping setup."
        return
    } else {
        Write-Host "H2 Database does not exist. Creating..."
        java -cp $h2Jar org.h2.tools.Shell -url $dbUrl -user sa -password "" -sql (Get-Content (Join-Path $PSScriptRoot "init.sql") -Raw)
        Write-Host "User created. username: 'nidam', password: 'gF2mshbI819AV2L3'"
        Write-Host "DB setup completed."
    }
}

function Stop-H2 {
#    Write-Host "Stopping H2..."
    java -cp h2-2.4.240.jar org.h2.tools.Server -tcpShutdown tcp://localhost:9092 -tcpPassword "shutdown-secret"
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
    while ($true) {
        try {
            $health = Invoke-RestMethod -Uri $regUrl -Method Get -TimeoutSec 2

            if ($health.status -eq "UP") {
                break
            }
        } catch {
            # ignore until service is up
        }
        Start-Sleep -Milliseconds 200
    }
#    Write-Host "$Name is READY"
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
        Write-Output "$Name already running."
        return
    }

    Write-Host "[$Index/7] Starting $Name…"

    $env:SPRING_OUTPUT_ANSI_ENABLED = "ALWAYS"
    if ($hasWt) {
        wt.exe -w 0 nt --title "$Name" -d "$Directory" pwsh -Command "$Command"
    } else {
        Start-Process pwsh -WorkingDirectory $Directory -ArgumentList ("-NoProfile", "-Command", $Command)
    }
    $env:SPRING_OUTPUT_ANSI_ENABLED = ""
    Wait-ForService -Name $Name -Port $Port -ContextPath $ContextPath
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
        Write-Host "Shutdown request failed or service already stopped."
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
        Write-Output "$Name already running."
        return
    }
    Write-Host "[$Index/7] Starting $Name…"

    if ($hasWt) {
        wt.exe -w 0 nt --title "$Name" -d "$Directory" pwsh -Command "$Command"
    } else {
        Start-Process pwsh -WorkingDirectory $Directory -ArgumentList ("-NoProfile", "-Command", "$Command")
    }
    $url = "http://localhost:$Port/react-ui"
    Wait-ForSpa -Name $Name -Url $url -Port 4001
}

function Wait-ForSpa {
    param (
        [string]$Name,
        [string]$Url,
        [int]$Port
    )
    while (-not (Test-PortListening $Port)) {
#        Write-Host "$Name not listening yet"
        Start-Sleep -Milliseconds 100
    }
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

function Start-Nidam {
    Write-Output "🚀 Starting Nidam Dev Env..."
    # Start-ServiceTab -Index 1 -Name "Documentation" -Directory "C:\Users\mehdi\Root\Dev\Auth Server\documentation" -Command "npm start"
#    Start-DBTab -Index 1 -Name "H2 db" -Directory $PSScriptRoot -Command "java -cp h2-2.4.240.jar org.h2.tools.Server -tcp -tcpAllowOthers -ifNotExists -baseDir ./db -tcpPassword 'shutdown-secret'" -Port 9092
#    SetupH2
    Start-SpringServiceTab -Index 2 -Name "Registration" -Directory (Join-Path $Root "registration") -Command "mvn spring-boot:run '-Dspring-boot.run.profiles=dev-sql'" -Port 4000
    Start-SpringServiceTab -Index 3 -Name "Token-Generator" -Directory (Join-Path $Root "token-generator") -Command "mvn spring-boot:run '-Dspring-boot.run.profiles=dev'" -Port 4002 -ContextPath "/auth"
    Start-SpringServiceTab -Index 4 -Name "Reverse-Proxy" -Directory (Join-Path $Root "reverse-proxy") -Command "mvn spring-boot:run '-Dspring-boot.run.profiles=dev'" -Port 7080
    Start-SpringServiceTab -Index 5 -Name "BFF" -Directory (Join-Path $Root "bff") -Command "mvn spring-boot:run '-Dspring-boot.run.profiles=dev'" -Port 7081
    Start-SpringServiceTab -Index 6 -Name "Nidam" -Directory (Join-Path $Root "nidam") -Command "mvn spring-boot:run '-Dspring-boot.run.profiles=dev'" -Port 4003
    # Recommended: remove/comment this section section after the first run, so logs are clean: START
#    Push-Location (Join-Path $Root "nidam-spa")
#    npm install
#    Pop-Location
    # END
    Start-Spa -Index 7 -Name "SPA" -Directory (Join-Path $Root "nidam-spa") -Command "npm start" -Port 4001

    Write-Host ""
    Write-Host "✅ Nidam started."

    Start-Process "http://localhost:7080/react-ui"
}

function Stop-Nidam {
    Write-Host "Stopping Nidam services..."
    Stop-ProcessByPort -Port 4001
    Stop-SpringService -Name "Nidam" -Port 4003
    Stop-SpringService -Name "BFF" -Port 7081
    Stop-SpringService -Name "Reverse-Proxy" -Port 7080
    Stop-SpringService -Name "Token-Generator" -Port 4002 -ContextPath "/auth"
    Stop-SpringService -Name "Registration" -Port 4000
#    Stop-H2
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

switch ($Command) {
    "start"   { Start-Nidam }
    "stop"    { Stop-Nidam }
    "restart" { Restart-Nidam }
}

