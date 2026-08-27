param(
    [ValidateSet("start", "stop", "restart")]
    [string]$Command = "start"
)

Set-Location $PSScriptRoot

$Root = $PSScriptRoot
$JavaExe = Join-Path $Root "runtime\bin\java.exe"
$logs = "logs"
$pids = "pids"

New-Item -ItemType Directory -Force -Path $logs | Out-Null
New-Item -ItemType Directory -Force -Path $pids | Out-Null


# -----------------------------------
# Helpers
# -----------------------------------

function Start-H2Database {

    $h2Jar = Join-Path $Root "db\h2-2.4.240.jar"

    if (!(Test-Path $h2Jar)) {
        Write-Output "H2 jar not found, skipping DB startup."
        return
    }

    if (Test-PortListening 9092) {
        Write-Output "H2 database already running."
        return
    }

    Write-Output "Starting H2 database..."

    $process = Start-Process $JavaExe -ArgumentList "-cp `"$h2Jar`" org.h2.tools.Server -tcp -tcpAllowOthers -ifNotExists -baseDir ./db" -RedirectStandardOutput "$logs\h2.log" -PassThru -WindowStyle Hidden

    $process.Id | Out-File "$pids\h2.pid"

    # Wait until port is open
    while (-not (Test-PortListening 9092)) {
        Start-Sleep -Milliseconds 100
    }

    Write-Output "H2 database is ready."
}

function Test-PortListening {
    param ([int]$Port)

    return (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue) -ne $null
}

function Wait-And-PrintJobs {
    param ($jobs)

    foreach ($job in ($jobs | Where-Object { $_ })) {
        Wait-Job $job | Out-Null
        Receive-Job $job
    }
}

function Start-JavaService {
    param (
        [string]$Name,
        [string]$PidName,
        [string]$Jar,
        [string]$JvmArgs = "",
        [string]$StdOut
    )

    Write-Output "Starting $Name..."

    $process = Start-Process $JavaExe -ArgumentList "-jar `"$Jar`" $JvmArgs" -RedirectStandardOutput $StdOut -PassThru -WindowStyle Hidden

    $process.Id | Out-File "$pids\$PidName.pid"
}

