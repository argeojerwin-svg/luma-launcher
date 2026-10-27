#requires -Version 5.1
[CmdletBinding()]
param(
    [ValidateSet('Start', 'Stop', 'Status', 'Watch', 'Install', 'Uninstall')]
    [string]$Action = 'Status',
    [string]$Serial = $env:P6_SERIAL,
    [string]$AdbPath = $env:ADB_BIN,
    [ValidateRange(5, 300)]
    [int]$IntervalSeconds = 20
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$stateDir = Join-Path $repoRoot '.local\menu-bridge'
$configPath = Join-Path $stateDir 'config.json'
$stopFile = Join-Path $stateDir 'stopped'
$logPath = Join-Path $stateDir 'watch.log'
New-Item -ItemType Directory -Force -Path $stateDir | Out-Null
if (Test-Path -LiteralPath $configPath) {
    $savedConfig = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
    if (-not $Serial) { $Serial = $savedConfig.Serial }
    if (-not $AdbPath) { $AdbPath = $savedConfig.AdbPath }
}
if ($Serial -notmatch '^[a-zA-Z0-9][a-zA-Z0-9.-]*:[0-9]{1,5}$') {
    throw 'Provide -Serial with the projector IP:port, such as 192.168.1.50:5555.'
}
if (-not $AdbPath) { $AdbPath = (Get-Command adb -ErrorAction Stop).Source }
$AdbPath = (Resolve-Path -LiteralPath $AdbPath).Path
$hashAlgorithm = [Security.Cryptography.SHA256]::Create()
try { $serialHash = ([BitConverter]::ToString($hashAlgorithm.ComputeHash([Text.Encoding]::UTF8.GetBytes($Serial)))).Replace('-', '').Substring(0, 12) }
finally { $hashAlgorithm.Dispose() }
$taskName = "Luma P6 Menu Bridge $serialHash"
$controller = '/data/local/tmp/luma-menu-control.sh'
$script:lastMessage = ''

function ConvertTo-NativeArgument {
    param([string]$Value)
    # Windows command-line quoting, including quotes and trailing backslashes.
    $escaped = [regex]::Replace($Value, '(\\*)"', '${1}${1}\"')
    $escaped = [regex]::Replace($escaped, '(\\+)$', '${1}${1}')
    return '"' + $escaped + '"'
}

function Invoke-AdbCommand {
    param([string[]]$CommandArgs, [int]$TimeoutMilliseconds = 15000)
    $start = New-Object System.Diagnostics.ProcessStartInfo
    $start.FileName = $AdbPath
    $start.Arguments = (($CommandArgs | ForEach-Object { ConvertTo-NativeArgument $_ }) -join ' ')
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $start
    try {
        [void]$process.Start()
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit($TimeoutMilliseconds)) {
            $process.Kill()
            $process.WaitForExit()
            throw 'ADB timed out; the next recovery check will retry.'
        }
        $result = [pscustomobject]@{
            ExitCode = $process.ExitCode
            Text = ($stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult()).Trim()
        }
        return $result
    }
    finally { $process.Dispose() }
}

function Invoke-Device {
    param([string[]]$CommandArgs)
    $result = Invoke-AdbCommand -CommandArgs (@('-s', $Serial) + $CommandArgs)
    if ($result.ExitCode -ne 0) { throw "ADB failed: $($result.Text)" }
    return $result.Text
}

function Write-BridgeLog {
    param([string]$Message)
    if ($Message -eq $script:lastMessage) { return }
    $script:lastMessage = $Message
    $line = '{0} {1}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message
    Add-Content -LiteralPath $logPath -Value $line
    Write-Output $line
}

function Connect-Projector {
    [void](Invoke-AdbCommand -CommandArgs @('connect', $Serial))
    $state = Invoke-AdbCommand -CommandArgs @('-s', $Serial, 'get-state')
    if ($state.ExitCode -ne 0 -or $state.Text -ne 'device') {
        [void](Invoke-AdbCommand -CommandArgs @('disconnect', $Serial))
        [void](Invoke-AdbCommand -CommandArgs @('connect', $Serial))
        $state = Invoke-AdbCommand -CommandArgs @('-s', $Serial, 'get-state')
        if ($state.ExitCode -ne 0 -or $state.Text -ne 'device') {
            throw 'Projector is unavailable or needs ADB authorization.'
        }
    }
}

