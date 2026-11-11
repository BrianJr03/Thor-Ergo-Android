"""Source regression checks only: NOT PowerShell execution or parsing.

Runtime verification of the actual binary stdin pipe happens automatically on
Windows in Test-BinaryTransport before any device connection. No password is used.
"""
from pathlib import Path
import re, unittest
ROOT=Path(__file__).resolve().parents[1]
PS=(ROOT/'Installers/install-windows.ps1').read_text(encoding='ascii')
CMD=(ROOT/'Install-Windows.cmd').read_text(encoding='ascii')

class WindowsSourceTests(unittest.TestCase):
    def test_no_unsupported_input_encoding(self):self.assertNotRegex(PS,r'\.StandardInputEncoding\s*=')
    def test_no_modern_only_argument_list(self):self.assertNotIn('.ArgumentList',PS)
    def test_no_async_process_wait_api(self):self.assertNotIn('WaitForExitAsync',PS)
    def test_process_properties_compatible_with_framework(self):
        props=set(re.findall(r'\$(?:Info|ProbeInfo)\.(\w+)\s*=',PS))
        self.assertLessEqual(props,{'FileName','Arguments','UseShellExecute','RedirectStandardInput','RedirectStandardOutput','RedirectStandardError','CreateNoWindow'})
    def test_pipe_sends_bytes_not_text(self):
        self.assertIn('$Pipe = $Process.StandardInput.BaseStream',PS);self.assertIn('$File.CopyTo($Pipe)',PS)
        self.assertNotRegex(PS,r'\$Process.StandardInput\.(Write|WriteLine)\(')
    def test_bom_free_console_before_start(self):
        self.assertLess(PS.index('[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)'),PS.index('if (-not $Process.Start())'))
    def test_binary_preflight_is_called(self):self.assertIn('    Test-BinaryTransport\n',PS)
    def test_probe_checks_hash(self):self.assertIn('$Result -cne $Expected',PS);self.assertIn('$ProbeProcess.StandardInput.BaseStream.Write',PS)
    def test_hashes_are_checked(self):self.assertIn("Get-VerifiedHash $Archive 'Payload/Thor.zip'",PS);self.assertIn("Get-VerifiedHash $Bootstrap 'Thor/transfer.py'",PS)
    def test_ip_uses_numeric_parser(self):self.assertIn('[System.Net.IPAddress]::TryParse',PS)
    def test_no_automatic_hostkey_accept(self):
        self.assertIn('StrictHostKeyChecking=ask',PS);self.assertNotIn('StrictHostKeyChecking=no',PS);self.assertNotIn('accept-new',PS)
    def test_no_password_argument(self):self.assertNotRegex(PS,r'(?i)(-pw |PasswordInClearText|sshpass)')
    def test_no_password_read_host(self):
        lines=[line for line in PS.splitlines() if 'Read-Host' in line]
        self.assertFalse(any('password' in line.lower() for line in lines))
    def test_exit_code_preserved(self):self.assertIn('$ExitCode = $Process.ExitCode',PS);self.assertIn('exit $ExitCode',PS)
    def test_restore_console_in_finally(self):self.assertIn('[Console]::InputEncoding=$SavedInputEncoding',PS);self.assertIn('[Console]::OutputEncoding=$SavedOutputEncoding',PS)
    def test_wrapper_quotes_path(self):self.assertIn('"%~dp0Installers\\install-windows.ps1"',CMD)
    def test_wrapper_disables_delayed_expansion(self):self.assertIn('DisableDelayedExpansion',CMD)
    def test_wrapper_returns_child_code(self):self.assertIn('exit /b %errorlevel%',CMD)
    def test_minimum_ps_version(self):self.assertIn('#requires -Version 5.1',PS)
    def test_no_host_archive_rebuild(self):self.assertNotIn('Compress-Archive',PS);self.assertNotIn('CreateFromDirectory',PS)

if __name__=='__main__':unittest.main(verbosity=1)