function Start-ServiceAsync {
    param(
        [int]$Port,
        [string]$Name,
        [string]$PidName,
        [string]$Jar,
        [string]$LogFile,
        [string]$ReadyPattern,
        [string]$Arguments = ""
    )

    if (Test-PortListening $Port) {
        Write-Output "$Name already running."
        return
    }

    return Start-Job -ScriptBlock {
        param($JavaExe, $Name, $PidName, $Jar, $LogFile, $ReadyPattern, $pids, $Arguments)

        # Write-Output "Starting $Name..."
        $process = Start-Process $JavaExe -ArgumentList "-jar `"$Jar`" $Arguments" -RedirectStandardOutput $LogFile -PassThru -WindowStyle Hidden

        $process.Id | Out-File "$pids\$PidName.pid"

        # Wait for readiness
        if ($ReadyPattern) {
            while (-not (Select-String $ReadyPattern $LogFile -Quiet)) {
                Start-Sleep -Milliseconds 100
            }
        }

        Write-Output "$Name is ready."
    } -ArgumentList $JavaExe, $Name, $PidName, $Jar, $LogFile, $ReadyPattern, $pids, $Arguments
}

# $GraceSeconds = 5
function Stop-ServiceByPid {
    param (
        [string]$Name
    )

    $pidFile = "$pids\$Name.pid"

    if (!(Test-Path $pidFile)) {
        Write-Output "No PID file for $Name."
        return
    }

    $processId = Get-Content $pidFile
    $proc = Get-Process -Id $processId -ErrorAction SilentlyContinue

    if ($proc) {
        Write-Output "Stopping $Name (PID $processId)..."
        # taskkill /F /PID $processId /T | Out-Null
        Stop-Process -Id $processId -Force
    } else {
        Write-Output "$Name already stopped."
    }

    Remove-Item $pidFile -Force -ErrorAction SilentlyContinue

}

function Wait-ForLog {
    param(
        [string]$File,
        [string]$Pattern
    )

    while (-not (Select-String $Pattern $File -Quiet)) {
        Start-Sleep -Milliseconds 100
    }
}


function Get-YamlValue {
    param (
        [string]$File,
        [string]$Key
    )

    $line = Select-String -Path $File -Pattern "^\s*$Key\s*:\s*(.+)$" | Select-Object -First 1

    if ($line) {
        return $line.Matches[0].Groups[1].Value.Trim()
    }

    return $null
}

function Get-ResolvedPort {
    param (
        [string]$ConfigPath,
        [string]$Key,
        [int]$DefaultPort
    )

    # No configuration.yml → use script default
    if (!(Test-Path $ConfigPath)) {
        return $DefaultPort
    }

    $configuredPort = Get-YamlValue $ConfigPath $Key

    # Missing or invalid value → use script default
    if (-not $configuredPort -or $configuredPort -notmatch '^\d+$') {
        return $DefaultPort
    }

    return [int]$configuredPort
}

function Get-ResolvedReactProxyUri {
    $defaultHost = "http://localhost"
    $defaultReverseProxyPort = 7080
    $defaultReactPrefix = "/react-ui"

    $configPath = Join-Path $PSScriptRoot "configuration.yml"

    # 1. File does not exist → fallback
    if (!(Test-Path $configPath)) {
        $hostUri = $defaultHost
        $reverseProxyPort = $defaultReverseProxyPort
        $reactPrefix = $defaultReactPrefix
    } else {
        # Each property independently overrides its default
        $hostUri = Get-YamlValue $configPath "host"
        if (-not $hostUri) {
            $hostUri = $defaultHost
        }

        $reverseProxyPort = Get-ResolvedPort $configPath "reverse-proxy-port" $defaultReverseProxyPort

        $reactPrefix = Get-YamlValue $configPath "react-prefix"
        if (-not $reactPrefix) {
            $reactPrefix = $defaultReactPrefix
        }
    }

    return "${hostUri}:$reverseProxyPort$reactPrefix"
}

function SetupH2 {
    #$h2Jar = Join-Path $PSScriptRoot "h2-2.4.240.jar" dev
    $h2Jar = Join-Path $Root "db\h2-2.4.240.jar"
    $dbUrl = "jdbc:h2:tcp://localhost:9092/identity_hub"

    $result = & $JavaExe -cp $h2Jar org.h2.tools.Shell -url $dbUrl -user sa -password "" -sql "SELECT COUNT(*) FROM INFORMATION_SCHEMA.USERS WHERE USER_NAME = 'NIDAM';" 2>&1
    Write-Host $result
    $lines = $result -split "`n"
    $nidamcount = [int]$lines[1].Trim()
    # Write-Host "Parsed count = $nidamcount"
    if ($nidamcount -eq "1") {
        Write-Host "H2 Database already configured. Skipping setup."
        return
    } else {
        Write-Host "H2 Nidam user does not exist. Creating..."
        & $JavaExe -cp $h2Jar org.h2.tools.Shell -url $dbUrl -user sa -password "" -sql (Get-Content (Join-Path $PSScriptRoot "db\init.sql") -Raw)
        Write-Host "User created. username: 'nidam', password: 'gF2mshbI819AV2L3'"
        Write-Host "DB setup completed."
    }
}

# -----------------------------------
# START
# -----------------------------------

function Start-Nidam {

    Write-Output "🚀 Starting Nidam (parallel mode)..."

    $configPath = Join-Path $PSScriptRoot "configuration.yml"

    # Resolve ports: configuration.yml overrides script defaults
    $registrationPort           = Get-ResolvedPort $configPath "registration-port" 4000
    $spaPort                    = Get-ResolvedPort $configPath "react-port" 4001
    $tokenGeneratorPort         = Get-ResolvedPort $configPath "authorization-server-port" 4002
    $nidamPort                  = Get-ResolvedPort $configPath "resource-server-port" 4003
    $reverseProxyPort           = Get-ResolvedPort $configPath "reverse-proxy-port" 7080
    $bffPort                    = Get-ResolvedPort $configPath "bff-port" 7081

    Write-Output "Ports:"
    Write-Output "  Registration:       $registrationPort"
    Write-Output "  SPA:                $spaPort"
    Write-Output "  Token Generator:    $tokenGeneratorPort"
    Write-Output "  Nidam:              $nidamPort"
    Write-Output "  Reverse Proxy:      $reverseProxyPort"
    Write-Output "  BFF:                $bffPort"

    # -------------------------------
    # Phase 1
    # -------------------------------
    Start-H2Database
    SetupH2


    # -------------------------------
    # Phase 2
    # -------------------------------
    $jobRegistration = Start-ServiceAsync $registrationPort "Registration" "registration" "registration-2.0.0.jar" "$logs\registration.log" "Started RegistrationApplication" "--spring.profiles.active=prod"

    $jobSpa = Start-ServiceAsync $spaPort "SPA Server" "spa" "spa-server-1.0.0.jar" "$logs\spa.log" "Started SpaServerApplication"

    $jobsPhase1 = @($jobRegistration, $jobSpa) | Where-Object { $_ -ne $null }
    if ($jobsPhase1.Count -gt 0) {
        Wait-And-PrintJobs @($jobRegistration, $jobSpa)
    }

    ## -------------------------------
    ## Phase 3
    ## -------------------------------
    $jobProxy = Start-ServiceAsync $reverseProxyPort "Reverse Proxy" "reverse-proxy" "reverse-proxy-2.0.0.jar" "$logs\proxy.log" "Started ReverseProxyApplication" "--spring.profiles.active=prod"

    $jobToken = Start-ServiceAsync $tokenGeneratorPort "Token Generator" "token-generator" "token-generator-2.0.0.jar" "$logs\token.log" "Started TokenGeneratorApplication" "--spring.profiles.active=prod"

    $jobsPhase2 = @($jobProxy, $jobToken) | Where-Object { $_ -ne $null }

    if ($jobsPhase2.Count -gt 0) {
        Wait-And-PrintJobs $jobsPhase2
    }

    ## -------------------------------
    ## Phase 4
    ## -------------------------------
    $jobNidam = Start-ServiceAsync $nidamPort "Nidam" "nidam" "nidam-2.0.0.jar" "$logs\nidam.log" "Started NidamApplication" "--spring.profiles.active=prod"

    $jobBff = Start-ServiceAsync $bffPort "BFF" "bff" "bff-2.0.0.jar" "$logs\bff.log" "Started BffApplication" "--spring.profiles.active=prod"

    $jobsPhase3 = @($jobNidam, $jobBff) | Where-Object { $_ -ne $null }

    if ($jobsPhase3.Count -gt 0) {
        Wait-And-PrintJobs $jobsPhase3
    }

    # Find all PowerShell jobs and delete them from the current PowerShell session.
    Get-Job | Remove-Job -Force -ErrorAction SilentlyContinue

    Write-Output "✅ Nidam started."

    $url = Get-ResolvedReactProxyUri
    Write-Output "Opening $url ..."
    Start-Process $url

}

# -----------------------------------
# STOP
# -----------------------------------

function Stop-Nidam {

    Write-Output "Stopping Nidam services..."

    "spa", "bff", "nidam", "token-generator", "reverse-proxy", "registration", "h2" | ForEach-Object { Stop-ServiceByPid $_ }

    Write-Output "✅ Nidam stopped."
}


# -----------------------------------
# RESTART
# -----------------------------------

function Restart-Nidam {
    Stop-Nidam
    Start-Sleep 1
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