function Ensure-Bridge {
    Connect-Projector
    if ((Invoke-Device -CommandArgs @('shell', 'id', '-u')) -ne '0') {
        [void](Invoke-Device -CommandArgs @('root'))
        Start-Sleep -Seconds 2
        Connect-Projector
        if ((Invoke-Device -CommandArgs @('shell', 'id', '-u')) -ne '0') {
            throw 'This projector does not allow root-capable ADB, which the P6 bridge requires.'
        }
    }
    $check = Invoke-AdbCommand -CommandArgs @('-s', $Serial, 'shell', 'sh', $controller, 'check')
    if ($check.ExitCode -eq 0 -and $check.Text -eq 'running') {
        Write-BridgeLog 'Menu bridge is running.'
        return
    }
    if ((Invoke-Device -CommandArgs @('shell', 'pm', 'path', 'dev.luma.launcher')) -notmatch '^package:') {
        throw 'Luma is not installed; the bridge was not started.'
    }
    $inputDevices = Invoke-Device -CommandArgs @('shell', 'cat', '/proc/bus/input/devices')
    if ($inputDevices -notmatch 'Name="sunxi-ir-uinput"') {
        throw 'This projector does not expose the supported P6 IR device.'
    }
    foreach ($name in @('luma-menu-bridge.sh', 'luma-menu-control.sh')) {
        # Git for Windows may check out CRLF files; Android requires LF shell scripts.
        $source = Join-Path $PSScriptRoot $name
        $normalized = Join-Path $stateDir $name
        $content = [IO.File]::ReadAllText($source).Replace("`r`n", "`n")
        [IO.File]::WriteAllText($normalized, $content, (New-Object Text.UTF8Encoding($false)))
        [void](Invoke-Device -CommandArgs @('push', $normalized, "/data/local/tmp/$name"))
    }
    [void](Invoke-Device -CommandArgs @('shell', 'sh', $controller, 'start'))
    Write-BridgeLog 'Menu bridge recovered and is running.'
}

function Stop-Bridge {
    New-Item -ItemType File -Force -Path $stopFile | Out-Null
    $task = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
    if ($task -and $task.State -eq 'Running') { Stop-ScheduledTask -TaskName $taskName }
    Connect-Projector
    [void](Invoke-Device -CommandArgs @('shell', 'sh', $controller, 'stop'))
    Write-Output 'Menu bridge and Windows recovery stopped.'
}

switch ($Action) {
    'Start' {
        if (Test-Path -LiteralPath $stopFile) { Remove-Item -LiteralPath $stopFile }
        Ensure-Bridge
        $task = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
        if ($task -and $task.State -ne 'Running') { Start-ScheduledTask -TaskName $taskName }
    }
    'Stop' { Stop-Bridge }
    'Status' {
        Connect-Projector
        $result = Invoke-AdbCommand -CommandArgs @('-s', $Serial, 'shell', 'sh', $controller, 'status')
        if ($result.ExitCode -eq 0) { Write-Output $result.Text }
        else { Write-Output 'Menu bridge is not installed or is unavailable.' }
        $task = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
        if ($task) { Write-Output "Windows recovery: $($task.State)" }
        else { Write-Output 'Windows recovery is not installed.' }
    }
    'Watch' {
        $mutex = New-Object Threading.Mutex($false, "Local\LumaP6MenuBridge-$serialHash")
        $ownsMutex = $false
        try {
            try { $ownsMutex = $mutex.WaitOne(0) }
            catch [Threading.AbandonedMutexException] { $ownsMutex = $true }
            if (-not $ownsMutex) { exit 0 }
            while (-not (Test-Path -LiteralPath $stopFile)) {
                try { Ensure-Bridge }
                catch { Write-BridgeLog $_.Exception.Message }
                Start-Sleep -Seconds $IntervalSeconds
            }
        }
        finally {
            if ($ownsMutex) { $mutex.ReleaseMutex() }
            $mutex.Dispose()
        }
    }
    'Install' {
        # Register per-user recovery; no administrator-level task or password is needed.
        @{ Serial = $Serial; AdbPath = $AdbPath } | ConvertTo-Json | Set-Content -LiteralPath $configPath
        if (Test-Path -LiteralPath $stopFile) { Remove-Item -LiteralPath $stopFile }
        Ensure-Bridge
        $windowsPowerShell = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
        $taskArguments = @('-NoProfile', '-NonInteractive', '-WindowStyle', 'Hidden', '-ExecutionPolicy', 'Bypass', '-File', $PSCommandPath,
            '-Action', 'Watch', '-Serial', $Serial, '-AdbPath', $AdbPath, '-IntervalSeconds', "$IntervalSeconds")
        $taskAction = New-ScheduledTaskAction -Execute $windowsPowerShell -Argument (($taskArguments | ForEach-Object { ConvertTo-NativeArgument $_ }) -join ' ')
        $user = [Security.Principal.WindowsIdentity]::GetCurrent().Name
        $trigger = New-ScheduledTaskTrigger -AtLogOn -User $user
        $principal = New-ScheduledTaskPrincipal -UserId $user -LogonType Interactive -RunLevel Limited
        $settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable `
            -ExecutionTimeLimit ([TimeSpan]::Zero) -MultipleInstances IgnoreNew -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1)
        Register-ScheduledTask -TaskName $taskName -Action $taskAction -Trigger $trigger -Principal $principal -Settings $settings `
            -Description 'Recover the optional Luma P6 remote Menu bridge while this Windows PC is awake.' -Force | Out-Null
        Start-ScheduledTask -TaskName $taskName
        Write-Output "Windows login recovery installed: $taskName"
    }
    'Uninstall' {
        try { Stop-Bridge } catch { Write-Warning $_.Exception.Message }
        Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue
        Write-Output 'Windows login recovery removed.'
    }
}
