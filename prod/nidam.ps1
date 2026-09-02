param(
    [ValidateSet("start", "stop", "restart")]
    [string]$Command = "start",

    [ValidateSet("FRESH")]
    [string]$Mode,

    [ValidateSet("h2", "registration", "token-generator", "reverse-proxy", "bff", "nidam", "spa")]
    [string[]]$Exclude = @()
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
        Write-Host "║ Configure these properties in configuration.yml:                                                      ║" -ForegroundColor Yellow
        Write-Host "║                                                                                                       ║" -ForegroundColor Yellow
        Write-Host "║   users-db-url    users-db-user   users-db-password                                                   ║" -ForegroundColor Yellow
        Write-Host "║                                                                                                       ║" -ForegroundColor Yellow
        Write-Host "║ Read the documentation for more information                                                           ║" -ForegroundColor Yellow
        Write-Host "║ To Remove this message from appearing again remove lines from 33 to 47 in nidam.ps1                   ║" -ForegroundColor Yellow
        Write-Host "╚═══════════════════════════════════════════════════════════════════════════════════════════════════════╝" -ForegroundColor Yellow
        Write-Host ""
    }
    if ($EffectiveExclude.Count -gt 0) {
        Write-Host "Excluded: [$($EffectiveExclude -join ', ')]"
    }
    return $EffectiveExclude
}

function Test-Excluded {
    param ([string]$Name)

    return $EffectiveExclude -contains $Name
}

function Start-H2Database {

    $h2Jar = Join-Path $Root "db\h2-2.4.240.jar"

    if (!(Test-Path $h2Jar)) {
        Write-Host "H2 jar not found, skipping DB startup."
        return
    }

    if (Test-PortListening 9092) {
        Write-Host "H2 database already running."
        return
    }

    #Write-Host "Starting H2 database..."

    $process = Start-Process $JavaExe -ArgumentList "-cp `"$h2Jar`" org.h2.tools.Server -tcp -tcpAllowOthers -ifNotExists -baseDir ./db" -RedirectStandardOutput "$logs\h2.log" -PassThru -WindowStyle Hidden

    $process.Id | Out-File "$pids\h2.pid"

    # Wait until port is open
    while (-not (Test-PortListening 9092)) {
        Start-Sleep -Milliseconds 100
    }

    Write-Host "H2 database is ready."
}

function Test-PortListening {
    param ([int]$Port)

    return (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue) -ne $null
}

function Wait-And-PrintJobs {
    param ($jobs)

    $jobs = @($jobs | Where-Object { $_ })
    $success = $true

    #foreach ($job in ($jobs | Where-Object { $_ })) {
    #    Wait-Job $job | Out-Null
    #    $result = Receive-Job $job
    #    foreach ($line in $result) {
    #        if ($line -eq $false) {
    #            $success = $false
    #        }
    #    }
    #}
    #return $success

    while ($jobs | Where-Object { $_.State -eq "Running" }) {
        foreach ($job in $jobs) {
            $result = Receive-Job $job

            foreach ($line in $result) {
                if ($line -eq $false) {
                    $success = $false
                }
            }
        }
        Start-Sleep -Milliseconds 100
    }

    # Receive anything that was produced between the last poll
    # and the job completing.
    foreach ($job in $jobs) {
        $result = Receive-Job $job

        foreach ($line in $result) {
            if ($line -eq $false) {
                $success = $false
            }
        }
    }
    return $success
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
        Write-Host "$Name already running."
        return $null
    }

    return Start-Job -ScriptBlock {
        param($JavaExe, $Name, $PidName, $Jar, $LogFile, $ReadyPattern, $pids, $Arguments)

        # Write-Host "Starting $Name..."
        $process = Start-Process $JavaExe -ArgumentList "-jar `"$Jar`" $Arguments" -RedirectStandardOutput $LogFile -PassThru -WindowStyle Hidden

        $process.Id | Out-File "$pids\$PidName.pid"

        # Wait for readiness
        if ($ReadyPattern) {
            #while (-not (Select-String $ReadyPattern $LogFile -Quiet)) {
            #    Start-Sleep -Milliseconds 100
            #}
            $TimeoutSeconds = 60
            $timeout = [DateTime]::Now.AddSeconds($TimeoutSeconds)

            while ([DateTime]::Now -lt $timeout) {
                if (Select-String $ReadyPattern $LogFile -Quiet) {
                    Write-Host "$Name is ready."
                    return $true
                }

                # The Java process died before becoming ready.
                if ($process.HasExited) {
                    Write-Host "❌ $Name process exited before becoming ready."
                    return $false
                }

                Start-Sleep -Milliseconds 100
            }
        }

        Write-Host "❌ $Name failed to become ready within $TimeoutSeconds seconds."
        return $false
    } -ArgumentList $JavaExe, $Name, $PidName, $Jar, $LogFile, $ReadyPattern, $pids, $Arguments
}

