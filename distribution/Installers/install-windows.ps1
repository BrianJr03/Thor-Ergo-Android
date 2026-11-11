#requires -Version 5.1
# Windows 10/11 host installer. Passwords are read by Windows OpenSSH, not here.
# Compatible with Windows PowerShell 5.1 / .NET Framework: no modern-only APIs.
[CmdletBinding()]
param([switch]$NoPause,[switch]$AynDiagnostic)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0
$Root = Split-Path -Parent $PSScriptRoot
$ExitCode = 1
$Process = $null
$ProcessStarted = $false
$File = $null
$Pipe = $null
$SavedInputEncoding = $null
$SavedOutputEncoding = $null
function Quote-NativeArgument([string]$Value) {
    # Microsoft C argv quoting. No cmd.exe or Invoke-Expression is involved.
    if ($Value -notmatch '[\s"]' -and $Value.Length -gt 0) { return $Value }
    $Value = [regex]::Replace($Value, '(\\*)"', '$1$1\"')
    $Value = [regex]::Replace($Value, '(\\+)$', '$1$1')
    return '"' + $Value + '"'
}
function Get-VerifiedHash([string]$Path, [string]$Name, [string[]]$Manifest) {
    $Prefix = '^[0-9a-f]{64}  ' + [regex]::Escape($Name) + '$'
    $MatchesForFile = @($Manifest | Where-Object { $_ -cmatch $Prefix })
    if ($MatchesForFile.Count -ne 1) { throw "Invalid release checksum entry: $Name" }
    $Expected = $MatchesForFile[0].Substring(0,64)
    $Actual = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($Actual -cne $Expected) { throw "Package checksum mismatch: $Name. Extract a fresh complete ZIP." }
    return $Actual
}
function Test-BinaryTransport {
    # Test the same raw-byte pipe locally BEFORE connecting to any device.
    # This catches .NET/encoding/BOM/argument problems on the actual Windows host.
    $ChildExe = Join-Path $PSHOME 'powershell.exe'
    if (-not (Test-Path -LiteralPath $ChildExe -PathType Leaf)) {
        $ChildExe = Join-Path $PSHOME 'pwsh.exe'
    }
    if (-not (Test-Path -LiteralPath $ChildExe -PathType Leaf)) { throw 'Cannot locate this PowerShell runtime.' }
    $ChildScript = @'
$ErrorActionPreference='Stop'
try {
    $InputStream=[Console]::OpenStandardInput()
    $Buffer=New-Object System.IO.MemoryStream
    $InputStream.CopyTo($Buffer)
    $Hasher=[System.Security.Cryptography.SHA256]::Create()
    $Hash=$Hasher.ComputeHash($Buffer.ToArray())
    [Console]::Out.WriteLine([BitConverter]::ToString($Hash).Replace('-','').ToLowerInvariant())
    $Hasher.Dispose();$Buffer.Dispose();$InputStream.Dispose()
    exit 0
} catch { [Console]::Error.WriteLine($_.Exception.Message); exit 1 }
'@
    $Encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($ChildScript))
    [byte[]]$Probe = New-Object byte[] 1024
    for ($Index=0; $Index -lt $Probe.Length; $Index++) { $Probe[$Index]=[byte]($Index % 256) }
    $Hasher = [Security.Cryptography.SHA256]::Create()
    try { $Expected=[BitConverter]::ToString($Hasher.ComputeHash($Probe)).Replace('-','').ToLowerInvariant() }
    finally { $Hasher.Dispose() }
    $ProbeInfo=New-Object System.Diagnostics.ProcessStartInfo
    $ProbeInfo.FileName=$ChildExe
    $ProbeInfo.Arguments='-NoLogo -NoProfile -NonInteractive -EncodedCommand '+$Encoded
    $ProbeInfo.UseShellExecute=$false
    $ProbeInfo.RedirectStandardInput=$true
    $ProbeInfo.RedirectStandardOutput=$true
    $ProbeInfo.RedirectStandardError=$true
    $ProbeInfo.CreateNoWindow=$true
    $ProbeProcess=New-Object System.Diagnostics.Process
    $ProbeProcess.StartInfo=$ProbeInfo
    $Started=$false
    try {
        if (-not $ProbeProcess.Start()) { throw 'Local transfer preflight could not start.' }
        $Started=$true
        $ProbeProcess.StandardInput.BaseStream.Write($Probe,0,$Probe.Length)
        $ProbeProcess.StandardInput.BaseStream.Close()
        if (-not $ProbeProcess.WaitForExit(15000)) { throw 'Local transfer preflight timed out.' }
        $Result=$ProbeProcess.StandardOutput.ReadToEnd().Trim()
        $Detail=$ProbeProcess.StandardError.ReadToEnd().Trim()
        if ($ProbeProcess.ExitCode -ne 0 -or $Result -cne $Expected) {
            throw ('Local binary transport verification failed. No SSH connection was made. '+$Detail)
        }
    } finally {
        if ($Started) { try { if (-not $ProbeProcess.HasExited) { $ProbeProcess.Kill(); [void]$ProbeProcess.WaitForExit(5000) } } catch { } }
        $ProbeProcess.Dispose()
    }
    Write-Host 'Windows binary transport preflight: OK.'
}
try {
    Write-Host "`nThor Ergo 1.0 - independent AYN Thor / ArmadaOS distribution"
    $Ssh = Get-Command ssh.exe -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $Ssh) { throw 'Windows OpenSSH Client is required. Enable this Windows optional feature, then rerun.' }
    $SavedInputEncoding = [Console]::InputEncoding
    $SavedOutputEncoding = [Console]::OutputEncoding
    [Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
    [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
    Test-BinaryTransport
    $Archive = Join-Path $Root 'Payload\Thor.zip'
    $Bootstrap = Join-Path $Root 'Thor\transfer.py'
    $ManifestPath = Join-Path $PSScriptRoot 'payload.sha256'
    foreach ($Path in @($Archive,$Bootstrap,$ManifestPath)) {
        if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
            throw 'Extract the ENTIRE release folder first. Do not run inside the ZIP or move the launcher alone.'
        }
    }
    $Manifest = @(Get-Content -LiteralPath $ManifestPath -Encoding ASCII)
    $Sha = Get-VerifiedHash $Archive 'Payload/Thor.zip' $Manifest
    $null = Get-VerifiedHash $Bootstrap 'Thor/transfer.py' $Manifest
    $Size = (Get-Item -LiteralPath $Archive).Length
    if ($Size -lt 1 -or $Size -gt 67108864) { throw 'Invalid payload size (maximum 64 MiB).' }
    Write-Host 'Complete payload and SSH receiver: checksums OK.'
    if ($AynDiagnostic) {
        $Action='diagnostics'
        Write-Host 'AYN read-only diagnosis selected. No app or display changes.'
    } else {
        Write-Host '1 Install / update   2 Undo   3 Diagnostics   4 Wake lower screen'
        $Choice = Read-Host 'Operation [1]'
        switch ($Choice) {
            '' {$Action='install'} '1' {$Action='install'} '2' {$Action='undo'}
            '3' {$Action='diagnostics'} '4' {$Action='wake'} default {throw 'Invalid operation.'}
        }
    }
    $Address = (Read-Host 'Thor local IP address').Trim()
    if ($Address.StartsWith('[') -and $Address.EndsWith(']')) { $Address=$Address.Substring(1,$Address.Length-2) }
    # Strict dotted decimal for IPv4; IPAddress.TryParse also accepts legacy
    # numbers such as 127.1, hex and octal, which must not pass as local IP input.
    if ($Address.IndexOf(':') -lt 0) {
        if ($Address -notmatch '^(0|[1-9][0-9]{0,2})(\.(0|[1-9][0-9]{0,2})){3}$') {
            throw 'Enter a valid numeric IPv4 or IPv6 address.'
        }
    } elseif ($Address -notmatch '^[0-9a-fA-F:.]+(%[a-zA-Z0-9_.-]+)?$') {
        throw 'Enter a valid numeric IPv4 or IPv6 address.'
    }
    $ParsedAddress = $null
    if (-not [System.Net.IPAddress]::TryParse($Address,[ref]$ParsedAddress)) {
        throw 'Enter a valid numeric IPv4 or IPv6 address.'
    }
    $Address = $ParsedAddress.ToString()
    $User = (Read-Host 'SSH user [armada]').Trim()
    if ([string]::IsNullOrWhiteSpace($User)) { $User='armada' }
    if ($User -notmatch '^[a-zA-Z0-9_][a-zA-Z0-9_.-]*$') { throw 'Invalid SSH username.' }
    $PortText = (Read-Host 'SSH port [22]').Trim()
    if ([string]::IsNullOrWhiteSpace($PortText)) { $PortText='22' }
    $Port = 0
    if ($PortText -notmatch '^[0-9]{1,5}$' -or -not [int]::TryParse($PortText,[ref]$Port) -or $Port -lt 1 -or $Port -gt 65535) {
        throw 'SSH port must be between 1 and 65535.'
    }
    $Boot = [Convert]::ToBase64String([IO.File]::ReadAllBytes($Bootstrap))
    $Python = 'exec(__import__("base64").b64decode("'+$Boot+'"))'
    $Remote = "python3 -B -c '$Python' zip $Sha $Size $Action"
    $Arguments = @('-T','-o','StrictHostKeyChecking=ask','-o','BatchMode=no','-o','ConnectTimeout=12',
        '-o','ServerAliveInterval=15','-o','ServerAliveCountMax=3','-o','ForwardAgent=no',
        '-o','ForwardX11=no','-o','ClearAllForwardings=yes','-p',"$Port",("$User@$Address"),$Remote)
    $Info = New-Object System.Diagnostics.ProcessStartInfo
    $Info.FileName = $Ssh.Source
    $Info.Arguments = ($Arguments | ForEach-Object { Quote-NativeArgument $_ }) -join ' '
    $Info.UseShellExecute = $false
    $Info.RedirectStandardInput = $true
    $Info.CreateNoWindow = $false
    # .NET Framework derives the stdin StreamWriter encoding from Console.
    # Set BOM-free UTF-8 BEFORE Start(): even AutoFlush can emit a preamble.
    # The archive itself is copied as raw bytes, never through a text pipeline.
    Write-Host "`nKeep the Thor awake, with games closed."
    Write-Host 'TOP screen: Steam Game Mode started and unlocked.'
    Write-Host 'BOTTOM screen: Plasma Mobile must be enabled in Armada Control on the top screen.'
    Write-Host 'Do not switch the whole Thor to Desktop Mode. Diagnostics does not change the app.'
    Write-Host 'SSH password (default: "armada"): enter it in the OpenSSH prompt.'
    Write-Host 'The password is not saved. Verify any new SSH host fingerprint.'
    Write-Host 'Changed host keys are NOT automatically accepted or removed.'
    $File = [IO.File]::OpenRead($Archive)
    $Process = New-Object System.Diagnostics.Process
    $Process.StartInfo = $Info
    if (-not $Process.Start()) { throw 'Could not start OpenSSH.' }
    $ProcessStarted = $true
    $Pipe = $Process.StandardInput.BaseStream
    $File.CopyTo($Pipe)
    $File.Dispose(); $File=$null
    $Pipe.Close(); $Pipe=$null
    # Small waits keep cancellation responsive while the Thor performs tests.
    while (-not $Process.WaitForExit(250)) { }
    $ExitCode = $Process.ExitCode
    if ($ExitCode -eq 0) { Write-Host "`nOperation completed." }
    else { Write-Host "`nOperation failed (code $ExitCode). Read the OpenSSH/Thor message above." }
} catch {
    Write-Host ("ERROR: "+$_.Exception.Message)
    Write-Host 'No automatic retry. Keep the complete folder and read the message above.'
    $ExitCode=1
} finally {
    if ($File) { try { $File.Dispose() } catch { } }
    if ($Pipe) { try { $Pipe.Close() } catch { } }
    if ($Process) {
        if ($ProcessStarted) {
            try { if (-not $Process.HasExited) { $Process.Kill(); [void]$Process.WaitForExit(5000) } } catch { }
        }
        try { $Process.Dispose() } catch { }
    }
    if ($null -ne $SavedInputEncoding) { try { [Console]::InputEncoding=$SavedInputEncoding } catch { } }
    if ($null -ne $SavedOutputEncoding) { try { [Console]::OutputEncoding=$SavedOutputEncoding } catch { } }
    if (-not $NoPause) { try { [void](Read-Host 'Press Enter to close') } catch { } }
}
exit $ExitCode
