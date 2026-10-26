param(
	[Parameter(Position = 0)]
	[ValidateSet("start", "stop", "restart")]
	[string]$Command = "start",

	[ValidateSet("FRESH")]
	[string]$Mode,

	[ValidateSet("h2", "registration", "token-generator", "reverse-proxy", "bff", "nidam", "spa")]
	[string[]]$Exclude = @(),

	[Parameter(Position = 1)]
	[ValidateSet("h2", "registration", "token-generator", "reverse-proxy", "bff", "nidam", "spa")]
	[string]$Only
)

#Set-Location $PSScriptRoot

#$Root = $PSScriptRoot
#$JavaExe = Join-Path $Root "runtime\bin\java.exe"
#$logs = "logs"
#$pids = "pids"
#
#New-Item -ItemType Directory -Force -Path $logs | Out-Null
#New-Item -ItemType Directory -Force -Path $pids | Out-Null




Set-Location $PSScriptRoot

$Root = $PSScriptRoot

$RuntimeBin = Join-Path (Join-Path $Root "runtime") "bin"
$JavaName = if ($IsWindows)
{
	"java.exe"
}
else
{
	"java"
}
$JavaExe = Join-Path $RuntimeBin $JavaName

$DbDir = Join-Path $Root "db"
$H2Jar = Join-Path $DbDir "h2-2.5.252.jar"

$logs = Join-Path $Root "logs"
$pids = Join-Path $Root "pids"

New-Item -ItemType Directory -Force -Path $logs | Out-Null
New-Item -ItemType Directory -Force -Path $pids | Out-Null


# -----------------------------------
# Helpers
# -----------------------------------

function Initialize-Exclusions
{
	$EffectiveExclude = @($Exclude)

	if ($Mode -eq "FRESH")
	{
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
		Write-Host "║ To Remove this message from appearing again remove lines from 38 to 52 in nidam.ps1                   ║" -ForegroundColor Yellow
		Write-Host "╚═══════════════════════════════════════════════════════════════════════════════════════════════════════╝" -ForegroundColor Yellow
		Write-Host ""
	}
	if ($EffectiveExclude.Count -gt 0)
	{
		Write-Host "Excluded: [$( $EffectiveExclude -join ', ' )]"
	}
	return $EffectiveExclude
}

function Test-Excluded
{
	param ([string]$Name)

	return $EffectiveExclude -contains $Name
}

# Download JDBC Drivers for Oracle and IBM Databases
$JdbcDrivers = @{
	oracle = @{
		GroupId = "com.oracle.database.jdbc"
		ArtifactId = "ojdbc11"
		Version = "23.26.3.0.0"
	}

	db2 = @{
		GroupId = "com.ibm.db2"
		ArtifactId = "jcc"
		Version = "12.1.5.0"
	}
}

function Get-MavenCentralUrl
{
	param(
		[hashtable]$Driver
	)

	$groupPath = $Driver.GroupId.Replace(".", "/")

	#return "https://repo1.maven.org/maven2/$groupPath/$ArtifactId/$Version/$ArtifactId-$Version.jar"
	return "https://repo1.maven.org/maven2/$groupPath/$( $Driver.ArtifactId )/$( $Driver.Version )/$( $Driver.ArtifactId )-$( $Driver.Version ).jar"
}

function Get-DatabaseType
{
	param([string]$JdbcUrl)

	if ($JdbcUrl -match '^jdbc:oracle:')
	{
		return "oracle"
	}

	if ($JdbcUrl -match '^jdbc:db2:')
	{
		return "db2"
	}

	return $null
}

