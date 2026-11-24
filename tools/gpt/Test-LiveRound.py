"""Isolated dry-run, occupied lease and failed-build rollback proof. No live/network."""
from pathlib import Path
import subprocess
import tempfile

wrapper = Path(__file__).with_name('Invoke-LiveRound.ps1').resolve()
with tempfile.TemporaryDirectory(prefix='rsps-live-round-proof-') as tmp:
    root = Path(tmp)
    repo = root / 'repo'
    subprocess.run(['git', 'init', '-q', '-b', 'project48', str(repo)], check=True)
    def git(*args):
        return subprocess.check_output(['git', '-C', str(repo), '-c', 'user.name=LiveRoundProof', '-c', 'user.email=proof@localhost', *args], text=True).strip()
    (repo / 'a.txt').write_text('baseline\n')
    git('add', '.')
    git('commit', '-qm', 'baseline')
    baseline = git('rev-parse', 'HEAD')
    git('checkout', '-qb', 'source')
    (repo / 'a.txt').write_text('source\n')
    git('commit', '-qam', 'source')
    source = git('rev-parse', 'HEAD')
    git('checkout', '-q', 'project48')
    slot = root / 'trial-slot.txt'
    dossier = root / 'trial-dossier.txt'
    logs = root / 'logs'
    fail = root / 'fail.ps1'
    fail.write_text("Write-Output 'intentional red build'; exit 7\n")
    later = root / 'later.ps1'
    later.write_text("throw 'later phase must never run'\n")
    args = ['powershell.exe', '-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', str(wrapper),
            '-Repository', str(repo), '-ExpectedHead', baseline, '-SourceRef', 'source',
            '-SlotPath', str(slot), '-Dossier', str(dossier), '-LogDirectory', str(logs),
            '-BuildScript', str(fail), '-BootcheckScript', str(later), '-ReleaseScript', str(later)]
    def run(extra, expected):
        result = subprocess.run(args + extra, capture_output=True, text=True, timeout=30)
        print(result.stdout, result.stderr)
        assert result.returncode == expected, result.returncode
    run(['-DryRun'], 0)
    assert not slot.exists() and git('rev-parse', 'HEAD') == baseline
    slot.write_text('occupied-by-another-agent\n')
    run([], 1)
    assert slot.read_text() == 'occupied-by-another-agent\n'
    assert git('rev-parse', 'HEAD') == baseline
    slot.unlink()  # Only our own isolated fixture marker.
    run([], 1)
    assert git('rev-parse', 'HEAD') == baseline and (repo / 'a.txt').read_text() == 'baseline\n'
    assert slot.is_file() and 'token=' in slot.read_text(encoding='utf-8-sig')
    assert 'STOP LIVE-ROUND fase=build' in dossier.read_text(encoding='utf-8-sig')
    assert 'build exit 7;' in dossier.read_text(encoding='utf-8-sig')
    assert len(list(logs.glob('*build.out.log'))) == 1
    assert not list(logs.glob('*bootcheck*')) and not list(logs.glob('*release*'))
    slot.unlink()
    bare = root / 'remote.git'
    subprocess.run(['git', 'init', '--bare', '-q', str(bare)], check=True)
    git('remote', 'add', 'audit', str(bare))
    fail.write_text("Write-Output 'phase success'; exit 0\n")
    later.write_text("Write-Output 'phase success'; exit 0\n")
    run([], 0)
    assert not slot.exists() and git('rev-parse', 'HEAD') == source
    assert subprocess.check_output(['git', '--git-dir', str(bare), 'rev-parse', 'project48'], text=True).strip() == source
    print('LIVE ROUND PROOF PASS: dry-run, occupied lease, actual ff merge + red build rollback + retained marker + STOP')
