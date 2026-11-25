"""Conservative compile check against a hash-verified successful-build package.

No mtime or branch merge-base. Unknown provenance/closure is SKIPPED (2),
never a source failure. Tests and dependency jars come from the same package.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import time


def say(message):
    print('[precheck] ' + message, flush=True)


def skip(reason):
    say('PRECHECK OVERGESLAGEN (' + reason + '); volledige Gradle-compile beslist')
    return 2


def long_path(path):
    # 25-11 Claude: Kotlin test classes ('...Tests$departure reduces ... $$inlined$groupingBy$1.class') under
    # build/precheck-pull.XXXX exceed MAX_PATH 260 -> Errno 2 -> baseline never published -> precheck always skipped.
    # 06:3x: the baseline check (is_file) needs the same prefix, not only the hashing.
    name = str(Path(path).resolve())
    if os.name == 'nt' and not name.startswith('\\\\?\\'):
        name = '\\\\?\\' + name
    return name


def digest(path):
    with open(long_path(path), 'rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def source_files(root):
    # Same scope as the exporter, including ignored module build.gradle files.
    result = {}
    for base, dirs, files in os.walk(root):
        dirs[:] = [d for d in dirs if d not in {'build', '.git', '.gradle', 'file-server', '.codegraph', 'graphify-out'}]
        for name in files:
            p = Path(base, name)
            rel = p.relative_to(root).as_posix()
            if ('/src/' in '/' + rel and p.suffix in {'.kt', '.kts', '.java'}) or name.endswith(('.gradle', '.gradle.kts')) or rel == 'gradle.properties' or rel.startswith('gradle/'):
                result[rel] = digest(p)
    return result


def run():
    started = time.monotonic()
    root = Path(subprocess.check_output(['git', 'rev-parse', '--show-toplevel'], text=True).strip())
    candidates = [Path(os.environ['PRECHECK_BASELINE'])] if os.environ.get('PRECHECK_BASELINE') else [root, Path('C:/RSPS/game/game')]
    package = None
    for candidate in candidates:
        if (candidate / 'manifest.json').is_file():
            package = candidate
            break
        pointer = candidate / 'build/precheck-baseline.path'
        if pointer.is_file():
            package = candidate / pointer.read_text().strip()
            break
    if package is None:
        return skip('geen manifest van een geslaagde build; oude losse jars/testklassen worden niet gebruikt')
    m = json.loads((package / 'manifest.json').read_text())
    if m.get('version') != 1:
        return skip('onbekende manifestversie')
    for name, expected in m['artifacts'].items():
        p = package / name
        if not os.path.isfile(long_path(p)) or digest(p) != expected:
            return skip('baseline artifact ontbreekt of hash wijkt af: ' + name)
    for name in m['jars'] + m['compiler']:
        if name not in m['artifacts']:
            return skip('classpath zonder artifactbewijs: ' + name)
    current = source_files(root)
    changed = sorted(p for p in current.keys() | m['sources'].keys() if current.get(p) != m['sources'].get(p))
    say('baseline=' + str(package) + ' snapshot=' + m['snapshot'])
    if not changed:
        say('PRECHECK OK (broninhoud gelijk aan geslaagde build)')
        return 0
    for f in changed:
        if f not in current or not f.endswith('.kt'):
            return skip('verwijderd bestand, script of buildinput vereist volledige compile: ' + f)
        if not f.startswith(('game/src/', 'game/plugins/src/')):
            return skip('gewijzigde module buiten game/plugins: ' + f)
    # Compile all changed .kt sources in module order. Include CURRENT test fixtures
    # in each selected package to avoid stale internal N11Fixture shadowing.
    groups = [('game-main', 'game/src/main/', ':game'),
              ('plugins-main', 'game/plugins/src/main/', ':game:plugins'),
              ('game-test', 'game/src/test/', ':game'),
              ('plugins-test', 'game/plugins/src/test/', ':game:plugins')]
    java = os.environ.get('JAVA_HOME')
    java = str(Path(java, 'bin/java.exe')) if java else 'java'
    compiler_cp = os.pathsep.join(str((package / p).resolve()) for p in m['compiler'])
    jars = [str((package / p).resolve()) for p in m['jars']]
    new_outputs = []
    with tempfile.TemporaryDirectory(prefix='rsps-precheck-') as tmp:
        for group, prefix, module in groups:
            selected = [p for p in changed if p.startswith(prefix)]
            if not selected:
                continue
            cp = list(new_outputs) + jars
            friends = [p for p in jars if p.endswith(('-game-0.0.4.jar', '-plugins-0.0.4.jar'))] + new_outputs
            if group.endswith('-test'):
                if module not in m['tests']:
                    return skip('geen testklassen uit dezelfde build voor ' + module)
                testdir = str((package / m['tests'][module]).resolve())
                cp.insert(0, testdir)
                friends.append(testdir)
                # Entire package, including shared internal helpers, from current source.
                parents = {str(Path(p).parent).replace('\\', '/') for p in selected}
                selected = sorted(p for p in current if p.endswith('.kt') and str(Path(p).parent).replace('\\', '/') in parents)
            options = m['modules'][module]
            output = str(Path(tmp, group))
            args = [java, '-Xmx2g', '-cp', compiler_cp, 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
                    '-no-stdlib', '-no-reflect', '-jvm-target', options['jvmTarget'],
                    '-module-name', module.rsplit(':', 1)[-1], '-Xfriend-paths=' + ','.join(friends),
                    '-cp', os.pathsep.join(cp), '-d', output]
            args += options['args']
            for option in ('languageVersion', 'apiVersion'):
                if options.get(option):
                    args += ['-language-version' if option == 'languageVersion' else '-api-version', options[option]]
            # Argfile also avoids Windows' command-line limit on fixture packages.
            argfile = Path(tmp, group + '.args')
            argfile.write_text('\n'.join('"' + str((root / p).resolve()).replace('\\', '/') + '"' for p in selected))
            args.append('@' + str(argfile))
            result = subprocess.run(args, capture_output=True, text=True, timeout=max(1, 180 - (time.monotonic() - started)))
            log = result.stdout + result.stderr
            if result.returncode:
                say(log[-12000:])
                # Unresolved dependencies and compiler/tool failures are ambiguous.
                # Only unequivocal parse/type diagnostics are allowed to block Gradle.
                source_error = re.search(r'error: (type mismatch|expecting |unexpected tokens|the integer literal does not conform|a .return. expression required)', log, re.I)
                uncertain = re.search(r'unresolved reference|cannot access|cannot find|NoClassDefFound|Exception|OutOfMemory', log, re.I)
                if source_error and not uncertain:
                    say('PRECHECK FOUT (' + group + '): aantoonbare bronfout')
                    return 1
                return skip('compilerresultaat niet betrouwbaar aan bron toe te schrijven: ' + group)
            say(group + ': ' + str(len(selected)) + ' bestanden schoon')
            new_outputs.insert(0, output)
    say('PRECHECK OK (' + str(len(changed)) + ' gewijzigde bestanden; Gradle controleert ook afhankelijke bronnen)')
    return 0


if __name__ == '__main__':
    try:
        if len(sys.argv) == 4 and sys.argv[1] == '--publish-pulled':
            stage = Path(sys.argv[2]).resolve()
            manifest = json.loads((stage / 'manifest.json').read_text())
            if manifest['snapshot'] != sys.argv[3]:
                raise ValueError('snapshot mismatch')
            for name, expected in manifest['artifacts'].items():
                artifact = (stage / name).resolve()
                if not artifact.is_relative_to(stage) or digest(artifact) != expected:
                    raise ValueError('artifact mismatch: ' + name)
            root = Path(subprocess.check_output(['git', 'rev-parse', '--show-toplevel'], text=True).strip())
            dest = root / 'build/precheck-baselines' / stage.name
            dest.parent.mkdir(parents=True, exist_ok=True)
            stage.rename(dest)
            pointer = root / 'build/precheck-baseline.path.tmp'
            pointer.write_text(dest.relative_to(root).as_posix() + '\n')
            pointer.replace(root / 'build/precheck-baseline.path')
            say('baselinepakket geverifieerd en gepubliceerd: ' + manifest['snapshot'])
            sys.exit(0)
        sys.exit(run())
    except (OSError, ValueError, KeyError, subprocess.SubprocessError) as error:
        sys.exit(skip(str(error)))