function Ensure-JdbcDriver
{
	param([string]$DatabaseType)

	if (-not $JdbcDrivers.ContainsKey($DatabaseType))
	{
		return $null
	}

	$driver = $JdbcDrivers[$DatabaseType]

	$driversRoot = Join-Path $Root "jdbc"
	$driverDir = Join-Path $driversRoot $DatabaseType
	$driverJar = Join-Path $driverDir "$( $driver.ArtifactId )-$( $driver.Version ).jar"

	if (!(Test-Path $driverJar))
	{
		New-Item -ItemType Directory -Force -Path $driverDir | Out-Null

		#$url = Get-MavenCentralUrl "com.oracle.database.jdbc" "ojdbc11" "23.26.1.0.0"
		$url = Get-MavenCentralUrl $driver

		Write-Host "$($DatabaseType.ToUpper() ) JDBC driver not found."
		Write-Host "Downloading $( $driver.ArtifactId ) $( $driver.Version )..."

		#Invoke-WebRequest `
		#    -Uri  Get-MavenCentralUrl"https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc11/23.26.1.0.0/ojdbc11-23.26.1.0.0.jar" `
		#    -OutFile $driverJar
		try
		{
			Invoke-WebRequest -Uri $url -OutFile $driverJar -UseBasicParsing

			Write-Host "$( $driver.ArtifactId ) JDBC driver downloaded."
		}
		catch
		{
			Write-Host "❌ Failed to download $( $driver.ArtifactId ) JDBC driver." -ForegroundColor Red
			Write-Host $_.Exception.Message -ForegroundColor Red
			return $null
		}
	}
	else
	{
		Write-Host "$( $driver.ArtifactId ) JDBC driver already installed."
	}

	return $driverDir
}

function Start-DetachedJava {
	param(
		[string]$JavaExe,
		[string[]]$JavaArguments,
		[string]$LogFile,
		[string]$WorkingDirectory
	)

	# Unix: Start-Process -RedirectStandardOutput is pumped through this pwsh process,
	# so output stops when pwsh (or the job host) exits. Let sh open the file instead.
	$psi = [System.Diagnostics.ProcessStartInfo]::new("/bin/sh")
	$psi.UseShellExecute = $false
	$psi.WorkingDirectory = $WorkingDirectory
	$psi.Environment["NIDAM_LOG_FILE"] = $LogFile

	# sh -c '<script>' $0 $1 ... ; ArgumentList quotes each argument correctly
	$psi.ArgumentList.Add("-c")
	$psi.ArgumentList.Add('exec nohup "$0" "$@" </dev/null >"$NIDAM_LOG_FILE" 2>&1')
	$psi.ArgumentList.Add($JavaExe)
	foreach ($arg in $JavaArguments)
	{
		$psi.ArgumentList.Add($arg)
	}

	return [System.Diagnostics.Process]::Start($psi)
}

# Jobs run in a separate process and can't see script functions
$DetachedJavaInit = [scriptblock]::Create("function Start-DetachedJava { ${function:Start-DetachedJava} }")

function Start-H2Database
{
	param(
		[int]$Port
	)

	$h2Jar = $H2Jar

	if (!(Test-Path $h2Jar))
	{
		Write-Host "H2 jar not found, skipping DB startup."
		return
	}

	if (Test-PortListening $Port)
	{
		Write-Host "H2 database already running."
		return
	}

	#Write-Host "Starting H2 database..."


	$logFile = Join-Path $logs "h2.log"
	$pidFile = Join-Path $pids "h2.pid"

	if ($IsWindows) {
		$javaArguments = "-cp `"$H2Jar`" org.h2.tools.Server -tcp -tcpAllowOthers -ifNotExists -tcpPort $Port -baseDir `"$DbDir`""
		$process = Start-Process -FilePath $JavaExe -ArgumentList $javaArguments -RedirectStandardOutput $logFile -PassThru -WorkingDirectory $Root -WindowStyle Hidden
	}
	else {
		$javaArgs = @("-cp", $H2Jar, "org.h2.tools.Server", "-tcp", "-tcpAllowOthers", "-ifNotExists", "-tcpPort", "$Port", "-baseDir", $DbDir)
		$process = Start-DetachedJava $JavaExe $javaArgs $logFile $Root
	}

	#$startParams = @{
	#	FilePath = $JavaExe
	#	ArgumentList = $javaArguments
	#	RedirectStandardOutput = $logFile
	#	PassThru = $true
	#	WorkingDirectory = $Root
	#}
	#
	#if ($IsWindows)
	#{
	#	$startParams.WindowStyle = "Hidden"
	#}
	#else
	#{
	#	$javaArgs = @("-cp", $H2Jar, "org.h2.tools.Server", "-tcp", "-tcpAllowOthers", "-ifNotExists", "-tcpPort", "$Port", "-baseDir", $DbDir)
	#	$process = Start-DetachedJava $JavaExe $javaArgs $logFile $Root
	#}
	#
	#$process = Start-Process @startParams

	$process.Id | Out-File $pidFile




	# Wait until port is open
	while (-not (Test-PortListening $Port))
	{
		Start-Sleep -Milliseconds 100
	}

	Write-Host "H2 database is ready."
}

