[CmdletBinding()]
param(
    [ValidateSet('Check', 'Build', 'Devices', 'Connect')]
    [string]$Task = 'Check',
    [string]$DeviceAddress
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$localEnvironment = Join-Path $repoRoot '.local\environment.ps1'
if (Test-Path -LiteralPath $localEnvironment) { . $localEnvironment }

function Invoke-Adb {
    param([string[]]$AdbArguments)
    $adb = if ($env:LUMA_ADB) { $env:LUMA_ADB } else { (Get-Command adb -ErrorAction Stop).Source }
    & $adb @AdbArguments
    if ($LASTEXITCODE -ne 0) { throw "ADB failed with exit code $LASTEXITCODE." }
}

Push-Location $repoRoot
try {
    switch ($Task) {
        'Check' {
            & .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
            if ($LASTEXITCODE -ne 0) { throw "Build checks failed with exit code $LASTEXITCODE." }
        }
        'Build' {
            & .\gradlew.bat :app:assembleDebug
            if ($LASTEXITCODE -ne 0) { throw "Build failed with exit code $LASTEXITCODE." }
        }
        'Devices' { Invoke-Adb -AdbArguments @('devices', '-l') }
        'Connect' {
            if ([string]::IsNullOrWhiteSpace($DeviceAddress) -or
                $DeviceAddress -notmatch '^[a-zA-Z0-9][a-zA-Z0-9.-]*(?::[0-9]{1,5})?$') {
                throw 'Provide the projector IP address, optionally followed by :port.'
            }
            if ($DeviceAddress -notmatch ':') { $DeviceAddress += ':5555' }
            Invoke-Adb -AdbArguments @('connect', $DeviceAddress)
            Invoke-Adb -AdbArguments @('-s', $DeviceAddress, 'get-state')
            Invoke-Adb -AdbArguments @('devices', '-l')
        }
    }
}
finally { Pop-Location }
