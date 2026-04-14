param(
    [ValidateSet("start", "stop", "restart")]
    [string]$Command = "start"
)

Set-Location $PSScriptRoot

$Root = $PSScriptRoot
$JavaExe = Join-Path $Root "jdk\bin\java.exe"
$logs = "logs"
$pids = "pids"

New-Item -ItemType Directory -Force -Path $logs | Out-Null
New-Item -ItemType Directory -Force -Path $pids | Out-Null


# $started = @{
#     registration = $false
#     token = $false
#     proxy = $false
#     bff   = $false
#     nidam = $false
#     spa   = $false
# }

# -----------------------------------
# Helpers
# -----------------------------------

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
        [string]$ReadyPattern
    )

    if (Test-PortListening $Port) {
        Write-Output "$Name already running."
        return
    }

    return Start-Job -ScriptBlock {
        param($JavaExe, $Name, $PidName, $Jar, $LogFile, $ReadyPattern, $pids)

        # Write-Output "Starting $Name..."

        $process = Start-Process $JavaExe -ArgumentList "-jar `"$Jar`"" -RedirectStandardOutput $LogFile -PassThru -WindowStyle Hidden

        $process.Id | Out-File "$pids\$PidName.pid"

        # Wait for readiness
        if ($ReadyPattern) {
            while (-not (Select-String $ReadyPattern $LogFile -Quiet)) {
                Start-Sleep -Milliseconds 100
            }
        }

        Write-Output "$Name is ready."
    } -ArgumentList $JavaExe, $Name, $PidName, $Jar, $LogFile, $ReadyPattern, $pids
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

function Get-ResolvedReactProxyUri {

    $configPath = Join-Path $PSScriptRoot "configuration.yml"

    $hostUri = Get-YamlValue $configPath "host"
    $reverseProxyPort = Get-YamlValue $configPath "reverse-proxy-port"
    $reactPrefix = Get-YamlValue $configPath "react-prefix"

    return "${hostUri}:$reverseProxyPort$reactPrefix"
}

# -----------------------------------
# START
# -----------------------------------

function Start-Nidam {

    Write-Output "🚀 Starting Nidam (parallel mode)..."

    # -------------------------------
    # Phase 1
    # -------------------------------
    $jobRegistration = Start-ServiceAsync 4000 "Registration" "registration" "registration-2.0.0.jar" "$logs\registration.log" "Started RegistrationApplication"

    $jobSpa = Start-ServiceAsync 4001 "SPA Server" "spa" "spa-server-1.0.0.jar" "$logs\spa.log" "Started SpaServerApplication"

    if ($jobRegistration) {
        Wait-And-PrintJobs @($jobRegistration, $jobSpa)
    }

    # -------------------------------
    # Phase 2
    # -------------------------------
    $jobProxy = Start-ServiceAsync 7080 "Reverse Proxy" "reverse-proxy" "reverse-proxy-2.0.0.jar" "$logs\proxy.log" "Started ReverseProxyApplication"

    $jobToken = Start-ServiceAsync 4002 "Token Generator" "token-generator" "token-generator-2.0.0.jar" "$logs\token.log" "Started TokenGeneratorApplication"

    $jobsPhase2 = @($jobProxy, $jobToken) | Where-Object { $_ -ne $null }

    if ($jobsPhase2.Count -gt 0) {
        Wait-And-PrintJobs $jobsPhase2
    }

    # -------------------------------
    # Phase 3
    # -------------------------------
    $jobNidam = Start-ServiceAsync 4003 "Nidam" "nidam" "nidam-2.0.0.jar" "$logs\nidam.log" "Started NidamApplication"

    $jobBff = Start-ServiceAsync 7081 "BFF" "bff" "bff-2.0.0.jar" "$logs\bff.log" "Started BffApplication"

    $jobsPhase3 = @($jobNidam, $jobBff) | Where-Object { $_ -ne $null }

    if ($jobsPhase3.Count -gt 0) {
        Wait-And-PrintJobs $jobsPhase3
    }

    Write-Output "✅ Nidam started."
    Get-Job | Remove-Job -Force -ErrorAction SilentlyContinue

    $url = Get-ResolvedReactProxyUri
    Write-Output "Opening $url ..."
    Start-Process $url


    # Start-IfNotRunning 4000 "Registration" "registration" "registration-2.0.0.jar" "$logs\registration.log" "Started RegistrationApplication"

    # Start-IfNotRunning 4002 "Token Generator" "token-generator" "token-generator-2.0.0.jar" "$logs\token.log" "Started TokenGeneratorApplication"

    # Start-IfNotRunning 7080 "Reverse Proxy" "reverse-proxy" "reverse-proxy-2.0.0.jar" "$logs\proxy.log" "Started ReverseProxyApplication"

    # Start-IfNotRunning 7081 "BFF" "bff" "bff-2.0.0.jar" "$logs\bff.log" "Started BffApplication"

    # Start-IfNotRunning 4003 "Nidam" "nidam" "nidam-2.0.0.jar" "$logs\nidam.log" "Started NidamApplication"

    # Start-IfNotRunning 4001 "SPA Server" "spa" "spa-server-1.0.0.jar" "$logs\spa.log" "Started SpaServerApplication"
    
}

# -----------------------------------
# STOP
# -----------------------------------

function Stop-Nidam {

    Write-Output "Stopping Nidam services..."

    "spa", "nidam", "bff", "reverse-proxy", "token-generator", "registration" |
        ForEach-Object { Stop-ServiceByPid $_ }

    Write-Output "✅ Nidam stopped."
}


# -----------------------------------
# RESTART
# -----------------------------------

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