function Test-PortListening
{
	param ([int]$Port)

	if ($IsWindows)
	{
		return $null -ne ( Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -First 1)
	}

	# Linux: inspect listening TCP sockets without opening a connection.
	if (Get-Command ss -ErrorAction SilentlyContinue)
	{
		$listeners = & ss -H -ltn "sport = :$Port" 2> $null
		return -not [string]::IsNullOrWhiteSpace(($listeners -join "`n"))
	}

	# macos
	if (Get-Command lsof -ErrorAction SilentlyContinue)
	{
		$listeners = & lsof -nP -t "-iTCP:$Port" -sTCP:LISTEN 2> $null
		return -not [string]::IsNullOrWhiteSpace(($listeners -join "`n"))
	}

	# Fallback for systems without ss.
	$client = [System.Net.Sockets.TcpClient]::new()

	try
	{
		$connection = $client.ConnectAsync([System.Net.IPAddress]::Loopback, $Port)
		return $connection.Wait(300) -and $client.Connected
	}
	catch
	{
		return $false
	}
	finally
	{
		$client.Dispose()
	}
}

function Wait-And-PrintJobs
{
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

	while ($jobs | Where-Object { $_.State -eq "Running" })
	{
		foreach ($job in $jobs)
		{
			$result = Receive-Job $job

			foreach ($line in $result)
			{
				if ($line -eq $false)
				{
					$success = $false
				}
			}
		}
		Start-Sleep -Milliseconds 100
	}

	# Receive anything that was produced between the last poll
	# and the job completing.
	foreach ($job in $jobs)
	{
		$result = Receive-Job $job

		foreach ($line in $result)
		{
			if ($line -eq $false)
			{
				$success = $false
			}
		}
	}
	return $success
}

function Handle-StartFailure
{
	param ([bool]$Started)

	if (-not $Started)
	{
		Write-Host "❌ Nidam did not start." -ForegroundColor Red
		Stop-Nidam | Out-Null
		return $false
	}

	return $true
}

