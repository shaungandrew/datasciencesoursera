$ErrorActionPreference = "Continue"
$base = Split-Path -Parent $MyInvocation.MyCommand.Path
$exe = Join-Path $base "AAII_MOOC_PC.exe"
$desktop = [Environment]::GetFolderPath("Desktop")
$out = Join-Path $desktop "AAII_MOOC_PC_SYSTEM_DIAG.txt"
$start = Get-Date

"AAII MOOC PC V1.1.5 SYSTEM WATCHDOG" | Set-Content $out
("Start: " + $start.ToString("yyyy-MM-dd HH:mm:ss")) | Add-Content $out
("OS: " + [Environment]::OSVersion) | Add-Content $out
("64-bit OS: " + [Environment]::Is64BitOperatingSystem) | Add-Content $out
("Executable: " + $exe) | Add-Content $out
"" | Add-Content $out

try {
    $p = Start-Process -FilePath $exe -WorkingDirectory $base -PassThru
    ("PID: " + $p.Id) | Add-Content $out
    $p.WaitForExit()
    $exit = $p.ExitCode
    ("Exit: " + (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")) | Add-Content $out
    ("ExitCode decimal: " + $exit) | Add-Content $out
    ("ExitCode hex: 0x{0:X8}" -f ([uint32]$exit)) | Add-Content $out
}
catch {
    ("Start/Wait error: " + $_.Exception.ToString()) | Add-Content $out
}

"" | Add-Content $out
"===== Recent Application Error / .NET Runtime / WER events =====" | Add-Content $out
try {
    Get-WinEvent -FilterHashtable @{LogName='Application'; StartTime=$start.AddMinutes(-1)} -ErrorAction Stop |
      Where-Object {
        $_.ProviderName -in @('Application Error','.NET Runtime','Windows Error Reporting') -or
        $_.Message -match 'AAII_MOOC_PC'
      } |
      Select-Object TimeCreated, Id, LevelDisplayName, ProviderName, Message |
      Format-List | Out-String -Width 240 | Add-Content $out
}
catch {
    ("Application event log read error: " + $_.Exception.Message) | Add-Content $out
}

"" | Add-Content $out
"===== Recent Windows Defender events =====" | Add-Content $out
try {
    Get-WinEvent -FilterHashtable @{
        LogName='Microsoft-Windows-Windows Defender/Operational'
        StartTime=$start.AddMinutes(-1)
    } -ErrorAction Stop |
      Where-Object { $_.Id -in @(1116,1117,1118,1121,5001,5004,5010) } |
      Select-Object TimeCreated, Id, LevelDisplayName, Message |
      Format-List | Out-String -Width 240 | Add-Content $out
}
catch {
    ("Defender event log read error: " + $_.Exception.Message) | Add-Content $out
}

"" | Add-Content $out
"===== Managed application log =====" | Add-Content $out
$managed = Join-Path $env:LOCALAPPDATA "AAIIMoocPC\crash.log"
if (Test-Path $managed) {
    Get-Content $managed -Tail 250 | Add-Content $out
} else {
    "No managed crash.log found." | Add-Content $out
}

try { Start-Process notepad.exe $out } catch {}
