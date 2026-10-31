"""Exercise the real launcher build boundary without starting a service or using secrets."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]


class StartLocalJavaTests(unittest.TestCase):
    @unittest.skipUnless(os.name == 'nt', 'Windows PowerShell launcher contract')
    def test_explicit_java_home_is_passed_to_maven_not_only_to_runtime(self):
        java_home = os.environ.get('LAUNCHER_TEST_JAVA_HOME', os.environ.get('JAVA_HOME', ''))
        self.assertTrue((Path(java_home) / 'bin/java.exe').is_file(), 'Set LAUNCHER_TEST_JAVA_HOME to a JDK21+ for this test')
        with tempfile.TemporaryDirectory() as directory:
            fixture = Path(directory)
            script = fixture / 'scripts/competition/start-local.ps1'
            script.parent.mkdir(parents=True)
            shutil.copyfile(ROOT / 'scripts/competition/start-local.ps1', script)
            (fixture / 'ai-learning-backend').mkdir()
            probe = fixture / 'maven-environment.json'
            launcher = fixture / 'probe.ps1'
            launcher.write_text('''param([string]$Script,[string]$SelectedHome,[string]$Probe)
$ErrorActionPreference='Stop'
$env:JAVA_HOME='C:\\not-the-selected-jdk'
function mvn {
    @{javaHome=$env:JAVA_HOME;arguments=@($args)} | ConvertTo-Json | Set-Content -LiteralPath $Probe -Encoding UTF8
    throw 'TEST_BUILD_BOUNDARY_REACHED'
}
try { & $Script -JavaRuntimeHome $SelectedHome }
catch { if ($_.Exception.Message -ne 'TEST_BUILD_BOUNDARY_REACHED') { throw } }
''', encoding='utf-8')
            result = subprocess.run(['powershell', '-NoProfile', '-NonInteractive', '-File', str(launcher),
                                     '-Script', str(script), '-SelectedHome', java_home, '-Probe', str(probe)],
                                    capture_output=True, text=True, timeout=30)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertTrue(probe.is_file(), result.stdout)
            actual = json.loads(probe.read_text(encoding='utf-8-sig'))
            self.assertEqual(str(Path(java_home).resolve()).casefold(), actual['javaHome'].casefold())
            self.assertEqual(['-DskipTests', 'package'], actual['arguments'])


if __name__ == '__main__':
    unittest.main()