function Start-ServiceAsync
{
	param(
		[int]$Port,
		[string]$Name,
		[string]$PidName,
		[string]$Jar,
		[string]$LogFile,
		[string]$ReadyPattern,
		[string]$Arguments = "",
		[string]$DriverPath = $null
	)

	if (Test-PortListening $Port)
	{
		Write-Host "$Name already running."
		return $null
	}

	$jarPath = Join-Path $Root $Jar

	return Start-Job -InitializationScript $DetachedJavaInit -ScriptBlock {
		param($JavaExe, $Name, $PidName, $jarPath, $LogFile, $ReadyPattern, $pids, $Arguments, $DriverPath, $Root)

		# Write-Host "Starting $Name..."

		if ($IsWindows) {
			$javaArguments = ""
			if ($DriverPath) { $javaArguments += "-Dloader.path=`"$DriverPath`" " }
			$javaArguments += "-jar `"$jarPath`" $Arguments"

			$process = Start-Process -FilePath $JavaExe -ArgumentList $javaArguments -RedirectStandardOutput $LogFile -PassThru -WorkingDirectory $Root -WindowStyle Hidden
		}
		else {
			$javaArgs = @()
			if ($DriverPath) { $javaArgs += "-Dloader.path=$DriverPath" }
			$javaArgs += "-jar", $jarPath
			$javaArgs += $Arguments.Split(" ", [StringSplitOptions]::RemoveEmptyEntries)

			$process = Start-DetachedJava $JavaExe $javaArgs $LogFile $Root
		}

		#$javaArguments += "-jar `"$jarPath`" $Arguments"
		#
		#$startParams = @{
		#	FilePath = $JavaExe
		#	ArgumentList = $javaArguments
		#	RedirectStandardOutput = $LogFile
		#	PassThru = $true
		#	WorkingDirectory = $Root
		#}
		#
		#if ($IsWindows)
		#{
		#	$startParams.WindowStyle = "Hidden"
		#}
		#else
		#{
		#	# Detach the process so it survives when PowerShell exits.
		#	$startParams.FilePath = (Get-Command nohup -ErrorAction Stop).Source
		#	$startParams.ArgumentList = "`"$JavaExe`" $javaArguments"
		#}
		#
		#
		#$process = Start-Process @startParams

		#$process = Start-Process $JavaExe -ArgumentList $javaArguments -RedirectStandardOutput $LogFile -PassThru -WindowStyle Hidden

		$pidFile = Join-Path $pids "$PidName.pid"

		$process.Id | Out-File $pidFile
		#$process.Id | Out-File (Join-Path $pids "$PidName.pid")

		# Wait for readiness
		if ($ReadyPattern)
		{
			#while (-not (Select-String $ReadyPattern $LogFile -Quiet)) {
			#    Start-Sleep -Milliseconds 100
			#}
			$TimeoutSeconds = 60
			$timeout = [DateTime]::Now.AddSeconds($TimeoutSeconds)

			while ([DateTime]::Now -lt $timeout)
			{
				if (Select-String $ReadyPattern $LogFile -Quiet)
				{
					Write-Host "$Name is ready."
					return $true
				}

				# The Java process died before becoming ready.
				if ($process.HasExited)
				{
					Write-Host "❌ $Name process exited before becoming ready."
					return $false
				}

				Start-Sleep -Milliseconds 100
			}
		}

		Write-Host "❌ $Name failed to become ready within $TimeoutSeconds seconds."
		return $false
	} -ArgumentList $JavaExe, $Name, $PidName, $jarPath, $LogFile, $ReadyPattern, $pids, $Arguments, $DriverPath, $Root
}

# $GraceSeconds = 5
function Stop-ServiceByPid
{
	param (
		[string]$Name
	)

	$pidFile = (Join-Path $pids "$Name.pid")

	if (!(Test-Path $pidFile))
	{
		Write-Host "No PID file for $Name."
		return
	}

	$processId = Get-Content $pidFile
	$proc = Get-Process -Id $processId -ErrorAction SilentlyContinue

	if ($proc)
	{
		Write-Host "Stopping $Name (PID $processId)..."
		# taskkill /F /PID $processId /T | Out-Null
		Stop-Process -Id $processId -Force
	}
	else
	{
		Write-Host "$Name already stopped."
	}

	Remove-Item $pidFile -Force -ErrorAction SilentlyContinue

}

function Wait-ForLog
{
	param(
		[string]$File,
		[string]$Pattern
	)

	while (-not (Select-String $Pattern $File -Quiet))
	{
		Start-Sleep -Milliseconds 100
	}
}


function Get-YamlValue
{
	param (
		[string]$File,
		[string]$Key
	)

	$line = Select-String -Path $File -Pattern "^\s*$Key\s*:\s*(.+)$" | Select-Object -First 1

	if ($line)
	{
		return $line.Matches[0].Groups[1].Value.Trim()
	}

	return $null
}

function Get-ResolvedPort
{
	param (
		[string]$ConfigPath,
		[string]$Key,
		[int]$DefaultPort
	)

	# No configuration.yml → use script default
	if (!(Test-Path $ConfigPath))
	{
		return $DefaultPort
	}

	$configuredPort = Get-YamlValue $ConfigPath $Key

	# Missing or invalid value → use script default
	if (-not $configuredPort -or $configuredPort -notmatch '^\d+$')
	{
		return $DefaultPort
	}

	return [int]$configuredPort
}

function Get-ResolvedReactProxyUri
{
	$defaultHost = "http://localhost"
	$defaultReverseProxyPort = 7080
	$defaultReactPrefix = "/spa"

	$configPath = Join-Path $PSScriptRoot "configuration.yml"

	# 1. File does not exist → fallback
	if (!(Test-Path $configPath))
	{
		$hostUri = $defaultHost
		$reverseProxyPort = $defaultReverseProxyPort
		$reactPrefix = $defaultReactPrefix
	}
	else
	{
		# Each property independently overrides its default
		$hostUri = Get-YamlValue $configPath "host"
		if (-not $hostUri)
		{
			$hostUri = $defaultHost
		}

		$reverseProxyPort = Get-ResolvedPort $configPath "reverse-proxy-port" $defaultReverseProxyPort

		$reactPrefix = Get-YamlValue $configPath "react-prefix"
		if (-not $reactPrefix)
		{
			$reactPrefix = $defaultReactPrefix
		}
	}

	return "${hostUri}:$reverseProxyPort$reactPrefix"
}

function SetupH2
{
	param(
		[int]$Port
	)
	#$h2Jar = Join-Path $PSScriptRoot "h2-2.5.252.jar" dev
	$h2Jar = $H2Jar
	$dbUrl = "jdbc:h2:tcp://localhost:$Port/identity_hub"

	$result = & $JavaExe -cp $h2Jar org.h2.tools.Shell -url $dbUrl -user sa -password "" -sql "SELECT COUNT(*) FROM INFORMATION_SCHEMA.USERS WHERE USER_NAME = 'NIDAM';" 2>&1
	#Write-Host $result
	$lines = $result -split "`n"
	$nidamcount = [int]$lines[1].Trim()
	# Write-Host "Parsed count = $nidamcount"
	if ($nidamcount -eq "1")
	{
		#Write-Host "H2 Database already configured. Skipping setup."
		return
	}
	else
	{
		Write-Host "H2 Nidam user does not exist. Creating..."
		& $JavaExe -cp $h2Jar org.h2.tools.Shell -url $dbUrl -user sa -password "" -sql (Get-Content (Join-Path $DbDir "init.sql") -Raw)
		Write-Host "User created. username: 'nidam', password: 'gF2mshbI819AV2L3'"
		Write-Host "DB setup completed."
	}
}

# -----------------------------------
# START
# -----------------------------------

function Start-Nidam
{

	Write-Host "🚀 Starting Nidam (parallel mode)..."

	$configPath = Join-Path $PSScriptRoot "configuration.yml"

	# Resolve ports: configuration.yml overrides script defaults
	$h2Port = Get-ResolvedPort $configPath "h2-embedded-port" 9092
	$registrationPort = Get-ResolvedPort $configPath "registration-port" 4000
	$spaPort = Get-ResolvedPort $configPath "react-port" 4001
	$tokenGeneratorPort = Get-ResolvedPort $configPath "authorization-server-port" 4002
	$nidamPort = Get-ResolvedPort $configPath "resource-server-port" 4003
	$reverseProxyPort = Get-ResolvedPort $configPath "reverse-proxy-port" 7080
	$bffPort = Get-ResolvedPort $configPath "bff-port" 7081

	Write-Host "Ports:"
	if (-not (Test-Excluded "h2"))
	{
		Write-Host "  H2:                 $h2Port"
	}
	if (-not (Test-Excluded "registration"))
	{
		Write-Host "  Registration:       $registrationPort"
	}
	if (-not (Test-Excluded "spa"))
	{
		Write-Host "  SPA:                $spaPort"
	}
	if (-not (Test-Excluded "token-generator"))
	{
		Write-Host "  Token Generator:    $tokenGeneratorPort"
	}
	if (-not (Test-Excluded "nidam"))
	{
		Write-Host "  Nidam:              $nidamPort"
	}
	if (-not (Test-Excluded "reverse-proxy"))
	{
		Write-Host "  Reverse Proxy:      $reverseProxyPort"
	}
	if (-not (Test-Excluded "bff"))
	{
		Write-Host "  BFF:                $bffPort"
	}

	# -------------------------------
	# Phase 1
	# -------------------------------
	if (-not (Test-Excluded "h2"))
	{
		Start-H2Database $h2Port
		SetupH2 $h2Port
	}

	# JDBC Drivers
	$dbUrl = Get-YamlValue $configPath "users-db-url"
	$dbType = Get-DatabaseType $dbUrl

	$driverPath = $null

	if ($dbType)
	{
		Write-Host "Database: $($dbType.ToUpper() )"
		$driverPath = Ensure-JdbcDriver $dbType
		if (-not $driverPath)
		{
			Write-Host "❌ Required JDBC driver could not be installed." -ForegroundColor Red
			return
		}
	}


	# -------------------------------
	# Phase 2
	# -------------------------------
	$jobRegistration = $null
	$jobSpa = $null

	if (-not (Test-Excluded "registration"))
	{
		$jobRegistration = Start-ServiceAsync $registrationPort "Registration" "registration" "registration-3.0.jar" (Join-Path $logs "registration.log") "Started RegistrationApplication" "--spring.profiles.active=prod" $driverPath
	}
	if (-not (Test-Excluded "spa"))
	{
		$jobSpa = Start-ServiceAsync $spaPort "SPA Server" "spa" "spa-server-3.0.jar" (Join-Path $logs "spa.log") "Started SpaServerApplication"
	}

	$jobsPhase1 = @($jobRegistration, $jobSpa) | Where-Object { $_ -ne $null }
	if ($jobsPhase1.Count -gt 0)
	{
		$started = Wait-And-PrintJobs @($jobRegistration, $jobSpa)
		if (-not (Handle-StartFailure $started))
		{
			return
		}
	}

	## -------------------------------
	## Phase 3
	## -------------------------------
	$jobProxy = $null
	$jobToken = $null

	if (-not (Test-Excluded "reverse-proxy"))
	{
		$jobProxy = Start-ServiceAsync $reverseProxyPort "Reverse Proxy" "reverse-proxy" "reverse-proxy-3.0.jar" (Join-Path $logs "proxy.log") "Started ReverseProxyApplication" "--spring.profiles.active=prod"
	}

	if (-not (Test-Excluded "token-generator"))
	{
		$jobToken = Start-ServiceAsync $tokenGeneratorPort "Token Generator" "token-generator" "token-generator-3.0.jar" (Join-Path $logs "token.log") "Started TokenGeneratorApplication" "--spring.profiles.active=prod" $driverPath
	}

	$jobsPhase2 = @($jobProxy, $jobToken) | Where-Object { $_ -ne $null }

	if ($jobsPhase2.Count -gt 0)
	{
		$started = Wait-And-PrintJobs $jobsPhase2
		if (-not (Handle-StartFailure $started))
		{
			return
		}
	}

	## -------------------------------
	## Phase 4
	## -------------------------------
	$jobNidam = $null
	$jobBff = $null

	if (-not (Test-Excluded "nidam"))
	{
		$jobNidam = Start-ServiceAsync $nidamPort "Nidam" "nidam" "nidam-3.0.jar" (Join-Path $logs "nidam.log") "Started NidamApplication" "--spring.profiles.active=prod"
	}

	if (-not (Test-Excluded "bff"))
	{
		$jobBff = Start-ServiceAsync $bffPort "BFF" "bff" "bff-3.0.jar" (Join-Path $logs "bff.log") "Started BffApplication" "--spring.profiles.active=prod"
	}

	$jobsPhase3 = @($jobNidam, $jobBff) | Where-Object { $_ -ne $null }

	if ($jobsPhase3.Count -gt 0)
	{
		$started = Wait-And-PrintJobs $jobsPhase3
		if (-not (Handle-StartFailure $started))
		{
			return
		}
	}

	# Find all PowerShell jobs and delete them from the current PowerShell session.
	Get-Job | Remove-Job -Force -ErrorAction SilentlyContinue

	Write-Host "✅ Nidam started."

	if (-not (Test-Excluded "spa") -and -not (Test-Excluded "reverse-proxy"))
	{
		$url = Get-ResolvedReactProxyUri
		Write-Host "Opening $url ..."
		if ($IsWindows)
		{
			Start-Process $url
		}
		elseif ($IsMacOS)
		{
			& open $url
		}
		elseif (Get-Command xdg-open -ErrorAction SilentlyContinue)
		{
			& xdg-open $url
		}
		else
		{
			Write-Host "Open this URL in your browser: $url"
		}
	}

}

# -----------------------------------
# STOP
# -----------------------------------

function Stop-Nidam
{

	if ($Only)
	{
		Write-Host "Stopping only: $Only..."
		Stop-ServiceByPid $Only
		Write-Host "✅ $Only stopped."
		return
	}

	Write-Host "Stopping Nidam services..."

	"spa", "bff", "nidam", "token-generator", "reverse-proxy", "registration", "h2" |
			Where-Object { -not (Test-Excluded $_) } | ForEach-Object { Stop-ServiceByPid $_ }

	Write-Host "✅ Nidam stopped."
}


# -----------------------------------
# RESTART
# -----------------------------------

function Restart-Nidam
{
	Stop-Nidam
	Start-Sleep 1
	Start-Nidam
}

$EffectiveExclude = Initialize-Exclusions

# -----------------------------------
# ENTRY POINT
# -----------------------------------

switch ($Command)
{
	"start"   {
		Start-Nidam
	}
	"stop"    {
		Stop-Nidam
	}
	"restart" {
		Restart-Nidam
	}
}