# $GraceSeconds = 5
function Stop-ServiceByPid {
    param (
        [string]$Name
    )

    $pidFile = "$pids\$Name.pid"

    if (!(Test-Path $pidFile)) {
        Write-Host "No PID file for $Name."
        return
    }

    $processId = Get-Content $pidFile
    $proc = Get-Process -Id $processId -ErrorAction SilentlyContinue

    if ($proc) {
        Write-Host "Stopping $Name (PID $processId)..."
        # taskkill /F /PID $processId /T | Out-Null
        Stop-Process -Id $processId -Force
    } else {
        Write-Host "$Name already stopped."
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
    #Write-Host $result
    $lines = $result -split "`n"
    $nidamcount = [int]$lines[1].Trim()
    # Write-Host "Parsed count = $nidamcount"
    if ($nidamcount -eq "1") {
        #Write-Host "H2 Database already configured. Skipping setup."
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

    Write-Host "🚀 Starting Nidam (parallel mode)..."

    $configPath = Join-Path $PSScriptRoot "configuration.yml"

    # Resolve ports: configuration.yml overrides script defaults
    $registrationPort           = Get-ResolvedPort $configPath "registration-port" 4000
    $spaPort                    = Get-ResolvedPort $configPath "react-port" 4001
    $tokenGeneratorPort         = Get-ResolvedPort $configPath "authorization-server-port" 4002
    $nidamPort                  = Get-ResolvedPort $configPath "resource-server-port" 4003
    $reverseProxyPort           = Get-ResolvedPort $configPath "reverse-proxy-port" 7080
    $bffPort                    = Get-ResolvedPort $configPath "bff-port" 7081

    Write-Host "Ports:"
    if (-not (Test-Excluded "h2")) {Write-Host "  H2:                 9092" }
    if (-not (Test-Excluded "registration")) {Write-Host "  Registration:       $registrationPort"}
    if (-not (Test-Excluded "spa")) {Write-Host "  SPA:                $spaPort"}
    if (-not (Test-Excluded "token-generator")) {Write-Host "  Token Generator:    $tokenGeneratorPort"}
    if (-not (Test-Excluded "nidam")) {Write-Host "  Nidam:              $nidamPort"}
    if (-not (Test-Excluded "reverse-proxy")) {Write-Host "  Reverse Proxy:      $reverseProxyPort"}
    if (-not (Test-Excluded "bff")) {Write-Host "  BFF:                $bffPort"}

    # -------------------------------
    # Phase 1
    # -------------------------------
    if (-not (Test-Excluded "h2")) {
        Start-H2Database
        SetupH2
    }


    # -------------------------------
    # Phase 2
    # -------------------------------
    $jobRegistration = $null
    $jobSpa = $null

    if (-not (Test-Excluded "registration")) {
        $jobRegistration = Start-ServiceAsync $registrationPort "Registration" "registration" "registration-2.0.0.jar" "$logs\registration.log" "Started RegistrationApplication" "--spring.profiles.active=prod"
    }
    if (-not (Test-Excluded "spa")) {
        $jobSpa = Start-ServiceAsync $spaPort "SPA Server" "spa" "spa-server-1.0.0.jar" "$logs\spa.log" "Started SpaServerApplication"
    }

    $jobsPhase1 = @($jobRegistration, $jobSpa) | Where-Object { $_ -ne $null }
    if ($jobsPhase1.Count -gt 0) {
        $started = Wait-And-PrintJobs @($jobRegistration, $jobSpa)
        if (-not (Handle-StartFailure $started)) {
            return
        }
    }

    ## -------------------------------
    ## Phase 3
    ## -------------------------------
    $jobProxy = $null
    $jobToken = $null

    if (-not (Test-Excluded "reverse-proxy")) {
        $jobProxy = Start-ServiceAsync $reverseProxyPort "Reverse Proxy" "reverse-proxy" "reverse-proxy-2.0.0.jar" "$logs\proxy.log" "Started ReverseProxyApplication" "--spring.profiles.active=prod"
    }

    if (-not (Test-Excluded "token-generator")) {
        $jobToken = Start-ServiceAsync $tokenGeneratorPort "Token Generator" "token-generator" "token-generator-2.0.0.jar" "$logs\token.log" "Started TokenGeneratorApplication" "--spring.profiles.active=prod"
    }

    $jobsPhase2 = @($jobProxy, $jobToken) | Where-Object { $_ -ne $null }

    if ($jobsPhase2.Count -gt 0) {
        $started = Wait-And-PrintJobs $jobsPhase2
        if (-not (Handle-StartFailure $started)) {
            return
        }
    }

    ## -------------------------------
    ## Phase 4
    ## -------------------------------
    $jobNidam = $null
    $jobBff = $null

    if (-not (Test-Excluded "nidam")) {
        $jobNidam = Start-ServiceAsync $nidamPort "Nidam" "nidam" "nidam-2.0.0.jar" "$logs\nidam.log" "Started NidamApplication" "--spring.profiles.active=prod"
    }

    if (-not (Test-Excluded "bff")) {
        $jobBff = Start-ServiceAsync $bffPort "BFF" "bff" "bff-2.0.0.jar" "$logs\bff.log" "Started BffApplication" "--spring.profiles.active=prod"
    }

    $jobsPhase3 = @($jobNidam, $jobBff) | Where-Object { $_ -ne $null }

    if ($jobsPhase3.Count -gt 0) {
        $started = Wait-And-PrintJobs $jobsPhase3
        if (-not (Handle-StartFailure $started)) {
            return
        }
    }

    # Find all PowerShell jobs and delete them from the current PowerShell session.
    Get-Job | Remove-Job -Force -ErrorAction SilentlyContinue

    Write-Host "✅ Nidam started."

    if (-not (Test-Excluded "spa") -and -not (Test-Excluded "reverse-proxy")) {
        $url = Get-ResolvedReactProxyUri
        Write-Host "Opening $url ..."
        Start-Process $url
    }

}

# -----------------------------------
# STOP
# -----------------------------------

function Stop-Nidam {

    Write-Host "Stopping Nidam services..."

    "spa", "bff", "nidam", "token-generator", "reverse-proxy", "registration", "h2" |
            Where-Object { -not (Test-Excluded $_) } | ForEach-Object { Stop-ServiceByPid $_ }

    Write-Host "✅ Nidam stopped."
}


# -----------------------------------
# RESTART
# -----------------------------------

function Restart-Nidam {
    Stop-Nidam
    Start-Sleep 1
    Start-Nidam
}

$EffectiveExclude = Initialize-Exclusions

# -----------------------------------
# ENTRY POINT
# -----------------------------------

switch ($Command) {
    "start"   { Start-Nidam }
    "stop"    { Stop-Nidam }
    "restart" { Restart-Nidam }
}
